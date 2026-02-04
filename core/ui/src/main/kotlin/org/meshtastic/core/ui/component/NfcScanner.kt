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

package org.meshtastic.core.ui.component

import android.app.Activity
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.nfc_not_supported
import org.meshtastic.core.strings.nfc_scan
import org.meshtastic.core.strings.nfc_scan_description

@Composable
fun NfcScannerPrompt(
    onUriDetected: (Uri) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity ?: return
    val nfcAdapter = NfcAdapter.getDefaultAdapter(context)

    if (nfcAdapter == null) {
        SimpleAlertDialog(
            title = Res.string.nfc_not_supported,
            onDismiss = onDismiss
        )
        return
    }

    DisposableEffect(Unit) {
        val flags = NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS

        nfcAdapter.enableReaderMode(activity, { tag ->
            val ndef = Ndef.get(tag)
            ndef?.let {
                try {
                    it.connect()
                    val message = it.ndefMessage
                    message?.records?.firstOrNull()?.toUri()?.let { uri ->
                        Logger.d { "NFC URI detected: $uri" }
                        onUriDetected(uri)
                    }
                } catch (e: Exception) {
                    Logger.e(e) { "Error reading NFC tag" }
                } finally {
                    try {
                        it.close()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }
        }, flags, null)

        onDispose {
            nfcAdapter.disableReaderMode(activity)
        }
    }

    SimpleAlertDialog(
        title = Res.string.nfc_scan,
        text = { Text(stringResource(Res.string.nfc_scan_description)) },
        onDismiss = onDismiss
    )
}
