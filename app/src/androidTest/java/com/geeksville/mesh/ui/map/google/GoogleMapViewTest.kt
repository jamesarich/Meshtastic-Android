package com.geeksville.mesh.ui.map.google

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.geeksville.mesh.R
import com.geeksville.mesh.model.UIViewModel
import com.geeksville.mesh.ui.map.MapViewModel
import com.geeksville.mesh.ui.map.MapView
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GoogleMapViewTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var mockUiViewModel: UIViewModel
    private lateinit var mockMapViewModel: MapViewModel // Google variant

    // Note: For UI tests with Google Maps, interactions with the map itself (markers, camera)
    // often require more setup, possibly using Hilt for dependency injection in tests,
    // and ensuring Google Play Services are available and correctly configured in the test environment.
    // These initial tests will focus on Compose UI elements around the map.

    @Test
    fun mapControlsOverlay_areDisplayed() {
        mockUiViewModel = mockk(relaxed = true)
        // Ensure this is the Google variant of MapViewModel, potentially with Hilt support if needed
        mockMapViewModel = mockk(relaxed = true)

        composeTestRule.setContent {
            MapView(
                uiViewModel = mockUiViewModel,
                mapViewModel = mockMapViewModel,
                navigateToNodeDetails = {}
            )
        }

        // Check for map type button
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.map_type_selection))
            .assertIsDisplayed()

        // Check for map filter button
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.map_filter))
            .assertIsDisplayed()

        // Check for manage layers button
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.manage_map_layers))
            .assertIsDisplayed()
    }

    @Test
    fun mapFilterDropdown_opensAndShowsOptions_google() {
        mockUiViewModel = mockk(relaxed = true)
        mockMapViewModel = mockk(relaxed = true) // Google variant

        composeTestRule.setContent {
            MapView(
                uiViewModel = mockUiViewModel,
                mapViewModel = mockMapViewModel,
                navigateToNodeDetails = {}
            )
        }

        // Open the filter dropdown via the controls overlay
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.map_filter))
            .performClick()

        // Check for filter options
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.only_favorites))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.show_waypoints))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.show_precision_circle))
            .assertIsDisplayed()
    }

    @Test
    fun mapTypeDropdown_opensAndShowsOptions_google() {
        mockUiViewModel = mockk(relaxed = true)
        mockMapViewModel = mockk(relaxed = true) // Google variant

        composeTestRule.setContent {
            MapView(
                uiViewModel = mockUiViewModel,
                mapViewModel = mockMapViewModel,
                navigateToNodeDetails = {}
            )
        }

        // Open the map type dropdown
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.map_type_selection))
            .performClick()

        // Check for map type options
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.map_type_normal))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.map_type_satellite))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.map_type_terrain))
            .assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.map_type_hybrid))
            .assertIsDisplayed()
    }

    @Test
    fun manageLayersBottomSheet_opens_google() {
        mockUiViewModel = mockk(relaxed = true)
        mockMapViewModel = mockk(relaxed = true) // Google variant

        composeTestRule.setContent {
            MapView(
                uiViewModel = mockUiViewModel,
                mapViewModel = mockMapViewModel,
                navigateToNodeDetails = {}
            )
        }

        // Open the manage layers bottom sheet
        composeTestRule.onNodeWithContentDescription(composeTestRule.activity.getString(R.string.manage_map_layers))
            .performClick()

        // Check if the bottom sheet title or a unique element within it is displayed
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.manage_map_layers)).assertIsDisplayed()
        composeTestRule.onNodeWithText(composeTestRule.activity.getString(R.string.add_layer)).assertIsDisplayed()
    }


    // Further tests needed:
    // - Verifying markers (node and waypoint) are displayed (requires GoogleMap interactions)
    // - Testing map camera movements and zoom (GoogleMap interactions)
    // - Testing KML/KMZ layer addition, visibility toggle, and removal
    // - Testing MyLocation button functionality (requires location permissions and GPS state)
    // - Testing waypoint creation/editing dialogs
}
