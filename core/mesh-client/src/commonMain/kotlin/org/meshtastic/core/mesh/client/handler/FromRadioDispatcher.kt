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
package org.meshtastic.core.mesh.client.handler

import co.touchlab.kermit.Logger
import org.meshtastic.proto.ClientNotification
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.ModuleConfig
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.QueueStatus

/**
 * Shared dispatcher for FromRadio messages. Extracted from the Android service layer so we can reuse the routing logic
 * across platforms.
 */
@Suppress("CyclomaticComplexMethod")
class FromRadioDispatcher(
    private val configFlowManager: ConfigFlowManager,
    private val configHandler: DeviceConfigHandler,
    private val mqttProxyHandler: MqttProxyHandler,
    private val queueStatusHandler: QueueStatusHandler,
    private val clientNotificationHandler: ClientNotificationHandler,
    private val statusMessageSink: StatusMessageSink,
    private val logger: Logger = Logger.withTag("FromRadioDispatcher"),
) {

    fun handle(message: FromRadio) {
        val myInfo = message.my_info
        val metadata = message.metadata
        val nodeInfo = message.node_info
        val configCompleteId = message.config_complete_id
        val mqttProxy = message.mqttClientProxyMessage
        val queueStatus = message.queueStatus
        val config = message.config
        val moduleConfig = message.moduleConfig
        val channel = message.channel
        val clientNotification = message.clientNotification

        when {
            myInfo != null -> configFlowManager.handleMyInfo(myInfo)
            metadata != null -> configFlowManager.handleLocalMetadata(metadata)
            nodeInfo != null -> {
                configFlowManager.handleNodeInfo(nodeInfo)
                statusMessageSink.updateStatusMessage("Nodes (${configFlowManager.newNodeCount})")
            }
            configCompleteId != null && configCompleteId > 0 -> configFlowManager.handleConfigComplete(configCompleteId)
            mqttProxy != null -> mqttProxyHandler.handleMqttProxyMessage(mqttProxy)
            queueStatus != null -> queueStatusHandler.handleQueueStatus(queueStatus)
            config != null -> configHandler.handleDeviceConfig(config)
            moduleConfig != null -> configHandler.handleModuleConfig(moduleConfig)
            channel != null -> configHandler.handleChannel(channel)
            clientNotification != null -> clientNotificationHandler.handleClientNotification(clientNotification)
            message.packet != null ||
                message.log_record != null ||
                message.rebooted != null ||
                message.xmodemPacket != null ||
                message.deviceuiConfig != null ||
                message.fileInfo != null -> {
                // handled elsewhere (MeshMessageProcessor et al)
            }

            else -> logger.d { "Ignoring unknown FromRadio variant" }
        }
    }

    interface ConfigFlowManager {
        val newNodeCount: Int

        fun handleMyInfo(myInfo: MyNodeInfo)

        fun handleLocalMetadata(metadata: org.meshtastic.proto.DeviceMetadata)

        fun handleNodeInfo(nodeInfo: NodeInfo)

        fun handleConfigComplete(configCompleteId: Int)
    }

    interface DeviceConfigHandler {
        fun handleDeviceConfig(config: Config)

        fun handleModuleConfig(config: ModuleConfig)

        fun handleChannel(channel: org.meshtastic.proto.Channel)
    }

    interface MqttProxyHandler {
        fun handleMqttProxyMessage(message: org.meshtastic.proto.MqttClientProxyMessage)
    }

    interface QueueStatusHandler {
        fun handleQueueStatus(queueStatus: QueueStatus)
    }

    interface ClientNotificationHandler {
        fun handleClientNotification(notification: ClientNotification)
    }

    interface StatusMessageSink {
        fun updateStatusMessage(message: String)
    }
}
