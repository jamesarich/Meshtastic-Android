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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okio.Buffer
import org.meshtastic.core.mesh.client.protocol.ProtoFramer.RawFromRadio
import org.meshtastic.core.mesh.client.protocol.ProtoFramer.RawToRadio
import org.meshtastic.core.mesh.client.transport.IRadioTransport
import org.meshtastic.core.mesh.client.transport.TransportSpec

/**
 * Lightweight protocol manager that translates between raw transport frames and high-level protobuf messages.
 *
 * Long term this will encapsulate the framing logic from [RadioInterfaceService] and [PacketHandler].
 */
class RadioProtocolManager(private val scope: CoroutineScope, private val fromRadioParser: FromRadioParser) :
    MeshProtocolEngine {

    private val currentTransportState = MutableStateFlow<TransportSpec?>(null)
    override val currentTransport: StateFlow<TransportSpec?> = currentTransportState.asStateFlow()

    private val incomingFlow =
        MutableSharedFlow<RawFromRadio>(
            replay = 0,
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val incomingMessages: Flow<RawFromRadio> = incomingFlow.asSharedFlow()

    private val decodedFlow =
        MutableSharedFlow<FromRadioMessage>(
            replay = 0,
            extraBufferCapacity = 32,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    override val decodedMessages: Flow<FromRadioMessage> = decodedFlow.asSharedFlow()

    private var transport: IRadioTransport? = null
    private val buffer = Buffer()

    /**
     * Attach a transport implementation to the protocol manager. Only one transport can be active at a time.
     *
     * @param transportSpec descriptive information about the transport connection.
     * @param radioTransport low level transport used for IO.
     * @param incomingBytes stream of raw bytes from the transport (framed protobuf data). If null the transport is
     *   assumed to push data via callbacks.
     */
    fun bindTransport(
        transportSpec: TransportSpec,
        radioTransport: IRadioTransport,
        incomingBytes: Flow<ByteArray>? = null,
    ) {
        transport?.let { Logger.w { "Replacing active transport ${currentTransportState.value}" } }
        transport = radioTransport
        currentTransportState.value = transportSpec

        incomingBytes?.let { stream -> scope.launch { stream.collect { bytes -> ingest(bytes) } } }
    }

    override suspend fun send(commandBytes: RawToRadio) {
        withContext(Dispatchers.IO) {
            transport?.send(commandBytes.bytes) ?: Logger.w { "No transport bound for command" }
        }
    }

    fun ingest(bytes: ByteArray) {
        buffer.write(bytes)
        runCatching {
            while (true) {
                val frame = ProtoFramer.readFramedMessage(buffer) ?: break
                incomingFlow.tryEmit(frame)
                fromRadioParser.decode(frame)?.let { decodedFlow.tryEmit(it) }
            }
        }
            .onFailure { error ->
                Logger.e(error) { "Failed to decode framed packet" }
                buffer.clear()
            }
    }
}
