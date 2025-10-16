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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for BLE exception classes
 */
class BLEExceptionTest {

    @Test
    fun testBLEException_createsWithMessage() {
        val message = "Test BLE error"
        val exception = BLEException(message)
        
        assertEquals("Exception message should match", message, exception.message)
        assertNotNull("Exception should not be null", exception)
    }

    @Test
    fun testBLEException_isRemoteException() {
        val exception = BLEException("Test error")
        
        assertTrue("BLEException should be a RemoteException",
            exception is android.os.RemoteException)
    }

    @Test
    fun testBLECharacteristicNotFoundException_createsWithUUID() {
        val testUUID = UUID.fromString("00002a00-0000-1000-8000-00805f9b34fb")
        val exception = BLECharacteristicNotFoundException(testUUID)
        
        assertNotNull("Exception should not be null", exception)
        assertTrue("Exception message should contain UUID",
            exception.message?.contains(testUUID.toString()) == true)
        assertTrue("Exception message should indicate characteristic not found",
            exception.message?.contains("Can't get characteristic") == true)
    }

    @Test
    fun testBLECharacteristicNotFoundException_isBLEException() {
        val testUUID = UUID.randomUUID()
        val exception = BLECharacteristicNotFoundException(testUUID)
        
        assertTrue("BLECharacteristicNotFoundException should be a BLEException",
            exception is BLEException)
    }

    @Test
    fun testBLEConnectionClosing_hasCorrectMessage() {
        val exception = BLEConnectionClosing()
        
        assertNotNull("Exception should not be null", exception)
        assertTrue("Exception message should indicate connection closing",
            exception.message?.contains("Connection closing") == true)
    }

    @Test
    fun testBLEConnectionClosing_isBLEException() {
        val exception = BLEConnectionClosing()
        
        assertTrue("BLEConnectionClosing should be a BLEException",
            exception is BLEException)
    }

    @Test
    fun testBLEStatusException_storesStatusCode() {
        val statusCode = 133
        val message = "Connection failed"
        val exception = SafeBluetooth.BLEStatusException(statusCode, message)
        
        assertEquals("Status code should match", statusCode, exception.status)
        assertEquals("Message should match", message, exception.message)
    }

    @Test
    fun testBLEStatusException_isBLEException() {
        val exception = SafeBluetooth.BLEStatusException(0, "Test")
        
        assertTrue("BLEStatusException should be a BLEException",
            exception is BLEException)
    }

    @Test
    fun testBLEStatusException_differentStatusCodes() {
        val statusCodes = listOf(0, 133, 257, 4404)
        
        statusCodes.forEach { code ->
            val exception = SafeBluetooth.BLEStatusException(code, "Status $code")
            assertEquals("Status code should be preserved", code, exception.status)
        }
    }
}
