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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import dev.mage.age.store.ThemeMode

// Mage brand fallback palette (an indigo "mobile age"), used pre-Android 12 where there is no
// Material You wallpaper extraction and no user accent seed.
private val LightColors =
    lightColorScheme(
        primary = Color(0xFF2E4BA6),
        secondary = Color(0xFF4A5B92),
        tertiary = Color(0xFF6750A4),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB4C5FF),
        secondary = Color(0xFFBAC6EA),
        tertiary = Color(0xFFCFBCFF),
    )

/** A background/foreground colour pair for a status indicator. */
data class StatusColors(
    val container: Color,
    val content: Color,
)

/** Outcome severities for the status banner, used to pick a contrast-safe colour pair. */
enum class StatusLevel { WORKING, SUCCESS, ERROR }

/**
 * Colour pairs for the operation status banner, hand-picked so the foreground meets WCAG AA contrast
 * (≥4.5:1, all pairs here are ≥5:1) against their own container in BOTH light and dark themes.
 *
 * The banner must not depend on the Material You dynamic containers, whose exact value is derived
 * from the user's wallpaper and therefore cannot be contrast-guaranteed. Rendering on its own
 * controlled container removes that dependency. Colour is paired with a distinct icon at the call
 * site so meaning never rests on colour alone (WCAG 1.4.1). Darkness is taken from the *active*
 * scheme (not the system), so the pairs stay correct when the theme is forced.
 */
@Composable
fun statusColors(level: StatusLevel): StatusColors {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (level) {
        StatusLevel.SUCCESS -> {
            if (dark) {
                StatusColors(Color(0xFF2E4730), Color(0xFFA5D6A7))
            } else {
                StatusColors(Color(0xFFC8E6C9), Color(0xFF1B5E20))
            }
        }

        StatusLevel.WORKING -> {
            if (dark) {
                StatusColors(Color(0xFF1F3A4A), Color(0xFF9FD0E5))
            } else {
                StatusColors(Color(0xFFD3E9F2), Color(0xFF0A3A4D))
            }
        }

        StatusLevel.ERROR -> {
            if (dark) {
                StatusColors(Color(0xFF4A2426), Color(0xFFF2B8B5))
            } else {
                StatusColors(Color(0xFFFFCDD2), Color(0xFF8C1D18))
            }
        }
    }
}

/**
 * Application theme. The colour scheme is resolved by priority:
 *
 *  1. [seedColorArgb] — an accent colour the user picked in Settings;
 *  2. the OS Material You palette, when [useDynamicColor] is on and the device is Android 12+;
 *  3. a fixed brand fallback scheme.
 *
 * Light/dark follows [themeMode] (SYSTEM defers to [isSystemInDarkTheme]).
 *
 * Note: the Material 3 Expressive theme/components are still `internal` in the pinned `material3`
 * release, so the expressive *look* (floating nav, segmented groups, soft shapes, gradient surfaces)
 * is built from stable APIs plus custom composables rather than `MaterialExpressiveTheme`.
 */
@Composable
fun MageTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    useDynamicColor: Boolean = true,
    seedColorArgb: Int? = null,
    content: @Composable () -> Unit,
) {
    val dark =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val context = LocalContext.current
    val colorScheme =
        when {
            seedColorArgb != null -> {
                schemeFromSeed(Color(seedColorArgb), dark)
            }

            useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }

            else -> {
                if (dark) DarkColors else LightColors
            }
        }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
