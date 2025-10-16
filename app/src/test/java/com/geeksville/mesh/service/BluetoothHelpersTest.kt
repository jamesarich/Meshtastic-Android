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

package com.geeksville.mesh.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for Bluetooth helper functions
 */
class BluetoothHelpersTest {

    @Test
    fun testLongBLEUUID_convertsCorrectly() {
        val shortUUID = "2902"
        val expectedUUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        val result = longBLEUUID(shortUUID)
        
        assertEquals("UUID should match expected format", expectedUUID, result)
        assertNotNull("UUID should not be null", result)
    }

    @Test
    fun testLongBLEUUID_differentShortCodes() {
        val testCases = mapOf(
            "2901" to "00002901-0000-1000-8000-00805f9b34fb",
            "2902" to "00002902-0000-1000-8000-00805f9b34fb",
            "2a00" to "00002a00-0000-1000-8000-00805f9b34fb",
            "FFFF" to "0000FFFF-0000-1000-8000-00805f9b34fb"
        )
        
        testCases.forEach { (short, expected) ->
            val result = longBLEUUID(short)
            val expectedUUID = UUID.fromString(expected)
            assertEquals("UUID for $short should match", expectedUUID, result)
        }
    }

    @Test
    fun testLongBLEUUID_lowercaseInput() {
        val shortUUID = "2a00"
        val expectedUUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        
        val result = longBLEUUID(shortUUID)
        
        assertEquals("Lowercase input should work", expectedUUID, result)
    }

    @Test
    fun testLongBLEUUID_uppercaseInput() {
        val shortUUID = "2A00"
        val expectedUUID = UUID.fromString("00002A00-0000-1000-8000-00805f9b34fb")
        
        val result = longBLEUUID(shortUUID)
        
        assertEquals("Uppercase input should work", expectedUUID, result)
    }

    @Test
    fun testLongBLEUUID_standardDescriptorUUID() {
        // Standard Client Characteristic Configuration Descriptor
        val configDescriptorShort = "2902"
        val result = longBLEUUID(configDescriptorShort)
        
        assertEquals("Should match standard BLE descriptor UUID",
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"), result)
    }

    @Test
    fun testLongBLEUUID_hexValues() {
        val hexCodes = listOf("0000", "1234", "ABCD", "FFFF", "0001", "9999")
        
        hexCodes.forEach { hex ->
            val result = longBLEUUID(hex)
            assertNotNull("UUID should not be null for hex: $hex", result)
            
            // Verify format: 0000XXXX-0000-1000-8000-00805f9b34fb
            val uuidString = result.toString()
            assertEquals("UUID should be 36 characters long", 36, uuidString.length)
            assertEquals("UUID should end with standard suffix", 
                "0000-1000-8000-00805f9b34fb", uuidString.substring(13))
        }
    }
}
