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

import kotlinx.coroutines.flow.StateFlow

/** Core interface for all radio transport implementations. Provides low-level communication with Meshtastic devices. */
interface IRadioTransport {
    /** Current connection state of this transport. */
    val isConnected: StateFlow<Boolean>

    /**
     * Send raw bytes to the radio device.
     *
     * @param data The protobuf-encoded packet to send
     */
    suspend fun send(data: ByteArray)

    /**
     * Keep the connection alive. Called periodically to detect zombie connections. Implementations can send ping
     * packets or check connection health.
     */
    suspend fun keepAlive() {}

    /** Close the transport and release all resources. */
    suspend fun close()
}
