/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.common.util

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSLocale
import platform.Foundation.NSURLComponents
import platform.Foundation.currentLocale
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.usesMetricSystem
import kotlin.math.abs

actual object BuildUtils {
    actual val isEmulator: Boolean = false
    actual val sdkInt: Int = 0
}

// region CommonUri — real implementation backed by NSURLComponents

actual class CommonUri(
    actual val host: String?,
    actual val fragment: String?,
    actual val pathSegments: List<String>,
    private val queryItems: Map<String, String>,
    private val rawString: String,
) {
    actual fun getQueryParameter(key: String): String? = queryItems[key]

    actual fun getBooleanQueryParameter(key: String, defaultValue: Boolean): Boolean {
        val value = getQueryParameter(key) ?: return defaultValue
        return value != "false" && value != "0"
    }

    actual override fun toString(): String = rawString

    actual companion object {
        actual fun parse(uriString: String): CommonUri {
            val components = NSURLComponents(string = uriString)
            val host = components.host
            val fragment = components.fragment
            val path = components.path.orEmpty()
            val pathSegments = path.split('/').filter { it.isNotBlank() }
            val queryItems = mutableMapOf<String, String>()
            components.queryItems?.forEach { item ->
                val qi = item as platform.Foundation.NSURLQueryItem
                queryItems[qi.name] = qi.value.orEmpty()
            }
            return CommonUri(host, fragment, pathSegments, queryItems, uriString)
        }
    }
}

actual fun CommonUri.toPlatformUri(): Any = platform.Foundation.NSURL(string = this.toString()) as Any

// endregion

// region DateFormatter — real implementation using NSDateFormatter

private const val MINUTE_MILLIS = 60_000L
private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 86_400_000L
private const val SECONDS_PER_MILLI = 0.001

private fun Long.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this * SECONDS_PER_MILLI)

actual object DateFormatter {
    private fun shortTimeFormatter(): NSDateFormatter = NSDateFormatter().apply {
        dateStyle = platform.Foundation.NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }

    private fun mediumTimeFormatter(): NSDateFormatter = NSDateFormatter().apply {
        dateStyle = platform.Foundation.NSDateFormatterNoStyle
        timeStyle = NSDateFormatterMediumStyle
    }

    private fun shortDateFormatter(): NSDateFormatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeStyle = platform.Foundation.NSDateFormatterNoStyle
    }

    private fun shortDateMediumTimeFormatter(): NSDateFormatter = NSDateFormatter().apply {
        dateStyle = NSDateFormatterShortStyle
        timeStyle = NSDateFormatterMediumStyle
    }

    actual fun formatRelativeTime(timestampMillis: Long): String {
        val deltaMillis = nowMillis - timestampMillis
        val absDeltaMillis = abs(deltaMillis)
        val suffix = if (deltaMillis >= 0) "ago" else "from now"

        return when {
            absDeltaMillis < MINUTE_MILLIS -> if (deltaMillis >= 0) "just now" else "in a moment"
            absDeltaMillis < HOUR_MILLIS -> "${absDeltaMillis / MINUTE_MILLIS}m $suffix"
            absDeltaMillis < DAY_MILLIS -> "${absDeltaMillis / HOUR_MILLIS}h $suffix"
            else -> "${absDeltaMillis / DAY_MILLIS}d $suffix"
        }
    }

    actual fun formatDateTime(timestampMillis: Long): String =
        shortDateMediumTimeFormatter().stringFromDate(timestampMillis.toNSDate())

    actual fun formatShortDate(timestampMillis: Long): String {
        val isWithin24Hours = (nowMillis - timestampMillis) <= DAY_MILLIS
        val date = timestampMillis.toNSDate()
        return if (isWithin24Hours) {
            shortTimeFormatter().stringFromDate(date)
        } else {
            shortDateFormatter().stringFromDate(date)
        }
    }

    actual fun formatTime(timestampMillis: Long): String =
        shortTimeFormatter().stringFromDate(timestampMillis.toNSDate())

    actual fun formatTimeWithSeconds(timestampMillis: Long): String =
        mediumTimeFormatter().stringFromDate(timestampMillis.toNSDate())

    actual fun formatDate(timestampMillis: Long): String =
        shortDateFormatter().stringFromDate(timestampMillis.toNSDate())

    actual fun formatDateTimeShort(timestampMillis: Long): String =
        shortDateMediumTimeFormatter().stringFromDate(timestampMillis.toNSDate())
}

// endregion

// region Measurement system

actual fun getSystemMeasurementSystem(): MeasurementSystem {
    val usesMetric = NSLocale.currentLocale.usesMetricSystem
    return if (usesMetric) MeasurementSystem.METRIC else MeasurementSystem.IMPERIAL
}

// endregion

// region IP validation

actual fun String?.isValidAddress(): Boolean {
    val value = this?.trim()
    return when {
        value.isNullOrEmpty() -> false
        value == "localhost" -> true
        IPV4_PATTERN.matches(value) -> value.split('.').all { segment -> segment.toIntOrNull() in 0..MAX_IPV4_SEGMENT }
        value.contains(':') -> true // Accept IPv6 syntax on iOS
        else -> DOMAIN_PATTERN.matches(value)
    }
}

private val IPV4_PATTERN = Regex("^(?:\\d{1,3}\\.){3}\\d{1,3}$")
private val DOMAIN_PATTERN = Regex("^(?=.{1,253}$)(?:(?!-)[A-Za-z0-9-]{1,63}(?<!-)\\.)+[A-Za-z]{2,63}$")
private const val MAX_IPV4_SEGMENT = 255

// endregion

// region Parcelable — no-op on iOS (no Parcelable concept)

actual interface CommonParcelable

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
actual annotation class CommonParcelize actual constructor()

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
actual annotation class CommonIgnoredOnParcel actual constructor()

actual interface CommonParceler<T> {
    actual fun create(parcel: CommonParcel): T

    actual fun T.write(parcel: CommonParcel, flags: Int)
}

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
@Repeatable
actual annotation class CommonTypeParceler<T, P : CommonParceler<in T>> actual constructor()

actual class CommonParcel {
    actual fun readString(): String? = null

    actual fun readInt(): Int = 0

    actual fun readLong(): Long = 0L

    actual fun readFloat(): Float = 0.0f

    actual fun createByteArray(): ByteArray? = null

    actual fun writeByteArray(b: ByteArray?) {}
}

// endregion
