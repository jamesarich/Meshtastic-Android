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

package org.meshtastic.feature.firmware

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.database.entity.FirmwareReleaseType
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_alpha
import org.meshtastic.core.strings.firmware_update_button
import org.meshtastic.core.strings.firmware_update_checking
import org.meshtastic.core.strings.firmware_update_device
import org.meshtastic.core.strings.firmware_update_disconnect_warning
import org.meshtastic.core.strings.firmware_update_done
import org.meshtastic.core.strings.firmware_update_downloading
import org.meshtastic.core.strings.firmware_update_error
import org.meshtastic.core.strings.firmware_update_latest
import org.meshtastic.core.strings.firmware_update_retry
import org.meshtastic.core.strings.firmware_update_stable
import org.meshtastic.core.strings.firmware_update_success
import org.meshtastic.core.strings.firmware_update_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirmwareUpdateScreen(navController: NavController, viewModel: FirmwareUpdateViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val selectedReleaseType by viewModel.selectedReleaseType.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.firmware_update_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (val s = state) {
                is FirmwareUpdateState.Idle,
                FirmwareUpdateState.Checking -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(Res.string.firmware_update_checking))
                }
                is FirmwareUpdateState.Ready -> {
                    Text(stringResource(Res.string.firmware_update_device, s.deviceHardware.displayName), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.firmware_update_latest, s.release.title), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(
                            selected = selectedReleaseType == FirmwareReleaseType.STABLE,
                            onClick = { viewModel.setReleaseType(FirmwareReleaseType.STABLE) },
                            label = { Text(stringResource(Res.string.firmware_update_stable)) },
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = selectedReleaseType == FirmwareReleaseType.ALPHA,
                            onClick = { viewModel.setReleaseType(FirmwareReleaseType.ALPHA) },
                            label = { Text(stringResource(Res.string.firmware_update_alpha)) },
                        )
                    }

                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { viewModel.startUpdate() }) { Text(stringResource(Res.string.firmware_update_button)) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(Res.string.firmware_update_disconnect_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                is FirmwareUpdateState.Downloading -> {
                    LinearProgressIndicator(progress = { s.progress })
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(Res.string.firmware_update_downloading, (s.progress * 100).toInt()))
                }
                is FirmwareUpdateState.Processing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(s.message)
                }
                is FirmwareUpdateState.Updating -> {
                    LinearProgressIndicator(progress = { s.progress })
                    Spacer(Modifier.height(8.dp))
                    Text(s.message)
                }
                is FirmwareUpdateState.Error -> {
                    Text(stringResource(Res.string.firmware_update_error, s.error), color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.checkForUpdates() }) { Text(stringResource(Res.string.firmware_update_retry)) }
                }
                is FirmwareUpdateState.Success -> {
                    Text(
                        stringResource(Res.string.firmware_update_success),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { navController.navigateUp() }) { Text(stringResource(Res.string.firmware_update_done)) }
                }
            }
        }
    }
}
