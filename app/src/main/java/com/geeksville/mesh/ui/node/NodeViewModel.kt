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

package com.geeksville.mesh.ui.node

import android.content.SharedPreferences
import android.os.RemoteException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.model.NodeSortOption
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.ServiceAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// Moved from UIState.kt
data class NodesUiState(
    val sort: NodeSortOption = NodeSortOption.LAST_HEARD,
    val filter: String = "",
    val includeUnknown: Boolean = false,
    val onlyOnline: Boolean = false,
    val onlyDirect: Boolean = false,
    val gpsFormat: Int = 0,
    val distanceUnits: Int = 0,
    val tempInFahrenheit: Boolean = false,
    val showDetails: Boolean = false,
) {
    companion object {
        val Empty = NodesUiState()
    }
}

@HiltViewModel
class NodeViewModel @Inject constructor(
    private val nodeDB: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository, // For actions like favoriteNode, ignoreNode
    private val preferences: SharedPreferences
) : ViewModel(), Logging {

    private val nodeFilterText = MutableStateFlow(preferences.getString("node-filter-text", "") ?: "")
    private val nodeSortOption = MutableStateFlow(
        NodeSortOption.entries.getOrElse(
            preferences.getInt("node-sort-option", NodeSortOption.VIA_FAVORITE.ordinal)
        ) { NodeSortOption.VIA_FAVORITE }
    )
    private val includeUnknown = MutableStateFlow(preferences.getBoolean("include-unknown", false))
    private val showDetails = MutableStateFlow(preferences.getBoolean("show-details", false))
    private val onlyOnline = MutableStateFlow(preferences.getBoolean("only-online", false))
    private val onlyDirect = MutableStateFlow(preferences.getBoolean("only-direct", false))

    fun setNodeFilterText(text: String) {
        nodeFilterText.value = text
        preferences.edit().putString("node-filter-text", text).apply()
    }

    fun setSortOption(sort: NodeSortOption) {
        nodeSortOption.value = sort
        preferences.edit().putInt("node-sort-option", sort.ordinal).apply()
    }

    fun toggleShowDetails() {
        showDetails.value = !showDetails.value
        preferences.edit().putBoolean("show-details", showDetails.value).apply()
    }

    fun toggleIncludeUnknown() {
        includeUnknown.value = !includeUnknown.value
        preferences.edit().putBoolean("include-unknown", includeUnknown.value).apply()
    }

    fun toggleOnlyOnline() {
        onlyOnline.value = !onlyOnline.value
        preferences.edit().putBoolean("only-online", onlyOnline.value).apply()
    }

    fun toggleOnlyDirect() {
        onlyDirect.value = !onlyDirect.value
        preferences.edit().putBoolean("only-direct", onlyDirect.value).apply()
    }

    data class NodeFilterState(
        val filterText: String,
        val includeUnknown: Boolean,
        val onlyOnline: Boolean,
        val onlyDirect: Boolean,
    )

    private val nodeFilterStateFlow: Flow<NodeFilterState> = combine(
        nodeFilterText,
        includeUnknown,
        onlyOnline,
        onlyDirect,
    ) { filterText, includeUnknown, onlyOnline, onlyDirect ->
        NodeFilterState(filterText, includeUnknown, onlyOnline, onlyDirect)
    }

    val nodesUiState: StateFlow<NodesUiState> = combine(
        nodeFilterStateFlow,
        nodeSortOption,
        showDetails,
        radioConfigRepository.deviceProfileFlow, // Assuming this is needed for gpsFormat etc.
    ) { filterFlow, sort, details, profile ->
        NodesUiState(
            sort = sort,
            filter = filterFlow.filterText,
            includeUnknown = filterFlow.includeUnknown,
            onlyOnline = filterFlow.onlyOnline,
            onlyDirect = filterFlow.onlyDirect,
            gpsFormat = profile.config.display.gpsFormat.number,
            distanceUnits = profile.config.display.units.number,
            tempInFahrenheit = profile.moduleConfig.telemetry.environmentDisplayFahrenheit,
            showDetails = details,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NodesUiState.Empty,
    )

    val unfilteredNodeList: StateFlow<List<Node>> = nodeDB.getNodes().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val nodeList: StateFlow<List<Node>> = nodesUiState.flatMapLatest { state ->
        nodeDB.getNodes(state.sort, state.filter, state.includeUnknown, state.onlyOnline, state.onlyDirect)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    val onlineNodeCount: StateFlow<Int> = nodeDB.onlineNodeCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val totalNodeCount: StateFlow<Int> = nodeDB.totalNodeCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0,
    )

    val filteredNodeList: StateFlow<List<Node>> = nodeList.mapLatest { list ->
        list.filter { node ->
            !node.isIgnored
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    // _lastTraceRouteTime and related actions (removeNode, ignoreNode, favoriteNode, requestUserInfo, requestPosition, requestTraceroute, handleNodeMenuAction)
    // have been moved to MainViewModel.

    // Potentially needed by NodeScreen or related composables
    val ourNodeInfo: StateFlow<Node?> get() = nodeDB.ourNodeInfo

    fun addSharedContact(sharedContact: com.geeksville.mesh.AdminProtos.SharedContact) = viewModelScope.launch {
        try {
            radioConfigRepository.onServiceAction(ServiceAction.AddSharedContact(sharedContact))
        } catch (ex: RemoteException) {
            // Logging.errormsg("Add shared contact error:", ex) // Add logging if desired
        }
    }
}
