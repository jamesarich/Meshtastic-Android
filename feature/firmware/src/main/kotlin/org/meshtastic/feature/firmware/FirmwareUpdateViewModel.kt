package org.meshtastic.feature.firmware

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_extracting
import org.meshtastic.core.strings.firmware_update_failed
import org.meshtastic.core.strings.firmware_update_invalid_address
import org.meshtastic.core.strings.firmware_update_no_device
import org.meshtastic.core.strings.firmware_update_not_found_in_release
import org.meshtastic.core.strings.firmware_update_starting_dfu
import org.meshtastic.core.strings.firmware_update_starting_service
import org.meshtastic.core.strings.firmware_update_unknown_hardware
import org.meshtastic.core.strings.firmware_update_updating
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import no.nordicsemi.android.dfu.DfuProgressListenerAdapter

sealed interface FirmwareUpdateState {
    data object Idle : FirmwareUpdateState
    data object Checking : FirmwareUpdateState
    data class Ready(val release: FirmwareRelease, val deviceHardware: NetworkDeviceHardware) : FirmwareUpdateState
    data class Downloading(val progress: Float) : FirmwareUpdateState
    data class Processing(val message: String) : FirmwareUpdateState
    data class Updating(val progress: Float, val message: String) : FirmwareUpdateState
    data class Error(val error: String) : FirmwareUpdateState
    data object Success : FirmwareUpdateState
}

