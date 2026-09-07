package com.example.home_garden_system.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.home_garden_system.garden.ControlSettings
import com.example.home_garden_system.ui.theme.GardenMuted

@Composable
fun SettingsDialog(settings: ControlSettings, onDismiss: () -> Unit, onSave: (ControlSettings) -> Unit) {
    var start by rememberSaveable { mutableStateOf(settings.start.toString()) }
    var stop by rememberSaveable { mutableStateOf(settings.stop.toString()) }
    var duration by rememberSaveable { mutableStateOf((settings.durationMs / 1000).toString()) }
    val startValue = start.toIntOrNull()
    val stopValue = stop.toIntOrNull()
    val durationValue = duration.toIntOrNull()
    val draft = if (startValue != null && stopValue != null && durationValue != null && durationValue in 1..15)
        settings.copy(start = startValue, stop = stopValue, durationMs = durationValue * 1000) else null
    val error = draft?.validationError() ?: if (draft == null) "Isi angka yang valid. Durasi harus 1–15 detik." else null
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Pengaturan penyiraman") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Dua batas kelembapan menjaga pompa agar tidak terlalu sering hidup dan mati.", fontSize = 13.sp, color = GardenMuted)
                OutlinedTextField(start, { start = it.take(3) }, label = { Text("Mulai menyiram (%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(stop, { stop = it.take(3) }, label = { Text("Berhenti menyiram (%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it.take(2) }, label = { Text("Durasi manual (detik)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        }, confirmButton = { TextButton(onClick = { draft?.let(onSave) }, enabled = error == null) { Text("Simpan") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } })
}
