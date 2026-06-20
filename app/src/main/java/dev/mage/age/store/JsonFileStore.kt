/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import java.io.File
import org.json.JSONArray

/**
 * Minimal JSON-array persistence to a file in app-private storage. The data set here is tiny (a
 * handful of identities / recipients), so a Room database would be overkill — and avoiding it keeps
 * the build free of the KSP annotation processor. Writes are atomic (temp file + rename) and access
 * is synchronized.
 */
internal class JsonFileStore(context: Context, fileName: String) {

    private val file = File(context.filesDir, fileName)
    private val lock = Any()

    fun read(): JSONArray = synchronized(lock) {
        if (!file.exists()) return JSONArray()
        return runCatching { JSONArray(file.readText()) }.getOrDefault(JSONArray())
    }

    fun write(array: JSONArray) = synchronized(lock) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            // renameTo can fail across some filesystems; fall back to a direct overwrite.
            file.writeText(array.toString())
            tmp.delete()
        }
    }
}
