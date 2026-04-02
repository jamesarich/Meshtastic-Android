/*
 * Copyright (c) 2026 Meshtastic LLC
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
package org.meshtastic.ios.di

// Generated Koin module extensions from core KMP modules
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.meshtastic.core.data.datasource.BootloaderOtaQuirksJsonDataSource
import org.meshtastic.core.data.datasource.DeviceHardwareJsonDataSource
import org.meshtastic.core.data.datasource.FirmwareReleaseJsonDataSource
import org.meshtastic.core.model.BootloaderOtaQuirk
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.NetworkDeviceHardware
import org.meshtastic.core.model.NetworkFirmwareReleases
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.network.repository.MQTTRepository
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.LocationRepository
import org.meshtastic.core.repository.MeshLocationManager
import org.meshtastic.core.repository.MeshServiceNotifications
import org.meshtastic.core.repository.MeshWorkerManager
import org.meshtastic.core.repository.MessageQueue
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.repository.ServiceBroadcasts
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.ios.stub.NoopAppWidgetUpdater
import org.meshtastic.ios.stub.NoopCompassHeadingProvider
import org.meshtastic.ios.stub.NoopLocationRepository
import org.meshtastic.ios.stub.NoopMQTTRepository
import org.meshtastic.ios.stub.NoopMagneticFieldProvider
import org.meshtastic.ios.stub.NoopMeshLocationManager
import org.meshtastic.ios.stub.NoopMeshServiceNotifications
import org.meshtastic.ios.stub.NoopMeshWorkerManager
import org.meshtastic.ios.stub.NoopPhoneLocationProvider
import org.meshtastic.ios.stub.NoopPlatformAnalytics
import org.meshtastic.ios.stub.NoopRadioInterfaceService
import org.meshtastic.ios.stub.NoopServiceBroadcasts
import org.meshtastic.core.ble.di.module as coreBleModule
import org.meshtastic.core.common.di.module as coreCommonModule
import org.meshtastic.core.data.di.module as coreDataModule
import org.meshtastic.core.database.di.module as coreDatabaseModule
import org.meshtastic.core.datastore.di.module as coreDatastoreModule
import org.meshtastic.core.di.di.module as coreDiModule
import org.meshtastic.core.domain.di.module as coreDomainModule
import org.meshtastic.core.network.di.module as coreNetworkModule
import org.meshtastic.core.prefs.di.module as corePrefsModule
import org.meshtastic.core.repository.di.module as coreRepositoryModule
import org.meshtastic.core.service.di.module as coreServiceModule
import org.meshtastic.core.takserver.di.module as coreTakServerModule
import org.meshtastic.core.ui.di.module as coreUiModule
import org.meshtastic.feature.connections.di.module as featureConnectionsModule
import org.meshtastic.feature.firmware.di.module as featureFirmwareModule
import org.meshtastic.feature.intro.di.module as featureIntroModule
import org.meshtastic.feature.map.di.module as featureMapModule
import org.meshtastic.feature.messaging.di.module as featureMessagingModule
import org.meshtastic.feature.node.di.module as featureNodeModule
import org.meshtastic.feature.settings.di.module as featureSettingsModule
import org.meshtastic.ios.di.module as iosDiModule

/**
 * Koin module for the iOS target.
 *
 * Includes the generated Koin K2 modules from core KMP libraries (which provide real implementations of prefs, data
 * repositories, managers, datastore data sources, use cases, and ViewModels from `commonMain`).
 *
 * Only truly platform-specific interfaces are stubbed here — things that require native iOS APIs (CoreBluetooth
 * transport, notifications, location services, broadcasts, widgets).
 *
 * Platform infrastructure (DataStores, Room database, Lifecycle) is provided by [iosPlatformModule].
 */
