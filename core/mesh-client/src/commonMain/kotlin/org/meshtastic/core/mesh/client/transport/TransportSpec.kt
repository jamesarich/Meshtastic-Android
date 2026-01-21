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

/** Specification for a device transport, containing address and configuration. */
data class TransportSpec(val type: TransportType, val address: String) {
    companion object {
        /**
         * Parse a transport address string into a TransportSpec. Format: "{type}{address}" where type is a single char
         * from TransportType. Examples: "xAA:BB:CC:DD:EE:FF", "t192.168.1.100:4403", "m"
         */
        fun fromAddress(address: String): TransportSpec? = address
            .takeIf { it.isNotEmpty() }
            ?.let { value ->
                val type = TransportType.forIdChar(value.first()) ?: return null
                TransportSpec(type, value.substring(1))
            }

        /** Create a transport address string from type and address. */
        fun toAddress(type: TransportType, address: String): String = "${type.id}$address"
    }

    /** Convert this spec to an address string. */
    fun toAddress(): String = toAddress(type, address)
}
