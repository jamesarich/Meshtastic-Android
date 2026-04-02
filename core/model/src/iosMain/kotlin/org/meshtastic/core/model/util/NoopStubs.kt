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
package org.meshtastic.core.model.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import org.meshtastic.core.common.util.DateFormatter
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault

/** Real iOS implementations for core:model. */
actual fun getShortDateTime(time: Long): String = DateFormatter.formatShortDate(time)

@OptIn(ExperimentalForeignApi::class)
actual fun platformRandomBytes(size: Int): ByteArray {
    val bytes = ByteArray(size)
    bytes.usePinned { pinned -> SecRandomCopyBytes(kSecRandomDefault, size.convert(), pinned.addressOf(0)) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
actual object SfppHasher {
    private const val HASH_SIZE = 16
    private const val INT_BYTES = 4

    actual fun computeMessageHash(encryptedPayload: ByteArray, to: Int, from: Int, id: Int): ByteArray {
        // Build the input buffer: payload + to(LE) + from(LE) + id(LE)
        val toBytes = to.toLittleEndianBytes()
        val fromBytes = from.toLittleEndianBytes()
        val idBytes = id.toLittleEndianBytes()

        val input = encryptedPayload + toBytes + fromBytes + idBytes
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)

        input.usePinned { pinnedInput ->
            digest.usePinned { pinnedDigest ->
                CC_SHA256(
                    pinnedInput.addressOf(0).reinterpret<UByteVar>(),
                    input.size.convert(),
                    pinnedDigest.addressOf(0).reinterpret<UByteVar>(),
                )
            }
        }
        return digest.copyOf(HASH_SIZE)
    }

    private fun Int.toLittleEndianBytes(): ByteArray = byteArrayOf(
        (this and 0xFF).toByte(),
        (this shr 8 and 0xFF).toByte(),
        (this shr 16 and 0xFF).toByte(),
        (this shr 24 and 0xFF).toByte(),
    )
}
