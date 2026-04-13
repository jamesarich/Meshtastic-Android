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
package org.meshtastic.feature.connections.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.isValidAddress
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.network.repository.NetworkConstants
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.add_network_device
import org.meshtastic.core.resources.add_network_device_manually
import org.meshtastic.core.resources.address
import org.meshtastic.core.resources.bluetooth
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.discovered_network_devices
import org.meshtastic.core.resources.ip_port
import org.meshtastic.core.resources.no_devices_found
import org.meshtastic.core.resources.recent_network_devices
import org.meshtastic.core.resources.scan_bluetooth_devices
import org.meshtastic.core.resources.scan_network_devices
import org.meshtastic.core.resources.scanning_bluetooth
import org.meshtastic.core.resources.scanning_network
import org.meshtastic.core.resources.usb
import org.meshtastic.core.ui.icon.Add
import org.meshtastic.core.ui.icon.Close
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NoDevice
import org.meshtastic.core.ui.icon.Search
import org.meshtastic.feature.connections.model.DeviceListEntry

/**
 * Unified device list composable that displays all available devices grouped by transport type.
 *
 * Replaces the previous tab-based UI (BLE / Network / Serial tabs) with a single scrollable list. Each transport type
 * is rendered as a section with a header. Empty sections are hidden.
 *
 * BLE and network scanning are controlled by explicit toggle buttons rather than auto-starting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("LongMethod", "LongParameterList")
@Composable
fun DeviceList(
    connectionState: ConnectionState,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry>,
    usbDevices: List<DeviceListEntry>,
    discoveredTcpDevices: List<DeviceListEntry>,
    recentTcpDevices: List<DeviceListEntry>,
    isBleScanning: Boolean,
    isNetworkScanning: Boolean,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onToggleBleScan: () -> Unit,
    onToggleNetworkScan: () -> Unit,
    onAddManualAddress: (address: String, fullAddress: String) -> Unit,
    onRemoveRecentAddress: (DeviceListEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    val hideAndDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { if (!sheetState.isVisible) showAddDialog = false }
    }

    if (showAddDialog) {
        AddDeviceDialog(
            sheetState = sheetState,
            onHideDialog = hideAndDismiss,
            onClickAdd = { address, fullAddress ->
                onAddManualAddress(address, fullAddress)
                hideAndDismiss()
            },
        )
    }

    val hasAnyDevices =
        bleDevices.isNotEmpty() ||
            usbDevices.isNotEmpty() ||
            discoveredTcpDevices.isNotEmpty() ||
            recentTcpDevices.isNotEmpty()

    if (!hasAnyDevices && !isBleScanning && !isNetworkScanning) {
        EmptyDeviceList(
            onToggleBleScan = onToggleBleScan,
            onToggleNetworkScan = onToggleNetworkScan,
            onAddManually = { showAddDialog = true },
        )
        return
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Bluetooth section ──
        BluetoothSection(
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            bleDevices = bleDevices,
            isBleScanning = isBleScanning,
            onSelectDevice = onSelectDevice,
            onToggleBleScan = onToggleBleScan,
        )

        // ── USB section ──
        AnimatedVisibility(
            visible = usbDevices.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DeviceListSection(
                devices = usbDevices,
                title = stringResource(Res.string.usb),
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                onSelect = onSelectDevice,
            )
        }

        // ── Network section ──
        NetworkSection(
            connectionState = connectionState,
            selectedDevice = selectedDevice,
            discoveredTcpDevices = discoveredTcpDevices,
            recentTcpDevices = recentTcpDevices,
            isNetworkScanning = isNetworkScanning,
            onSelectDevice = onSelectDevice,
            onToggleNetworkScan = onToggleNetworkScan,
            onAddManually = { showAddDialog = true },
            onRemoveRecentAddress = onRemoveRecentAddress,
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

/** Bluetooth section: scan toggle + device list. */
@Composable
private fun BluetoothSection(
    connectionState: ConnectionState,
    selectedDevice: String,
    bleDevices: List<DeviceListEntry>,
    isBleScanning: Boolean,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onToggleBleScan: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ScanToggleButton(
            isScanning = isBleScanning,
            scanLabel = stringResource(Res.string.scan_bluetooth_devices),
            scanningLabel = stringResource(Res.string.scanning_bluetooth),
            onToggle = onToggleBleScan,
        )

        AnimatedVisibility(
            visible = bleDevices.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DeviceListSection(
                devices = bleDevices,
                title = stringResource(Res.string.bluetooth),
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                onSelect = onSelectDevice,
            )
        }
    }
}

