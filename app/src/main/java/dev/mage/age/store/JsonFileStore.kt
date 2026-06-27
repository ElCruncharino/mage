/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import org.json.JSONArray
import org.json.JSONException
import java.io.File

/**
 * Minimal JSON-array persistence to a file in app-private storage. The data set here is tiny (a
 * handful of identities / recipients), so a Room database would be overkill — and avoiding it keeps
 * the build free of the KSP annotation processor. Writes are atomic (temp file + rename) and access
 * is synchronized.
 */
internal class JsonFileStore(
    context: Context,
    fileName: String,
) {
    private val file = File(context.filesDir, fileName)
    private val lock = Any()

    fun read(): JSONArray = synchronized(lock) { readLocked() }

    fun write(array: JSONArray) = synchronized(lock) { writeLocked(array) }

    /**
     * Atomic read-modify-write: [transform] receives the current contents and returns the new
     * contents, all under the same lock. Mutating stores MUST use this rather than a separate
     * [read] then [write] — otherwise two concurrent mutations can interleave and silently lose one
     * update (and these records hold irreplaceable sealed private keys).
     */
    fun update(transform: (JSONArray) -> JSONArray) =
        synchronized(lock) {
            writeLocked(transform(readLocked()))
        }

    private fun readLocked(): JSONArray {
        if (!file.exists()) return JSONArray()
        val text = file.readText()
        if (text.isBlank()) return JSONArray()
        return try {
            JSONArray(text)
        } catch (e: JSONException) {
            // The file exists but is unparseable (a truncated fallback write, storage corruption…).
            // Returning an empty array here would be catastrophic: the next read-modify-write would
            // persist that empty list and destroy the real records — including sealed private keys
            // that exist nowhere else. Preserve a copy and fail loudly so nothing overwrites them.
            val salvage = File(file.parentFile, "${file.name}.corrupt")
            if (!salvage.exists()) runCatching { file.copyTo(salvage, overwrite = false) }
            throw IllegalStateException(
                "Stored data in ${file.name} is corrupt; a copy was saved as ${salvage.name}",
                e,
            )
        }
    }

    private fun writeLocked(array: JSONArray) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            // renameTo can fail across some filesystems; fall back to a direct overwrite.
            file.writeText(array.toString())
            tmp.delete()
        }
    }
}
