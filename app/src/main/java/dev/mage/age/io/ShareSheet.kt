/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.io

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Hands a produced file out to other apps via the system share sheet, using a `content://` URI from
 * the app's private cache (see res/xml/file_paths.xml). Mirrors the proven pattern from the author's
 * NeoStego app.
 */
object ShareSheet {

    private const val MIME_AGE = "application/octet-stream"

    /** Write [bytes] into the share cache under [name] and return a chooser Intent for it. */
    fun shareBytes(context: Context, name: String, bytes: ByteArray, mime: String = MIME_AGE): Intent {
        val file = stage(context, name)
        file.writeBytes(bytes)
        return chooserFor(context, file, mime)
    }

    /** Stage a file in the share cache so callers can stream into it, then call [chooserFor]. */
    fun stage(context: Context, name: String): File {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        // Clean prior shares so the cache doesn't accumulate plaintext/ciphertext.
        dir.listFiles()?.forEach { it.delete() }
        return File(dir, sanitize(name))
    }

    fun chooserFor(context: Context, file: File, mime: String = MIME_AGE): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }

    /** Share plain text (e.g. armored ciphertext) directly, no file. */
    fun shareText(text: String): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        return Intent.createChooser(send, null)
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[/\\\\:*?\"<>|]"), "_").ifBlank { "mage-output" }
}
