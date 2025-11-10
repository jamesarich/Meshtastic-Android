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

package com.geeksville.mesh.model

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.RemoteException
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.repository.network.NetworkRepository
import com.geeksville.mesh.repository.network.NetworkRepository.Companion.toAddressString
import com.geeksville.mesh.repository.radio.InterfaceId
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import com.geeksville.mesh.repository.radio.ble.BluetoothConstants.BTM_SERVICE_UUID
import com.geeksville.mesh.repository.usb.UsbRepository
import com.geeksville.mesh.service.MeshService
import com.hoho.android.usbserial.driver.UsbSerialDriver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.client.android.exception.BondingFailedException
import no.nordicsemi.kotlin.ble.client.android.preview.PreviewPeripheral
import no.nordicsemi.kotlin.ble.client.distinctByPeripheral
import no.nordicsemi.kotlin.ble.core.BondState
import no.nordicsemi.kotlin.ble.core.Manager
import no.nordicsemi.kotlin.ble.core.Phy
import no.nordicsemi.kotlin.ble.core.PhyInUse
import org.meshtastic.core.common.hasBluetoothPermission
import org.meshtastic.core.datastore.RecentAddressesDataSource
import org.meshtastic.core.datastore.model.RecentAddress
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.ui.viewmodel.stateInWhileSubscribed
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid
import org.meshtastic.core.strings.R as Res

/**
 * A sealed class is used here to represent the different types of devices that can be displayed in the list. This is
 * more type-safe and idiomatic than using a base class with boolean flags (e.g., isBLE, isUSB). It allows for
 * exhaustive `when` expressions in the code, making it more robust and readable.
 *
 * @param name The display name of the device.
 * @param fullAddress The unique address of the device, prefixed with a type identifier.
 * @param bonded Indicates whether the device is bonded (for BLE) or has permission (for USB).
 */
sealed class DeviceListEntry(open val name: String, open val fullAddress: String, open val bonded: Boolean) {
    val address: String
        get() = fullAddress.substring(1)

    override fun toString(): String =
        "DeviceListEntry(name=${name.anonymize}, addr=${address.anonymize}, bonded=$bonded)"

    @Suppress("MissingPermission")
    data class Ble(val peripheral: Peripheral) :
        DeviceListEntry(
            name = peripheral.name ?: "unnamed-${peripheral.address}",
            fullAddress = "x${peripheral.address}",
            bonded = peripheral.bondState.value == BondState.BONDED,
        )

    data class Usb(
        private val radioInterfaceService: RadioInterfaceService,
        private val usbManager: UsbManager,
        val driver: UsbSerialDriver,
    ) : DeviceListEntry(
        name = driver.device.deviceName,
        fullAddress = radioInterfaceService.toInterfaceAddress(InterfaceId.SERIAL, driver.device.deviceName),
        bonded = usbManager.hasPermission(driver.device),
    )

    data class Tcp(override val name: String, override val fullAddress: String) :
        DeviceListEntry(name, fullAddress, true)

    data class Mock(override val name: String) : DeviceListEntry(name, "m", true)
}

