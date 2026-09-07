package com.example.home_garden_system.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val GardenGreen = Color(0xFF215B42)
val GardenInk = Color(0xFF203D30)
val GardenMuted = Color(0xFF65766B)
val GardenBackground = Color(0xFFF6F8F2)
val GardenLime = Color(0xFFD8EDAB)

private val LightColorScheme = lightColorScheme(
    primary = GardenGreen,
    secondary = GardenMuted,
    tertiary = Color(0xFF8D6A35),
    background = GardenBackground,
    surface = Color.White,
    onPrimary = Color.White,
    onBackground = GardenInk,
    onSurface = GardenInk,
    surfaceVariant = Color(0xFFEDF2E7),
    onSurfaceVariant = GardenMuted,
    outline = Color(0xFFB8C6B8),
)

@Composable
fun Home_Garden_SystemTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        content = content
    )
}
