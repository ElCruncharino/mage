/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Android-free) serialization of an identity backup bundle. The bundle is the *plaintext* that
 * gets age-encrypted to a passphrase by [BackupManager]; this object only turns the list of
 * identities into bytes and back. Kept Android-free so it can be unit-tested on the JVM.
 */
object BackupCodec {
    const val VERSION = 1
    const val TYPE = "mage-identity-backup"

    /** One backed-up identity: a human label and its `AGE-SECRET-KEY-1...` private key. */
    data class Entry(
        val label: String,
        val key: String,
    )

    fun serialize(entries: List<Entry>): ByteArray {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(JSONObject().put("label", entry.label).put("key", entry.key))
        }
        val root =
            JSONObject()
                .put("type", TYPE)
                .put("version", VERSION)
                .put("identities", array)
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    /**
     * Parse a decrypted bundle. Throws [IllegalArgumentException] if it is not a recognizable Mage
     * backup (e.g. the passphrase was wrong and decryption produced garbage — though age's AEAD
     * usually fails first).
     */
    fun deserialize(bytes: ByteArray): List<Entry> {
        val root =
            runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }
                .getOrElse { throw IllegalArgumentException("Not a Mage backup file") }
        require(root.optString("type") == TYPE) { "Not a Mage backup file" }
        val array = root.optJSONArray("identities") ?: JSONArray()
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            Entry(label = obj.optString("label", "Identity"), key = obj.getString("key"))
        }
    }
}
