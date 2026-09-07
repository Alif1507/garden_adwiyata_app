package com.example.home_garden_system

import com.example.home_garden_system.garden.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DemoWateringTest {
    @Test fun manualWateringIsAcknowledgedAndPumpStarts() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = DemoGardenRepository()
        try {
            val model = GardenViewModel(repository, backgroundScope)
            model.login("", "")
            runCurrent()
            model.saveSettings(ControlSettings(mode = WateringMode.MANUAL))
            runCurrent()
            assertTrue(model.ui.value.canWater)
            model.water()
            runCurrent()
            assertNotNull(model.ui.value.pending)
            advanceTimeBy(501)
            runCurrent()
            assertEquals(true, model.ui.value.repository.garden.pumpRunning)
            assertNull(model.ui.value.pending)
        } finally {
            repository.close()
            Dispatchers.resetMain()
        }
    }
}
