/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import android.graphics.Color as AndroidColor

/**
 * Builds a coherent Material light/dark [ColorScheme] from a single seed [Color].
 *
 * Material's own tonal-palette generator (HCT/CAM16) is not exposed by the stable `material3`
 * artifact and pulling in a colour-utilities dependency was out of scope, so this derives the key
 * colour roles by manipulating the seed's hue/saturation/value. It is not a pixel-exact Material You
 * palette, but it yields a consistent, on-brand scheme that visibly tracks the seed — which is all
 * the accent-picker theming needs. Neutral surfaces are left to the Material defaults so body-text
 * contrast stays predictable; only the accent roles and `surfaceTint` follow the seed.
 */
fun schemeFromSeed(
    seed: Color,
    dark: Boolean,
): ColorScheme {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(seed.toArgb(), hsv)
    val hue = hsv[0]
    val sat = hsv[1].coerceIn(0.35f, 0.95f)

    // A secondary hue near the seed and a tertiary ~50° around the wheel for a little contrast.
    val secHue = hue
    val terHue = (hue + 50f) % 360f

    return if (dark) {
        darkColorScheme(
            primary = hsv(hue, sat * 0.6f, 0.85f),
            onPrimary = hsv(hue, sat, 0.18f),
            primaryContainer = hsv(hue, sat * 0.85f, 0.32f),
            onPrimaryContainer = hsv(hue, sat * 0.45f, 0.92f),
            secondary = hsv(secHue, sat * 0.4f, 0.82f),
            onSecondary = hsv(secHue, sat, 0.16f),
            secondaryContainer = hsv(secHue, sat * 0.5f, 0.30f),
            onSecondaryContainer = hsv(secHue, sat * 0.35f, 0.90f),
            tertiary = hsv(terHue, sat * 0.55f, 0.85f),
            onTertiary = hsv(terHue, sat, 0.18f),
            tertiaryContainer = hsv(terHue, sat * 0.7f, 0.32f),
            onTertiaryContainer = hsv(terHue, sat * 0.4f, 0.92f),
            surfaceTint = hsv(hue, sat * 0.6f, 0.85f),
        )
    } else {
        lightColorScheme(
            primary = hsv(hue, sat, 0.55f),
            onPrimary = Color.White,
            primaryContainer = hsv(hue, sat * 0.30f, 0.94f),
            onPrimaryContainer = hsv(hue, sat, 0.18f),
            secondary = hsv(secHue, sat * 0.65f, 0.50f),
            onSecondary = Color.White,
            secondaryContainer = hsv(secHue, sat * 0.25f, 0.93f),
            onSecondaryContainer = hsv(secHue, sat, 0.16f),
            tertiary = hsv(terHue, sat * 0.8f, 0.52f),
            onTertiary = Color.White,
            tertiaryContainer = hsv(terHue, sat * 0.30f, 0.93f),
            onTertiaryContainer = hsv(terHue, sat, 0.18f),
            surfaceTint = hsv(hue, sat, 0.55f),
        )
    }
}

/** Builds a [Color] from hue (0-360), saturation (0-1) and value (0-1). */
private fun hsv(
    hue: Float,
    saturation: Float,
    value: Float,
): Color =
    Color(
        AndroidColor.HSVToColor(
            floatArrayOf(
                ((hue % 360f) + 360f) % 360f,
                saturation.coerceIn(0f, 1f),
                value.coerceIn(0f, 1f),
            ),
        ),
    )
