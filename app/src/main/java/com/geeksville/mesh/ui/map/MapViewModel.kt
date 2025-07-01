package com.geeksville.mesh.ui.map

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.database.PacketRepository
import com.geeksville.mesh.model.Node // May need this if nodesWithPosition returns List<Node>
import com.geeksville.mesh.MeshProtos // For Packet.MeshPacket if waypoints are raw packets
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.geeksville.mesh.ui.map.MAP_STYLE_ID // Import the constant
import com.geeksville.mesh.DataPacket // Import DataPacket

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
    private val mainViewModel: com.geeksville.mesh.ui.MainViewModel // Inject MainViewModel
) : ViewModel() {

    private val onlyFavorites = MutableStateFlow(preferences.getBoolean("only-favorites", false))
    private val showWaypointsOnMap = MutableStateFlow(preferences.getBoolean("show-waypoints-on-map", true))
    private val showPrecisionCircleOnMap = MutableStateFlow(preferences.getBoolean("show-precision-circle-on-map", true))

    fun setOnlyFavorites(value: Boolean) {
        onlyFavorites.value = value
        preferences.edit().putBoolean("only-favorites", value).apply()
    }

    fun setShowWaypointsOnMap(value: Boolean) {
        showWaypointsOnMap.value = value
        preferences.edit().putBoolean("show-waypoints-on-map", value).apply()
    }

    fun setShowPrecisionCircleOnMap(value: Boolean) {
        showPrecisionCircleOnMap.value = value
        preferences.edit().putBoolean("show-precision-circle-on-map", value).apply()
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
        set(value) = preferences.edit().putInt(MAP_STYLE_ID, value).apply()

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

    fun sendWaypoint(wpt: com.geeksville.mesh.MeshProtos.Waypoint, contactKey: String = "0${com.geeksville.mesh.DataPacket.ID_BROADCAST}") {
        val channel = contactKey[0].digitToIntOrNull()
        val dest = if (channel != null) contactKey.substring(1) else contactKey

        // If ID is 0, generate one. This mirrors logic from UIViewModel's EditWaypointDialog interaction.
        val finalWpt = if (wpt.id == 0) {
            wpt.toBuilder().setId(mainViewModel.generatePacketId() ?: 0).build() // Fallback to 0 if generatePacketId is null
        } else {
            wpt
        }

        if (finalWpt.id != 0) { // Ensure we have a valid ID before sending
            val p = com.geeksville.mesh.DataPacket(dest, channel ?: 0, finalWpt)
            mainViewModel.sendDataPacket(p)
        } else {
            // Log error or show snackbar: could not generate packet ID
            mainViewModel.showSnackbar("Could not generate waypoint ID.")
        }
    }
}
