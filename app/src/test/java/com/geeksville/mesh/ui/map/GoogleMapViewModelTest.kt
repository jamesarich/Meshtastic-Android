package com.geeksville.mesh.ui.map

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.geeksville.mesh.database.NodeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Specific to Google variant due to Application dependency in constructor
@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Config.OLDEST_SDK])
class GoogleMapViewModelTest { // Renamed to be specific

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: MapViewModel // This will be the Google variant
    private lateinit var mockPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockNodeRepository: NodeRepository
    private lateinit var mockApplication: Application

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        every { mockPreferences.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        // No putInt for mapStyleId in Google variant's direct preference management

        mockNodeRepository = mockk(relaxed = true)
        every { mockNodeRepository.getNodes() } returns flowOf(emptyList())

        mockApplication = mockk(relaxed = true)
        every { mockApplication.applicationContext } returns mockk<Context>(relaxed = true)
        // Mock file system access for KML layers if those tests are added here
        val mockFilesDir = mockk<java.io.File>(relaxed = true)
        every { mockApplication.filesDir } returns mockFilesDir
        every { mockFilesDir.exists() } returns true
        every { mockFilesDir.isDirectory } returns true
        every { mockFilesDir.listFiles() } returns emptyArray() // No persisted layers initially


        viewModel = MapViewModel(
            application = mockApplication,
            preferences = mockPreferences,
            nodeRepository = mockNodeRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `setOnlyFavorites updates preferences and flow`() = runTest {
        val testValue = true
        every { mockPreferences.getBoolean("only-favorites", false) } returns !testValue

        viewModel.setOnlyFavorites(testValue)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { mockEditor.putBoolean("only-favorites", testValue) }
        verify { mockEditor.apply() }

        assert(viewModel.mapFilterStateFlow.value.onlyFavorites == testValue)
    }

    @Test
    fun `setShowWaypointsOnMap updates preferences and flow`() = runTest {
        val testValue = false
        every { mockPreferences.getBoolean("show-waypoints-on-map", true) } returns !testValue

        viewModel.setShowWaypointsOnMap(testValue)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { mockEditor.putBoolean("show-waypoints-on-map", testValue) }
        verify { mockEditor.apply() }

        assert(viewModel.mapFilterStateFlow.value.showWaypoints == testValue)
    }

    @Test
    fun `setShowPrecisionCircleOnMap updates preferences and flow`() = runTest {
        val testValue = false
        every { mockPreferences.getBoolean("show-precision-circle-on-map", true) } returns !testValue

        viewModel.setShowPrecisionCircleOnMap(testValue)
        testDispatcher.scheduler.advanceUntilIdle()

        verify { mockEditor.putBoolean("show-precision-circle-on-map", testValue) }
        verify { mockEditor.apply() }

        assert(viewModel.mapFilterStateFlow.value.showPrecisionCircle == testValue)
    }

    // KML/KMZ layer tests would go here, requiring more extensive mocking
    // for ContentResolver, InputStream, FileOutputStream, KmlLayer, etc.
    // For example:
    // @Test
    // fun `addMapLayer copies file and updates flow`() = runTest { ... }

    // @Test
    // fun `removeMapLayer deletes file and updates flow`() = runTest { ... }

    // @Test
    // fun `toggleLayerVisibility updates flow`() = runTest { ... }

    // @Test
    // fun `loadPersistedLayers loads layers from storage`() = runTest { ... }
}
