package com.example.home_garden_system

import com.example.home_garden_system.garden.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GardenViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun failedLoginIsVisibleWithoutOpeningDashboard() = runTest(dispatcher) {
        val repository = FailingRepository()
        val model = GardenViewModel(repository, backgroundScope)
        model.login("admin@garden.test", "wrong")
        runCurrent()
        assertNull(model.ui.value.repository.email)
        assertEquals("Email atau kata sandi salah.", model.ui.value.message)
        assertFalse(model.ui.value.busy)
    }
    @Test fun rejectedSettingsWriteDoesNotChangeDisplayedMode() = runTest(dispatcher) {
        val repository = FailingRepository()
        repository.state.value = RepositoryState(email = "admin", connected = true,
            garden = GardenSnapshot(settings = ControlSettings()))
        val model = GardenViewModel(repository, backgroundScope)
        runCurrent()
        model.saveSettings(ControlSettings(mode = WateringMode.MANUAL))
        runCurrent()
        assertEquals(WateringMode.AUTO, model.ui.value.repository.garden.settings!!.mode)
        assertEquals("Akses ditolak.", model.ui.value.message)
    }
    @Test fun staleDeviceDisablesWateringAsClockAdvances() = runTest(dispatcher) {
        val repository = FailingRepository()
        repository.state.value = RepositoryState(email = "admin", connected = true,
            garden = GardenSnapshot(settings = ControlSettings(mode = WateringMode.MANUAL),
                lastSeen = 100, deviceOnline = true, pumpRunning = false))
        val model = GardenViewModel(repository, backgroundScope) { 100 + testScheduler.currentTime / 1000 }
        runCurrent()
        assertTrue(model.ui.value.canWater)
        advanceTimeBy(30001)
        runCurrent()
        assertFalse(model.ui.value.canWater)
    }
    private class FailingRepository : GardenRepository {
        override val isDemo = false
        override val state = MutableStateFlow(RepositoryState())
        override suspend fun login(email: String, password: String) { throw GardenException("Email atau kata sandi salah.") }
        override fun logout() { state.value = RepositoryState() }
        override suspend fun saveSettings(settings: ControlSettings) { throw GardenException("Akses ditolak.") }
        override suspend fun water(request: PendingWatering) { throw GardenException("Akses ditolak.") }
        override fun close() = Unit
    }
}