/** Network section: scan toggle + discovered + recent + add manually. */
@Suppress("LongParameterList")
@Composable
private fun NetworkSection(
    connectionState: ConnectionState,
    selectedDevice: String,
    discoveredTcpDevices: List<DeviceListEntry>,
    recentTcpDevices: List<DeviceListEntry>,
    isNetworkScanning: Boolean,
    onSelectDevice: (DeviceListEntry) -> Unit,
    onToggleNetworkScan: () -> Unit,
    onAddManually: () -> Unit,
    onRemoveRecentAddress: (DeviceListEntry) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            visible = discoveredTcpDevices.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DeviceListSection(
                devices = discoveredTcpDevices,
                title = stringResource(Res.string.discovered_network_devices),
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                onSelect = onSelectDevice,
            )
        }

        AnimatedVisibility(
            visible = recentTcpDevices.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            DeviceListSection(
                devices = recentTcpDevices,
                title = stringResource(Res.string.recent_network_devices),
                connectionState = connectionState,
                selectedDevice = selectedDevice,
                onSelect = onSelectDevice,
                onDelete = onRemoveRecentAddress,
            )
        }

        ScanToggleButton(
            isScanning = isNetworkScanning,
            scanLabel = stringResource(Res.string.scan_network_devices),
            scanningLabel = stringResource(Res.string.scanning_network),
            onToggle = onToggleNetworkScan,
        )

        OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
            Icon(MeshtasticIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.add_network_device_manually))
        }
    }
}

/**
 * Reusable scan toggle button with progress indicator. Uses [FilledTonalButton] when active for clear state feedback.
 */
@Composable
private fun ScanToggleButton(isScanning: Boolean, scanLabel: String, scanningLabel: String, onToggle: () -> Unit) {
    if (isScanning) {
        FilledTonalButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(MeshtasticIcons.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(scanningLabel)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    } else {
        OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Icon(MeshtasticIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(scanLabel)
        }
    }
}

/** Shown when there are no devices of any type and no scan is running. */
@Composable
private fun EmptyDeviceList(onToggleBleScan: () -> Unit, onToggleNetworkScan: () -> Unit, onAddManually: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        EmptyStateContent(
            text = stringResource(Res.string.no_devices_found),
            imageVector = MeshtasticIcons.NoDevice,
            modifier = Modifier.weight(1f),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onToggleBleScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(MeshtasticIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.scan_bluetooth_devices))
                }
                Button(onClick = onToggleNetworkScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(MeshtasticIcons.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.scan_network_devices))
                }
                OutlinedButton(onClick = onAddManually, modifier = Modifier.fillMaxWidth()) {
                    Icon(MeshtasticIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.add_network_device_manually))
                }
            }
        }
    }
}

/** Dialog for manually adding a TCP device by IP address and port. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddDeviceDialog(
    sheetState: SheetState,
    onHideDialog: () -> Unit,
    onClickAdd: (address: String, fullAddress: String) -> Unit,
) {
    val addressState = rememberTextFieldState("")
    val portState = rememberTextFieldState(NetworkConstants.SERVICE_PORT.toString())

    @Suppress("MagicNumber")
    ModalBottomSheet(onDismissRequest = onHideDialog, sheetState = sheetState) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    state = addressState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(Res.string.address)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(.7f),
                )

                OutlinedTextField(
                    state = portState,
                    labelPosition = TextFieldLabelPosition.Above(),
                    placeholder = { Text(NetworkConstants.SERVICE_PORT.toString()) },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    label = { Text(stringResource(Res.string.ip_port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(.3f),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(modifier = Modifier.weight(1f), onClick = { onHideDialog() }) {
                    Text(stringResource(Res.string.cancel))
                }

                Button(
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val address = addressState.text.toString()
                        if (address.isValidAddress()) {
                            val portString = portState.text.toString()
                            val port = portString.toIntOrNull()

                            val combinedString =
                                if (port != null && port != NetworkConstants.SERVICE_PORT) {
                                    "$address:$portString"
                                } else {
                                    address
                                }

                            onClickAdd(combinedString, "t$combinedString")
                        }
                    },
                ) {
                    Text(stringResource(Res.string.add_network_device))
                }
            }
        }
    }
}
