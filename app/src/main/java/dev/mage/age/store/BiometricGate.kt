/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Wraps [BiometricPrompt] in a suspend function. A successful prompt unlocks the [KeystoreVault]
 * master key for its validity window (see [KeystoreVault.AUTH_VALIDITY_SECONDS]).
 *
 * Allowed authenticators are strong biometric OR device credential, so the app still works on
 * devices with only a PIN/pattern and no enrolled fingerprint/face.
 *
 * NOTE: requires on-device testing.
 */
object BiometricGate {
    // Pre-R can't combine DEVICE_CREDENTIAL with a biometric class in one call, so pick a single type:
    // BIOMETRIC_WEAK (not STRONG, which many real fingerprint sensors don't certify as) if enrolled,
    // else DEVICE_CREDENTIAL alone.
    private fun authenticators(context: Context): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Authenticators.BIOMETRIC_STRONG or Authenticators.DEVICE_CREDENTIAL
        } else if (BiometricManager.from(context).canAuthenticate(Authenticators.BIOMETRIC_WEAK) ==
            BiometricManager.BIOMETRIC_SUCCESS
        ) {
            Authenticators.BIOMETRIC_WEAK
        } else {
            Authenticators.DEVICE_CREDENTIAL
        }

    private fun deviceSecurePreR(context: Context): Boolean =
        (context.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager)?.isDeviceSecure == true

    /** Whether the device can satisfy our auth requirement right now. */
    fun status(context: Context): Int = BiometricManager.from(context).canAuthenticate(authenticators(context))

    fun canAuthenticate(context: Context): Boolean =
        status(context) == BiometricManager.BIOMETRIC_SUCCESS ||
            (Build.VERSION.SDK_INT < Build.VERSION_CODES.R && deviceSecurePreR(context))

    /**
     * Show the prompt and suspend until the user succeeds, cancels, or it errors.
     * @return true on success, false otherwise.
     */
    suspend fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Mage",
        subtitle: String = "Authenticate to use your identities",
    ): Boolean =
        suspendCancellableCoroutine { cont ->
            val executor =
                androidx.core.content.ContextCompat
                    .getMainExecutor(activity)
            val callback =
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onAuthenticationError(
                        errorCode: Int,
                        errString: CharSequence,
                    ) {
                        if (cont.isActive) cont.resume(false)
                    }

                    // Single failed attempt: let the prompt continue; only terminal error/cancel resumes.
                }

            val allowed = authenticators(activity)
            val prompt = BiometricPrompt(activity, executor, callback)
            val infoBuilder =
                BiometricPrompt.PromptInfo
                    .Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setAllowedAuthenticators(allowed)

            // A negative button is mandatory when DEVICE_CREDENTIAL is not in the allowed set, and
            // forbidden (throws) when it is.
            if (allowed and Authenticators.DEVICE_CREDENTIAL == 0) {
                infoBuilder.setNegativeButtonText("Cancel")
            }

            val info = infoBuilder.build()
            runCatching { prompt.authenticate(info) }
                .onFailure { if (cont.isActive) cont.resume(false) }

            cont.invokeOnCancellation { runCatching { prompt.cancelAuthentication() } }
        }
}
