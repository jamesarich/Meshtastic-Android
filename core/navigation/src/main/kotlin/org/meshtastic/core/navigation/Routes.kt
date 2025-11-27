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

package org.meshtastic.core.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

const val DEEP_LINK_BASE_URI = "meshtastic://meshtastic"

interface Route : NavKey

interface Graph : Route

sealed interface ChannelsRoute : Route
object ChannelsRoutes {
    @Serializable data object ChannelsGraph : Graph, ChannelsRoute

    @Serializable data object Channels : ChannelsRoute
}

sealed interface ConnectionsRoute : Route
object ConnectionsRoutes {
    @Serializable data object ConnectionsGraph : Graph, ConnectionsRoute

    @Serializable data object Connections : ConnectionsRoute
}

sealed interface ContactsRoute : Route
object ContactsRoutes {
    @Serializable data object ContactsGraph : Graph, ContactsRoute

    @Serializable data object Contacts : ContactsRoute

    @Serializable data class Messages(val contactKey: String, val message: String = "") : ContactsRoute

    @Serializable data class Share(val message: String) : Route

    @Serializable data object QuickChat : Route
}

sealed interface MapRoute : Route
object MapRoutes {
    @Serializable data object Map : MapRoute
}

sealed interface NodesRoute : Route
object NodesRoutes {
    @Serializable data object NodesGraph : Graph, NodesRoute

    @Serializable data object Nodes : NodesRoute

    @Serializable data class NodeDetailGraph(val destNum: Int? = null) : Graph, NodesRoute

    @Serializable data class NodeDetail(val destNum: Int? = null) : NodesRoute
}

object NodeDetailRoutes {
    @Serializable data object DeviceMetrics : NodesRoute

    @Serializable data object NodeMap : NodesRoute

    @Serializable data object PositionLog : NodesRoute

    @Serializable data object EnvironmentMetrics : NodesRoute

    @Serializable data object SignalMetrics : NodesRoute

    @Serializable data object PowerMetrics : NodesRoute

    @Serializable data object TracerouteLog : NodesRoute

    @Serializable data object HostMetricsLog : NodesRoute

    @Serializable data object PaxMetrics : NodesRoute
}

sealed interface SettingsRoute : Route
object SettingsRoutes {
    @Serializable data class SettingsGraph(val destNum: Int? = null) : Graph, SettingsRoute

    @Serializable data class Settings(val destNum: Int? = null) : SettingsRoute

    // region radio Config Routes

    @Serializable data object User : SettingsRoute

    @Serializable data object ChannelConfig : SettingsRoute

    @Serializable data object Device : SettingsRoute

    @Serializable data object Position : SettingsRoute

    @Serializable data object Power : SettingsRoute

    @Serializable data object Network : SettingsRoute

    @Serializable data object Display : SettingsRoute

    @Serializable data object LoRa : SettingsRoute

    @Serializable data object Bluetooth : SettingsRoute

    @Serializable data object Security : SettingsRoute

    // endregion

    // region module config routes

    @Serializable data object MQTT : SettingsRoute

    @Serializable data object Serial : SettingsRoute

    @Serializable data object ExtNotification : SettingsRoute

    @Serializable data object StoreForward : SettingsRoute

    @Serializable data object RangeTest : SettingsRoute

    @Serializable data object Telemetry : SettingsRoute

    @Serializable data object CannedMessage : SettingsRoute

    @Serializable data object Audio : SettingsRoute

    @Serializable data object RemoteHardware : SettingsRoute

    @Serializable data object NeighborInfo : SettingsRoute

    @Serializable data object AmbientLighting : SettingsRoute

    @Serializable data object DetectionSensor : SettingsRoute

    @Serializable data object Paxcounter : SettingsRoute

    // endregion

    // region advanced config routes

    @Serializable data object CleanNodeDb : SettingsRoute

    @Serializable data object DebugPanel : SettingsRoute

    // endregion
}

sealed interface FirmwareRoute : Route
object FirmwareRoutes {
    @Serializable data object FirmwareGraph : Graph, FirmwareRoute

    @Serializable data object FirmwareUpdate : FirmwareRoute
}
