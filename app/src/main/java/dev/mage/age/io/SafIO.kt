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
    fun openInput(context: Context, uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: error("Unable to open the selected file for reading")

    /** Open an [OutputStream] for writing [uri]. Caller closes it (use `.use { }`). */
    fun openOutput(context: Context, uri: Uri): OutputStream =
        context.contentResolver.openOutputStream(uri)
            ?: error("Unable to open the selected location for writing")

    /** The human-facing file name for [uri], or null if it cannot be resolved. */
    fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx)
                }
            }
        return uri.lastPathSegment
    }

    /** The size of [uri] in bytes, or null if unknown. */
    fun sizeBytes(context: Context, uri: Uri): Long? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx)
                }
            }
        return null
    }

    /**
     * Suggested output name for an encrypt/decrypt result:
     *  - encrypting `report.pdf`  -> `report.pdf.age`
     *  - decrypting `report.pdf.age` -> `report.pdf`
     *  - decrypting a name without `.age` -> `<name>.decrypted`
     */
    fun suggestEncryptedName(sourceName: String?): String =
        (sourceName?.takeIf { it.isNotBlank() } ?: "mage-output") + ".age"

    fun suggestDecryptedName(sourceName: String?): String {
        val name = sourceName?.takeIf { it.isNotBlank() } ?: "mage-output.age"
        return if (name.endsWith(".age", ignoreCase = true)) {
            name.dropLast(4)
        } else {
            "$name.decrypted"
        }
    }
}
