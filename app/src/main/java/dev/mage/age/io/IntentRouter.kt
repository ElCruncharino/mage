/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.io

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Parcelable

/** Where the app should open, and with what payload, given the launching intent. */
data class LaunchTarget(
    val destination: Destination,
    val fileUris: List<Uri> = emptyList(),
    val sharedText: String? = null,
) {
    enum class Destination { HOME, ENCRYPT, DECRYPT }
}

/**
 * Maps an incoming [Intent] to a [LaunchTarget]:
 *  - share-sheet aliases `.ShareEncrypt` / `.ShareDecrypt` (ACTION_SEND / SEND_MULTIPLE)
 *  - opening a `.age` file (ACTION_VIEW) -> decrypt
 *  - launcher shortcuts carrying the `START_DEST` extra
 *  - plain launch -> home
 */
object IntentRouter {
    const val EXTRA_START_DEST = "dev.mage.age.START_DEST"

    fun route(intent: Intent?): LaunchTarget {
        if (intent == null) return LaunchTarget(LaunchTarget.Destination.HOME)

        val viaDecryptAlias = intent.component?.className?.endsWith("ShareDecrypt") == true
        val viaEncryptAlias = intent.component?.className?.endsWith("ShareEncrypt") == true

        return when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.parcelable<Uri>(Intent.EXTRA_STREAM)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                val dest =
                    if (viaDecryptAlias) {
                        LaunchTarget.Destination.DECRYPT
                    } else {
                        LaunchTarget.Destination.ENCRYPT
                    }
                LaunchTarget(dest, listOfNotNull(uri), text)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.parcelableList<Uri>(Intent.EXTRA_STREAM)
                val dest =
                    if (viaDecryptAlias) {
                        LaunchTarget.Destination.DECRYPT
                    } else {
                        LaunchTarget.Destination.ENCRYPT
                    }
                LaunchTarget(dest, uris)
            }

            Intent.ACTION_VIEW -> {
                // Opening a file directly (e.g. a .age from a file manager) -> decrypt it.
                val uri = intent.data
                LaunchTarget(LaunchTarget.Destination.DECRYPT, listOfNotNull(uri))
            }

            else -> {
                val dest =
                    when (intent.getStringExtra(EXTRA_START_DEST)) {
                        "encrypt" -> {
                            LaunchTarget.Destination.ENCRYPT
                        }

                        "decrypt" -> {
                            LaunchTarget.Destination.DECRYPT
                        }

                        else -> {
                            if (viaEncryptAlias) {
                                LaunchTarget.Destination.ENCRYPT
                            } else if (viaDecryptAlias) {
                                LaunchTarget.Destination.DECRYPT
                            } else {
                                LaunchTarget.Destination.HOME
                            }
                        }
                    }
                LaunchTarget(dest)
            }
        }
    }

    private inline fun <reified T : Parcelable> Intent.parcelable(key: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    private inline fun <reified T : Parcelable> Intent.parcelableList(key: String): List<T> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableArrayListExtra(key, T::class.java) ?: emptyList()
        } else {
            @Suppress("DEPRECATION")
            getParcelableArrayListExtra(key) ?: emptyList()
        }
}
