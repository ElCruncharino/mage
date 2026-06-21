/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import dev.mage.age.io.IntentRouter
import dev.mage.age.io.LaunchTarget
import dev.mage.age.store.BiometricGate
import dev.mage.age.ui.MageRoot
import dev.mage.age.ui.theme.MageTheme

/**
 * Single activity hosting the whole Compose UI. Extends [FragmentActivity] (not plain
 * ComponentActivity) because [androidx.biometric.BiometricPrompt] requires it.
 *
 * FLAG_SECURE blocks screenshots, screen recording and the Recents thumbnail — plaintext and key
 * material should never leak to those surfaces.
 */
class MainActivity : FragmentActivity() {
    private var launchTarget by mutableStateOf(LaunchTarget(LaunchTarget.Destination.HOME))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        launchTarget = IntentRouter.route(intent)

        val container = MageApp.from(application)

        setContent {
            val theme = container.theme
            MageTheme(
                themeMode = theme.themeMode,
                useDynamicColor = theme.dynamicColor,
                seedColorArgb = theme.accentSeed,
            ) {
                MageRoot(
                    container = container,
                    initialTarget = launchTarget,
                    unlock = ::unlockVault,
                )
            }
        }
    }

    /**
     * Refresh the Keystore auth window if the vault is biometric-locked. Returns true when the vault
     * is usable (either no lock configured, or the user authenticated successfully).
     */
    private suspend fun unlockVault(): Boolean {
        val settings = MageApp.from(application).settings
        if (!settings.biometricLock) return true
        if (!BiometricGate.canAuthenticate(this)) return true // nothing enrolled; key is unlocked
        return BiometricGate.authenticate(this)
    }
}
