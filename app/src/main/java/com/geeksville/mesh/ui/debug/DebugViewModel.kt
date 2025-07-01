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

package com.geeksville.mesh.ui.debug // Updated package

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.android.Logging
import com.geeksville.mesh.database.MeshLogRepository
import com.geeksville.mesh.database.entity.MeshLog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import com.geeksville.mesh.Portnums.PortNum
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import android.net.Uri // For saveMessagesCSV
import com.geeksville.mesh.database.NodeRepository // For saveMessagesCSV
import com.geeksville.mesh.MeshProtos // For saveMessagesCSV (Position)
import com.geeksville.mesh.Position // For saveMessagesCSV (Position type)
import com.geeksville.mesh.util.positionToMeter // For saveMessagesCSV
import java.io.BufferedWriter // For saveMessagesCSV
import java.io.FileNotFoundException // For saveMessagesCSV
import java.io.FileWriter // For saveMessagesCSV
import java.text.SimpleDateFormat // For saveMessagesCSV
import kotlinx.coroutines.flow.first // For saveMessagesCSV
import kotlinx.coroutines.withContext // For saveMessagesCSV
import kotlin.math.roundToInt // For saveMessagesCSV


data class SearchMatch(
    val logIndex: Int,
    val start: Int,
    val end: Int,
    val field: String
)

data class SearchState(
    val searchText: String = "",
    val currentMatchIndex: Int = -1,
    val allMatches: List<SearchMatch> = emptyList(),
    val hasMatches: Boolean = false
)

// --- Search and Filter Managers ---
class LogSearchManager {
    data class SearchMatch(
        val logIndex: Int,
        val start: Int,
        val end: Int,
        val field: String
    )

    data class SearchState(
        val searchText: String = "",
        val currentMatchIndex: Int = -1,
        val allMatches: List<SearchMatch> = emptyList(),
        val hasMatches: Boolean = false
    )

    private val _searchText = MutableStateFlow("")
    val searchText = _searchText.asStateFlow()

    private val _currentMatchIndex = MutableStateFlow(-1)
    val currentMatchIndex = _currentMatchIndex.asStateFlow()

    private val _searchState = MutableStateFlow(SearchState())
    val searchState = _searchState.asStateFlow()

    fun setSearchText(text: String) {
        _searchText.value = text
        _currentMatchIndex.value = -1
    }

    fun goToNextMatch() {
        val matches = _searchState.value.allMatches
        if (matches.isNotEmpty()) {
            val nextIndex = if (_currentMatchIndex.value < matches.lastIndex) _currentMatchIndex.value + 1 else 0
            _currentMatchIndex.value = nextIndex
            _searchState.value = _searchState.value.copy(currentMatchIndex = nextIndex)
        }
    }

    fun goToPreviousMatch() {
        val matches = _searchState.value.allMatches
        if (matches.isNotEmpty()) {
            val prevIndex = if (_currentMatchIndex.value > 0) _currentMatchIndex.value - 1 else matches.lastIndex
            _currentMatchIndex.value = prevIndex
            _searchState.value = _searchState.value.copy(currentMatchIndex = prevIndex)
        }
    }

    fun clearSearch() {
        setSearchText("")
    }

    fun updateMatches(searchText: String, filteredLogs: List<DebugViewModel.UiMeshLog>) {
        val matches = findSearchMatches(searchText, filteredLogs)
        val hasMatches = matches.isNotEmpty()
        _searchState.value = _searchState.value.copy(
            searchText = searchText,
            allMatches = matches,
            hasMatches = hasMatches,
            currentMatchIndex = if (hasMatches) _currentMatchIndex.value.coerceIn(0, matches.lastIndex) else -1
        )
    }

    fun findSearchMatches(searchText: String, filteredLogs: List<DebugViewModel.UiMeshLog>): List<SearchMatch> {
        if (searchText.isEmpty()) {
            return emptyList()
        }
        return filteredLogs.flatMapIndexed { logIndex, log ->
            searchText.split(" ").flatMap { term ->
                val escapedTerm = Regex.escape(term)
                val regex = escapedTerm.toRegex(RegexOption.IGNORE_CASE)
                val messageMatches = regex.findAll(log.logMessage)
                    .map { match -> SearchMatch(logIndex, match.range.first, match.range.last, "message") }
                val typeMatches = regex.findAll(log.messageType)
                    .map { match -> SearchMatch(logIndex, match.range.first, match.range.last, "type") }
                val dateMatches = regex.findAll(log.formattedReceivedDate)
                    .map { match -> SearchMatch(logIndex, match.range.first, match.range.last, "date") }
                messageMatches + typeMatches + dateMatches
            }
        }.sortedBy { it.start }
    }
}

