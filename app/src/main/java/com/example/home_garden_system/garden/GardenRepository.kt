package com.example.home_garden_system.garden

import kotlinx.coroutines.flow.StateFlow

data class RepositoryState(
    val email: String? = null,
    val connected: Boolean = false,
    val loading: Boolean = false,
    val garden: GardenSnapshot = GardenSnapshot(),
    val serverOffsetMs: Long = 0,
    val error: String? = null,
)

interface GardenRepository {
    val isDemo: Boolean
    val state: StateFlow<RepositoryState>
    suspend fun login(email: String, password: String)
    fun logout()
    suspend fun saveSettings(settings: ControlSettings)
    suspend fun water(request: PendingWatering)
    suspend fun refresh() {}
    fun close()
}
