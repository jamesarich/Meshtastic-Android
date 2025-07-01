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

package com.geeksville.mesh.ui.contact

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.database.QuickChatActionRepository
import com.geeksville.mesh.database.entity.Packet
import com.geeksville.mesh.database.entity.QuickChatAction
import com.geeksville.mesh.model.Message
import com.geeksville.mesh.model.getChannel
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.ServiceAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ContactViewModel @Inject constructor(
    private val app: Application, // For string resources, etc.
    private val nodeDB: NodeRepository,
    private val packetRepository: PacketRepository,
    private val quickChatActionRepository: QuickChatActionRepository,
    private val radioConfigRepository: RadioConfigRepository, // For meshService, channelSetFlow
    private val meshServiceNotifications: com.geeksville.mesh.service.MeshServiceNotifications // Added dependency
) : ViewModel() {

    // Moved from UIState.kt
    data class Contact(
        val contactKey: String,
        val shortName: String,
        val longName: String,
        val lastMessageTime: String?,
        val lastMessageText: String?,
        val unreadCount: Int,
        val messageCount: Int,
        val isMuted: Boolean,
        val isUnmessageable: Boolean,
        val nodeColors: Pair<Int, Int>? = null,
        val isDefaultPSK: Boolean? = false
    )

    fun getNode(userId: String?) = nodeDB.getNode(userId ?: DataPacket.ID_BROADCAST)
    fun getUser(userId: String?) = nodeDB.getUser(userId ?: DataPacket.ID_BROADCAST)
    val contactList: StateFlow<List<Contact>> = combine(
        nodeDB.myNodeInfo,
        packetRepository.getContacts(),
        radioConfigRepository.channelSetFlow, // Assuming channels come from radioConfigRepository
        packetRepository.getContactSettings(),
    ) { myNodeInfo, contacts, channelSet, settings ->
        val myNodeNum = myNodeInfo?.myNodeNum ?: return@combine emptyList()
        // Add empty channel placeholders (always show Broadcast contacts, even when empty)
        val placeholder = (0 until channelSet.settingsCount).associate { ch ->
            val contactKey = "$ch${DataPacket.ID_BROADCAST}"
            val data = DataPacket(bytes = null, dataType = 1, time = 0L, channel = ch)
            contactKey to Packet(0L, myNodeNum, 1, contactKey, 0L, true, data)
        }

        (contacts + (placeholder - contacts.keys)).values.map { packet ->
            val data = packet.data
            val contactKey = packet.contact_key

            val fromLocal = data.from == DataPacket.ID_LOCAL
            val toBroadcast = data.to == DataPacket.ID_BROADCAST

            val user = getUser(if (fromLocal) data.to else data.from)
            val node = getNode(if (fromLocal) data.to else data.from)

            val shortName = user.shortName
            val longName = if (toBroadcast) {
                channelSet.getChannel(data.channel)?.name ?: app.getString(com.geeksville.mesh.R.string.channel_name)
            } else {
                user.longName
            }
            val isDefaultPSK = if (toBroadcast) {
                val _channel = channelSet.getChannel(data.channel)
                (_channel?.settings?.psk?.size() == 1 &&
                        _channel.settings.psk.byteAt(0) == 1.toByte()) ||
                        _channel?.settings?.psk?.toByteArray()?.isEmpty() == true
            } else {
                false
            }

            Contact(
                contactKey = contactKey,
                shortName = if (toBroadcast) "${data.channel}" else shortName,
                longName = longName,
                lastMessageTime = com.geeksville.mesh.util.getShortDate(data.time),
                lastMessageText = if (fromLocal) data.text else "$shortName: ${data.text}",
                unreadCount = packetRepository.getUnreadCount(contactKey),
                messageCount = packetRepository.getMessageCount(contactKey),
                isMuted = settings[contactKey]?.isMuted == true,
                isUnmessageable = user.isUnmessagable,
                nodeColors = if (!toBroadcast) node.colors else null,
                isDefaultPSK = isDefaultPSK
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _contactKeyForMessages: MutableStateFlow<String?> = MutableStateFlow(null)
    val messagesForContactKey: StateFlow<List<Message>> =
        _contactKeyForMessages.filterNotNull().flatMapLatest { contactKey ->
            // Assuming packetRepository.getMessagesFrom can correctly resolve getNode
            // This might require passing nodeDB::getNode or similar if it was a direct reference
            packetRepository.getMessagesFrom(contactKey, ::getNode)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun getMessagesFrom(contactKey: String): StateFlow<List<Message>> {
        _contactKeyForMessages.value = contactKey
        return messagesForContactKey
    }

    // generatePacketId moved to MainViewModel
    // sendDataPacket moved to MainViewModel

    fun sendMessage(str: String, contactKey: String = "0${DataPacket.ID_BROADCAST}", replyId: Int? = null) {
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        if (channel == null) { // Direct message
            val node = nodeDB.getNode(dest)
            if (!node.isFavorite) {
                viewModelScope.launch { radioConfigRepository.onServiceAction(ServiceAction.Favorite(node)) }
            }
        }
        val p = DataPacket(dest, channel ?: 0, str, replyId)
        // Call MainViewModel's sendDataPacket or a repository equivalent.
        // For now, assuming a way to access it, or this needs further refactoring if MainViewModel isn't directly injectable here.
        // This might mean ContactViewModel needs to inject MainViewModel or a shared service.
        // Temporary: radioConfigRepository.meshService?.send(p)
        try {
            radioConfigRepository.meshService?.send(p) // Direct send, as sendDataPacket was just a wrapper.
        } catch (ex: android.os.RemoteException) {
            // log error
        }
    }

    fun sendWaypoint(wpt: com.geeksville.mesh.MeshProtos.Waypoint, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        val p = DataPacket(dest, channel ?: 0, wpt)
        if (wpt.id != 0) {
            try {
                radioConfigRepository.meshService?.send(p) // Direct send
            } catch (ex: android.os.RemoteException) {
                // log error
            }
        }
    }

    fun sendReaction(emoji: String, replyId: Int, contactKey: String) = viewModelScope.launch {
        radioConfigRepository.onServiceAction(ServiceAction.Reaction(emoji, replyId, contactKey))
    }

    fun addSharedContact(sharedContact: com.geeksville.mesh.AdminProtos.SharedContact) = viewModelScope.launch {
        radioConfigRepository.onServiceAction(ServiceAction.AddSharedContact(sharedContact))
    }

    fun setMuteUntil(contacts: List<String>, until: Long) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        packetRepository.setMuteUntil(contacts, until)
    }

    fun deleteContacts(contacts: List<String>) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        packetRepository.deleteContacts(contacts)
    }

    fun deleteMessages(uuidList: List<Long>) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        packetRepository.deleteMessages(uuidList)
    }

    fun clearUnreadCount(contact: String, timestamp: Long) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        packetRepository.clearUnreadCount(contact, timestamp)
        val unreadCount = packetRepository.getUnreadCount(contact)
        if (unreadCount == 0) meshServiceNotifications.cancelMessageNotification(contact)
    }

    val quickChatActions: StateFlow<List<QuickChatAction>> =
        quickChatActionRepository.getAllActions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun addQuickChatAction(action: QuickChatAction) = viewModelScope.launch(Dispatchers.IO) {
        quickChatActionRepository.upsert(action)
    }

    fun deleteQuickChatAction(action: QuickChatAction) = viewModelScope.launch(Dispatchers.IO) {
        quickChatActionRepository.delete(action)
    }

    fun updateActionPositions(actions: List<QuickChatAction>) = viewModelScope.launch(Dispatchers.IO) {
        for (position in actions.indices) {
            quickChatActionRepository.setItemPosition(actions[position].uuid, position)
        }
    }
}
