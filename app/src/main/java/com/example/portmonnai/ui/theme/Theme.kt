package com.example.portmonnai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkGray = Color(0xFF121212)
val LightGray = Color(0xFF1E1E1E)
val SoberBlue = Color(0xFF2563EB)
val SoberBlueLight = Color(0xFF60A5FA) // Lighter blue for dark theme contrast
val SoberSuccess = Color(0xFF2E7D32)
val SoberError = Color(0xFFC62828)
val SurfaceLight = Color(0xFFF8FAFC)

private val DarkColorScheme = darkColorScheme(
    primary = SoberBlueLight,
    secondary = Color(0xFF94A3B8),
    tertiary = SoberSuccess,
    background = Color(0xFF1E293B), // Was 0F172A, now slightly lighter slate
    surface = Color(0xFF334155),    // Was 1E293B, now lighter gray-slate
    onPrimary = Color(0xFF0F172A),
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF1F5F9),
    onSurface = Color(0xFFF1F5F9)
)

private val LightColorScheme = lightColorScheme(
    primary = SoberBlue,
    secondary = Color(0xFF64748B),
    tertiary = SoberSuccess,
    background = Color.White,
    surface = SurfaceLight,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

@Composable
fun PortMonnaiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
