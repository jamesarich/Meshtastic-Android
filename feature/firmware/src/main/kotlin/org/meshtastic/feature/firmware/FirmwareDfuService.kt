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

package org.meshtastic.feature.firmware

import android.app.Activity
import no.nordicsemi.android.dfu.DfuBaseService

class FirmwareDfuService : DfuBaseService() {
    override fun getNotificationTarget(): Class<out Activity>? = try {
        // Best effort to find the main activity
        Class.forName("com.geeksville.mesh.MainActivity") as Class<out Activity>
    } catch (e: ClassNotFoundException) {
        null
    }

    override fun isDebug(): Boolean = BuildConfig.DEBUG
}
