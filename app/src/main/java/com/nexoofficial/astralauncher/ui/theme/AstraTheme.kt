package com.nexoofficial.astralauncher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AstraDarkScheme = darkColorScheme(
    primary = Color(0xFFFFA000),
    secondary = Color(0xFFFFC04D),
    tertiary = Color(0xFFFF6D00),
    background = Color(0xFF08090C),
    surface = Color(0xFF111217),
    onPrimary = Color(0xFF171007),
    onBackground = Color(0xFFF7F4EE),
    onSurface = Color(0xFFF7F4EE)
)

@Composable
fun AstraLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AstraDarkScheme,
        content = content
    )
}
