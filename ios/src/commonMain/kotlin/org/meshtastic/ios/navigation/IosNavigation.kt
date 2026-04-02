/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.ios.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.feature.connections.navigation.connectionsGraph
import org.meshtastic.feature.firmware.navigation.firmwareGraph
import org.meshtastic.feature.map.navigation.mapGraph
import org.meshtastic.feature.messaging.navigation.contactsGraph
import org.meshtastic.feature.node.navigation.nodesGraph
import org.meshtastic.feature.settings.navigation.settingsGraph
import org.meshtastic.feature.settings.radio.channel.channelsGraph

/**
 * Registers entry providers for all top-level iOS destinations.
 *
 * Mirrors [desktopNavGraph] — all feature modules share the same `commonMain` composables, so every graph function is
 * identical to the Desktop wiring.
 */
fun EntryProviderScope<NavKey>.iosNavGraph(backStack: NavBackStack<NavKey>, uiViewModel: UIViewModel) {
    // Nodes — real composables from feature:node
    nodesGraph(
        backStack = backStack,
        scrollToTopEvents = uiViewModel.scrollToTopEventFlow,
        onHandleDeepLink = uiViewModel::handleDeepLink,
    )

    // Conversations — real composables from feature:messaging
    contactsGraph(backStack, uiViewModel.scrollToTopEventFlow)

    // Map — placeholder until feature:map ships a real iOS-compatible implementation
    mapGraph(backStack)

    // Firmware — in-flow destination (e.g. triggered from Settings), not a top-level rail tab
    firmwareGraph(backStack)

    // Settings — real composables from feature:settings
    settingsGraph(backStack)

    // Channels
    channelsGraph(backStack)

    // Connections — shared screen
    connectionsGraph(backStack)
}
