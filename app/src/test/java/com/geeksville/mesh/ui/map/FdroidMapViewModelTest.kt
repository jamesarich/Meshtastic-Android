package com.geeksville.mesh.ui.map

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

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [Config.OLDEST_SDK])
class FdroidMapViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private lateinit var viewModel: MapViewModel // This will be the F-Droid variant
    private lateinit var mockPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockNodeRepository: NodeRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        mockPreferences = mockk(relaxed = true)
        mockEditor = mockk(relaxed = true)
        every { mockPreferences.edit() } returns mockEditor
        every { mockEditor.putBoolean(any(), any()) } returns mockEditor
        every { mockEditor.putInt(any(), any()) } returns mockEditor // For mapStyleId

        mockNodeRepository = mockk(relaxed = true)
        every { mockNodeRepository.getNodes() } returns flowOf(emptyList())

        // F-Droid MapViewModel constructor
        viewModel = MapViewModel(
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

    @Test
    fun `mapStyleId updates preferences`() = runTest {
        val testValue = 1
        // Simulate that the current preference value is different
        every { mockPreferences.getInt(MAP_STYLE_ID, 0) } returns 0

        viewModel.mapStyleId = testValue
        // No need to advance dispatcher as preference edit is synchronous for setter,
        // but getter relies on flow which might need it if tested immediately after set through flow.
        // However, here we are checking the direct setter's effect on SharedPreferences.

        verify { mockEditor.putInt(MAP_STYLE_ID, testValue) }
        verify { mockEditor.apply() }

        // Update mock to return the new value for subsequent calls to getter
        every { mockPreferences.getInt(MAP_STYLE_ID, 0) } returns testValue
        assert(viewModel.mapStyleId == testValue)
    }
}
