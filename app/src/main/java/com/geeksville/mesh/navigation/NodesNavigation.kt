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
import androidx.core.os.bundleOf
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.NodeDetailRoutes
import org.meshtastic.core.navigation.NodesRoute
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.map.node.NodeMapScreen
import org.meshtastic.feature.map.node.NodeMapViewModel
import org.meshtastic.feature.node.detail.NodeDetailScreen
import org.meshtastic.feature.node.list.NodeListScreen
import org.meshtastic.feature.node.metrics.DeviceMetricsScreen
import org.meshtastic.feature.node.metrics.EnvironmentMetricsScreen
import org.meshtastic.feature.node.metrics.HostMetricsLogScreen
import org.meshtastic.feature.node.metrics.MetricsViewModel
import org.meshtastic.feature.node.metrics.PaxMetricsScreen
import org.meshtastic.feature.node.metrics.PositionLogScreen
import org.meshtastic.feature.node.metrics.PowerMetricsScreen
import org.meshtastic.feature.node.metrics.SignalMetricsScreen
import org.meshtastic.feature.node.metrics.TracerouteLogScreen

@Composable
fun NodesFlow(
    backStack: MutableList<Any>,
    onNavigateExternal: (Any) -> Unit,
    onBack: () -> Unit,
    scrollToTopEvents: Flow<ScrollToTopEvent>
) {
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
            entry<NodesRoutes.Nodes> {
                NodeListScreen(
                    navigateToNodeDetails = {
                        backStack.add(NodesRoutes.NodeDetailGraph(it))
                    },
                    scrollToTopEvents = scrollToTopEvents,
                )
            }

            entry<NodesRoutes.NodeDetailGraph> { args ->
                NodeDetailFlow(
                    args = args,
                    onNavigateExternal = onNavigateExternal,
                    onBack = { backStack.removeLast() }
                )
            }
        }
    )
}

@Composable
fun NodeDetailFlow(
    args: NodesRoutes.NodeDetailGraph,
    onNavigateExternal: (Any) -> Unit,
    onBack: () -> Unit
) {
    val owner = LocalViewModelStoreOwner.current
    val defaultExtras = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
    val extras = MutableCreationExtras(defaultExtras)
    extras[DEFAULT_ARGS_KEY] = bundleOf("destNum" to args.destNum)

    val metricsViewModel: MetricsViewModel = hiltViewModel(extras = extras)
    // Scope NodeMapViewModel to this flow as well to match original behavior
    val nodeMapViewModel: NodeMapViewModel = hiltViewModel(extras = extras)

    val backStack = rememberNavBackStack(NodesRoutes.NodeDetail(args.destNum))

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
            entry<NodesRoutes.NodeDetail> {
                NodeDetailScreen(
                    navigateToMessages = { onNavigateExternal(ContactsRoutes.Messages(it)) },
                    onNavigate = { route ->
                        if (route is NodesRoute && route !is NodesRoutes.NodeDetailGraph && route !is NodesRoutes.NodesGraph && route !is NodesRoutes.Nodes) {
                            backStack.add(route)
                        } else {
                            onNavigateExternal(route)
                        }
                    },
                    onNavigateUp = onBack,
                    viewModel = metricsViewModel,
                )
            }

            entry<NodeDetailRoutes.NodeMap> {
                NodeMapScreen(
                    viewModel = nodeMapViewModel,
                    onNavigateUp = { backStack.removeLast() },
                )
            }

            entry<NodeDetailRoutes.DeviceMetrics> {
                DeviceMetricsScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.PositionLog> {
                PositionLogScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.EnvironmentMetrics> {
                EnvironmentMetricsScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.SignalMetrics> {
                SignalMetricsScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.PowerMetrics> {
                PowerMetricsScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.TracerouteLog> {
                TracerouteLogScreen(viewModel = metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.HostMetricsLog> {
                HostMetricsLogScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
            entry<NodeDetailRoutes.PaxMetrics> {
                PaxMetricsScreen(metricsViewModel, onNavigateUp = { backStack.removeLast() })
            }
        }
    )
}
