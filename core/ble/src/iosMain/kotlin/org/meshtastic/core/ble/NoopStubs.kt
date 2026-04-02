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
package org.meshtastic.core.ble

import com.juul.kable.Peripheral
import com.juul.kable.PeripheralBuilder
import com.juul.kable.toIdentifier

/** Real iOS BLE implementation using Kable's CoreBluetooth backend. */
internal actual fun PeripheralBuilder.platformConfig(device: BleDevice, autoConnect: () -> Boolean) {
    // iOS CoreBluetooth does not have an autoConnect concept like Android.
    // Kable handles connection strategy internally on iOS.
    // No additional platform configuration needed.
}

internal actual fun createPeripheral(address: String, builderAction: PeripheralBuilder.() -> Unit): Peripheral =
    com.juul.kable.Peripheral(address.toIdentifier(), builderAction)

// iOS CoreBluetooth negotiates MTU automatically and does not expose it via Kable; fall back to null.
internal actual fun Peripheral.negotiatedMaxWriteLength(): Int? = null
