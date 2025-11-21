package com.geeksville.mesh.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import org.meshtastic.core.navigation.FirmwareRoutes
import org.meshtastic.feature.firmware.FirmwareUpdateScreen

fun NavGraphBuilder.firmwareGraph(navController: NavController) {
    navigation<FirmwareRoutes.FirmwareGraph>(startDestination = FirmwareRoutes.FirmwareUpdate) {
        composable<FirmwareRoutes.FirmwareUpdate> {
            FirmwareUpdateScreen(navController)
        }
    }
}
