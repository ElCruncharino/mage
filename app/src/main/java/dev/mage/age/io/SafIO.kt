/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.io

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.InputStream
import java.io.OutputStream

/**
 * Storage Access Framework helpers: open input/output streams on `content://` URIs and read display
 * metadata. Streaming is used throughout (the crypto facade copies stream-to-stream) so even large
 * files never sit fully in memory.
 */
object SafIO {
    /** Open an [InputStream] for reading [uri]. Caller closes it (use `.use { }`). */
    fun openInput(
        context: Context,
        uri: Uri,
    ): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected file for reading")

    /** Open an [OutputStream] for writing [uri]. Caller closes it (use `.use { }`). */
    fun openOutput(
        context: Context,
        uri: Uri,
    ): OutputStream =
        context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open the selected location for writing")

    /** The human-facing file name for [uri], or null if it cannot be resolved. */
    fun displayName(
        context: Context,
        uri: Uri,
    ): String? {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    /**
     * The size of [uri] in bytes, or null if genuinely unknown. Tries the `OpenableColumns.SIZE`
     * metadata column first, then falls back to the file descriptor's length — the column is absent
     * for many content providers, and callers use this for an OOM guard, so a too-eager null would
     * silently disable that guard.
     */
    fun sizeBytes(
        context: Context,
        uri: Uri,
    ): Long? {
        context.contentResolver
            .query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        // Fallback: the descriptor length. AssetFileDescriptor.UNKNOWN_LENGTH (-1) means still unknown.
        runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { afd ->
                val len = afd.length
                if (len >= 0) return len
            }
        }
        return null
    }

    /** age's conventional file extension. Both binary and ASCII-armored age files use it. */
    const val AGE_EXTENSION = ".age"

    /**
     * True if [name] is an age file by its extension (case-insensitive). Used to gate decryption to
     * `.age` files: the Storage Access Framework can only filter the system picker by MIME type, and
     * age files have no registered MIME type, so name-based validation after selection is the only
     * reliable way to restrict input to age files.
     */
    fun isAgeName(name: String?): Boolean = name?.endsWith(AGE_EXTENSION, ignoreCase = true) == true

    /**
     * Suggested output name for an encrypt/decrypt result:
     *  - encrypting `report.pdf`  -> `report.pdf.age`
     *  - decrypting `report.pdf.age` -> `report.pdf`
     *  - decrypting a name without `.age` -> `<name>.decrypted`
     */
    fun suggestEncryptedName(sourceName: String?): String = (sourceName?.takeIf { it.isNotBlank() } ?: "mage-output") + ".age"

    fun suggestDecryptedName(sourceName: String?): String {
        val name = sourceName?.takeIf { it.isNotBlank() } ?: "mage-output.age"
        return if (name.endsWith(".age", ignoreCase = true)) {
            name.dropLast(4)
        } else {
            "$name.decrypted"
        }
    }
}
