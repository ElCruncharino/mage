/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.backup

import dev.mage.age.crypto.Identities
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

    /** A decrypted restore file is either a Mage bundle or a plain age key file (text, maybe CRLF or BOM). */
    fun parseRestored(bytes: ByteArray): List<Entry> =
        runCatching { deserialize(bytes) }.getOrElse {
            val text = decodeText(bytes)
            Identities.parseIdentity(text)
            listOf(Entry("Imported key", text))
        }

    // Windows PowerShell 5.1 writes UTF-16LE for `>` redirects. The BOM (if any) is stripped by extractSecret.
    private fun decodeText(bytes: ByteArray): String {
        val head = bytes.take(2)
        val charset =
            when {
                head == listOf(0xFF.toByte(), 0xFE.toByte()) || head.getOrNull(1) == 0.toByte() -> Charsets.UTF_16LE
                head == listOf(0xFE.toByte(), 0xFF.toByte()) || head.getOrNull(0) == 0.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
        return String(bytes, charset).removePrefix("\uFEFF")
    }
}
