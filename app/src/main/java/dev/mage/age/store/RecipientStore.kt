/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import dev.mage.age.crypto.Recipients
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Address book of public recipients (`age1…` or `ssh-ed25519`/`ssh-rsa`). Public keys aren't secret,
 * so they are stored in clear.
 */
class RecipientStore(
    context: Context,
) {
    private val json = JsonFileStore(context, "recipients.json")

    fun list(): List<SavedRecipient> = records(json.read()).sortedBy { it.label.lowercase() }

    /** Validate and add a recipient. Throws if [recipientText] is not a valid age or SSH public key. */
    fun add(
        label: String,
        recipientText: String,
    ): SavedRecipient {
        val canonical = Recipients.canonical(recipientText) // parses + normalizes; throws on bad input
        val record =
            SavedRecipient(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { "Recipient" },
                recipient = canonical,
                addedAt = System.currentTimeMillis(),
            )
        // Atomic read-modify-write, de-duping on the canonical key.
        json.update { toArray(records(it).filterNot { r -> r.recipient == canonical } + record) }
        return record
    }

    fun delete(id: String) {
        json.update { toArray(records(it).filterNot { r -> r.id == id }) }
    }

    private fun records(arr: JSONArray): List<SavedRecipient> = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }

    private fun toArray(records: List<SavedRecipient>): JSONArray = JSONArray().apply { records.forEach { put(toJson(it)) } }

    private fun toJson(r: SavedRecipient) =
        JSONObject().apply {
            put("id", r.id)
            put("label", r.label)
            put("recipient", r.recipient)
            put("addedAt", r.addedAt)
        }

    private fun fromJson(o: JSONObject) =
        SavedRecipient(
            id = o.getString("id"),
            label = o.getString("label"),
            recipient = o.getString("recipient"),
            addedAt = o.optLong("addedAt"),
        )
}
