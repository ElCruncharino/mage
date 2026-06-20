/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui.theme

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

// Mage brand fallback palette (an indigo "mobile age"), used pre-Android 12 where there is no
// Material You wallpaper extraction.
private val LightColors = lightColorScheme(
    primary = Color(0xFF2E4BA6),
    secondary = Color(0xFF4A5B92),
    tertiary = Color(0xFF6750A4),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C5FF),
    secondary = Color(0xFFBAC6EA),
    tertiary = Color(0xFFCFBCFF),
)

/**
 * Application theme: Material You dynamic colors on Android 12+, a fixed Mage palette below that, and
 * follows the system light/dark setting.
 */
@Composable
fun MageTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (dark) DarkColors else LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
