/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.core.model.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaDate
import java.text.DateFormat
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val ONLINE_WINDOW_HOURS = 2

// return time if within 24 hours, otherwise date
fun getShortDate(time: Long): String? {
    if (time == 0L) return null
    val instant = Instant.fromEpochMilliseconds(time)
    val isWithin24Hours = Clock.System.now() - instant <= 1.days

    val date = instant.toJavaDate()
    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateInstance(DateFormat.SHORT).format(date)
    }
}

// return time if within 24 hours, otherwise date/time
fun getShortDateTime(time: Long): String {
    val instant = Instant.fromEpochMilliseconds(time)
    val isWithin24Hours = Clock.System.now() - instant <= 1.days

    val date = instant.toJavaDate()
    return if (isWithin24Hours) {
        DateFormat.getTimeInstance(DateFormat.SHORT).format(date)
    } else {
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(date)
    }
}

fun formatUptime(seconds: Int): String = formatUptime(seconds.toLong())

private fun formatUptime(seconds: Long): String {
    val duration = seconds.seconds
    val days = duration.inWholeDays
    val hours = duration.inWholeHours % 24
    val minutes = duration.inWholeMinutes % 60
    val secs = duration.inWholeSeconds % 60

    return listOfNotNull(
        "${days}d".takeIf { days > 0 },
        "${hours}h".takeIf { hours > 0 },
        "${minutes}m".takeIf { minutes > 0 },
        "${secs}s".takeIf { secs > 0 },
    )
        .joinToString(" ")
}

fun onlineTimeThreshold(): Int {
    val now = Clock.System.now()
    val threshold = now - ONLINE_WINDOW_HOURS.hours
    return threshold.epochSeconds.toInt()
}

/**
 * Calculates the remaining mute time in days and hours.
 *
 * @param remainingMillis The remaining time in milliseconds
 * @return Pair of (days, hours), where days is Int and hours is Double
 */
fun formatMuteRemainingTime(remainingMillis: Long): Pair<Int, Double> {
    if (remainingMillis <= 0) return Pair(0, 0.0)
    val duration = remainingMillis.milliseconds
    val days = duration.inWholeDays.toInt()
    val totalHours = duration.inWholeMilliseconds.toDouble() / 1.hours.inWholeMilliseconds
    val hours = totalHours % 24
    return Pair(days, hours)
}
