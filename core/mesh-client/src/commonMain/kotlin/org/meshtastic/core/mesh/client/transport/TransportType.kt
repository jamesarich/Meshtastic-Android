/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.mesh.client.transport

/** Supported transport types for connecting to Meshtastic devices. */
enum class TransportType(val id: Char) {
    /** Bluetooth Low Energy */
    BLE('b'),

    /** TCP/IP tunnel */
    TCP('t'),

    /** Serial/USB */
    SERIAL('s'),

    /** Unknown/unsupported */
    UNKNOWN('?'),
    ;

    companion object {
        /** Resolve a transport type for its identifier. */
        fun forIdChar(value: Char): TransportType? = entries.firstOrNull { it.id == value }
    }
}
