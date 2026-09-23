package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = CineastGold,
    onPrimary = Color.Black,
    primaryContainer = CineastGoldDark,
    onPrimaryContainer = CineastGoldBright,
    secondary = CineastCyan,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF0E3D48),
    onSecondaryContainer = Color(0xFFA5F3FC),
    tertiary = CineastGreen,
    onTertiary = Color.Black,
    background = CineastBackgroundDark,
    onBackground = CineastTextPrimary,
    surface = CineastSurfaceDark,
    onSurface = CineastTextPrimary,
    surfaceVariant = CineastSurfaceElevated,
    onSurfaceVariant = CineastTextSecondary,
    outline = CineastBorderDark
)

private val LightColorScheme = lightColorScheme(
    primary = CineastGoldDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFEF3C7),
    onPrimaryContainer = Color(0xFF78350F),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = Color(0xFF0369A1),
    tertiary = Color(0xFF059669),
    onTertiary = Color.White,
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Default to sleek cinema dark studio mode
    dynamicColor: Boolean = false, // Keep signature cinematic look
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
