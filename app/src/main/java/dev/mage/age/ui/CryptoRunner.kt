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
import kage.Identity
import kage.Recipient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Runs encrypt/decrypt against the verified [AgeCrypto] facade, wiring Storage Access Framework
 * streams in and out. Everything runs on [Dispatchers.IO]; streaming keeps memory flat for big files.
 */
object CryptoRunner {
    /**
     * Encrypt [input] to [output]. Runs through a private cache file rather than streaming straight
     * into the SAF destination (see [encryptToFile] / [copyFileToUri]) — some content providers have
     * been observed silently dropping bytes written directly to a `content://` OutputStream, so the
     * encrypt itself only ever touches ordinary local storage and the SAF destination just receives a
     * plain byte copy.
     */
    suspend fun encryptToUri(
        context: Context,
        recipients: List<Recipient>,
        input: Uri,
        output: Uri,
        armor: Boolean,
    ) = withContext(Dispatchers.IO) {
        val temp = File.createTempFile("mage-enc", ".tmp", context.cacheDir)
        try {
            encryptToFile(context, recipients, input, temp, armor)
            copyFileToUri(context, temp, output)
        } finally {
            temp.delete()
        }
    }

    /** Encrypt [input] into a private cache [dest] file. See [encryptToUri] / [decryptToFile]. */
    suspend fun encryptToFile(
        context: Context,
        recipients: List<Recipient>,
        input: Uri,
        dest: File,
        armor: Boolean,
    ) = withContext(Dispatchers.IO) {
        SafIO.openInput(context, input).use { src ->
            FileOutputStream(dest).use { dst ->
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

    /**
     * Decrypt [input] into a private cache [dest] file. This exists so the UI can *validate* that a
     * file is decryptable (correct identity / passphrase) before asking the user where to save —
     * avoiding the case where a wrong passphrase leaves an empty 0-byte file at the destination the
     * SAF picker already created. The plaintext lives only in app-private cache and the caller is
     * responsible for copying it out (see [copyFileToUri]) and deleting it promptly.
     */
    suspend fun decryptToFile(
        context: Context,
        identities: List<Identity>,
        input: Uri,
        dest: File,
    ) = withContext(Dispatchers.IO) {
        SafIO.openInput(context, input).use { src ->
            FileOutputStream(dest).use { dst ->
                AgeCrypto.decrypt(identities, src, dst)
            }
        }
    }

    /** Copy an already-encrypted/decrypted local [file] out to the user-picked [output] location. */
    suspend fun copyFileToUri(
        context: Context,
        file: File,
        output: Uri,
    ) = withContext(Dispatchers.IO) {
        FileInputStream(file).use { src ->
            SafIO.openOutput(context, output).use { dst ->
                src.copyTo(dst)
            }
        }
        val doc = DocumentFile.fromSingleUri(context, output)
        if (doc?.length() == 0L && file.length() != 0L) {
            // The destination silently dropped the write; a genuinely empty result here would mean
            // the (already-validated, non-empty) local file failed to copy, not that it was empty.
            doc.delete()
            error("The destination wrote an empty file — this storage location may not support the write")
        }
    }

    suspend fun encryptText(
        recipients: List<Recipient>,
        text: String,
        armor: Boolean,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            AgeCrypto.encryptBytes(recipients, text.toByteArray(Charsets.UTF_8), armor)
        }

    /**
     * Conservative largest `.age` input we should attempt to decrypt on this device. kage's decrypt
     * is NOT streaming — `AgeFile.parse` reads the whole ciphertext into a ByteArrayOutputStream and
     * then copies it again (`toByteArray()`), so peak memory is ~2–3× the file size. We allow up to a
     * quarter of the heap to leave room for that copy plus the rest of the app.
     */
    fun maxDecryptInputBytes(): Long = Runtime.getRuntime().maxMemory() / 4

    /** Outcome of a batch run: how many succeeded, and the source names that failed. */
    data class BatchResult(
        val ok: Int,
        val failed: List<String>,
    ) {
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
    ): BatchResult =
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: error("Cannot open the chosen folder")
            runBatch(context, inputs, onProgress) { uri, srcName ->
                val child =
                    dir.createFile("application/octet-stream", SafIO.suggestEncryptedName(srcName))
                        ?: error("Could not create output file")
                try {
                    SafIO.openInput(context, uri).use { src ->
                        SafIO.openOutput(context, child.uri).use { dst ->
                            AgeCrypto.encrypt(recipients, src, dst, armor)
                        }
                    }
                    if (child.length() == 0L) error("Encryption produced an empty file")
                } catch (t: Throwable) {
                    child.delete() // don't leave a partial/empty file behind on failure
                    throw t
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
    ): BatchResult =
        withContext(Dispatchers.IO) {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: error("Cannot open the chosen folder")
            runBatch(context, inputs, onProgress) { uri, srcName ->
                val child =
                    dir.createFile("application/octet-stream", SafIO.suggestDecryptedName(srcName))
                        ?: error("Could not create output file")
                try {
                    SafIO.openInput(context, uri).use { src ->
                        SafIO.openOutput(context, child.uri).use { dst ->
                            AgeCrypto.decrypt(identities, src, dst)
                        }
                    }
                } catch (t: Throwable) {
                    child.delete() // a wrong passphrase/identity must not leave a 0-byte file behind
                    throw t
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
            } catch (e: CancellationException) {
                throw e // never swallow coroutine cancellation
            } catch (e: Throwable) {
                // Throwable, not Exception: a per-file OutOfMemoryError (kage's decrypt buffers the
                // whole file) must be recorded as one failure, not abort the whole batch.
                failed += (srcName ?: uri.lastPathSegment ?: "file ${index + 1}")
            }
        }
        return BatchResult(ok, failed)
    }
}