@HiltViewModel
class FirmwareUpdateViewModel @Inject constructor(
    private val firmwareReleaseRepository: FirmwareReleaseRepository,
    private val deviceHardwareRepository: DeviceHardwareRepository,
    private val nodeRepository: NodeRepository,
    private val radioPrefs: RadioPrefs,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val state: StateFlow<FirmwareUpdateState> = _state.asStateFlow()

    private val _selectedReleaseType = MutableStateFlow(FirmwareReleaseType.STABLE)
    val selectedReleaseType: StateFlow<FirmwareReleaseType> = _selectedReleaseType.asStateFlow()

    private var currentJob: Job? = null
    private val client = OkHttpClient()

    private val dfuProgressListener = object : DfuProgressListenerAdapter() {
        override fun onProgressChanged(deviceAddress: String, percent: Int, speed: Float, avgSpeed: Float, currentPart: Int, partsTotal: Int) {
            viewModelScope.launch {
                val msg = getString(Res.string.firmware_update_updating, "$percent%")
                _state.value = FirmwareUpdateState.Updating(percent / 100f, msg)
            }
        }

        override fun onError(deviceAddress: String, error: Int, errorType: Int, message: String?) {
             // Error message from DFU lib is usually English, hard to translate.
            _state.value = FirmwareUpdateState.Error("DFU Error: $message")
        }

        override fun onCompleted(deviceAddress: String) {
            _state.value = FirmwareUpdateState.Success
        }

        override fun onDfuProcessStarting(deviceAddress: String) {
             viewModelScope.launch {
                val msg = getString(Res.string.firmware_update_starting_dfu)
                _state.value = FirmwareUpdateState.Updating(0f, msg)
             }
        }
    }

    init {
        DfuServiceListenerHelper.registerProgressListener(context, dfuProgressListener)
        checkForUpdates()
    }

    override fun onCleared() {
        super.onCleared()
        DfuServiceListenerHelper.unregisterProgressListener(context, dfuProgressListener)
    }

    fun setReleaseType(type: FirmwareReleaseType) {
        _selectedReleaseType.value = type
        checkForUpdates()
    }

    fun checkForUpdates() {
        currentJob?.cancel()
        currentJob = viewModelScope.launch {
            _state.value = FirmwareUpdateState.Checking
            try {
                val ourNode = nodeRepository.ourNodeInfo.value

                if (ourNode == null) {
                    _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_no_device))
                    return@launch
                }

                // Check if address is available and valid for DFU (BLE)
                val address = radioPrefs.devAddr
                if (!isValidBluetoothAddress(address)) {
                     _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_invalid_address, address.orEmpty()))
                     return@launch
                }

                val hwModel = ourNode.user.hwModel.number
                val hardwareList = deviceHardwareRepository.getAllDeviceHardware()
                val deviceHardware = hardwareList.find { it.hwModel == hwModel }

                if (deviceHardware == null) {
                    _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_unknown_hardware, hwModel))
                    return@launch
                }

                val releaseFlow = if (_selectedReleaseType.value == FirmwareReleaseType.STABLE) {
                    firmwareReleaseRepository.stableRelease
                } else {
                    firmwareReleaseRepository.alphaRelease
                }

                releaseFlow.collect { release ->
                    if (release != null) {
                         _state.value = FirmwareUpdateState.Ready(release, deviceHardware)
                         // Stop collecting after first valid value to avoid loops
                         currentJob?.cancel()
                    }
                }

            } catch (e: Exception) {
                Timber.e(e)
                _state.value = FirmwareUpdateState.Error(e.message ?: "Unknown error")
            }
        }
    }

    fun startUpdate() {
        val address = radioPrefs.devAddr
        if (!isValidBluetoothAddress(address)) {
             // Should be caught by UI state, but check again
             return
        }

        val currentState = _state.value
        if (currentState !is FirmwareUpdateState.Ready) return

        viewModelScope.launch {
            try {
                _state.value = FirmwareUpdateState.Downloading(0f)
                val zipFile = downloadFirmware(currentState.release.zipUrl)

                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_extracting))
                val firmwareFile = extractFirmware(zipFile, currentState.deviceHardware)

                if (firmwareFile == null) {
                    _state.value = FirmwareUpdateState.Error(getString(Res.string.firmware_update_not_found_in_release, currentState.deviceHardware.displayName))
                    return@launch
                }

                _state.value = FirmwareUpdateState.Processing(getString(Res.string.firmware_update_starting_service))

                // Initiate DFU
                val starter = DfuServiceInitiator(address!!)
                    .setDeviceName(currentState.deviceHardware.displayName)
                    .setKeepBond(true)
                    .setZip(Uri.fromFile(firmwareFile))
                    .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
                    .setForeground(true) // Keep service in foreground

                starter.start(context, FirmwareDfuService::class.java)

            } catch (e: Exception) {
                Timber.e(e)
                val msg = getString(Res.string.firmware_update_failed)
                _state.value = FirmwareUpdateState.Error(e.message ?: msg)
            }
        }
    }

    private fun isValidBluetoothAddress(address: String?): Boolean {
        return address != null && address.matches(Regex("([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}"))
    }

    private suspend fun downloadFirmware(url: String): File = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) throw Exception("Download failed: ${response.code}")

        val body = response.body ?: throw Exception("Empty response")
        val file = File(context.cacheDir, "firmware_release.zip")

        val inputStream = body.byteStream()
        val outputStream = FileOutputStream(file)
        val buffer = ByteArray(8192)
        var bytesRead: Int
        var totalBytesRead = 0L
        val totalBytes = body.contentLength()

        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            outputStream.write(buffer, 0, bytesRead)
            totalBytesRead += bytesRead
            if (totalBytes > 0) {
                _state.value = FirmwareUpdateState.Downloading(totalBytesRead.toFloat() / totalBytes)
            }
        }

        outputStream.close()
        inputStream.close()
        file
    }

    private suspend fun extractFirmware(zipFile: File, hardware: NetworkDeviceHardware): File? = withContext(Dispatchers.IO) {
        // Look for a file that matches the target and ends in .zip
        val target = hardware.hwModelSlug.ifEmpty { hardware.platformioTarget }
        if (target.isEmpty()) return@withContext null

        val zipInputStream = ZipInputStream(zipFile.inputStream())
        var entry = zipInputStream.nextEntry
        var foundFile: File? = null

        while (entry != null) {
            // Case insensitive check
            val name = entry.name.lowercase()
            // Looking for something like "firmware-rak4631-2.5.0.zip" or "update/firmware-rak4631..."
            // Must contain the target slug and end in .zip

            if (!entry.isDirectory && name.contains(target.lowercase()) && name.endsWith(".zip")) {
                val outFile = File(context.cacheDir, File(name).name)
                val outputStream = FileOutputStream(outFile)
                zipInputStream.copyTo(outputStream)
                outputStream.close()
                foundFile = outFile
                break
            }
            entry = zipInputStream.nextEntry
        }
        zipInputStream.close()
        foundFile
    }
}
