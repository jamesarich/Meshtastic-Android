/*
 * Copyright (c) 2025 Meshtastic LLC
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

package com.geeksville.mesh.repository.radio

import android.annotation.SuppressLint
import android.app.Application
import com.geeksville.mesh.service.RadioNotConnectedException
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.android.kotlin.ble.client.main.callback.ClientBleGatt
import no.nordicsemi.android.kotlin.ble.client.main.connect
import no.nordicsemi.android.kotlin.ble.core.data.GattConnectionState
import no.nordicsemi.android.kotlin.ble.scanner.BleScanner
import org.meshtastic.core.analytics.platform.PlatformAnalytics
import org.meshtastic.core.service.ConnectionState
import timber.log.Timber
import java.util.UUID

@SuppressLint("MissingPermission")
class NordicBleInterface @AssistedInject constructor(
    private val context: Application,
    private val service: RadioInterfaceService,
    private val analytics: PlatformAnalytics,
    @Assisted val address: String,
) : IRadioInterface {

    private var client: ClientBleGatt? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    companion object {
        val BTM_SERVICE_UUID: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
        val BTM_TORADIO_CHARACTER: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
        val BTM_FROMNUM_CHARACTER: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
    }

    init {
        connect()
    }

    private fun connect() {
        scope.launch {
            try {
                val scanner = BleScanner(context)
                val device = scanner.scan()
                    .mapNotNull { it.device }
                    .firstOrNull { it.address == address }

                if (device == null) {
                    Timber.e("Device not found")
                    service.onDisconnect(true)
                    return@launch
                }

                client = device.connect(context, autoConnect = true)
            } catch (e: Exception) {
                Timber.e(e, "Error connecting to device")
                service.onDisconnect(true)
            }

            client?.connectionState?.onEach {
                when (it) {
                    GattConnectionState.STATE_CONNECTED -> service.onConnect()
                    GattConnectionState.STATE_DISCONNECTED -> {
                        service.onDisconnect(true)
                        connect() // Reconnect on failure
                    }
                    GattConnectionState.STATE_CONNECTING -> {}
                    GattConnectionState.STATE_DISCONNECTING -> {}
                }
            }?.launchIn(scope)

            client?.services?.onEach { services ->
                val service = services.findService(BTM_SERVICE_UUID)
                val fromRadio = service?.findCharacteristic(BTM_FROMNUM_CHARACTER)
                fromRadio?.getNotifications()
                    ?.onEach { service.handleFromRadio(it.value) }
                    ?.catch { Timber.e(it, "Error reading from radio") }
                    ?.launchIn(scope)
            }?.launchIn(scope)
        }
    }

    override fun handleSendToRadio(p: ByteArray) {
        if (client == null) throw RadioNotConnectedException("No GATT connection")
        scope.launch {
            client?.services?.findService(BTM_SERVICE_UUID)
                ?.findCharacteristic(BTM_TORADIO_CHARACTER)
                ?.write(p)
        }
    }

    override fun close() {
        Timber.d("Closing NordicBleInterface")
        scope.cancel()
        client?.close()
    }
}