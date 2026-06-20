/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import dev.mage.age.crypto.Identities
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Address book of public recipients (`age1...`). Public keys aren't secret, so stored in clear. */
class RecipientStore(context: Context) {

    private val json = JsonFileStore(context, "recipients.json")

    fun list(): List<SavedRecipient> {
        val arr = json.read()
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
            .sortedBy { it.label.lowercase() }
    }

    /** Validate and add a recipient. Throws if [recipientText] is not a valid `age1...` key. */
    fun add(label: String, recipientText: String): SavedRecipient {
        val parsed = Identities.parseRecipient(recipientText) // throws on bad input
        val canonical = Identities.encode(parsed)
        val record = SavedRecipient(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Recipient" },
            recipient = canonical,
            addedAt = System.currentTimeMillis(),
        )
        // De-dupe on the canonical key.
        persist(list().filterNot { it.recipient == canonical } + record)
        return record
    }

    fun delete(id: String) {
        persist(list().filterNot { it.id == id })
    }

    private fun persist(records: List<SavedRecipient>) {
        val arr = JSONArray()
        records.forEach { arr.put(toJson(it)) }
        json.write(arr)
    }

    private fun toJson(r: SavedRecipient) = JSONObject().apply {
        put("id", r.id)
        put("label", r.label)
        put("recipient", r.recipient)
        put("addedAt", r.addedAt)
    }

    private fun fromJson(o: JSONObject) = SavedRecipient(
        id = o.getString("id"),
        label = o.getString("label"),
        recipient = o.getString("recipient"),
        addedAt = o.optLong("addedAt"),
    )
}
