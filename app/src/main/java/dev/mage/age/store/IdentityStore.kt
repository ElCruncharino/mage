/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import android.util.Base64
import dev.mage.age.crypto.Identities
import kage.crypto.x25519.X25519Identity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Stores identities: public recipient in clear, private key sealed by [KeystoreVault].
 *
 * Reading a private key ([open]) decrypts via the Keystore master key and therefore needs a valid
 * auth window (see [BiometricGate]); listing/adding metadata does not require the private key but
 * sealing on [add] does touch the Keystore key.
 */
class IdentityStore(
    context: Context,
    private val vault: KeystoreVault,
) {
    private val json = JsonFileStore(context, "identities.json")

    fun list(): List<VaultIdentity> = records(json.read())

    fun get(id: String): VaultIdentity? = list().firstOrNull { it.id == id }

    /** Seal and store [identity] under [label]. Returns the stored record. */
    fun add(
        label: String,
        identity: X25519Identity,
    ): VaultIdentity {
        val sealed = vault.seal(Identities.encode(identity).toByteArray(Charsets.US_ASCII))
        val record =
            VaultIdentity(
                id = UUID.randomUUID().toString(),
                label = label.ifBlank { "Identity" },
                recipient = Identities.encode(identity.recipient()),
                createdAt = System.currentTimeMillis(),
                sealedIvB64 = b64(sealed.iv),
                sealedCtB64 = b64(sealed.ciphertext),
            )
        // Atomic append so a concurrent add/delete can't drop this record (and its sealed key).
        json.update { toArray(records(it) + record) }
        return record
    }

    /** Generate a brand-new identity and store it. */
    fun generate(label: String): VaultIdentity = add(label, Identities.generate())

    /** Import an `AGE-SECRET-KEY-1...` string. Throws if it does not parse. */
    fun import(
        label: String,
        identityText: String,
    ): VaultIdentity = add(label, Identities.parseIdentity(identityText))

    fun delete(id: String) {
        json.update { toArray(records(it).filterNot { r -> r.id == id }) }
    }

    fun rename(
        id: String,
        label: String,
    ) {
        json.update { toArray(records(it).map { r -> if (r.id == id) r.copy(label = label) else r }) }
    }

    /** Decrypt and reconstruct the private identity. Requires a valid auth window. */
    fun open(record: VaultIdentity): X25519Identity {
        val bytes = vault.open(KeystoreVault.Sealed(unb64(record.sealedIvB64), unb64(record.sealedCtB64)))
        return Identities.parseIdentity(String(bytes, Charsets.US_ASCII))
    }

    private fun records(arr: JSONArray): List<VaultIdentity> = (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }

    private fun toArray(records: List<VaultIdentity>): JSONArray = JSONArray().apply { records.forEach { put(toJson(it)) } }

    private fun toJson(r: VaultIdentity) =
        JSONObject().apply {
            put("id", r.id)
            put("label", r.label)
            put("recipient", r.recipient)
            put("createdAt", r.createdAt)
            put("iv", r.sealedIvB64)
            put("ct", r.sealedCtB64)
        }

    private fun fromJson(o: JSONObject) =
        VaultIdentity(
            id = o.getString("id"),
            label = o.getString("label"),
            recipient = o.getString("recipient"),
            createdAt = o.optLong("createdAt"),
            sealedIvB64 = o.getString("iv"),
            sealedCtB64 = o.getString("ct"),
        )

    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(text: String) = Base64.decode(text, Base64.NO_WRAP)
}
