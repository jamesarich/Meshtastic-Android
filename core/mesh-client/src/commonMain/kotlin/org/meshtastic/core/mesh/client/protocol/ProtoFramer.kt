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
package org.meshtastic.core.mesh.client.protocol

import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

/**
 * Utility for reading/writing protobuf messages with length prefixes. Android transport uses a simple framed format,
 * but Wire already supports delimited IO. This helper keeps the logic encapsulated for multiplatform usage.
 */
@Suppress("MagicNumber", "ReturnCount")
object ProtoFramer {
    fun readDelimited(source: BufferedSource): RawFromRadio? {
        val size = source.readVarint32()
        if (size == null || size <= 0) return null
        if (!source.request(size.toLong())) return null
        return RawFromRadio(source.readByteArray(size.toLong()))
    }

    fun writeDelimited(sink: BufferedSink, bytes: RawToRadio) {
        sink.writeVarint32(bytes.bytes.size)
        sink.write(bytes.bytes)
        sink.flush()
    }

    /** Attempts to consume a single framed message from [buffer], returning null if there isn't enough data yet. */
    fun readFramedMessage(buffer: Buffer): RawFromRadio? {
        if (buffer.exhausted()) return null
        val mark = buffer.size
        val size =
            buffer.readVarint32()
                ?: run {
                    buffer.skip(buffer.size - mark)
                    return null
                }
        if (!buffer.request(size.toLong())) {
            buffer.skip(buffer.size - mark)
            return null
        }
        return RawFromRadio(buffer.readByteArray(size.toLong()))
    }

    private fun BufferedSource.readVarint32(): Int? {
        var shift = 0
        var result = 0
        while (shift < 32) {
            if (!request(1)) return null
            val byte = readByte().toInt()
            result = result or ((byte and 0x7F) shl shift)
            if (byte and 0x80 == 0) return result
            shift += 7
        }
        return null
    }

    private fun BufferedSink.writeVarint32(value: Int) {
        var temp = value
        while (true) {
            if (temp and 0x7F.inv() == 0) {
                writeByte(temp)
                return
            }
            writeByte((temp and 0x7F) or 0x80)
            temp = temp ushr 7
        }
    }

    data class RawFromRadio(val bytes: ByteArray)

    data class RawToRadio(val bytes: ByteArray)
}
