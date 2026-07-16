/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import androidx.core.content.edit

/** Light/dark preference. SYSTEM defers to the OS setting. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Small key/value app preferences. */
class SettingsStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences("mage.settings", Context.MODE_PRIVATE)

    /** Whether the identity vault is locked behind biometric/device-credential auth. */
    var biometricLock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK, true)
        set(value) = prefs.edit { putBoolean(KEY_BIOMETRIC_LOCK, value) }

    /** Default state of the "ASCII armor" toggle for new encrypts. */
    var defaultArmor: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_ARMOR, false)
        set(value) = prefs.edit { putBoolean(KEY_DEFAULT_ARMOR, value) }

    /** True once initial vault setup has run. */
    var initialized: Boolean
        get() = prefs.getBoolean(KEY_INITIALIZED, false)
        set(value) = prefs.edit { putBoolean(KEY_INITIALIZED, value) }

    /** Light/dark/system appearance choice. */
    var themeMode: ThemeMode
        get() =
            runCatching { ThemeMode.valueOf(prefs.getString(KEY_THEME_MODE, null) ?: "") }
                .getOrDefault(ThemeMode.SYSTEM)
        set(value) = prefs.edit { putString(KEY_THEME_MODE, value.name) }

    /** Use the OS Material You wallpaper palette on Android 12+ (when no accent seed is set). */
    var dynamicColor: Boolean
        get() = prefs.getBoolean(KEY_DYNAMIC_COLOR, true)
        set(value) = prefs.edit { putBoolean(KEY_DYNAMIC_COLOR, value) }

    /** A user-picked accent seed colour (ARGB), or null to fall back to dynamic/brand colours. */
    var accentSeed: Int?
        get() = if (prefs.contains(KEY_ACCENT_SEED)) prefs.getInt(KEY_ACCENT_SEED, 0) else null
        set(value) {
            prefs.edit {
                if (value == null) remove(KEY_ACCENT_SEED) else putInt(KEY_ACCENT_SEED, value)
            }
        }

    companion object {
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_DEFAULT_ARMOR = "default_armor"
        private const val KEY_INITIALIZED = "initialized"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_DYNAMIC_COLOR = "dynamic_color"
        private const val KEY_ACCENT_SEED = "accent_seed"
    }
}
