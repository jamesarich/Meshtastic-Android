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

package com.geeksville.mesh.navigation

import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.feature.settings.SettingsScreen
import org.meshtastic.feature.settings.debugging.DebugScreen
import org.meshtastic.feature.settings.radio.CleanNodeDatabaseScreen
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.channel.ChannelConfigScreen
import org.meshtastic.feature.settings.radio.component.AmbientLightingConfigScreen
import org.meshtastic.feature.settings.radio.component.AudioConfigScreen
import org.meshtastic.feature.settings.radio.component.BluetoothConfigScreen
import org.meshtastic.feature.settings.radio.component.CannedMessageConfigScreen
import org.meshtastic.feature.settings.radio.component.DetectionSensorConfigScreen
import org.meshtastic.feature.settings.radio.component.DeviceConfigScreen
import org.meshtastic.feature.settings.radio.component.DisplayConfigScreen
import org.meshtastic.feature.settings.radio.component.ExternalNotificationConfigScreen
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen
import org.meshtastic.feature.settings.radio.component.MQTTConfigScreen
import org.meshtastic.feature.settings.radio.component.NeighborInfoConfigScreen
import org.meshtastic.feature.settings.radio.component.NetworkConfigScreen
import org.meshtastic.feature.settings.radio.component.PaxcounterConfigScreen
import org.meshtastic.feature.settings.radio.component.PositionConfigScreen
import org.meshtastic.feature.settings.radio.component.PowerConfigScreen
import org.meshtastic.feature.settings.radio.component.RangeTestConfigScreen
import org.meshtastic.feature.settings.radio.component.RemoteHardwareConfigScreen
import org.meshtastic.feature.settings.radio.component.SecurityConfigScreen
import org.meshtastic.feature.settings.radio.component.SerialConfigScreen
import org.meshtastic.feature.settings.radio.component.StoreForwardConfigScreen
import org.meshtastic.feature.settings.radio.component.TelemetryConfigScreen
import org.meshtastic.feature.settings.radio.component.UserConfigScreen

@Composable
fun SettingsFlow(
    backStack: MutableList<Any>,
    onNavigateExternal: (Any) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: RadioConfigViewModel = hiltViewModel()

    NavDisplay(
        backStack = backStack,
        onBack = {
            if (backStack.size > 1) {
                backStack.removeLast()
            } else {
                onBack()
            }
        },
        entryProvider = entryProvider {
            entry<SettingsRoutes.Settings> {
                SettingsScreen(
                    viewModel = viewModel,
                    onClickNodeChip = {
                        onNavigateExternal(NodesRoutes.NodeDetailGraph(it))
                    },
                    onNavigate = { route ->
                        if (route is SettingsRoute && route !is SettingsRoutes.SettingsGraph) {
                            backStack.add(route)
                        } else {
                            onNavigateExternal(route)
                        }
                    }
                )
            }

            entry<SettingsRoutes.CleanNodeDb> { CleanNodeDatabaseScreen() }

            entry<SettingsRoutes.User> { UserConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.ChannelConfig> { ChannelConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Device> { DeviceConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Position> { PositionConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Power> { PowerConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Network> { NetworkConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Display> { DisplayConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.LoRa> { LoRaConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Bluetooth> { BluetoothConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Security> { SecurityConfigScreen(viewModel, onBack = { backStack.removeLast() }) }

            entry<SettingsRoutes.MQTT> { MQTTConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Serial> { SerialConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.ExtNotification> {
                ExternalNotificationConfigScreen(viewModel = viewModel, onBack = { backStack.removeLast() })
            }
            entry<SettingsRoutes.StoreForward> { StoreForwardConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.RangeTest> { RangeTestConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Telemetry> { TelemetryConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.CannedMessage> { CannedMessageConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Audio> { AudioConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.RemoteHardware> { RemoteHardwareConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.NeighborInfo> { NeighborInfoConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.AmbientLighting> { AmbientLightingConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.DetectionSensor> { DetectionSensorConfigScreen(viewModel, onBack = { backStack.removeLast() }) }
            entry<SettingsRoutes.Paxcounter> { PaxcounterConfigScreen(viewModel, onBack = { backStack.removeLast() }) }

            entry<SettingsRoutes.DebugPanel> { DebugScreen(onNavigateUp = { backStack.removeLast() }) }
        }
    )
}
