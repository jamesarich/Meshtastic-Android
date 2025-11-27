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

import androidx.activity.compose.BackHandler
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun NodesFlow(
    args: NodesRoutes.NodesGraph,
    navigator: ThreePaneScaffoldNavigator<NodesRoutes.NodeDetailGraph>,
    onNavigateExternal: (Any) -> Unit,
    onBack: () -> Unit,
    scrollToTopEvents: Flow<ScrollToTopEvent>
) {
    LaunchedEffect(args.startNodeId, args.target) {
        if (args.startNodeId != null) {
            navigator.navigateTo(
                ListDetailPaneScaffoldRole.Detail,
                NodesRoutes.NodeDetailGraph(args.startNodeId, args.target)
            )
        }
    }

    BackHandler(enabled = true) {
        if (navigator.canNavigateBack()) {
            navigator.navigateBack()
        } else {
            onBack()
        }
    }

    ListDetailPaneScaffold(
        directive = navigator.scaffoldDirective,
        value = navigator.scaffoldValue,
        listPane = {
            AnimatedPane {
                NodeListScreen(
                    navigateToNodeDetails = { nodeId ->
                        navigator.navigateTo(
                            ListDetailPaneScaffoldRole.Detail,
                            NodesRoutes.NodeDetailGraph(nodeId)
                        )
                    },
                    scrollToTopEvents = scrollToTopEvents,
                )
            }
        },
        detailPane = {
            AnimatedPane {
                val selection = navigator.currentDestination?.content
                if (selection != null) {
                    NodeDetailFlow(
                        args = selection,
                        onNavigateExternal = onNavigateExternal,
                        onBack = { navigator.navigateBack() }
                    )
                }
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

    // Use unique key based on destNum to ensure correct scoping and args
    val vmKey = args.destNum.toString()
    val metricsViewModel: MetricsViewModel = hiltViewModel(key = vmKey, extras = extras)
    val nodeMapViewModel: NodeMapViewModel = hiltViewModel(key = vmKey, extras = extras)

    val backStack = rememberNavBackStack(NodesRoutes.NodeDetail(args.destNum))

    LaunchedEffect(args.target) {
        args.target?.let { target ->
            val route = when (target) {
                "node_map", "map" -> NodeDetailRoutes.NodeMap
                "device" -> NodeDetailRoutes.DeviceMetrics
                "position" -> NodeDetailRoutes.PositionLog
                "environment" -> NodeDetailRoutes.EnvironmentMetrics
                "signal" -> NodeDetailRoutes.SignalMetrics
                "power" -> NodeDetailRoutes.PowerMetrics
                "traceroute" -> NodeDetailRoutes.TracerouteLog
                "host" -> NodeDetailRoutes.HostMetricsLog
                "pax" -> NodeDetailRoutes.PaxMetrics
                else -> null
            }
            if (route != null) {
                backStack.add(route)
            }
        }
    }

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
