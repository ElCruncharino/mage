/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import kage.Recipient
import kage.crypto.ssh.SshKey
import kage.crypto.x25519.X25519Recipient

/**
 * Unified parsing for encryption recipients. Mage encrypts to two kinds of public key:
 *  - native age: `age1…` (X25519)
 *  - OpenSSH:    `ssh-ed25519 AAAA…` / `ssh-rsa AAAA…` (parsed by kage's [SshKey] facade)
 *
 * age permits mixing both kinds on a single file — each becomes its own recipient stanza — so the
 * Encrypt screen can hold any combination. Passphrase (scrypt) recipients are handled separately in
 * [Passphrase] because age requires them to be the *only* recipient on a file.
 *
 * Note: this is the recipient (public, encrypt-to) side only. X25519 *identity* generation and
 * private-key handling stay in [Identities]; SSH private-key identities are not yet imported because
 * kage cannot read passphrase-encrypted OpenSSH keys and SSH recipients have no canonical string to
 * store for display.
 */
object Recipients {
    enum class Kind { AGE, SSH }

    /**
     * Parse [text] as an age or OpenSSH public key. Whitespace is trimmed so pasted keys with stray
     * newlines still parse.
     *
     * @throws IllegalArgumentException if it is neither an age nor a recognized SSH public key.
     * @throws kage.errors.CryptoException (e.g. [kage.errors.UnsupportedSshKeyException]) on a
     *   structurally-recognized-but-invalid key.
     */
    fun parse(text: String): Recipient {
        val t = text.trim()
        return when (kindOf(t)) {
            Kind.AGE -> X25519Recipient.decode(t)

            Kind.SSH -> SshKey.parseRecipient(t)

            null -> throw IllegalArgumentException(
                "Not an age (age1…) or SSH (ssh-ed25519 / ssh-rsa) public key",
            )
        }
    }

    /**
     * Normalized string used for storage and de-duplication; re-parsing it with [parse] round-trips.
     *  - age keys are re-encoded via bech32 (canonical lowercase form).
     *  - SSH keys are reduced to `"<type> <base64>"`, dropping any trailing comment, so the same key
     *    pasted with different comments de-dupes to one entry.
     *
     * Validates as a side effect (parses the key), so callers can use it as a single add-time check.
     */
    fun canonical(text: String): String {
        val t = text.trim()
        return when (kindOf(t)) {
            Kind.AGE -> {
                X25519Recipient.decode(t).encodeToString()
            }

            // Validate the key parses, then store the comment-stripped authorized-keys form.
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

    /** Loose, parse-free classification for input validation and QR sniffing. */
    fun kindOf(text: String): Kind? {
        val t = text.trim()
        return when {
            t.startsWith("age1") -> Kind.AGE
            t.startsWith("ssh-ed25519 ") || t.startsWith("ssh-rsa ") -> Kind.SSH
            else -> null
        }
    }

    /** True if [text] looks like a recipient we can encrypt to (age or SSH). */
    fun looksLikeRecipient(text: String): Boolean = kindOf(text) != null

    private val WHITESPACE = Regex("\\s+")
}
