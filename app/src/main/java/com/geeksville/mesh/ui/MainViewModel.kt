package com.geeksville.mesh.ui

import android.app.Application
import android.content.SharedPreferences
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.geeksville.mesh.AppOnlyProtos
import com.geeksville.mesh.LocalOnlyProtos
import com.geeksville.mesh.MeshProtos
import com.geeksville.mesh.R
import com.geeksville.mesh.database.NodeRepository
import com.geeksville.mesh.model.Node
import com.geeksville.mesh.repository.datastore.RadioConfigRepository
import com.geeksville.mesh.service.MeshService
import com.geeksville.mesh.service.MeshServiceNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject

// Using UIViewModel's AlertData for now, might move it here later if appropriate
import com.geeksville.mesh.model.UIViewModel.AlertData // Keep if AlertData is not moved from UIViewModel yet
import com.geeksville.mesh.model.toChannelSet // For requestChannelUrl
import com.geeksville.mesh.database.entity.asDeviceVersion
import com.geeksville.mesh.model.DeviceVersion
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.geeksville.mesh.model.Node // For Node actions
import com.geeksville.mesh.Position // For requestPosition
import com.geeksville.mesh.repository.radio.ServiceAction // For ignoreNode, favoriteNode
import com.geeksville.mesh.ui.node.components.NodeMenuAction // For handleNodeMenuAction
import kotlinx.coroutines.Dispatchers // For IO dispatcher
import android.os.RemoteException // For service calls
import com.geeksville.mesh.android.Logging // For logging, if implemented
import com.geeksville.mesh.DataPacket // For sendDataPacket


