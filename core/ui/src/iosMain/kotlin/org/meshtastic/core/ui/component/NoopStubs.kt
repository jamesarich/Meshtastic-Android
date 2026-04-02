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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import org.meshtastic.core.common.util.nowMillis

private const val TICK_INTERVAL_MS = 60_000L

@Composable
actual fun rememberTimeTickWithLifecycle(): Long {
    var tick by remember { mutableLongStateOf(nowMillis) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TICK_INTERVAL_MS)
            tick = nowMillis
        }
    }
    return tick
}

/**
 * Kotlin/Native does not support Java reflection. We return an empty list as a fallback. The DropDownPreference
 * component will need to be refactored to pass enum entries explicitly from callers (which is the better KMP-clean
 * approach) rather than relying on reflection. This is tracked as a known limitation for the iOS target.
 */
internal actual fun <T : Enum<T>> enumEntriesOf(selectedItem: T): List<T> = emptyList()

/** Kotlin/Native has no annotation reflection; always returns false. */
internal actual fun Enum<*>.isDeprecatedEnumEntry(): Boolean = false
