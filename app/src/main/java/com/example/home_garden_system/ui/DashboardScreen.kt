package com.example.home_garden_system.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.home_garden_system.garden.*
import com.example.home_garden_system.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    state: GardenUiState,
    saveSettings: (ControlSettings) -> Unit,
    water: () -> Unit,
    refresh: () -> Unit,
    logout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showSettings by remember { mutableStateOf(false) }
    var showLogout by remember { mutableStateOf(false) }
    val garden = state.repository.garden
    val settings = garden.settings
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Brand()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = refresh, enabled = !state.busy,
                    modifier = Modifier.semantics { contentDescription = "Segarkan data" },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFFE7EEDC))) {
                    if (state.busy) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = GardenGreen, strokeWidth = 2.dp)
                    } else {
                        GardenIcon(GardenSymbol.REFRESH, Modifier.size(16.dp), color = GardenInk)
                    }
                }
                FilledTonalIconButton(onClick = { showLogout = true }, enabled = !state.busy,
                    modifier = Modifier.semantics { contentDescription = "Akun dan keluar" },
                    colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color(0xFFE7EEDC))) {
                    Text("SH", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        if (state.demo) {
            Surface(color = Color(0xFFECEDD9), shape = RoundedCornerShape(10.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(6.dp).background(Color(0xFF8D743B), CircleShape))
                    Text("SIMULASI DEMO", color = Color(0xFF716030), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
                    Text("·  Data & penyiraman virtual", color = Color(0xFF716030), fontSize = 10.sp)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("RUANG HIJAU SEKOLAH", color = GardenMuted, fontSize = 10.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium)
            Text("Kebun kecil,\nmanfaat besar.", fontSize = 32.sp, lineHeight = 37.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-.9).sp, color = GardenInk)
            Text("Mari tumbuhkan kebiasaan baik hari ini.", color = GardenMuted, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                GardenIcon(GardenSymbol.LEAF, Modifier.size(17.dp))
                Text("Kebun Zona A", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            StatusPill(if (state.online) "Perangkat online" else "Perangkat offline", state.online)
        }
        state.repository.error?.let { Notice(it, warning = true) }
        if (!state.repository.connected && !state.repository.loading) {
            Notice("Koneksi terputus. Data terakhir ditampilkan; kontrol dinonaktifkan.", warning = true)
        }
        if (state.repository.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Menghubungkan ke kebun…", fontSize = 13.sp, color = GardenMuted)
        }
        SoilCard(garden.sensors, state.online)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SensorCard("Suhu udara", garden.sensors.temperature?.let { String.format(Locale.forLanguageTag("id-ID"), "%.1f", it) } ?: "—",
                "°C", GardenSymbol.THERMOMETER, Color(0xFFF6EADC), Modifier.weight(1f))
            SensorCard("Kelembapan", garden.sensors.humidity?.toInt()?.toString() ?: "—",
                "% udara", GardenSymbol.DROP, Color(0xFFE6EDF4), Modifier.weight(1f))
            SensorCard("Cahaya", garden.sensors.light?.toInt()?.toString() ?: "—",
                "lux", GardenSymbol.SUN, Color(0xFFF6F0D8), Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("PEMBARUAN TERAKHIR", fontSize = 9.sp, letterSpacing = .8.sp, color = GardenMuted)
            Text(lastUpdate(garden.sensors.updatedAt, state.now), fontSize = 10.sp, color = GardenMuted)
        }
        WateringCard(state, saveSettings, water) { showSettings = true }
        Notice("Dirawat dengan bijak", "Pompa dibatasi 15 detik per penyiraman. Jeda otomatis 60 detik membantu menjaga tanah tetap sehat.")
        Row(Modifier.fillMaxWidth().padding(bottom = 22.dp), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            GardenIcon(GardenSymbol.LEAF, Modifier.size(14.dp), GardenMuted)
            Spacer(Modifier.width(7.dp))
            Text("SATU LANGKAH KECIL UNTUK BUMI", fontSize = 9.sp, letterSpacing = 1.sp, color = GardenMuted)
        }
    }
    if (showSettings && settings != null) SettingsDialog(settings, onDismiss = { showSettings = false }) {
        saveSettings(it)
        showSettings = false
    }
    if (showLogout) AlertDialog(onDismissRequest = { showLogout = false },
        title = { Text("Keluar dari kebun?") },
        text = { Text(if (state.demo) "Simulasi akan direset saat masuk kembali." else "Penyiraman otomatis tetap dikelola oleh ESP32.") },
        confirmButton = { TextButton(onClick = { showLogout = false; logout() }) { Text("Keluar") } },
        dismissButton = { TextButton(onClick = { showLogout = false }) { Text("Batal") } })
}

@Composable
private fun SoilCard(sensors: SensorReadings, online: Boolean) {
    Surface(color = GardenGreen, shape = RoundedCornerShape(26.dp), contentColor = Color.White) {
        Column(Modifier.fillMaxWidth().padding(22.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("KELEMBAPAN TANAH", fontSize = 10.sp, letterSpacing = 1.2.sp, color = Color(0xFFD0DFCF))
                GardenIcon(GardenSymbol.DROP, Modifier.size(20.dp), GardenLime)
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(sensors.soil?.toString() ?: "—", fontSize = 66.sp, lineHeight = 72.sp,
                            fontWeight = FontWeight.Light, letterSpacing = (-3).sp)
                        Text("%", fontSize = 26.sp, modifier = Modifier.padding(start = 4.dp, bottom = 9.dp), color = GardenLime)
                    }
                    Surface(color = GardenLime, shape = RoundedCornerShape(30.dp)) {
                        Text(if (online) sensors.soilLabel else "Data terakhir", color = GardenInk,
                            fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val inset = 7.dp.toPx()
                        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                        drawArc(Color.White.copy(alpha = .13f), 135f, 270f, false, Offset(inset, inset), arcSize,
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                        sensors.soil?.let { drawArc(GardenLime, 135f, 270f * it / 100, false, Offset(inset, inset), arcSize,
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)) }
                    }
                    GardenIcon(GardenSymbol.LEAF, Modifier.size(36.dp), GardenLime)
                }
            }
            Spacer(Modifier.height(19.dp))
            HorizontalDivider(color = Color.White.copy(alpha = .16f))
            Spacer(Modifier.height(13.dp))
            Text(when {
                !online -> "Menunggu pembaruan dari perangkat."
                sensors.soil == null -> "Sensor belum mengirim pembacaan yang valid."
                sensors.soil < 30 -> "Tanah mulai kering. Periksa kebutuhan airnya."
                sensors.soil < 60 -> "Kelembapan terjaga. Terus pantau tanamanmu."
                else -> "Tanah masih basah. Beri waktu untuk menyerap."
            }, fontSize = 11.sp, lineHeight = 17.sp, color = Color(0xFFE0EBDD))
        }
    }
}

@Composable
private fun SensorCard(label: String, value: String, unit: String, symbol: GardenSymbol, tint: Color, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Box(Modifier.size(34.dp).background(tint, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                GardenIcon(symbol, Modifier.size(19.dp))
            }
            Text(label, fontSize = 10.sp, color = GardenMuted)
            Column {
                Text(value, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-.5).sp)
                Text(unit, fontSize = 10.sp, color = GardenMuted)
            }
        }
    }
}

