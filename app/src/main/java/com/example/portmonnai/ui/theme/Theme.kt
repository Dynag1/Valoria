package com.example.portmonnai.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val DarkGray = Color(0xFF121212)
val LightGray = Color(0xFF1E1E1E)
val Gold = Color(0xFFFFD700)
val Silver = Color(0xFFC0C0C0)
val GreenHedge = Color(0xFF00C853)
val RedHedge = Color(0xFFFF1744)

private val DarkColorScheme = darkColorScheme(
    primary = Gold,
    secondary = Silver,
    tertiary = GreenHedge,
    background = DarkGray,
    surface = LightGray,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Gold,
    secondary = Silver,
    tertiary = GreenHedge,
    background = Color.White,
    surface = Color(0xFFF5F5F5),
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
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
