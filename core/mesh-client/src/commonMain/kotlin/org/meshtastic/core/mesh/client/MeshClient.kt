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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.mesh.client.connection.ConnectionState
import org.meshtastic.core.mesh.client.protocol.FromRadioMessage
import org.meshtastic.core.mesh.client.protocol.MeshProtocolEngine
import org.meshtastic.core.mesh.client.protocol.ProtoFramer.RawFromRadio

/**
 * High-level client façade that exposes stateful updates and incoming packets to consumers (UI or platform-specific
 * services).
 */
class MeshClient(private val scope: CoroutineScope, private val protocolEngine: MeshProtocolEngine) {
    val incomingMessages: Flow<RawFromRadio> = protocolEngine.incomingMessages
    val decodedMessages: Flow<FromRadioMessage> = protocolEngine.decodedMessages

    val connectionState: StateFlow<ConnectionState> = TODO("bind to protocol once transport manager is connected")

    private var incomingJob: Job? = null

    fun start() {
        incomingJob?.cancel()
        incomingJob =
            scope.launch {
                protocolEngine.incomingMessages.collect { message -> Logger.d { "MeshClient received message" } }
            }
    }
}
