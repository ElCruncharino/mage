/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age

import android.app.Application
import dev.mage.age.store.IdentityStore
import dev.mage.age.store.KeystoreVault
import dev.mage.age.store.RecipientStore
import dev.mage.age.store.SettingsStore

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
class AppContainer(app: Application) {
    val settings = SettingsStore(app)
    val vault = KeystoreVault(app)
    val identities = IdentityStore(app, vault)
    val recipients = RecipientStore(app)
}
