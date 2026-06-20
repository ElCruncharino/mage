/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.ui

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
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

    /**
     * Conservative largest `.age` input we should attempt to decrypt on this device. kage's decrypt
     * is NOT streaming — `AgeFile.parse` reads the whole ciphertext into a ByteArrayOutputStream and
     * then copies it again (`toByteArray()`), so peak memory is ~2–3× the file size. We allow up to a
     * quarter of the heap to leave room for that copy plus the rest of the app.
     */
    fun maxDecryptInputBytes(): Long = Runtime.getRuntime().maxMemory() / 4

    /** Outcome of a batch run: how many succeeded, and the source names that failed. */
    data class BatchResult(val ok: Int, val failed: List<String>) {
        val total get() = ok + failed.size
    }

    /**
     * Encrypt each of [inputs] into a new `<name>.age` file inside the user-picked folder [tree].
     * [onProgress] is invoked (off the main thread) before each file with the 1-based index. A
     * per-file failure is recorded and the batch continues.
     */
    suspend fun encryptBatch(
        context: Context,
        recipients: List<Recipient>,
        inputs: List<Uri>,
        tree: Uri,
        armor: Boolean,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): BatchResult = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: error("Cannot open the chosen folder")
        runBatch(context, inputs, onProgress) { uri, srcName ->
            val child = dir.createFile("application/octet-stream", SafIO.suggestEncryptedName(srcName))
                ?: error("Could not create output file")
            SafIO.openInput(context, uri).use { src ->
                SafIO.openOutput(context, child.uri).use { dst ->
                    AgeCrypto.encrypt(recipients, src, dst, armor)
                }
            }
        }
    }

    /** Decrypt each of [inputs] into the user-picked folder [tree], stripping the `.age` suffix. */
    suspend fun decryptBatch(
        context: Context,
        identities: List<Identity>,
        inputs: List<Uri>,
        tree: Uri,
        onProgress: (Int, Int) -> Unit = { _, _ -> },
    ): BatchResult = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, tree) ?: error("Cannot open the chosen folder")
        runBatch(context, inputs, onProgress) { uri, srcName ->
            val child = dir.createFile("application/octet-stream", SafIO.suggestDecryptedName(srcName))
                ?: error("Could not create output file")
            SafIO.openInput(context, uri).use { src ->
                SafIO.openOutput(context, child.uri).use { dst ->
                    AgeCrypto.decrypt(identities, src, dst)
                }
            }
        }
    }

    private fun runBatch(
        context: Context,
        inputs: List<Uri>,
        onProgress: (Int, Int) -> Unit,
        op: (Uri, String?) -> Unit,
    ): BatchResult {
        var ok = 0
        val failed = mutableListOf<String>()
        inputs.forEachIndexed { index, uri ->
            onProgress(index + 1, inputs.size)
            val srcName = SafIO.displayName(context, uri)
            try {
                op(uri, srcName)
                ok++
            } catch (e: Exception) {
                failed += (srcName ?: uri.lastPathSegment ?: "file ${index + 1}")
            }
        }
        return BatchResult(ok, failed)
    }
}
