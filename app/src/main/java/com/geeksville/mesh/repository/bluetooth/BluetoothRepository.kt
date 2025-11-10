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

package com.geeksville.mesh.repository.bluetooth

import android.app.Application
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.core.Manager
import org.meshtastic.core.common.hasBluetoothPermission
import org.meshtastic.core.di.CoroutineDispatchers
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** Repository responsible for maintaining and updating the state of Bluetooth availability. */
@Singleton
class BluetoothRepository
@Inject
constructor(
    private val application: Application,
    private val centralManager: CentralManager,
    private val dispatchers: CoroutineDispatchers,
    private val processLifecycle: Lifecycle,
) {
    private val _state =
        MutableStateFlow(
            BluetoothState(
                // Assume we have permission until we get our initial state update to prevent premature
                // notifications to the user.
                hasPermissions = true,
            ),
        )
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    init {
        // Initial snapshot
        processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() }
        // React to Bluetooth adapter state changes automatically
        centralManager.state
            .onEach { processLifecycle.coroutineScope.launch(dispatchers.default) { updateBluetoothState() } }
            .launchIn(processLifecycle.coroutineScope)
    }

    internal suspend fun updateBluetoothState() {
        val hasPerms = application.hasBluetoothPermission()
        val enabled = centralManager.state.value == Manager.State.POWERED_ON
        val bondedDevices = if (hasPerms) centralManager.getBondedPeripherals() else emptyList()
        val newState =
            BluetoothState(
                hasPermissions = hasPerms,
                enabled = enabled,
                bondedDevices =
                if (!enabled) {
                    emptyList()
                } else {
                    bondedDevices.filter { it.name?.matches(Regex(BLE_NAME_PATTERN)) == true }
                },
            )

        _state.emit(newState)
        Timber.d("Detected our bluetooth access=$newState")
    }

    companion object {
        const val BLE_NAME_PATTERN = "^.*_([0-9a-fA-F]{4})$"
    }
}
