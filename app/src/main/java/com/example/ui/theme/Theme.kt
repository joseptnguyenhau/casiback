package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val CyberpunkColorScheme =
    darkColorScheme(
        primary = NeonCyan,
        secondary = NeonPink,
        tertiary = GoldCash,
        background = CyberBackground,
        surface = CyberSurface,
        onPrimary = CyberBackground,
        onSecondary = TextPrimary,
        onBackground = TextPrimary,
        onSurface = TextPrimary,
        error = ErrorRed
    )

@Composable
fun ShopeeCashbackTheme(
    darkTheme: Boolean = true, // Force Cyberpunk dark theme by default
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = CyberpunkColorScheme,
        typography = Typography,
        content = content
    )
}
