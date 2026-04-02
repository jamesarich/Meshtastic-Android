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

import platform.Foundation.NSString
import platform.Foundation.stringWithFormat

/** Apple (iOS) implementation of string formatting using NSString.stringWithFormat. */
@Suppress("SpreadOperator")
actual fun formatString(pattern: String, vararg args: Any?): String {
    // NSString.stringWithFormat only supports a single vararg in Kotlin/Native interop.
    // We handle the common cases explicitly to avoid the interop limitation.
    return when (args.size) {
        0 -> pattern
        1 -> NSString.stringWithFormat(pattern, args[0])
        2 -> NSString.stringWithFormat(pattern, args[0], args[1])
        3 -> NSString.stringWithFormat(pattern, args[0], args[1], args[2])
        4 -> NSString.stringWithFormat(pattern, args[0], args[1], args[2], args[3])
        else -> {
            // Fallback: manual substitution for simple %s / %d / %f patterns
            var result = pattern
            for (arg in args) {
                result = result.replaceFirst(Regex("%[sdfSDF@]"), arg.toString())
            }
            result
        }
    }
}
