package com.myenvironment.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LauncherDarkColorScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF002E6B),
    primaryContainer = Color(0xFF1E3A5F),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFA8C7FA),
    onSecondary = Color(0xFF0F2F5F),
    background = Color.Transparent,
    surface = Color(0xFF16181D),
    surfaceVariant = Color(0xFF232730),
    onSurface = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFFBDC1C6),
    error = Color(0xFFF28B82)
)

@Composable
fun MyLauncherTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LauncherDarkColorScheme,
        content = content
    )
}
