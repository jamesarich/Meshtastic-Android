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
import com.geeksville.mesh.ui.connections.ConnectionsScreen
import org.meshtastic.core.navigation.ConnectionsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.navigation.SettingsRoutes
import org.meshtastic.feature.settings.radio.RadioConfigViewModel
import org.meshtastic.feature.settings.radio.component.LoRaConfigScreen

@Composable
fun ConnectionsFlow(
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
            entry<ConnectionsRoutes.Connections> {
                ConnectionsScreen(
                    radioConfigViewModel = viewModel,
                    onClickNodeChip = {
                        onNavigateExternal(NodesRoutes.NodeDetailGraph(it))
                    },
                    onNavigateToNodeDetails = {
                        onNavigateExternal(NodesRoutes.NodeDetailGraph(it))
                    },
                    onConfigNavigate = { route ->
                        if (route is SettingsRoutes.LoRa) {
                            backStack.add(route)
                        } else {
                            onNavigateExternal(route)
                        }
                    },
                )
            }

            entry<SettingsRoutes.LoRa> {
                LoRaConfigScreen(viewModel = viewModel, onBack = { backStack.removeLast() })
            }
        }
    )
}
