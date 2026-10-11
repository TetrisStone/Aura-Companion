package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = AmbientIndigo,
    secondary = ElectricCyan,
    tertiary = RadiantGold,
    background = MidnightViolet,
    surface = DeepObsidian,
    onPrimary = Color.White,
    onSecondary = Color(0xFF0C0A1E),
    onTertiary = Color(0xFF0C0A1E),
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = AmbientIndigo,
    secondary = ElectricCyan,
    tertiary = RadiantGold,
    background = Color(0xFF13112B),
    surface = Color(0xFF1B173B),
    onPrimary = Color.White,
    onSecondary = Color(0xFF0C0A1E),
    onTertiary = Color(0xFF0C0A1E),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Keep consistent visual novel theme
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else DarkColorScheme // Companion apps look best in cinematic dark mode

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
