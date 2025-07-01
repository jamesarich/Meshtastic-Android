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

package com.geeksville.mesh.ui.map

import android.content.SharedPreferences
import android.os.RemoteException
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.DataPacket
import com.geeksville.mesh.IMeshService
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.android.BuildUtils.errormsg
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// From UIState.kt, specific to map filtering
data class MapFilterState(
    val onlyFavorites: Boolean,
    val showWaypoints: Boolean,
    val showPrecisionCircle: Boolean,
) {
    companion object {
        // Default values can be defined here if needed, e.g.
        // val DEFAULT = MapFilterState(onlyFavorites = false, showWaypoints = true, showPrecisionCircle = true)
    }
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val preferences: SharedPreferences,
    private val nodeRepository: NodeRepository,
    private val packetRepository: PacketRepository,
    private val radioConfigRepository: RadioConfigRepository,
) : ViewModel() {

    val meshService: IMeshService? get() = radioConfigRepository.meshService

    private val onlyFavorites = MutableStateFlow(preferences.getBoolean("only-favorites", false))
    private val showWaypointsOnMap = MutableStateFlow(preferences.getBoolean("show-waypoints-on-map", true))
    private val showPrecisionCircleOnMap = MutableStateFlow(preferences.getBoolean("show-precision-circle-on-map", true))

    fun setOnlyFavorites(value: Boolean) {
        onlyFavorites.value = value
        preferences.edit { putBoolean("only-favorites", value) }
    }

    fun setShowWaypointsOnMap(value: Boolean) {
        showWaypointsOnMap.value = value
        preferences.edit { putBoolean("show-waypoints-on-map", value) }
    }

    fun setShowPrecisionCircleOnMap(value: Boolean) {
        showPrecisionCircleOnMap.value = value
        preferences.edit { putBoolean("show-precision-circle-on-map", value) }
    }

    val mapFilterStateFlow: StateFlow<MapFilterState> = combine(
        onlyFavorites,
        showWaypointsOnMap,
        showPrecisionCircleOnMap,
    ) { favoritesOnly, showWaypoints, showPrecisionCircle ->
        MapFilterState(favoritesOnly, showWaypoints, showPrecisionCircle)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MapFilterState( // Initial value based on current pref values
            onlyFavorites.value,
            showWaypointsOnMap.value,
            showPrecisionCircleOnMap.value
        )
    )

    // Nodes with position - UIViewModel had: nodeDB.nodeDBbyNum.value.values.filter { it.validPosition != null }
    // This is a snapshot. A flow would be better. NodeRepository might need a getNodesWithPositionFlow().
    // For now, replicating the snapshot behavior, but this should be revisited for reactivity.
    val nodesWithPosition: List<Node>
        get() = nodeRepository.nodeDBbyNum.value.values.filter { it.validPosition != null }
    // A reactive version (if NodeRepository could support it, or by combining flows):
    // val nodesWithPositionFlow: StateFlow<List<Node>> = nodeRepository.getNodes() /* or a specific flow */
    //    .mapLatest { nodes -> nodes.filter { it.validPosition != null } }
    //    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())


    var mapStyleId: Int
        get() = preferences.getInt(MAP_STYLE_ID, 0) // Use imported constant
        set(value) = preferences.edit { putInt(MAP_STYLE_ID, value) }

    // Waypoints from UIState.kt: packetRepository.getWaypoints().mapLatest { ... }
    val waypoints: StateFlow<Map<Int, com.geeksville.mesh.database.entity.Packet>> = packetRepository.getWaypoints().mapLatest { list ->
        list.associateBy { packet -> packet.data.waypoint!!.id }
            .filterValues {
                it.data.waypoint!!.expire == 0 || it.data.waypoint!!.expire > System.currentTimeMillis() / 1000
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    fun deleteWaypoint(id: Int) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        packetRepository.deleteWaypoint(id)
    }


    private fun sendDataPacket(p: DataPacket) {
        try {
            meshService?.send(p)
        } catch (ex: RemoteException) {
            errormsg("Send DataPacket error: ${ex.message}")
        }
    }

    fun generatePacketId(): Int? {
        return try {
            meshService?.packetId
        } catch (ex: RemoteException) {
            errormsg("RemoteException: ${ex.message}")
            return null
        }
    }

    fun sendWaypoint(wpt: MeshProtos.Waypoint, contactKey: String = "0${DataPacket.ID_BROADCAST}") {
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        // If ID is 0, generate one. This mirrors logic from UIViewModel's EditWaypointDialog interaction.
        val finalWpt = if (wpt.id == 0) {
            wpt.toBuilder().setId(generatePacketId() ?: 0)
                .build() // Fallback to 0 if generatePacketId is null
        } else {
            wpt
        }

        if (finalWpt.id != 0) { // Ensure we have a valid ID before sending
            val p = DataPacket(dest, channel ?: 0, finalWpt)
            sendDataPacket(p)
        }
    }
}