class LogFilterManager {
    private val _filterTexts = MutableStateFlow<List<String>>(emptyList())
    val filterTexts = _filterTexts.asStateFlow()

    private val _filteredLogs = MutableStateFlow<List<DebugViewModel.UiMeshLog>>(emptyList())
    val filteredLogs = _filteredLogs.asStateFlow()

    fun setFilterTexts(filters: List<String>) {
        _filterTexts.value = filters
    }

    fun updateFilteredLogs(logs: List<DebugViewModel.UiMeshLog>) {
        _filteredLogs.value = logs
    }
}

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val app: Application, // Added Application for contentResolver
    private val meshLogRepository: MeshLogRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val nodeRepository: NodeRepository // Added NodeRepository
) : ViewModel(), Logging {

    val meshLog: StateFlow<ImmutableList<UiMeshLog>> = meshLogRepository.getAllLogs()
        .map(::toUiState)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    // --- Managers ---
    val searchManager = LogSearchManager()
    val filterManager = LogFilterManager()

    val searchText get() = searchManager.searchText
    val currentMatchIndex get() = searchManager.currentMatchIndex
    val searchState get() = searchManager.searchState
    val filterTexts get() = filterManager.filterTexts
    val filteredLogs get() = filterManager.filteredLogs

    private val _selectedLogId = MutableStateFlow<String?>(null)
    val selectedLogId = _selectedLogId.asStateFlow()

    fun updateFilteredLogs(logs: List<UiMeshLog>) {
        filterManager.updateFilteredLogs(logs)
        searchManager.updateMatches(searchManager.searchText.value, logs)
    }

    init {
        debug("DebugViewModel created")
        viewModelScope.launch {
            combine(searchManager.searchText, filterManager.filteredLogs) { searchText, logs ->
                searchManager.findSearchMatches(searchText, logs)
            }.collect { matches ->
                searchManager.updateMatches(searchManager.searchText.value, filterManager.filteredLogs.value)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        debug("DebugViewModel cleared")
    }

    private fun toUiState(databaseLogs: List<MeshLog>) = databaseLogs.map { log ->
        UiMeshLog(
            uuid = log.uuid,
            messageType = log.message_type,
            formattedReceivedDate = TIME_FORMAT.format(log.received_date),
            logMessage = annotateMeshLogMessage(log),
        )
    }.toImmutableList()

    /**
     * Transform the input [MeshLog] by enhancing the raw message with annotations.
     */
    private fun annotateMeshLogMessage(meshLog: MeshLog): String {
        val annotated = when (meshLog.message_type) {
            "Packet" -> meshLog.meshPacket?.let { packet ->
                annotateRawMessage(meshLog.raw_message, packet.from, packet.to)
            }

            "NodeInfo" -> meshLog.nodeInfo?.let { nodeInfo ->
                annotateRawMessage(meshLog.raw_message, nodeInfo.num)
            }

            "MyNodeInfo" -> meshLog.myNodeInfo?.let { nodeInfo ->
                annotateRawMessage(meshLog.raw_message, nodeInfo.myNodeNum)
            }

            else -> null
        }
        return annotated ?: meshLog.raw_message
    }

    /**
     * Annotate the raw message string with the node IDs provided, in hex, if they are present.
     */
    private fun annotateRawMessage(rawMessage: String, vararg nodeIds: Int): String {
        val msg = StringBuilder(rawMessage)
        var mutated = false
        nodeIds.forEach { nodeId ->
            mutated = mutated or msg.annotateNodeId(nodeId)
        }
        return if (mutated) {
            return msg.toString()
        } else {
            rawMessage
        }
    }

    /**
     * Look for a single node ID integer in the string and annotate it with the hex equivalent
     * if found.
     */
    private fun StringBuilder.annotateNodeId(nodeId: Int): Boolean {
        val nodeIdStr = nodeId.toUInt().toString()
        indexOf(nodeIdStr).takeIf { it >= 0 }?.let { idx ->
            insert(idx + nodeIdStr.length, " (${nodeId.asNodeId()})")
            return true
        }
        return false
    }

    private fun Int.asNodeId(): String {
        return "!%08x".format(Locale.getDefault(), this)
    }

    fun deleteAllLogs() = viewModelScope.launch(Dispatchers.IO) {
        meshLogRepository.deleteAll()
    }

    @Immutable
    data class UiMeshLog(
        val uuid: String,
        val messageType: String,
        val formattedReceivedDate: String,
        val logMessage: String,
    )

    companion object {
        private val TIME_FORMAT = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
    }

    val presetFilters: List<String>
        get() = buildList {
            // Our address if available
            radioConfigRepository.myNodeInfo.value?.myNodeNum?.let { add("!%08x".format(it)) }
            // broadcast
            add("!ffffffff")
            // decoded
            add("decoded")
            // today (locale-dependent short date format)
            add(DateFormat.getDateInstance(DateFormat.SHORT).format(Date()))
            // Each app name
            addAll(PortNum.entries.map { it.name })
        }

    fun setSelectedLogId(id: String?) { _selectedLogId.value = id }

    // Copied from UIViewModel.kt
    /**
     * Write the persisted packet data out to a CSV file in the specified location.
     */
    fun saveMessagesCSV(uri: Uri) {
        viewModelScope.launch(Dispatchers.Main) {
            val myNodeNum = radioConfigRepository.myNodeInfo.value?.myNodeNum ?: return@launch

            val nodes = nodeRepository.nodeDBbyNum.value

            val positionToPos: (MeshProtos.Position?) -> com.geeksville.mesh.Position? = { meshPosition ->
                meshPosition?.let { com.geeksville.mesh.Position(it) }.takeIf {
                    it?.isValid() == true
                }
            }

            writeToUri(uri) { writer ->
                val nodePositions = mutableMapOf<Int, MeshProtos.Position?>()

                writer.appendLine("\"date\",\"time\",\"from\",\"sender name\",\"sender lat\",\"sender long\",\"rx lat\",\"rx long\",\"rx elevation\",\"rx snr\",\"distance\",\"hop limit\",\"payload\"")

                val dateFormat =
                    SimpleDateFormat("\"yyyy-MM-dd\",\"HH:mm:ss\"", Locale.getDefault())
                meshLogRepository.getAllLogsInReceiveOrder(Int.MAX_VALUE).first()
                    .forEach { packet ->
                        packet.nodeInfo?.let { nodeInfo ->
                            positionToPos.invoke(nodeInfo.position)?.let {
                                nodePositions[nodeInfo.num] = nodeInfo.position
                            }
                        }

                        packet.meshPacket?.let { proto ->
                            packet.position?.let { position ->
                                positionToPos.invoke(position)?.let {
                                    nodePositions[proto.from.takeIf { it != 0 } ?: myNodeNum] =
                                        position
                                }
                            }

                            if (proto.rxSnr != 0.0f) {
                                val rxDateTime = dateFormat.format(packet.received_date)
                                val rxFrom = proto.from.toUInt()
                                val senderName = nodes[proto.from]?.user?.longName ?: ""

                                val senderPosition = nodePositions[proto.from]
                                val senderPos = positionToPos.invoke(senderPosition)
                                val senderLat = senderPos?.latitude ?: ""
                                val senderLong = senderPos?.longitude ?: ""

                                val rxPosition = nodePositions[myNodeNum]
                                val rxPos = positionToPos.invoke(rxPosition)
                                val rxLat = rxPos?.latitude ?: ""
                                val rxLong = rxPos?.longitude ?: ""
                                val rxAlt = rxPos?.altitude ?: ""
                                val rxSnr = proto.rxSnr

                                val dist = if (senderPos == null || rxPos == null) {
                                    ""
                                } else {
                                    positionToMeter(
                                        rxPosition!!,
                                        senderPosition!!
                                    ).roundToInt().toString()
                                }

                                val hopLimit = proto.hopLimit

                                val payload = when {
                                    proto.decoded.portnumValue !in setOf(
                                        PortNum.TEXT_MESSAGE_APP_VALUE,
                                        PortNum.RANGE_TEST_APP_VALUE,
                                    ) -> "<${proto.decoded.portnum}>"
                                    proto.hasDecoded() -> proto.decoded.payload.toStringUtf8()
                                        .replace("\"", "\"\"")
                                    proto.hasEncrypted() -> "${proto.encrypted.size()} encrypted bytes"
                                    else -> ""
                                }
                                writer.appendLine("$rxDateTime,\"$rxFrom\",\"$senderName\",\"$senderLat\",\"$senderLong\",\"$rxLat\",\"$rxLong\",\"$rxAlt\",\"$rxSnr\",\"$dist\",\"$hopLimit\",\"$payload\"")
                            }
                        }
                    }
            }
        }
    }

    private suspend inline fun writeToUri(
        uri: Uri,
        crossinline block: suspend (BufferedWriter) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            try {
                app.contentResolver.openFileDescriptor(uri, "wt")?.use { parcelFileDescriptor ->
                    FileWriter(parcelFileDescriptor.fileDescriptor).use { fileWriter ->
                        BufferedWriter(fileWriter).use { writer ->
                            block.invoke(writer)
                        }
                    }
                }
            } catch (ex: FileNotFoundException) {
                errormsg("Can't write file error: ${ex.message}")
            }
        }
    }
}
