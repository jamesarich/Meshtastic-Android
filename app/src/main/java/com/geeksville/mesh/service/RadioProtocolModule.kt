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
package com.geeksville.mesh.service

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.mesh.client.handler.FromRadioDispatcher
import org.meshtastic.core.mesh.client.protocol.AndroidFromRadioParser
import org.meshtastic.core.mesh.client.protocol.FromRadioParser
import org.meshtastic.core.mesh.client.protocol.RadioProtocolManager
import org.meshtastic.core.service.MeshServiceNotifications
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RadioProtocolModule {
    @Provides @Singleton
    fun provideFromRadioParser(): FromRadioParser = AndroidFromRadioParser

    @Provides
    @Singleton
    fun provideRadioProtocolManager(dispatchers: CoroutineDispatchers, parser: FromRadioParser): RadioProtocolManager {
        val scope = CoroutineScope(dispatchers.io + SupervisorJob())
        return RadioProtocolManager(scope, parser)
    }

    @Provides
    @Singleton
    fun provideFromRadioDispatcher(
        serviceRepository: ServiceRepository,
        router: MeshRouter,
        mqttManager: MeshMqttManager,
        packetHandler: PacketHandler,
        serviceNotifications: MeshServiceNotifications,
    ): FromRadioDispatcher = FromRadioDispatcher(
        configFlowManager =
        object : FromRadioDispatcher.ConfigFlowManager {
            override val newNodeCount: Int
                get() = router.configFlowManager.newNodeCount

            override fun handleMyInfo(myInfo: org.meshtastic.proto.MyNodeInfo) =
                router.configFlowManager.handleMyInfo(myInfo)

            override fun handleLocalMetadata(metadata: org.meshtastic.proto.DeviceMetadata) =
                router.configFlowManager.handleLocalMetadata(metadata)

            override fun handleNodeInfo(nodeInfo: org.meshtastic.proto.NodeInfo) =
                router.configFlowManager.handleNodeInfo(nodeInfo)

            override fun handleConfigComplete(configCompleteId: Int) =
                router.configFlowManager.handleConfigComplete(configCompleteId)
        },
        configHandler =
        object : FromRadioDispatcher.DeviceConfigHandler {
            override fun handleDeviceConfig(config: org.meshtastic.proto.Config) =
                router.configHandler.handleDeviceConfig(config)

            override fun handleModuleConfig(config: org.meshtastic.proto.ModuleConfig) =
                router.configHandler.handleModuleConfig(config)

            override fun handleChannel(channel: org.meshtastic.proto.Channel) =
                router.configHandler.handleChannel(channel)
        },
        mqttProxyHandler =
        object : FromRadioDispatcher.MqttProxyHandler {
            override fun handleMqttProxyMessage(message: org.meshtastic.proto.MqttClientProxyMessage) =
                mqttManager.handleMqttProxyMessage(message)
        },
        queueStatusHandler =
        object : FromRadioDispatcher.QueueStatusHandler {
            override fun handleQueueStatus(queueStatus: org.meshtastic.proto.QueueStatus) =
                packetHandler.handleQueueStatus(queueStatus)
        },
        clientNotificationHandler =
        object : FromRadioDispatcher.ClientNotificationHandler {
            override fun handleClientNotification(notification: org.meshtastic.proto.ClientNotification) {
                serviceRepository.setClientNotification(notification)
                serviceNotifications.showClientNotification(notification)
                packetHandler.removeResponse(notification.reply_id ?: 0, complete = false)
            }
        },
        statusMessageSink =
        object : FromRadioDispatcher.StatusMessageSink {
            override fun updateStatusMessage(message: String) = serviceRepository.setStatusMessage(message)
        },
    )
}
