/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age

import android.app.Application
import dev.mage.age.store.BiometricGate
import dev.mage.age.store.IdentityStore
import dev.mage.age.store.KeystoreVault
import dev.mage.age.store.RecipientStore
import dev.mage.age.store.SettingsStore
import dev.mage.age.ui.theme.ThemeController

/**
 * Application entry point and tiny manual DI container. Mage has few moving parts, so a full DI
 * framework would be overkill; everything is constructed once here and read via [from].
 */
class MageApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        fun from(application: Application): AppContainer = (application as MageApp).container
    }
}

/** Holds the singletons used across the app. */
class AppContainer(
    app: Application,
) {
    private val appContext = app.applicationContext
    val settings = SettingsStore(app)
    val vault = KeystoreVault(app)
    val identities = IdentityStore(app, vault)
    val recipients = RecipientStore(app)
    val theme = ThemeController(settings)

    /**
     * The lock mode to actually create the vault key with. Auth-binding the key only makes sense if
     * the device can satisfy that auth; otherwise sealing (which also needs auth on a bound key)
     * could never succeed, so fall back to a non-auth key.
     */
    val effectiveBiometricLock: Boolean
        get() = settings.biometricLock && BiometricGate.canAuthenticate(appContext)

    /** Create the vault key if absent, using the effective lock mode. */
    fun ensureVaultKey() = vault.ensureKey(effectiveBiometricLock)
}