@Composable
private fun WateringCard(state: GardenUiState, save: (ControlSettings) -> Unit, water: () -> Unit, configure: () -> Unit) {
    val garden = state.repository.garden
    val settings = garden.settings
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Penyiraman", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Air secukupnya, tumbuh sebaiknya.", fontSize = 11.sp, color = GardenMuted)
                }
                IconButton(onClick = configure, enabled = state.canConfigure,
                    modifier = Modifier.semantics { contentDescription = "Pengaturan penyiraman" }) {
                    GardenIcon(GardenSymbol.TUNE, color = if (state.canConfigure) GardenGreen else GardenMuted)
                }
            }
            Row(Modifier.fillMaxWidth().background(GardenBackground, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                WateringMode.entries.forEach { mode ->
                    val selected = settings?.mode == mode
                    Button(onClick = { settings?.let { save(it.copy(mode = mode)) } },
                        enabled = state.canConfigure, modifier = Modifier.weight(1f).heightIn(min = 44.dp),
                        shape = RoundedCornerShape(11.dp), elevation = null,
                        colors = ButtonDefaults.buttonColors(containerColor = if (selected) GardenGreen else Color.Transparent,
                            contentColor = if (selected) Color.White else GardenMuted,
                            disabledContainerColor = if (selected) GardenGreen.copy(alpha = .35f) else Color.Transparent)) {
                        Text(if (mode == WateringMode.AUTO) "Otomatis" else "Manual", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(if (settings?.mode == WateringMode.MANUAL) "Kamu menentukan kapan tanaman disiram."
                else "ESP32 menyiram saat tanah mulai kering.", color = GardenMuted, fontSize = 12.sp, lineHeight = 18.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingValue("Mulai pada", "${settings?.start ?: "—"}%", Modifier.weight(1f))
                SettingValue("Berhenti pada", "${settings?.stop ?: "—"}%", Modifier.weight(1f))
                SettingValue("Durasi manual", "${settings?.durationMs?.div(1000) ?: "—"} dtk", Modifier.weight(1f))
            }
            TextButton(onClick = configure, enabled = state.canConfigure, contentPadding = PaddingValues(0.dp)) {
                Text("Atur batas & durasi", fontSize = 12.sp)
                Spacer(Modifier.width(8.dp))
                GardenIcon(GardenSymbol.ARROW, Modifier.size(16.dp))
            }
            HorizontalDivider(color = GardenBackground)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GardenIcon(GardenSymbol.POWER, Modifier.size(18.dp))
                Text(if (!state.online) "Status pompa belum terkonfirmasi" else when (garden.pumpRunning) {
                    true -> "Pompa menyala"
                    false -> "Pompa beristirahat"
                    null -> "Menunggu status pompa"
                }, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Button(onClick = water, enabled = state.canWater, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                if (state.pending != null) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
                else GardenIcon(GardenSymbol.DROP, Modifier.size(18.dp), if (state.canWater) Color.White else GardenMuted)
                Spacer(Modifier.width(9.dp))
                Text(if (state.pending != null) "Menunggu perangkat…" else "Siram sekarang", fontWeight = FontWeight.SemiBold)
            }
            Text(if (state.pending != null) "Perintah dikirim. Menunggu konfirmasi ESP32."
                else garden.wateringBlockedReason(state.now, state.repository.connected)
                    ?: "Penyiraman ${settings?.durationMs?.div(1000)} detik setelah dikonfirmasi perangkat.",
                fontSize = 10.sp, color = GardenMuted, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun SettingValue(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 9.sp, color = GardenMuted)
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Medium, color = GardenGreen)
    }
}

@Composable
private fun StatusPill(label: String, online: Boolean) {
    val color = if (online) GardenGreen else Color(0xFF8B6345)
    Row(Modifier.background(if (online) Color(0xFFE7EFDF) else Color(0xFFF0E8DC), RoundedCornerShape(30.dp))
        .padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(5.dp).background(color, CircleShape))
        Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Notice(title: String, body: String? = null, warning: Boolean = false) {
    Surface(color = if (warning) Color(0xFFF8EADA) else Color(0xFFEAF0E1), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GardenIcon(if (warning) GardenSymbol.SIGNAL else GardenSymbol.LEAF, Modifier.size(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                body?.let { Text(it, fontSize = 11.sp, lineHeight = 17.sp, color = GardenMuted) }
            }
        }
    }
}

private fun lastUpdate(timestamp: Long, now: Long): String {
    if (timestamp <= 0) return "Belum ada data"
    val age = now - timestamp
    return when {
        age < 0 -> "Waktu perangkat tidak sesuai"
        age < 10 -> "Baru saja"
        age < 60 -> "$age detik lalu"
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.forLanguageTag("id-ID")).format(Date(timestamp * 1000))
    }
}
