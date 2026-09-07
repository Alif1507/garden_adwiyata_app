package com.example.home_garden_system.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.home_garden_system.garden.GardenViewModel
import com.example.home_garden_system.garden.GardenUiState
import com.example.home_garden_system.ui.theme.*

@Composable
fun GardenApp(model: GardenViewModel) {
    val state by model.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); model.dismissMessage() }
    }
    Scaffold(containerColor = GardenBackground,
        snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        if (state.repository.email == null) {
            LoginScreen(state, model::login, Modifier.padding(padding))
        } else {
            DashboardScreen(state, model::saveSettings, model::water, model::logout, Modifier.padding(padding))
        }
    }
}

@Composable
private fun LoginScreen(state: GardenUiState, login: (String, String) -> Unit, modifier: Modifier) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var visible by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(28.dp))
        Brand()
        GardenIllustration(Modifier.fillMaxWidth().height(230.dp))
        Text("Rawat kebun.\nTumbuhkan kehidupan.", fontSize = 30.sp, lineHeight = 37.sp,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, color = GardenInk)
        Spacer(Modifier.height(12.dp))
        Text("Pantau tanaman dan atur penyiraman,\nlangsung dari genggamanmu.", color = GardenMuted,
            fontSize = 14.sp, lineHeight = 22.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (state.demo) {
            Surface(color = Color(0xFFEBF0DF), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Kenali kebun pintarmu", fontWeight = FontWeight.SemiBold)
                    Text("Coba pemantauan dan penyiraman dengan data simulasi. Tidak memerlukan akun atau perangkat.",
                        fontSize = 13.sp, color = GardenMuted, lineHeight = 20.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { login("", "") }, enabled = !state.busy,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Text("Jelajahi kebun demo", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(12.dp))
                GardenIcon(GardenSymbol.ARROW, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("MODE DEMO · TANPA PERANGKAT FISIK", color = GardenMuted, fontSize = 10.sp, letterSpacing = 1.sp)
        } else {
            OutlinedTextField(email, { email = it }, label = { Text("Email administrator") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !state.busy)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(password, { password = it }, label = { Text("Kata sandi") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { TextButton(onClick = { visible = !visible }) { Text(if (visible) "Sembunyi" else "Lihat", fontSize = 11.sp) } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), enabled = !state.busy)
            Spacer(Modifier.height(20.dp))
            Button(onClick = { login(email, password) }, enabled = !state.busy,
                shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Masuk ke kebun", fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(14.dp))
            Text("Belum punya akun? Hubungi administrator sekolah.", color = GardenMuted,
                fontSize = 12.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        Text("ADIWIYATA  /  PEDULI LINGKUNGAN", color = GardenMuted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun Brand(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp).background(GardenGreen, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            GardenIcon(GardenSymbol.LEAF, color = GardenLime)
        }
        Column {
            Text("adiwiyata", fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = (-.7).sp, color = GardenInk)
            Text("SMART GARDEN", fontSize = 9.sp, letterSpacing = 2.sp, color = GardenMuted)
        }
    }
}
