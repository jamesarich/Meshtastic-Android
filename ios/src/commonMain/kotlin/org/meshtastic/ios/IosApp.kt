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
package org.meshtastic.ios

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.ComposeUIViewController
import co.touchlab.kermit.Logger
import coil3.ImageLoader
import coil3.annotation.ExperimentalCoilApi
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import io.ktor.client.HttpClient
import okio.Path.Companion.toPath
import org.koin.core.context.startKoin
import org.meshtastic.core.navigation.TopLevelDestination
import org.meshtastic.core.navigation.rememberMultiBackstack
import org.meshtastic.core.repository.UiPrefs
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.core.ui.theme.AppTheme
import org.meshtastic.core.ui.viewmodel.UIViewModel
import org.meshtastic.ios.di.iosModule
import org.meshtastic.ios.di.iosPlatformModule
import org.meshtastic.ios.ui.IosMainScreen
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/** Meshtastic iOS — Compose Multiplatform entry point for the iOS target. */
private val LocalAppLocale = staticCompositionLocalOf { "" }

private const val MEMORY_CACHE_MAX_BYTES = 64L * 1024L * 1024L // 64 MiB
private const val DISK_CACHE_MAX_BYTES = 32L * 1024L * 1024L // 32 MiB

/** Resolves the iOS Caches directory for Coil disk cache. */
private fun iosCachesDir(): String {
    val paths =
        NSFileManager.defaultManager.URLsForDirectory(directory = NSCachesDirectory, inDomains = NSUserDomainMask)

    @Suppress("UNCHECKED_CAST")
    val cachesUrl = (paths.firstOrNull() as? NSURL)
    return cachesUrl?.path ?: ""
}

/**
 * Creates the root [ComposeUIViewController] that the Swift host wraps in a `UIViewControllerRepresentable`.
 *
 * This is the single entry point from Swift into the Compose Multiplatform UI. The function:
 * 1. Initializes Koin with all KMP modules and iOS platform bindings
 * 2. Starts the [MeshServiceOrchestrator] for radio communication
 * 3. Configures Coil image loading with Ktor (Darwin engine) and SVG support
 * 4. Sets up theme, locale, and navigation backstack
 * 5. Renders the shared [IosMainScreen] inside [AppTheme]
 */
@OptIn(ExperimentalCoilApi::class)
@Suppress("LongMethod", "FunctionName")
fun MainViewController() = ComposeUIViewController {
    Logger.i { "Meshtastic iOS — Starting" }

    val koinApp = remember { startKoin { modules(iosPlatformModule(), iosModule()) } }
    val uiViewModel = remember { koinApp.koin.get<UIViewModel>() }
    val httpClient = remember { koinApp.koin.get<HttpClient>() }

    // Start mesh service orchestrator and stop on dispose
    val meshServiceController = remember { koinApp.koin.get<MeshServiceOrchestrator>() }
    DisposableEffect(Unit) {
        meshServiceController.start()
        onDispose { meshServiceController.stop() }
    }

    // Theme and locale preferences
    val uiPrefs = remember { koinApp.koin.get<UiPrefs>() }
    val themePref by uiPrefs.theme.collectAsState(initial = -1)

    val isDarkTheme =
        when (themePref) {
            1 -> false
            2 -> true
            else -> isSystemInDarkTheme()
        }

    val localePref by uiPrefs.locale.collectAsState(initial = "")

    // Coil image loader
    setSingletonImageLoaderFactory { context ->
        val cacheDir = iosCachesDir() + "/image_cache_v3"
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = httpClient))
                add(SvgDecoder.Factory())
            }
            .memoryCache { MemoryCache.Builder().maxSizeBytes(MEMORY_CACHE_MAX_BYTES).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.toPath()).maxSizeBytes(DISK_CACHE_MAX_BYTES).build() }
            .crossfade(true)
            .build()
    }

    val multiBackstack = rememberMultiBackstack(TopLevelDestination.Connections.route)

    CompositionLocalProvider(LocalAppLocale provides localePref) {
        AppTheme(darkTheme = isDarkTheme) { IosMainScreen(uiViewModel, multiBackstack) }
    }
}
