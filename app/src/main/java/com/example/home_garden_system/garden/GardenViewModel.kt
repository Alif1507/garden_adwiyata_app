package com.example.home_garden_system.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class GardenUiState(
    val repository: RepositoryState = RepositoryState(),
    val demo: Boolean = false,
    val now: Long = 0,
    val busy: Boolean = false,
    val message: String? = null,
    val pending: PendingWatering? = null,
) {
    val online: Boolean get() = repository.garden.isOnline(now, repository.connected)
    val canWater: Boolean get() = !busy && pending == null &&
        repository.garden.wateringBlockedReason(now, repository.connected) == null
    val canConfigure: Boolean get() = !busy && pending == null && repository.connected &&
        repository.garden.settings != null && !repository.garden.manualWater
}

class GardenViewModel(
    private val repository: GardenRepository,
    scopeOverride: CoroutineScope? = null,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : ViewModel() {
    private val workScope = scopeOverride ?: viewModelScope
    private val mutableUi = MutableStateFlow(GardenUiState(demo = repository.isDemo))
    val ui = mutableUi.asStateFlow()

    init {
        workScope.launch {
            repository.state.collect { state ->
                mutableUi.update { it.copy(repository = state) }
                tick()
            }
        }
        workScope.launch { while (true) { tick(); delay(1000) } }
    }

    private fun tick() {
        mutableUi.update { current ->
            val now = clock() + current.repository.serverOffsetMs / 1000
            val pending = current.pending
            val status = pending?.status(current.repository.garden.acknowledgedRequestId, now)
            when (status) {
                RequestStatus.ACCEPTED -> current.copy(now = now, pending = null,
                    message = "Perintah diterima perangkat. Pantau status pompa.")
                RequestStatus.EXPIRED -> current.copy(now = now, pending = null,
                    message = "Perintah kedaluwarsa tanpa konfirmasi. Periksa perangkat sebelum mencoba lagi.")
                else -> current.copy(now = now)
            }
        }
    }

    fun login(email: String, password: String) {
        if (!repository.isDemo && (email.isBlank() || password.isBlank())) {
            mutableUi.update { it.copy(message = "Isi email dan kata sandi terlebih dahulu.") }
            return
        }
        action { repository.login(email.trim(), password) }
    }

    fun logout() {
        repository.logout()
        mutableUi.value = GardenUiState(demo = repository.isDemo)
    }

    fun saveSettings(settings: ControlSettings) {
        settings.validationError()?.let { error ->
            mutableUi.update { it.copy(message = error) }; return
        }
        if (!ui.value.canConfigure) return
        action {
            repository.saveSettings(settings)
            mutableUi.update { it.copy(message = "Pengaturan berhasil disimpan.") }
        }
    }

    fun water() {
        if (!ui.value.canWater) return
        val request = PendingWatering(UUID.randomUUID().toString(), ui.value.now + 10)
        mutableUi.update { it.copy(pending = request, message = null) }
        action {
            try { repository.water(request) }
            catch (error: Exception) {
                mutableUi.update { it.copy(pending = null) }
                throw error
            }
        }
    }

    fun dismissMessage() = mutableUi.update { it.copy(message = null) }

    private fun action(block: suspend () -> Unit) {
        if (mutableUi.value.busy) return
        mutableUi.update { it.copy(busy = true, message = null) }
        workScope.launch {
            try { block() }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutableUi.update { it.copy(message = (error as? GardenException)?.message
                    ?: "Terjadi kesalahan. Periksa koneksi dan coba lagi.") }
            } finally { mutableUi.update { it.copy(busy = false) } }
        }
    }

    override fun onCleared() { repository.close() }
}
