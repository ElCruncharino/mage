/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import kage.Recipient
import kage.crypto.mlkem.MlKem768X25519Recipient
import kage.crypto.ssh.SshKey
import kage.crypto.x25519.X25519Recipient

// Post-quantum recipients (age1pq…) can't be mixed with any other recipient on a file; the Encrypt
// screen enforces that. Passphrase (scrypt) recipients are handled separately in [Passphrase].
object Recipients {
    enum class Kind { AGE, PQ, SSH }

    fun parse(text: String): Recipient {
        val t = text.trim()
        return when (kindOf(t)) {
            Kind.AGE -> X25519Recipient.decode(t)

            Kind.PQ -> MlKem768X25519Recipient.decode(t)

            Kind.SSH -> SshKey.parseRecipient(t)

            null -> throw IllegalArgumentException(
                "Not an age, post-quantum age, or SSH (ssh-ed25519 / ssh-rsa) public key",
            )
        }
    }

    fun canonical(text: String): String {
        val t = text.trim()
        return when (kindOf(t)) {
            Kind.AGE -> X25519Recipient.decode(t).encodeToString()

            Kind.PQ -> MlKem768X25519Recipient.decode(t).encodeToString()

            Kind.SSH -> {
                SshKey.parseRecipient(t)
                t
                    .split(WHITESPACE)
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString(" ")
            }

            null -> {
                throw IllegalArgumentException("Not a recognized public key")
            }
        }
    }

    fun kindOf(text: String): Kind? {
        val t = text.trim()
        return when {
            t.startsWith("age1pq") -> Kind.PQ
            t.startsWith("age1") -> Kind.AGE
            t.startsWith("ssh-ed25519 ") || t.startsWith("ssh-rsa ") -> Kind.SSH
            else -> null
        }
    }

    fun looksLikeRecipient(text: String): Boolean = kindOf(text) != null

    private val WHITESPACE = Regex("\\s+")
}
