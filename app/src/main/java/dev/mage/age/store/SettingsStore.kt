/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context

/** Small key/value app preferences. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("mage.settings", Context.MODE_PRIVATE)

    /** Whether the identity vault is locked behind biometric/device-credential auth. */
    var biometricLock: Boolean
        get() = prefs.getBoolean(KEY_BIOMETRIC_LOCK, true)
        set(value) = prefs.edit().putBoolean(KEY_BIOMETRIC_LOCK, value).apply()

    /** Default state of the "ASCII armor" toggle for new encrypts. */
    var defaultArmor: Boolean
        get() = prefs.getBoolean(KEY_DEFAULT_ARMOR, false)
        set(value) = prefs.edit().putBoolean(KEY_DEFAULT_ARMOR, value).apply()

    /** True once initial vault setup has run. */
    var initialized: Boolean
        get() = prefs.getBoolean(KEY_INITIALIZED, false)
        set(value) = prefs.edit().putBoolean(KEY_INITIALIZED, value).apply()

    companion object {
        private const val KEY_BIOMETRIC_LOCK = "biometric_lock"
        private const val KEY_DEFAULT_ARMOR = "default_armor"
        private const val KEY_INITIALIZED = "initialized"
    }
}
