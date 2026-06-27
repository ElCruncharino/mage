/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.mage.age.store.SettingsStore
import dev.mage.age.store.ThemeMode

/**
 * Holds the appearance choices as reactive Compose state so a change made on the Settings screen
 * re-themes the whole app immediately, while persisting through to [SettingsStore]. A single
 * instance lives on the app container so the activity (which applies the theme) and the Settings
 * screen (which edits it) share the same state.
 */
class ThemeController(
    private val settings: SettingsStore,
) {
    // Compose-state backing fields; the public properties read these (so reads are observed for
    // recomposition) and persist through to settings on write.
    private var themeModeState by mutableStateOf(settings.themeMode)
    private var dynamicColorState by mutableStateOf(settings.dynamicColor)
    private var accentSeedState by mutableStateOf(settings.accentSeed)

    var themeMode: ThemeMode
        get() = themeModeState
        set(value) {
            themeModeState = value
            settings.themeMode = value
        }

    var dynamicColor: Boolean
        get() = dynamicColorState
        set(value) {
            dynamicColorState = value
            settings.dynamicColor = value
        }

    var accentSeed: Int?
        get() = accentSeedState
        set(value) {
            accentSeedState = value
            settings.accentSeed = value
        }
}
