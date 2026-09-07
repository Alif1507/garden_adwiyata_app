package com.example.home_garden_system.garden

enum class WateringMode(val wire: String) { AUTO("auto"), MANUAL("manual") }

data class ControlSettings(
    val mode: WateringMode = WateringMode.AUTO,
    val start: Int = 30,
    val stop: Int = 45,
    val durationMs: Int = 5000,
) {
    fun validationError(): String? = when {
        start !in 0..99 || stop !in 1..100 || start >= stop ->
            "Batas mulai harus lebih kecil dari batas berhenti (0–100%)."
        durationMs !in 1000..15000 -> "Durasi penyiraman harus 1–15 detik."
        else -> null
    }

    fun toMap(): Map<String, Any> = mapOf("mode" to mode.wire, "moisture_start" to start,
        "moisture_stop" to stop, "manual_duration_ms" to durationMs)
}

data class SensorReadings(
    val soil: Int? = null, val soilRaw: Int? = null, val temperature: Double? = null,
    val humidity: Double? = null, val light: Double? = null, val updatedAt: Long = 0,
) {
    val soilLabel: String get() = when {
        soil == null -> "Belum ada data"
        soil < 30 -> "Kering"
        soil < 60 -> "Lembap"
        else -> "Basah"
    }
}

data class GardenSnapshot(
    val sensors: SensorReadings = SensorReadings(),
    val settings: ControlSettings? = null,
    val pumpRunning: Boolean? = null,
    val lastSeen: Long = 0,
    val deviceOnline: Boolean = false,
    val manualWater: Boolean = false,
    val requestId: String = "",
    val requestExpiresAt: Long = 0,
    val acknowledgedRequestId: String = "",
) {
    fun isOnline(now: Long, connected: Boolean): Boolean =
        connected && deviceOnline && lastSeen > 0 && now - lastSeen in 0..29

    fun wateringBlockedReason(now: Long, connected: Boolean): String? = when {
        !isOnline(now, connected) -> "Perangkat belum terhubung."
        settings == null -> "Pengaturan kebun belum tersedia."
        settings.mode != WateringMode.MANUAL -> "Pilih mode Manual untuk menyiram."
        pumpRunning == null -> "Menunggu status pompa."
        pumpRunning -> "Penyiraman sedang berlangsung."
        manualWater && requestExpiresAt > now -> "Menunggu konfirmasi perangkat."
        else -> null
    }

    companion object {
        fun fromMap(data: Map<*, *>): GardenSnapshot {
            val sensors = data.child("sensors")
            val control = data.child("control")
            val device = data.child("device")
            val mode = WateringMode.entries.find { it.wire == control["mode"] }
            val start = control.integer("moisture_start")
            val stop = control.integer("moisture_stop")
            val duration = control.integer("manual_duration_ms")
            val settings = if (mode != null && start != null && stop != null && duration != null)
                ControlSettings(mode, start, stop, duration).takeIf { it.validationError() == null } else null
            return GardenSnapshot(
                sensors = SensorReadings(
                    soil = sensors.integer("soil_moisture")?.takeIf { it in 0..100 },
                    soilRaw = sensors.integer("soil_raw")?.takeIf { it in 0..4095 },
                    temperature = sensors.number("temperature")?.takeIf { it in -40.0..80.0 },
                    humidity = sensors.number("humidity")?.takeIf { it in 0.0..100.0 },
                    light = sensors.number("light")?.takeIf { it >= 0 },
                    updatedAt = sensors.seconds("updated_at"),
                ),
                settings = settings,
                pumpRunning = data.child("pump")["status"] as? Boolean,
                lastSeen = device.seconds("last_seen"),
                deviceOnline = device["online"] == true,
                manualWater = control["manual_water"] == true,
                requestId = control["request_id"] as? String ?: "",
                requestExpiresAt = control.seconds("request_expires_at"),
                acknowledgedRequestId = device["acknowledged_request_id"] as? String ?: "",
            )
        }
    }
}

private fun Map<*, *>.child(key: String): Map<*, *> = this[key] as? Map<*, *> ?: emptyMap<Any, Any>()
private fun Map<*, *>.number(key: String): Double? = (this[key] as? Number)?.toDouble()?.takeIf { it.isFinite() }
private fun Map<*, *>.integer(key: String): Int? = number(key)?.takeIf {
    it % 1.0 == 0.0 && it >= Int.MIN_VALUE && it <= Int.MAX_VALUE
}?.toInt()
private fun Map<*, *>.seconds(key: String): Long = number(key)?.takeIf {
    it % 1.0 == 0.0 && it > 0 && it < 100_000_000_000L
}?.toLong() ?: 0

enum class RequestStatus { WAITING, ACCEPTED, EXPIRED }
data class PendingWatering(val id: String, val expiresAt: Long) {
    fun status(acknowledgedId: String, now: Long): RequestStatus = when {
        acknowledgedId == id -> RequestStatus.ACCEPTED
        now >= expiresAt -> RequestStatus.EXPIRED
        else -> RequestStatus.WAITING
    }
}

class GardenException(message: String) : Exception(message)
