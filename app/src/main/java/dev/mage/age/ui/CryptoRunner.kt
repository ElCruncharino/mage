/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import android.content.Context
import android.net.Uri
import dev.mage.age.crypto.AgeCrypto
import dev.mage.age.io.SafIO
import java.io.ByteArrayOutputStream
import kage.Identity
import kage.Recipient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs encrypt/decrypt against the verified [AgeCrypto] facade, wiring Storage Access Framework
 * streams in and out. Everything runs on [Dispatchers.IO]; streaming keeps memory flat for big files.
 */
object CryptoRunner {

    suspend fun encryptToUri(
        context: Context,
        recipients: List<Recipient>,
        input: Uri,
        output: Uri,
        armor: Boolean,
    ) = withContext(Dispatchers.IO) {
        SafIO.openInput(context, input).use { src ->
            SafIO.openOutput(context, output).use { dst ->
                AgeCrypto.encrypt(recipients, src, dst, armor)
            }
        }
    }

    suspend fun decryptToUri(
        context: Context,
        identities: List<Identity>,
        input: Uri,
        output: Uri,
    ) = withContext(Dispatchers.IO) {
        SafIO.openInput(context, input).use { src ->
            SafIO.openOutput(context, output).use { dst ->
                AgeCrypto.decrypt(identities, src, dst)
            }
        }
    }

    suspend fun encryptText(
        recipients: List<Recipient>,
        text: String,
        armor: Boolean,
    ): ByteArray = withContext(Dispatchers.IO) {
        AgeCrypto.encryptBytes(recipients, text.toByteArray(Charsets.UTF_8), armor)
    }

    suspend fun decryptToBytes(
        context: Context,
        identities: List<Identity>,
        input: Uri,
    ): ByteArray = withContext(Dispatchers.IO) {
        val out = ByteArrayOutputStream()
        SafIO.openInput(context, input).use { src ->
            AgeCrypto.decrypt(identities, src, out)
        }
        out.toByteArray()
    }
}
