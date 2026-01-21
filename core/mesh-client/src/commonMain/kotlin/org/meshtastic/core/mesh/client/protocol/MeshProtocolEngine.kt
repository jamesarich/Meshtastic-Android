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
package org.meshtastic.core.mesh.client.protocol

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.mesh.client.protocol.ProtoFramer.RawFromRadio
import org.meshtastic.core.mesh.client.protocol.ProtoFramer.RawToRadio
import org.meshtastic.core.mesh.client.transport.TransportSpec

/**
 * High-level protocol engine bridging raw transport frames and protobuf messages. Responsible for framing, encoding,
 * and interpreting packets.
 */
interface MeshProtocolEngine {
    /** Currently active transport connection or null if disconnected. */
    val currentTransport: StateFlow<TransportSpec?>

    /** Stream of decoded messages coming from the transport layer. */
    val incomingMessages: Flow<RawFromRadio>

    /** Parsed FromRadio messages, when available on this platform. */
    val decodedMessages: Flow<FromRadioMessage>

    /** Send a command through the current transport. */
    suspend fun send(commandBytes: RawToRadio)
}
