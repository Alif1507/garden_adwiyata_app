package com.example.home_garden_system.garden

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

class FirebaseGardenRepository(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
) : GardenRepository {
    override val isDemo = false
    private val mutableState = MutableStateFlow(RepositoryState(loading = auth.currentUser != null))
    override val state = mutableState.asStateFlow()
    private val zone = database.getReference("smart_garden/zones/zone_a")
    private val connection = database.getReference(".info/connected")
    private val offset = database.getReference(".info/serverTimeOffset")
    private var zoneListener: ValueEventListener? = null
    private var socketConnected = false
    private var clockReady = false
    private var settingsWrite = false
    private var latestGarden = GardenSnapshot()

    private val connectionListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            socketConnected = snapshot.getValue(Boolean::class.java) == true
            if (!socketConnected) database.purgeOutstandingWrites()
            mutableState.update { it.copy(connected = socketConnected && clockReady) }
        }
        override fun onCancelled(error: DatabaseError) = report(error)
    }
    private val offsetListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val value = snapshot.getValue(Long::class.java)
            clockReady = value != null
            mutableState.update { it.copy(serverOffsetMs = value ?: 0, connected = socketConnected && clockReady) }
        }
        override fun onCancelled(error: DatabaseError) = report(error)
    }
    private val authListener = FirebaseAuth.AuthStateListener {
        zoneListener?.let(zone::removeEventListener)
        zoneListener = null
        latestGarden = GardenSnapshot()
        if (it.currentUser == null) {
            database.purgeOutstandingWrites()
            mutableState.update { previous -> RepositoryState(connected = previous.connected,
                serverOffsetMs = previous.serverOffsetMs) }
        } else {
            mutableState.update { previous -> previous.copy(email = it.currentUser?.email,
                garden = GardenSnapshot(), loading = true, error = null) }
            attachZone()
        }
    }

    init {
        connection.addValueEventListener(connectionListener)
        offset.addValueEventListener(offsetListener)
        auth.addAuthStateListener(authListener)
    }

    private fun attachZone() {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                latestGarden = GardenSnapshot.fromMap(snapshot.value as? Map<*, *> ?: emptyMap<Any, Any>())
                mutableState.update { previous -> previous.copy(loading = false, error = null,
                    garden = if (settingsWrite) latestGarden.copy(settings = previous.garden.settings) else latestGarden) }
            }
            override fun onCancelled(error: DatabaseError) {
                latestGarden = GardenSnapshot()
                mutableState.update { it.copy(garden = GardenSnapshot()) }
                report(error)
            }
        }
        zoneListener = listener
        zone.addValueEventListener(listener)
    }

    override suspend fun login(email: String, password: String) = translated {
        database.goOnline()
        withTimeout(15000) { auth.signInWithEmailAndPassword(email, password).await() }
        Unit
    }

    override fun logout() {
        database.purgeOutstandingWrites()
        database.goOffline()
        auth.signOut()
    }

    override suspend fun saveSettings(settings: ControlSettings) = translated {
        settings.validationError()?.let { throw GardenException(it) }
        requireConnection()
        if (state.value.garden.manualWater) throw GardenException("Tunggu konfirmasi penyiraman terlebih dahulu.")
        val previous = state.value.garden.settings
        settingsWrite = true
        try {
            withTimeout(8000) { zone.child("control").updateChildren(settings.toMap()).await() }
            mutableState.update { it.copy(garden = latestGarden.copy(settings = settings)) }
        } catch (error: Exception) {
            database.purgeOutstandingWrites()
            mutableState.update { it.copy(garden = it.garden.copy(settings = previous)) }
            throw error
        } finally { settingsWrite = false }
    }

    override suspend fun water(request: PendingWatering) = translated {
        requireConnection()
        val now = (System.currentTimeMillis() + state.value.serverOffsetMs) / 1000
        state.value.garden.wateringBlockedReason(now, state.value.connected)?.let { throw GardenException(it) }
        if (request.expiresAt <= now || request.expiresAt > now + 10) throw GardenException("Perintah sudah kedaluwarsa.")
        try {
            withTimeout(8000) {
                zone.child("control").updateChildren(mapOf(
                    "manual_water" to true,
                    "request_id" to request.id,
                    "request_expires_at" to request.expiresAt,
                )).await()
            }
        } catch (error: Exception) {
            database.purgeOutstandingWrites()
            throw error
        }
    }

    private fun requireConnection() {
        if (auth.currentUser == null) throw GardenException("Sesi berakhir. Silakan masuk kembali.")
        if (!state.value.connected) throw GardenException("Tidak terhubung. Perintah tidak dikirim.")
    }

    private fun report(error: DatabaseError) {
        mutableState.update { it.copy(loading = false, error = if (error.code == DatabaseError.PERMISSION_DENIED)
            "Akses kebun ditolak. Periksa akun dan izin administrator di Firebase."
            else "Koneksi Firebase bermasalah. Periksa jaringan lalu masuk kembali.") }
    }

    private suspend fun translated(block: suspend () -> Unit) {
        try { block() }
        catch (error: TimeoutCancellationException) { throw GardenException("Koneksi terlalu lama. Periksa jaringan lalu coba lagi.") }
        catch (error: CancellationException) { throw error }
        catch (error: GardenException) { throw error }
        catch (error: FirebaseNetworkException) { throw GardenException("Tidak dapat terhubung. Periksa koneksi internet.") }
        catch (error: FirebaseAuthException) { throw GardenException(when (error.errorCode) {
            "ERROR_INVALID_EMAIL" -> "Format email tidak valid."
            "ERROR_USER_DISABLED" -> "Akun dinonaktifkan. Hubungi administrator."
            "ERROR_TOO_MANY_REQUESTS" -> "Terlalu banyak percobaan. Tunggu sebentar."
            else -> "Email atau kata sandi salah."
        }) }
        catch (error: Exception) { throw GardenException("Perintah tidak berhasil. Periksa koneksi dan izin akun.") }
    }

    override fun close() {
        database.purgeOutstandingWrites()
        zoneListener?.let(zone::removeEventListener)
        connection.removeEventListener(connectionListener)
        offset.removeEventListener(offsetListener)
        auth.removeAuthStateListener(authListener)
    }
}
