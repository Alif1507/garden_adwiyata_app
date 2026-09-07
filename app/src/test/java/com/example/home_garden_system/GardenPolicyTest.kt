package com.example.home_garden_system

import com.example.home_garden_system.garden.*
import org.junit.Assert.*
import org.junit.Test

class GardenPolicyTest {
    @Test fun thresholdsMustHaveARealGap() {
        assertNotNull(ControlSettings(start = 45, stop = 45).validationError())
        assertNotNull(ControlSettings(start = -1).validationError())
        assertNotNull(ControlSettings(stop = 101).validationError())
        assertNull(ControlSettings(start = 0, stop = 100).validationError())
    }
    @Test fun manualDurationNeverExceedsHardwareLimit() {
        assertNotNull(ControlSettings(durationMs = 16000).validationError())
        assertNotNull(ControlSettings(durationMs = 999).validationError())
        assertNull(ControlSettings(durationMs = 15000).validationError())
    }
    @Test fun heartbeatExpiresWithoutAnotherDatabaseEvent() {
        val garden = GardenSnapshot(lastSeen = 100, deviceOnline = true)
        assertTrue(garden.isOnline(129, true))
        assertFalse(garden.isOnline(130, true))
        assertFalse(garden.isOnline(99, true))
        assertFalse(garden.isOnline(110, false))
    }
    @Test fun missingReadingsRemainUnavailable() {
        val garden = GardenSnapshot.fromMap(emptyMap<String, Any>())
        assertNull(garden.sensors.soil)
        assertNull(garden.sensors.temperature)
        assertNull(garden.settings)
        assertFalse(garden.isOnline(100, true))
    }
    @Test fun mixedFirebaseNumericTypesAreParsedWithoutFalseZeroes() {
        val garden = GardenSnapshot.fromMap(mapOf("sensors" to mapOf(
            "soil_moisture" to 42L, "temperature" to 28.5, "humidity" to 67L,
            "light" to -1, "updated_at" to 100L
        )))
        assertEquals(42, garden.sensors.soil)
        assertEquals(28.5, garden.sensors.temperature!!, 0.001)
        assertEquals(67.0, garden.sensors.humidity!!, 0.001)
        assertNull(garden.sensors.light)
    }
    @Test fun malformedControlDoesNotBecomeWateringDefaults() {
        val garden = GardenSnapshot.fromMap(mapOf("control" to mapOf(
            "mode" to "auto", "moisture_start" to 80L, "moisture_stop" to 20L,
            "manual_duration_ms" to 5000L
        )))
        assertNull(garden.settings)
    }
    @Test fun requestRequiresFreshDeviceManualModeAndIdlePump() {
        val garden = GardenSnapshot(settings = ControlSettings(mode = WateringMode.MANUAL), lastSeen = 100,
            deviceOnline = true, pumpRunning = false)
        assertNull(garden.wateringBlockedReason(105, true))
        assertNotNull(garden.copy(pumpRunning = true).wateringBlockedReason(105, true))
        assertNotNull(garden.copy(settings = ControlSettings()).wateringBlockedReason(105, true))
        assertNotNull(garden.wateringBlockedReason(130, true))
        assertNotNull(garden.copy(manualWater = true, requestExpiresAt = 110).wateringBlockedReason(105, true))
    }
    @Test fun acknowledgementMustMatchAndArriveBeforeExpiry() {
        val request = PendingWatering("request-1", 110)
        assertEquals(RequestStatus.WAITING, request.status("old-id", 105))
        assertEquals(RequestStatus.ACCEPTED, request.status("request-1", 105))
        assertEquals(RequestStatus.EXPIRED, request.status("old-id", 110))
    }
}
