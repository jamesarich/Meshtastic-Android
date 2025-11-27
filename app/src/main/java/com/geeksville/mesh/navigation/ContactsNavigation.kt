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
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.geeksville.mesh.ui.contact.ContactsScreen
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.navigation.ChannelsRoutes
import org.meshtastic.core.navigation.ContactsRoutes
import org.meshtastic.core.navigation.NodesRoutes
import org.meshtastic.core.ui.component.ScrollToTopEvent
import org.meshtastic.feature.messaging.MessageScreen

@Composable
fun ContactsFlow(
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
            entry<ContactsRoutes.Contacts> {
                ContactsScreen(
                    onClickNodeChip = {
                        onNavigateExternal(NodesRoutes.NodeDetailGraph(it))
                    },
                    onNavigateToMessages = { contact ->
                         backStack.add(contact)
                    },
                    onNavigateToNodeDetails = {
                        onNavigateExternal(NodesRoutes.NodeDetailGraph(it))
                    },
                    onNavigateToShare = {
                        onNavigateExternal(ChannelsRoutes.ChannelsGraph)
                    },
                    scrollToTopEvents = scrollToTopEvents,
                )
            }

            entry<ContactsRoutes.Messages> { args ->
                MessageScreen(
                    contactKey = args.contactKey,
                    message = args.message,
                    navigateToMessages = { backStack.add(it) },
                    navigateToNodeDetails = { onNavigateExternal(NodesRoutes.NodeDetailGraph(it)) },
                    navigateToQuickChatOptions = { onNavigateExternal(ContactsRoutes.QuickChat) },
                    onNavigateBack = { backStack.removeLast() },
                )
            }
        }
    )
}
