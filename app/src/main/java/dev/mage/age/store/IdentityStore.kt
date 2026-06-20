/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

import android.content.Context
import android.util.Base64
import dev.mage.age.crypto.Identities
import java.util.UUID
import kage.crypto.x25519.X25519Identity
import org.json.JSONArray
import org.json.JSONObject

/**
 * Stores identities: public recipient in clear, private key sealed by [KeystoreVault].
 *
 * Reading a private key ([open]) decrypts via the Keystore master key and therefore needs a valid
 * auth window (see [BiometricGate]); listing/adding metadata does not require the private key but
 * sealing on [add] does touch the Keystore key.
 */
class IdentityStore(context: Context, private val vault: KeystoreVault) {

    private val json = JsonFileStore(context, "identities.json")

    fun list(): List<VaultIdentity> {
        val arr = json.read()
        return (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
    }

    fun get(id: String): VaultIdentity? = list().firstOrNull { it.id == id }

    /** Seal and store [identity] under [label]. Returns the stored record. */
    fun add(label: String, identity: X25519Identity): VaultIdentity {
        val sealed = vault.seal(Identities.encode(identity).toByteArray(Charsets.US_ASCII))
        val record = VaultIdentity(
            id = UUID.randomUUID().toString(),
            label = label.ifBlank { "Identity" },
            recipient = Identities.encode(identity.recipient()),
            createdAt = System.currentTimeMillis(),
            sealedIvB64 = b64(sealed.iv),
            sealedCtB64 = b64(sealed.ciphertext),
        )
        persist(list() + record)
        return record
    }

    /** Generate a brand-new identity and store it. */
    fun generate(label: String): VaultIdentity = add(label, Identities.generate())

    /** Import an `AGE-SECRET-KEY-1...` string. Throws if it does not parse. */
    fun import(label: String, identityText: String): VaultIdentity =
        add(label, Identities.parseIdentity(identityText))

    fun delete(id: String) {
        persist(list().filterNot { it.id == id })
    }

    fun rename(id: String, label: String) {
        persist(list().map { if (it.id == id) it.copy(label = label) else it })
    }

    /** Decrypt and reconstruct the private identity. Requires a valid auth window. */
    fun open(record: VaultIdentity): X25519Identity {
        val bytes = vault.open(KeystoreVault.Sealed(unb64(record.sealedIvB64), unb64(record.sealedCtB64)))
        return Identities.parseIdentity(String(bytes, Charsets.US_ASCII))
    }

    private fun persist(records: List<VaultIdentity>) {
        val arr = JSONArray()
        records.forEach { arr.put(toJson(it)) }
        json.write(arr)
    }

    private fun toJson(r: VaultIdentity) = JSONObject().apply {
        put("id", r.id)
        put("label", r.label)
        put("recipient", r.recipient)
        put("createdAt", r.createdAt)
        put("iv", r.sealedIvB64)
        put("ct", r.sealedCtB64)
    }

    private fun fromJson(o: JSONObject) = VaultIdentity(
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
