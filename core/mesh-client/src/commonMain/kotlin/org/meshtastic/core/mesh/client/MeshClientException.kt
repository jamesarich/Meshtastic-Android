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
package org.meshtastic.core.mesh.client

/** Base exception for mesh client errors. */
sealed class MeshClientException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    /** Connection to device failed */
    class ConnectionFailed(address: String, cause: Throwable? = null) :
        MeshClientException("Failed to connect to $address", cause)

    /** Device not found or address invalid */
    class DeviceNotFound(address: String) : MeshClientException("Device not found: $address")

    /** Protocol error during communication */
    class ProtocolError(message: String, cause: Throwable? = null) :
        MeshClientException("Protocol error: $message", cause)

    /** Operation timed out */
    class Timeout(operation: String) : MeshClientException("Operation timed out: $operation")

    /** Transport type not supported on this platform */
    class UnsupportedTransport(transportType: String) : MeshClientException("Transport not supported: $transportType")
}
