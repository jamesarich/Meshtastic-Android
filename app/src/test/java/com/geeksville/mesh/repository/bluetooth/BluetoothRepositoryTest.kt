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

package com.geeksville.mesh.repository.bluetooth

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.lifecycle.Lifecycle
import com.geeksville.mesh.CoroutineDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.junit.MockitoJUnitRunner

/**
 * Unit tests for BluetoothRepository
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(MockitoJUnitRunner::class)
class BluetoothRepositoryTest {

    @Mock
    private lateinit var mockApplication: Application

    @Mock
    private lateinit var mockBluetoothAdapter: BluetoothAdapter

    @Mock
    private lateinit var mockBluetoothDevice: BluetoothDevice

    @Mock
    private lateinit var mockLifecycle: Lifecycle

    private lateinit var testDispatchers: CoroutineDispatchers
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        testDispatchers = CoroutineDispatchers(
            io = testDispatcher,
            default = testDispatcher,
            main = testDispatcher,
        )
    }

    @Test
    fun testIsValidAddress_validAddress_returnsTrue() {
        val validAddress = "00:11:22:33:44:55"
        assertTrue("Valid Bluetooth address should be recognized", 
            BluetoothAdapter.checkBluetoothAddress(validAddress))
    }

    @Test
    fun testIsValidAddress_invalidAddress_returnsFalse() {
        val invalidAddress = "invalid"
        assertFalse("Invalid Bluetooth address should be rejected",
            BluetoothAdapter.checkBluetoothAddress(invalidAddress))
    }

    @Test
    fun testIsValidAddress_emptyAddress_returnsFalse() {
        val emptyAddress = ""
        assertFalse("Empty Bluetooth address should be rejected",
            BluetoothAdapter.checkBluetoothAddress(emptyAddress))
    }

    @Test
    fun testBleNamePattern_validPattern_matches() {
        val validNames = listOf(
            "Meshtastic_1234",
            "Device_ABCD",
            "Test_0000",
            "MyRadio_FFFF"
        )
        
        val pattern = Regex(BluetoothRepository.BLE_NAME_PATTERN)
        
        validNames.forEach { name ->
            assertTrue("Name '$name' should match BLE pattern", pattern.matches(name))
        }
    }

    @Test
    fun testBleNamePattern_invalidPattern_doesNotMatch() {
        val invalidNames = listOf(
            "Meshtastic",        // No underscore and hex
            "Device_123",         // Only 3 hex digits
            "Test_12345",         // Too many hex digits
            "MyRadio",           // No underscore and hex
            "_1234",             // Starts with underscore
            ""                   // Empty string
        )
        
        val pattern = Regex(BluetoothRepository.BLE_NAME_PATTERN)
        
        invalidNames.forEach { name ->
            assertFalse("Name '$name' should not match BLE pattern", pattern.matches(name))
        }
    }

    @Test
    fun testBluetoothState_initialState_hasCorrectDefaults() {
        val state = BluetoothState()
        
        assertFalse("Initial state should not have permissions", state.hasPermissions)
        assertFalse("Initial state should not be enabled", state.enabled)
        assertTrue("Initial state should have empty bonded devices", state.bondedDevices.isEmpty())
    }

    @Test
    fun testBluetoothState_withPermissions_hasCorrectState() {
        val state = BluetoothState(
            hasPermissions = true,
            enabled = true,
            bondedDevices = emptyList()
        )
        
        assertTrue("State should have permissions", state.hasPermissions)
        assertTrue("State should be enabled", state.enabled)
        assertTrue("State should have empty bonded devices", state.bondedDevices.isEmpty())
    }

    @Test
    fun testBluetoothState_toString_containsExpectedInfo() {
        val state = BluetoothState(
            hasPermissions = true,
            enabled = false,
            bondedDevices = emptyList()
        )
        
        val stateString = state.toString()
        
        assertTrue("toString should contain hasPermissions", 
            stateString.contains("hasPermissions=true"))
        assertTrue("toString should contain enabled", 
            stateString.contains("enabled=false"))
        assertNotNull("toString should not be null", stateString)
    }
}