fun iosModule() = module {
    // Include generated Koin K2 modules from core KMP libraries (commonMain implementations)
    includes(
        org.meshtastic.core.di.di.CoreDiModule().coreDiModule(),
        org.meshtastic.core.common.di.CoreCommonModule().coreCommonModule(),
        org.meshtastic.core.datastore.di.CoreDatastoreModule().coreDatastoreModule(),
        org.meshtastic.core.prefs.di.CorePrefsModule().corePrefsModule(),
        org.meshtastic.core.database.di.CoreDatabaseModule().coreDatabaseModule(),
        org.meshtastic.core.data.di.CoreDataModule().coreDataModule(),
        org.meshtastic.core.domain.di.CoreDomainModule().coreDomainModule(),
        org.meshtastic.core.repository.di.CoreRepositoryModule().coreRepositoryModule(),
        org.meshtastic.core.network.di.CoreNetworkModule().coreNetworkModule(),
        org.meshtastic.core.ble.di.CoreBleModule().coreBleModule(),
        org.meshtastic.core.ui.di.CoreUiModule().coreUiModule(),
        org.meshtastic.core.service.di.CoreServiceModule().coreServiceModule(),
        org.meshtastic.core.takserver.di.CoreTakServerModule().coreTakServerModule(),
        org.meshtastic.feature.settings.di.FeatureSettingsModule().featureSettingsModule(),
        org.meshtastic.feature.node.di.FeatureNodeModule().featureNodeModule(),
        org.meshtastic.feature.messaging.di.FeatureMessagingModule().featureMessagingModule(),
        org.meshtastic.feature.connections.di.FeatureConnectionsModule().featureConnectionsModule(),
        org.meshtastic.feature.map.di.FeatureMapModule().featureMapModule(),
        org.meshtastic.feature.firmware.di.FeatureFirmwareModule().featureFirmwareModule(),
        org.meshtastic.feature.intro.di.FeatureIntroModule().featureIntroModule(),
        org.meshtastic.ios.di.IosDiModule().iosDiModule(),
        iosPlatformStubsModule(),
    )
}

/**
 * Stubs for truly platform-specific interfaces that have no `commonMain` implementation. These require native iOS APIs
 * (CoreBluetooth transport, notifications, location, etc.).
 *
 * As real iOS implementations become available (e.g., BLE transport via Kable, CLLocationManager), they will replace
 * individual stubs here.
 */
@Suppress("LongMethod")
private fun iosPlatformStubsModule() = module {
    single<ServiceRepository> { org.meshtastic.core.service.ServiceRepositoryImpl() }
    single<org.meshtastic.core.repository.RadioInterfaceService> { NoopRadioInterfaceService() }
    single<RadioTransportFactory> {
        // TODO: Implement IosRadioTransportFactory with Kable CoreBluetooth backend
        object : RadioTransportFactory {
            override val supportedDeviceTypes: List<DeviceType> = emptyList()

            override fun isMockInterface(): Boolean = false

            override fun isAddressValid(address: String?): Boolean = false

            override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = ""

            override fun createTransport(address: String, service: RadioInterfaceService): RadioTransport =
                object : RadioTransport {
                    override fun handleSendToRadio(p: ByteArray) = Unit

                    override fun close() = Unit
                }
        }
    }
    single<RadioController> {
        org.meshtastic.core.service.DirectRadioControllerImpl(
            serviceRepository = get(),
            nodeRepository = get(),
            commandSender = get(),
            router = get(),
            nodeManager = get(),
            radioInterfaceService = get(),
            locationManager = get(),
        )
    }
    single<MeshServiceNotifications> { NoopMeshServiceNotifications() }
    single<PlatformAnalytics> { NoopPlatformAnalytics() }
    single<ServiceBroadcasts> { NoopServiceBroadcasts() }
    single<AppWidgetUpdater> { NoopAppWidgetUpdater() }
    single<MeshWorkerManager> { NoopMeshWorkerManager() }
    single<MessageQueue> {
        // TODO: Implement real message queue for iOS
        object : MessageQueue {
            override suspend fun enqueue(packetId: Int) = Unit
        }
    }
    single<MeshLocationManager> { NoopMeshLocationManager() }
    single<LocationRepository> { NoopLocationRepository() }
    single<MQTTRepository> { NoopMQTTRepository() }
    single<org.meshtastic.feature.node.compass.CompassHeadingProvider> { NoopCompassHeadingProvider() }
    single<org.meshtastic.feature.node.compass.PhoneLocationProvider> { NoopPhoneLocationProvider() }
    single<org.meshtastic.feature.node.compass.MagneticFieldProvider> { NoopMagneticFieldProvider() }

    // Ktor HttpClient for iOS (Darwin engine via NSURLSession)
    single<HttpClient> { HttpClient(Darwin) { install(ContentNegotiation) { json(get<Json>()) } } }

    // iOS stubs for data sources that load from Android assets on mobile
    single<FirmwareReleaseJsonDataSource> {
        object : FirmwareReleaseJsonDataSource {
            override fun loadFirmwareReleaseFromJsonAsset() = NetworkFirmwareReleases()
        }
    }
    single<DeviceHardwareJsonDataSource> {
        object : DeviceHardwareJsonDataSource {
            override fun loadDeviceHardwareFromJsonAsset(): List<NetworkDeviceHardware> = emptyList()
        }
    }
    single<BootloaderOtaQuirksJsonDataSource> {
        object : BootloaderOtaQuirksJsonDataSource {
            override fun loadBootloaderOtaQuirksFromJsonAsset(): List<BootloaderOtaQuirk> = emptyList()
        }
    }
}
