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
package com.geeksville.mesh.service.transport

import com.geeksville.mesh.repository.radio.RadioInterfaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import org.meshtastic.core.mesh.client.transport.IRadioTransport
import org.meshtastic.core.service.ConnectionState

/** Bridges the legacy [RadioInterfaceService] to the multiplatform [IRadioTransport] abstraction. */
class RadioInterfaceTransport(private val radioInterfaceService: RadioInterfaceService, scope: CoroutineScope) :
    IRadioTransport {

    private val connectedState = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = connectedState.asStateFlow()

    init {
        radioInterfaceService.connectionState
            .onEach { state -> connectedState.value = state == ConnectionState.Connected }
            .launchIn(scope)
    }

    override suspend fun send(data: ByteArray) {
        withContext(Dispatchers.IO) { radioInterfaceService.sendToRadio(data) }
    }

    override suspend fun keepAlive() {
        withContext(Dispatchers.IO) { radioInterfaceService.keepAlive() }
    }

    override suspend fun close() {
        connectedState.value = false
    }
}
