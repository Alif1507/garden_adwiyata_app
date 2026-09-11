package com.example.home_garden_system.garden

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A visibly simulated, in-memory garden. Never used by the live flavor. */
class DemoGardenRepository : GardenRepository {
    override val isDemo = true
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(RepositoryState())
    override val state = mutableState.asStateFlow()
    private var pumpStarted = 0L
    private var stoppedAt = 0L
    private var ticks = 0

    init {
        scope.launch {
            while (true) {
                delay(1000)
                if (state.value.email != null) simulate()
            }
        }
    }

    override suspend fun login(email: String, password: String) {
        val now = System.currentTimeMillis() / 1000
        pumpStarted = 0
        stoppedAt = 0
        ticks = 0
        mutableState.value = RepositoryState(email = "Sahabat Hijau", connected = true,
            garden = GardenSnapshot(sensors = SensorReadings(42, 2140, 28.5, 67.0, 920.0, now),
                settings = ControlSettings(), pumpRunning = false, lastSeen = now, deviceOnline = true))
    }

    override fun logout() { mutableState.value = RepositoryState() }

    override suspend fun saveSettings(settings: ControlSettings) {
        settings.validationError()?.let { throw GardenException(it) }
        if (state.value.email == null) throw GardenException("Masuk terlebih dahulu.")
        val garden = state.value.garden
        val changingMode = garden.settings?.mode != settings.mode
        if (changingMode && garden.pumpRunning == true) stoppedAt = System.currentTimeMillis()
        mutableState.update { it.copy(garden = garden.copy(settings = settings,
            pumpRunning = if (changingMode) false else garden.pumpRunning)) }
    }

    override suspend fun water(request: PendingWatering) {
        val now = System.currentTimeMillis() / 1000
        state.value.garden.wateringBlockedReason(now, state.value.connected)?.let { throw GardenException(it) }
        mutableState.update { it.copy(garden = it.garden.copy(manualWater = true,
            requestId = request.id, requestExpiresAt = request.expiresAt)) }
        delay(500)
        if (state.value.email == null || System.currentTimeMillis() / 1000 >= request.expiresAt) return
        pumpStarted = System.currentTimeMillis()
        mutableState.update { it.copy(garden = it.garden.copy(manualWater = false,
            acknowledgedRequestId = request.id, pumpRunning = true)) }
    }

    override suspend fun refresh() {
        simulate()
    }

    private fun simulate() {
        ticks++
        val now = System.currentTimeMillis()
        val garden = state.value.garden
        val settings = garden.settings ?: return
        var running = garden.pumpRunning == true
        var soil = garden.sensors.soil ?: 42
        if (running) soil = (soil + 2).coerceAtMost(100)
        else if (ticks % 5 == 0) soil = (soil - 1).coerceAtLeast(0)
        if (running && (now - pumpStarted >= 15000 ||
                    (settings.mode == WateringMode.MANUAL && now - pumpStarted >= settings.durationMs) ||
                    (settings.mode == WateringMode.AUTO && soil >= settings.stop))) {
            running = false
            stoppedAt = now
        }
        if (!running && settings.mode == WateringMode.AUTO && soil <= settings.start && now - stoppedAt >= 60000) {
            running = true
            pumpStarted = now
        }
        mutableState.update { it.copy(garden = garden.copy(pumpRunning = running, lastSeen = now / 1000,
            sensors = garden.sensors.copy(soil = soil, soilRaw = 3100 - soil * 17, updatedAt = now / 1000))) }
    }

    override fun close() = scope.cancel()
}
