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
package org.meshtastic.feature.node.detail

import android.os.RemoteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.service.ServiceAction
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.notes_saved
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeManagementActions
@Inject
constructor(
    private val nodeRepository: NodeRepository,
    private val serviceRepository: ServiceRepository,
) {
    private val _effects = MutableSharedFlow<NodeRequestEffect>()
    val effects: SharedFlow<NodeRequestEffect> = _effects.asSharedFlow()

    fun removeNode(scope: CoroutineScope, nodeNum: Int) {
        scope.launch(Dispatchers.IO) {
            Logger.i { "Removing node '$nodeNum'" }
            try {
                val packetId = serviceRepository.meshService?.packetId ?: return@launch
                serviceRepository.meshService?.removeByNodenum(packetId, nodeNum)
                nodeRepository.deleteNode(nodeNum)
            } catch (ex: RemoteException) {
                Logger.e { "Remove node error: ${ex.message}" }
            }
        }
    }

    fun ignoreNode(scope: CoroutineScope, node: Node) {
        scope.launch(Dispatchers.IO) {
            try {
                serviceRepository.onServiceAction(ServiceAction.Ignore(node))
            } catch (ex: RemoteException) {
                Logger.e(ex) { "Ignore node error" }
            }
        }
    }

    fun muteNode(scope: CoroutineScope, node: Node) {
        scope.launch(Dispatchers.IO) {
            try {
                serviceRepository.onServiceAction(ServiceAction.Mute(node))
            } catch (ex: RemoteException) {
                Logger.e(ex) { "Mute node error" }
            }
        }
    }

    fun favoriteNode(scope: CoroutineScope, node: Node) {
        scope.launch(Dispatchers.IO) {
            try {
                serviceRepository.onServiceAction(ServiceAction.Favorite(node))
            } catch (ex: RemoteException) {
                Logger.e(ex) { "Favorite node error" }
            }
        }
    }

    fun setNodeNotes(scope: CoroutineScope, nodeNum: Int, notes: String) {
        scope.launch(Dispatchers.IO) {
            try {
                nodeRepository.setNodeNotes(nodeNum, notes)
                _effects.emit(NodeRequestEffect.ShowFeedback(Res.string.notes_saved))
            } catch (ex: java.io.IOException) {
                Logger.e { "Set node notes IO error: ${ex.message}" }
            } catch (ex: java.sql.SQLException) {
                Logger.e { "Set node notes SQL error: ${ex.message}" }
            }
        }
    }
}
