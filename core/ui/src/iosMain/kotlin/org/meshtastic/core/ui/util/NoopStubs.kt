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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLinkStyles
import org.jetbrains.compose.resources.StringResource
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.common.util.MeshtasticUri
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen

actual fun createClipEntry(text: String, label: String): ClipEntry {
    // On iOS, Compose Multiplatform's ClipEntry wraps platform clipboard data.
    // We set UIPasteboard directly since the ClipboardManager integration handles it.
    platform.UIKit.UIPasteboard.generalPasteboard.string = text
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    return ClipEntry.withPlainText(text)
}

actual fun annotatedStringFromHtml(html: String, linkStyles: TextLinkStyles?): AnnotatedString = AnnotatedString(html)

@Composable actual fun rememberOpenNfcSettings(): () -> Unit = {}

@Composable actual fun rememberShowToast(): suspend (String) -> Unit = { _ -> }

@Composable actual fun rememberShowToastResource(): suspend (StringResource) -> Unit = { _ -> }

@Composable
actual fun rememberOpenMap(): (latitude: Double, longitude: Double, label: String) -> Unit = { lat, lon, label ->
    val encodedLabel = label.replace(" ", "+")
    val urlString = "http://maps.apple.com/?ll=$lat,$lon&q=$encodedLabel"
    NSURL(string = urlString)?.let { url -> UIApplication.sharedApplication.openURL(url) }
}

@Composable
actual fun rememberOpenUrl(): (url: String) -> Unit = { urlString ->
    NSURL(string = urlString)?.let { url -> UIApplication.sharedApplication.openURL(url) }
}

@Composable
actual fun rememberSaveFileLauncher(
    onUriReceived: (MeshtasticUri) -> Unit,
): (defaultFilename: String, mimeType: String) -> Unit = { _, _ ->
    // TODO: Implement UIActivityViewController-based file export for iOS
}

@Composable
actual fun rememberOpenFileLauncher(onUriReceived: (CommonUri?) -> Unit): (mimeType: String) -> Unit = { _ ->
    // TODO: Implement UIDocumentPickerViewController for iOS file import
}

@Composable actual fun rememberReadTextFromUri(): suspend (CommonUri, Int) -> String? = { _, _ -> null }

@Composable
actual fun KeepScreenOn(enabled: Boolean) {
    DisposableEffect(enabled) {
        UIApplication.sharedApplication.idleTimerDisabled = enabled
        onDispose { UIApplication.sharedApplication.idleTimerDisabled = false }
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS uses swipe-back gesture natively; no explicit back handler needed.
}

@Composable
actual fun rememberRequestLocationPermission(onGranted: () -> Unit, onDenied: () -> Unit): () -> Unit {
    // TODO: Implement CLLocationManager permission request for iOS
    return {}
}

@Composable
actual fun rememberOpenLocationSettings(): () -> Unit = {
    NSURL(string = "App-Prefs:Privacy&path=LOCATION")?.let { url -> UIApplication.sharedApplication.openURL(url) }
}

@Composable
actual fun SetScreenBrightness(brightness: Float) {
    DisposableEffect(brightness) {
        val previousBrightness = UIScreen.mainScreen.brightness
        UIScreen.mainScreen.brightness = brightness.toDouble()
        onDispose { UIScreen.mainScreen.brightness = previousBrightness }
    }
}