@OptIn(ExperimentalUuidApi::class)
@HiltViewModel
@Suppress("LongParameterList", "TooManyFunctions", "UnusedPrivateMember")
class BTScanModel
@Inject
constructor(
    private val application: Application,
    private val centralManager: CentralManager,
    private val serviceRepository: ServiceRepository,
    private val usbRepository: UsbRepository,
    private val usbManagerLazy: dagger.Lazy<UsbManager>,
    private val networkRepository: NetworkRepository,
    private val radioInterfaceService: RadioInterfaceService,
    private val recentAddressesDataSource: RecentAddressesDataSource,
) : ViewModel() {
    private val context: Context
        get() = application.applicationContext

    private val _devices: MutableStateFlow<List<Peripheral>> =
        MutableStateFlow(centralManager.getBondedPeripherals())
    val devices: StateFlow<List<Peripheral>> = _devices.asStateFlow()

    val errorText = MutableLiveData<String?>(null)

    val showMockInterface: StateFlow<Boolean>
        get() = MutableStateFlow(radioInterfaceService.isMockInterface()).asStateFlow()

    private val bleDevicesFlow: StateFlow<List<DeviceListEntry.Ble>> =
        devices
            .map { devices -> devices.map { DeviceListEntry.Ble(it) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Flow for discovered TCP devices, using recent addresses for potential name enrichment
    @Suppress("UnusedPrivateMember")
    private val processedDiscoveredTcpDevicesFlow: StateFlow<List<DeviceListEntry.Tcp>> =
        combine(networkRepository.resolvedList, recentAddressesDataSource.recentAddresses) { tcpServices, recentList ->
            val recentMap = recentList.associateBy({ it.address }, { it.name })
            tcpServices
                .map { service ->
                    val address = "t${service.toAddressString()}"
                    val txtRecords = service.attributes // Map<String, ByteArray?>
                    val shortNameBytes = txtRecords["shortname"]
                    val idBytes = txtRecords["id"]

                    val shortName =
                        shortNameBytes?.let { String(it, Charsets.UTF_8) }
                            ?: context.getString(Res.string.meshtastic)
                    val deviceId = idBytes?.let { String(it, Charsets.UTF_8) }?.replace("!", "")
                    var displayName = recentMap[address] ?: shortName
                    if (deviceId != null && !displayName.split("_").none { it == deviceId }) {
                        displayName += "_$deviceId"
                    }
                    DeviceListEntry.Tcp(displayName, address)
                }
                .sortedBy { it.name }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Flow for recent TCP devices, filtered to exclude any currently discovered devices
    private val filteredRecentTcpDevicesFlow: StateFlow<List<DeviceListEntry.Tcp>> =
        combine(recentAddressesDataSource.recentAddresses, processedDiscoveredTcpDevicesFlow) {
                recentList,
                discoveredDevices,
            ->
            val discoveredDeviceAddresses = discoveredDevices.map { it.fullAddress }.toSet()
            recentList
                .filterNot { recentAddress -> discoveredDeviceAddresses.contains(recentAddress.address) }
                .map { recentAddress -> DeviceListEntry.Tcp(recentAddress.name, recentAddress.address) }
                .sortedBy { it.name }
        }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val usbDevicesFlow: StateFlow<List<DeviceListEntry.Usb>> =
        usbRepository.serialDevicesWithDrivers
            .map { usb -> usb.map { (_, d) -> DeviceListEntry.Usb(radioInterfaceService, usbManagerLazy.get(), d) } }
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val mockDevice = DeviceListEntry.Mock("Demo Mode")

    val bleDevicesForUi: StateFlow<List<DeviceListEntry>> =
        bleDevicesFlow.stateInWhileSubscribed(initialValue = emptyList())

    /** UI StateFlow for discovered TCP devices. */
    val discoveredTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        processedDiscoveredTcpDevicesFlow.stateInWhileSubscribed(initialValue = listOf())

    /** UI StateFlow for recently connected TCP devices that are not currently discovered. */
    val recentTcpDevicesForUi: StateFlow<List<DeviceListEntry>> =
        filteredRecentTcpDevicesFlow.stateInWhileSubscribed(initialValue = listOf())

    val usbDevicesForUi: StateFlow<List<DeviceListEntry>> =
        combine(usbDevicesFlow, showMockInterface) { usb, showMock ->
            usb + if (showMock) listOf(mockDevice) else emptyList()
        }
            .stateInWhileSubscribed(initialValue = if (showMockInterface.value) listOf(mockDevice) else emptyList())

    init {
        serviceRepository.statusMessage.onEach { errorText.value = it }.launchIn(viewModelScope)
        Timber.d("BTScanModel created")
        // Auto-stop scan if the manager state changes to a non-powered state while scanning
        centralManager.state
            .onEach { state ->
                if (scanJob != null && state != Manager.State.POWERED_ON) {
                    Timber.i("CentralManager state=$state; stopping active scan")
                    stopScan()
                }
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        Timber.d("BTScanModel cleared")
        // Ensure any running scan is stopped when the ViewModel is destroyed
        stopScan()
    }

    fun setErrorText(text: String) {
        errorText.value = text
    }

    private var scanJob: Job? = null

    val selectedAddressFlow: StateFlow<String?> = radioInterfaceService.currentDeviceAddressFlow

    val selectedNotNullFlow: StateFlow<String> =
        selectedAddressFlow
            .map { it ?: NO_DEVICE_SELECTED }
            .stateInWhileSubscribed(initialValue = selectedAddressFlow.value ?: NO_DEVICE_SELECTED)

    // removed legacy scanResult state

    fun stopScan() {
        if (scanJob != null) {
            Timber.d("stopping scan")
            try {
                scanJob?.cancel()
            } catch (ex: Throwable) {
                Timber.w("Ignoring error stopping scan, probably BT adapter was disabled suddenly: ${ex.message}")
            } finally {
                scanJob = null
            }
        }
        _spinner.value = false
    }

    @SuppressLint("MissingPermission")
    fun startScan(internal: Boolean = false) {
        Timber.d("starting scan (internal=$internal)")
        _spinner.value = true
        scanJob =
            centralManager.scan(5.seconds){ ServiceUuid(BTM_SERVICE_UUID.toKotlinUuid()) }
                .onStart { _spinner.update { true } }
                .distinctByPeripheral()
                .map { it.peripheral }
                .onEach { p ->
                    if (_devices.value.none { it.address == p.address }) {
                        Timber.i("Found new peripheral: $p")
                        _devices.update { it + p }
                        observePeripheralState(p, viewModelScope)
                        observeBondState(p, viewModelScope)
                    }
                }
                .catch { t -> Timber.e(t, "Error while scanning for peripherals") }
                .onCompletion {
                    _spinner.update { false }
                    scanJob = null
                }
                .launchIn(viewModelScope)
    }

    private fun changeDeviceAddress(address: String) {
        val service = serviceRepository.meshService ?: return
        try {
            MeshService.changeDeviceAddress(context, service, address)
        } catch (ex: RemoteException) {
            Timber.e(ex, "changeDeviceSelection failed, probably it is shutting down")
            // ignore the failure and the GUI won't be updating anyways
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestBonding(device: DeviceListEntry.Ble) {
        viewModelScope.launch {
            try {
                device.peripheral.createBond()
            } catch (ex: BondingFailedException){
                Timber.e(ex, "Bonding failed")
            }
        }
    }

    private fun requestPermission(it: DeviceListEntry.Usb) {
        usbRepository
            .requestPermission(it.driver.device)
            .onEach { granted ->
                if (granted) {
                    Timber.i("User approved USB access")
                    changeDeviceAddress(it.fullAddress)
                } else {
                    Timber.e("USB permission denied for device ${it.address}")
                }
            }
            .launchIn(viewModelScope)
    }

    fun addRecentAddress(address: String, name: String) {
        if (!address.startsWith("t")) return
        viewModelScope.launch { recentAddressesDataSource.add(RecentAddress(address, name)) }
    }

    fun removeRecentAddress(address: String) {
        viewModelScope.launch { recentAddressesDataSource.remove(address) }
    }

    // Called by the GUI when a new device has been selected by the user
    // @returns true if we were able to change to that item
    fun onSelected(it: DeviceListEntry): Boolean {
        // Using a `when` expression on the sealed class is much cleaner and safer than if/else chains.
        // It ensures that all device types are handled, and the compiler can catch any omissions.
        return when (it) {
            is DeviceListEntry.Ble -> {
                if (it.bonded) {
                    changeDeviceAddress(it.fullAddress)
                    true
                } else {
                    requestBonding(it)
                    false
                }
            }

            is DeviceListEntry.Usb -> {
                if (it.bonded) {
                    changeDeviceAddress(it.fullAddress)
                    true
                } else {
                    requestPermission(it)
                    false
                }
            }

            is DeviceListEntry.Tcp -> {
                viewModelScope.launch {
                    addRecentAddress(it.fullAddress, it.name)
                    changeDeviceAddress(it.fullAddress)
                }
                true
            }

            is DeviceListEntry.Mock -> {
                changeDeviceAddress(it.fullAddress)
                true
            }
        }
    }

    fun disconnect() {
        changeDeviceAddress(NO_DEVICE_SELECTED)
    }

    private val _spinner = MutableStateFlow(false)
    val spinner: StateFlow<Boolean>
        get() = _spinner.asStateFlow()

    private fun observePeripheralState(peripheral: Peripheral, scope: CoroutineScope) {
        peripheral.state
            .onEach { Timber.i("State of $peripheral: $it") }
            .onCompletion { Timber.d("State collection for $peripheral completed") }
            .launchIn(scope)
    }

    private fun observeBondState(peripheral: Peripheral, scope: CoroutineScope) {
        peripheral.bondState
            .onEach { Timber.i("Bond state of $peripheral: $it") }
            .onCompletion { Timber.d("Bond state collection for $peripheral completed") }
            .launchIn(scope)
    }
}

const val NO_DEVICE_SELECTED = "n"