@HiltViewModel
class MainViewModel @Inject constructor(
    private val app: Application,
    private val preferences: SharedPreferences,
    private val nodeRepository: NodeRepository,
    private val radioConfigRepository: RadioConfigRepository,
    private val meshServiceNotifications: MeshServiceNotifications, // For client notifications
    private val firmwareReleaseRepository: com.geeksville.mesh.repository.api.FirmwareReleaseRepository, // Added for latestStableFirmwareRelease
    private val locationRepository: com.geeksville.mesh.repository.location.LocationRepository // Added for receivingLocationUpdates
) : ViewModel() {

    // Theme management
    private val _theme = MutableStateFlow(preferences.getInt("theme", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    val theme: StateFlow<Int> = _theme.asStateFlow()

    fun setTheme(themeValue: Int) {
        _theme.value = themeValue
        preferences.edit().putInt("theme", themeValue).apply()
        // AppCompatDelegate.setDefaultNightMode(themeValue) // This would typically be called in Activity or Application class
    }

    // Alert management
    // Copied AlertData from UIViewModel, consider if it should be a top-level class or nested if only used here.
    data class AlertData(
        val title: String,
        val message: String? = null,
        val html: String? = null,
        val onConfirm: (() -> Unit)? = null,
        val onDismiss: (() -> Unit)? = null,
        val choices: Map<String, () -> Unit> = emptyMap(),
    )

    private val _currentAlert: MutableStateFlow<AlertData?> = MutableStateFlow(null)
    val currentAlert: StateFlow<AlertData?> = _currentAlert.asStateFlow()

    fun showAlert(
        title: String,
        message: String? = null,
        html: String? = null,
        onConfirm: (() -> Unit)? = {},
        dismissable: Boolean = true, // In UIViewModel this was 'dismissable', ensuring consistency
        choices: Map<String, () -> Unit> = emptyMap(),
    ) {
        _currentAlert.value =
            AlertData(
                title = title,
                message = message,
                html = html,
                onConfirm = {
                    onConfirm?.invoke()
                    dismissAlert()
                },
                onDismiss = {
                    if (dismissable) dismissAlert()
                },
                choices = choices,
            )
    }

    private fun dismissAlert() {
        _currentAlert.value = null
    }

    // Title management
    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title.asStateFlow()
    fun setTitle(titleValue: String) {
        viewModelScope.launch { // As it was in UIViewModel
            _title.value = titleValue
        }
    }

    // Connection state and radio configurations
    val connectionState: StateFlow<MeshService.ConnectionState> get() = radioConfigRepository.connectionState
    fun isConnected() = connectionState.value != MeshService.ConnectionState.DISCONNECTED
    val isConnectedFlow: StateFlow<Boolean> = // Renamed from isConnected to avoid conflict with function
        radioConfigRepository.connectionState.map { it != MeshService.ConnectionState.DISCONNECTED }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val meshService: com.geeksville.mesh.IMeshService? get() = radioConfigRepository.meshService

    val clientNotification: StateFlow<MeshProtos.ClientNotification?> =
        radioConfigRepository.clientNotification

    fun clearClientNotification(notification: MeshProtos.ClientNotification) {
        radioConfigRepository.clearClientNotification()
        meshServiceNotifications.clearClientNotification(notification) // Assuming meshServiceNotifications is injected
    }

    private val _localConfig = MutableStateFlow<LocalOnlyProtos.LocalConfig>(LocalOnlyProtos.LocalConfig.getDefaultInstance())
    val localConfig: StateFlow<LocalOnlyProtos.LocalConfig> = _localConfig.asStateFlow()

    private val _moduleConfig = MutableStateFlow<LocalOnlyProtos.LocalModuleConfig>(LocalOnlyProtos.LocalModuleConfig.getDefaultInstance())
    val moduleConfig: StateFlow<LocalOnlyProtos.LocalModuleConfig> = _moduleConfig.asStateFlow()

    private val _channels = MutableStateFlow(AppOnlyProtos.channelSet {})
    val channels: StateFlow<AppOnlyProtos.ChannelSet> get() = _channels.asStateFlow() // Ensure .asStateFlow() for public

    // MyNodeInfo (global sense of self)
    val myNodeInfo: StateFlow<com.geeksville.mesh.database.entity.MyNodeEntity?> = nodeRepository.myNodeInfo.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )


    init {
        // Copied from UIViewModel init block for radio config parts
        radioConfigRepository.errorMessage.filterNotNull().onEach {
            showAlert( // Assuming showAlert is now part of MainViewModel
                title = app.getString(R.string.client_notification),
                message = it,
                onConfirm = {
                    radioConfigRepository.clearErrorMessage()
                },
                dismissable = false
            )
        }.launchIn(viewModelScope)

        radioConfigRepository.localConfigFlow.onEach { config ->
            _localConfig.value = config
        }.launchIn(viewModelScope)
        radioConfigRepository.moduleConfigFlow.onEach { config ->
            _moduleConfig.value = config
        }.launchIn(viewModelScope)
        radioConfigRepository.channelSetFlow.onEach { channelSet ->
            _channels.value = channelSet
        }.launchIn(viewModelScope)
    }


    // Snackbar management
    val snackbarState = SnackbarHostState()
    fun showSnackbar(text: Int) = showSnackbar(app.getString(text))
    fun showSnackbar(text: String) = viewModelScope.launch {
        snackbarState.showSnackbar(text)
    }

    private val _excludedModulesUnlocked = MutableStateFlow(false) // Assuming default false if not in prefs
    val excludedModulesUnlocked: StateFlow<Boolean> = _excludedModulesUnlocked.asStateFlow()

    fun unlockExcludedModules() {
        viewModelScope.launch {
            _excludedModulesUnlocked.value = true
            // preferences.edit().putBoolean("excluded_modules_unlocked", true).apply() // Optional: persist if needed
        }
    }

    // Channel URL import
    private val _requestChannelSet = MutableStateFlow<AppOnlyProtos.ChannelSet?>(null)
    val requestChannelSet: StateFlow<AppOnlyProtos.ChannelSet?> get() = _requestChannelSet.asStateFlow()

    fun requestChannelUrl(url: android.net.Uri) = kotlin.runCatching {
        // Assuming com.geeksville.mesh.model.toChannelSet can be imported or moved.
        _requestChannelSet.value = com.geeksville.mesh.model.toChannelSet(url)
    }.onFailure { ex ->
        showSnackbar(app.getString(R.string.channel_invalid))
        // errormsg("Channel url error: ${ex.message}") // If Logging is implemented
         _requestChannelSet.value = null // Ensure it's nulled out on error
    }

    fun clearRequestChannelUrl() {
        _requestChannelSet.value = null
    }

    val latestStableFirmwareRelease: StateFlow<com.geeksville.mesh.model.DeviceVersion?> =
        firmwareReleaseRepository.stableRelease.mapNotNull { release ->
            // Assuming com.geeksville.mesh.database.entity.asDeviceVersion can be imported or moved
            release?.let { com.geeksville.mesh.database.entity.asDeviceVersion(it) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Node actions moved from NodeViewModel (and originally UIViewModel)
    private val _lastTraceRouteTime = MutableStateFlow<Long?>(null)
    val lastTraceRouteTime: StateFlow<Long?> = _lastTraceRouteTime.asStateFlow()

    fun removeNode(nodeNum: Int) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val packetId = radioConfigRepository.meshService?.packetId ?: return@launch
            radioConfigRepository.meshService?.removeByNodenum(packetId, nodeNum)
            nodeRepository.deleteNode(nodeNum)
            // Logging.info("Removing node '$nodeNum'") // Add logging if MainViewModel implements Logging
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Remove node error: ${ex.message}")
        }
    }

    fun ignoreNode(node: Node) = viewModelScope.launch {
        try {
            radioConfigRepository.onServiceAction(com.geeksville.mesh.repository.radio.ServiceAction.Ignore(node))
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Ignore node error:", ex)
        }
    }

    fun favoriteNode(node: Node) = viewModelScope.launch {
        try {
            radioConfigRepository.onServiceAction(com.geeksville.mesh.repository.radio.ServiceAction.Favorite(node))
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Favorite node error:", ex)
        }
    }

    fun requestUserInfo(destNum: Int) {
        try {
            radioConfigRepository.meshService?.requestUserInfo(destNum)
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Request NodeInfo error: ${ex.message}")
        }
    }

    fun requestPosition(destNum: Int, position: com.geeksville.mesh.Position = com.geeksville.mesh.Position.newBuilder().build()) {
        try {
            radioConfigRepository.meshService?.requestPosition(destNum, position)
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Request position error: ${ex.message}")
        }
    }

    fun requestTraceroute(destNum: Int) {
        try {
            val packetId = radioConfigRepository.meshService?.packetId ?: return
            radioConfigRepository.meshService?.requestTraceroute(packetId, destNum)
            _lastTraceRouteTime.value = System.currentTimeMillis()
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Request traceroute error: ${ex.message}")
        }
    }

    // This will need NodeMenuAction to be accessible, e.g. moved to a common place or fully qualified path
    // For now, assuming com.geeksville.mesh.ui.node.components.NodeMenuAction
    fun handleNodeMenuAction(
        action: com.geeksville.mesh.ui.node.components.NodeMenuAction,
        // Potentially add navigation lambdas if DirectMessage/MoreDetails are handled here
        // navigateToMessages: (String) -> Unit,
        // navigateToNodeDetails: (Int) -> Unit,
        // showSharedContact: (Node) -> Unit
    ) {
        when (action) {
            is com.geeksville.mesh.ui.node.components.NodeMenuAction.Remove -> removeNode(action.node.num)
            is com.geeksville.mesh.ui.node.components.NodeMenuAction.Ignore -> ignoreNode(action.node)
            is com.geeksville.mesh.ui.node.components.NodeMenuAction.Favorite -> favoriteNode(action.node)
            is com.geeksville.mesh.ui.node.components.NodeMenuAction.RequestUserInfo -> requestUserInfo(action.node.num)
            is com.geeksville.mesh.ui.node.components.NodeMenuAction.RequestPosition -> requestPosition(action.node.num)
            is com.geeksville.mesh.ui.node.components.NodeMenuAction.TraceRoute -> {
                requestTraceroute(action.node.num)
            }
            // Navigation/UI specific actions would typically be handled by the calling screen
            // is com.geeksville.mesh.ui.node.components.NodeMenuAction.DirectMessage -> navigateToMessages(...)
            // is com.geeksville.mesh.ui.node.components.NodeMenuAction.MoreDetails -> navigateToNodeDetails(...)
            // is com.geeksville.mesh.ui.node.components.NodeMenuAction.Share -> showSharedContact(...)
            else -> { /* Logging.debug("Unhandled NodeMenuAction in MainViewModel: $action") */ }
        }
    }


    fun generatePacketId(): Int? {
        return try {
            meshService?.packetId
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("RemoteException: ${ex.message}") // Add logging if MainViewModel implements Logging
            null
        }
    }

    fun sendDataPacket(p: com.geeksville.mesh.DataPacket) { // Ensure DataPacket is imported
        try {
            meshService?.send(p)
        } catch (ex: android.os.RemoteException) {
            // Logging.errormsg("Send DataPacket error: ${ex.message}")
        }
    }

    // Provide Location logic
    private fun getProvidePref(nodeNum: Int?): Boolean {
        return preferences.getBoolean("provide-location-$nodeNum", false)
    }

    private val _provideLocation = MutableStateFlow(false) // Initialized with a default
    val provideLocation: StateFlow<Boolean> get() = _provideLocation.asStateFlow()

    init { // Moved preference loading to init and on myNodeInfo change
        viewModelScope.launch {
            myNodeInfo.onEach { entity ->
                _provideLocation.value = getProvidePref(entity?.myNodeNum)
            }.launchIn(this)
        }
        // Initial call for refresh in case myNodeInfo is already populated or becomes populated quickly.
        refreshProvideLocation()
    }

    fun refreshProvideLocation() { // Simpler refresh
        _provideLocation.value = getProvidePref(myNodeInfo.value?.myNodeNum)
    }

    fun setProvideLocation(value: Boolean) {
        viewModelScope.launch {
            val currentMyNodeNum = myNodeInfo.value?.myNodeNum
            if (currentMyNodeNum != null) { // Only save if we have a node number
                preferences.edit().putBoolean("provide-location-$currentMyNodeNum", value).apply()
            }
            _provideLocation.value = value
            if (value) {
                meshService?.startProvideLocation()
            } else {
                meshService?.stopProvideLocation()
            }
        }
    }

    // Receiving location updates
    val receivingLocationUpdates: StateFlow<Boolean> get() = locationRepository.receivingLocationUpdates


    val isManaged: Boolean
        get() = localConfig.value.device.isManaged || localConfig.value.security.isManaged

    val tracerouteResponse: StateFlow<String?> get() = radioConfigRepository.tracerouteResponse

    fun clearTracerouteResponse() {
        radioConfigRepository.clearTracerouteResponse()
    }

    // For MainAppBar
    val onlineNodeCount: StateFlow<Int> = nodeRepository.onlineNodeCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    val totalNodeCount: StateFlow<Int> = nodeRepository.totalNodeCount.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0
    )
    val myNodeInfoAsNode: StateFlow<Node?> = nodeRepository.ourNodeInfo.stateIn( // ourNodeInfo from repo gives Node?
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )


    // TODO: Move other global states and actions from UIViewModel here
    // fun showSnackbar(text: Int) = showSnackbar(app.getString(text))
    // fun showSnackbar(text: String) = viewModelScope.launch {
    //     snackbarState.showSnackbar(text)
    // }
}
