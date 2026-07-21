/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Seals/opens small secrets (age private identities) with an AES-256-GCM key held in the Android
 * Keystore — the private key bytes never leave secure hardware in plaintext at rest.
 *
 * Auth model (when [biometricLock] is on at key creation): the key requires user authentication and
 * is usable for [AUTH_VALIDITY_SECONDS] after a successful biometric/device-credential auth. So the
 * flow is: [BiometricGate.authenticate] once, then call [seal]/[open] within the window. If the
 * window has lapsed, [open]/[seal] throw [android.security.keystore.UserNotAuthenticatedException]
 * and the caller should re-prompt.
 *
 * NOTE: requires on-device testing — Keystore auth-bound keys cannot be exercised on a host JVM.
 */
class KeystoreVault(
    private val appContext: Context,
) {
    data class Sealed(
        val iv: ByteArray,
        val ciphertext: ByteArray,
    )

    fun hasKey(): Boolean = keyStore().containsAlias(KEY_ALIAS)

    /**
     * Create the master key if absent. [biometricLock] = true binds it to user auth; false creates a
     * key that seals at rest but needs no unlock (for devices with no secure lock screen, or if the
     * user opts out). Changing the mode requires deleting and recreating the key (and re-importing
     * identities), so callers decide this once at setup.
     */
    fun ensureKey(biometricLock: Boolean) {
        if (hasKey()) return
        generate(biometricLock, strongBox = supportsStrongBox())
    }

    fun deleteKey() {
        runCatching { keyStore().deleteEntry(KEY_ALIAS) }
    }

    /**
     * Best-effort check that the key exists but is permanently invalidated (Android does this when the
     * device's enrolled biometrics change). A throwaway ENCRYPT init fails fast: a
     * [KeyPermanentlyInvalidatedException] means the key must be regenerated, while
     * UserNotAuthenticated or success both mean the key is fine (just locked). No plaintext is
     * touched, so this needs no auth window.
     */
    fun isInvalidated(): Boolean {
        if (!hasKey()) return false
        return try {
            Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, secretKey())
            false
        } catch (_: KeyPermanentlyInvalidatedException) {
            true
        } catch (_: Exception) {
            false
        }
    }

    fun seal(plaintext: ByteArray): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plaintext)
        return Sealed(iv, ct)
    }

    fun open(sealed: Sealed): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, sealed.iv))
        return cipher.doFinal(sealed.ciphertext)
    }

    private fun generate(
        biometricLock: Boolean,
        strongBox: Boolean,
    ) {
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec =
            KeyGenParameterSpec
                .Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .apply {
                    if (biometricLock) {
                        setUserAuthenticationRequired(true)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            setUserAuthenticationParameters(
                                AUTH_VALIDITY_SECONDS,
                                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            setUserAuthenticationValidityDurationSeconds(AUTH_VALIDITY_SECONDS)
                        }
                    }
                    if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        setIsStrongBoxBacked(true)
                    }
                }.build()

        try {
            generator.init(spec)
            generator.generateKey()
        } catch (e: Exception) {
            // StrongBox can be flaky / not support 256-bit AES on some devices. Retry in TEE.
            if (strongBox) {
                generate(biometricLock, strongBox = false)
            } else {
                throw e
            }
        }
    }

    private fun supportsStrongBox(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)

    private fun secretKey(): SecretKey = (keyStore().getEntry(KEY_ALIAS, null) as KeyStore.SecretKeyEntry).secretKey

    private fun keyStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    companion object {
        /**
         * True when [t] (or a cause in its chain) is the Keystore reporting that the master key was
         * permanently invalidated by a biometric-enrollment change. Every [seal]/[open] throws this
         * until the key is deleted and regenerated, so callers use it to give an honest, actionable
         * message and offer a reset rather than a generic failure.
         */
        fun isKeyInvalidated(t: Throwable): Boolean =
            generateSequence(t) { it.cause }.any { it is KeyPermanentlyInvalidatedException }

        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "mage.vault.master"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        const val AUTH_VALIDITY_SECONDS = 30
    }
}
