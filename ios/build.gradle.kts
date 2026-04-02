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

import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.meshtastic.detekt)
    alias(libs.plugins.meshtastic.spotless)
    alias(libs.plugins.meshtastic.koin)
    id("meshtastic.kover")
}

// Exclude generated Compose resource files from detekt analysis
tasks.withType<Detekt>().configureEach { exclude("**/generated/**") }

kotlin {
    // Export a static XCFramework for embedding in the Xcode project.
    // The Swift host app wraps ComposeUIViewController from this framework.
    val xcf = XCFramework("MeshtasticKit")

    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        target.binaries.framework {
            baseName = "MeshtasticKit"
            isStatic = true
            xcf.add(this)

            // Re-export core modules so Swift can access shared types if needed
            export(projects.core.common)
            export(projects.core.model)
            export(projects.core.navigation)
        }
    }

    // The hierarchy template creates: commonMain → nativeMain → iosMain → iosArm64Main/iosSimulatorArm64Main
    // All iOS host shell code lives in commonMain — it is iOS-only by virtue of this module's targets.
    sourceSets {
        commonMain {
            dependencies {
                // Core KMP modules (iOS variants)
                api(projects.core.common)
                api(projects.core.di)
                api(projects.core.model)
                api(projects.core.navigation)
                implementation(projects.core.repository)
                implementation(projects.core.domain)
                implementation(projects.core.data)
                implementation(projects.core.database)
                implementation(projects.core.datastore)
                implementation(projects.core.prefs)
                implementation(projects.core.network)
                implementation(projects.core.takserver)
                implementation(projects.core.resources)
                implementation(projects.core.service)
                implementation(projects.core.ui)
                implementation(projects.core.proto)
                implementation(projects.core.ble)

                // Feature modules (iOS variants)
                implementation(projects.feature.settings)
                implementation(projects.feature.node)
                implementation(projects.feature.messaging)
                implementation(projects.feature.connections)
                implementation(projects.feature.map)
                implementation(projects.feature.firmware)
                implementation(projects.feature.intro)

                // Coil image loading (network + SVG decoding for device hardware images)
                implementation(libs.coil)
                implementation(libs.coil.network.ktor3)
                implementation(libs.coil.svg)

                // Compose Multiplatform
                implementation(libs.compose.multiplatform.material3)
                implementation(libs.compose.multiplatform.materialIconsExtended)
                implementation(libs.compose.multiplatform.runtime)
                implementation(libs.compose.multiplatform.foundation)
                implementation(libs.compose.multiplatform.resources)

                // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
                implementation(libs.jetbrains.compose.material3.adaptive)
                implementation(libs.jetbrains.compose.material3.adaptive.layout)
                implementation(libs.jetbrains.compose.material3.adaptive.navigation)

                // Navigation 3 (JetBrains fork — multiplatform)
                implementation(libs.jetbrains.navigation3.ui)
                implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
                implementation(libs.jetbrains.lifecycle.viewmodel.compose)
                implementation(libs.jetbrains.lifecycle.runtime.compose)

                // Koin DI
                implementation(libs.koin.core)
                implementation(libs.koin.compose.viewmodel)

                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.kotlinx.serialization.core)
                implementation(libs.kermit)
                implementation(libs.okio)

                // Ktor HttpClient (Darwin engine for iOS)
                implementation(libs.ktor.client.darwin)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)

                implementation(libs.androidx.paging.common)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.datastore)
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.sqlite.bundled)
                implementation(libs.koin.annotations)
                implementation(libs.kotlinx.collections.immutable)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.koin.test)
                implementation(kotlin("test"))
            }
        }
    }
}
