/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import kage.crypto.x25519.X25519Identity
import kage.crypto.x25519.X25519Recipient

/**
 * X25519 identity/recipient helpers — generation, and parsing/encoding to and from age's textual
 * forms:
 *  - identity (private):  `AGE-SECRET-KEY-1...`
 *  - recipient (public):  `age1...`
 */
object Identities {
    /** Generate a fresh random X25519 identity (keypair). */
    fun generate(): X25519Identity = X25519Identity.`new`()

    /**
     * Parse a private identity string (`AGE-SECRET-KEY-1...`). Whitespace is trimmed so pasted keys
     * with stray newlines still parse.
     *
     * @throws kage.errors.InvalidIdentityException / [kage.errors.Bech32Exception] on bad input.
     */
    fun parseIdentity(text: String): X25519Identity = X25519Identity.decode(text.trim())

    /**
     * Parse a public recipient string (`age1...`).
     *
     * @throws kage.errors.InvalidRecipientException / [kage.errors.Bech32Exception] on bad input.
     */
    fun parseRecipient(text: String): X25519Recipient = X25519Recipient.decode(text.trim())

    /** The public recipient corresponding to a private identity. */
    fun recipientOf(identity: X25519Identity): X25519Recipient = identity.recipient()

    /** Encode a private identity back to its `AGE-SECRET-KEY-1...` string. */
    fun encode(identity: X25519Identity): String = identity.encodeToString()

    /** Encode a public recipient back to its `age1...` string. */
    fun encode(recipient: X25519Recipient): String = recipient.encodeToString()

    /** Loose check that a string looks like an age public recipient, for input validation. */
    fun looksLikeRecipient(text: String): Boolean = text.trim().startsWith("age1")

    /** Loose check that a string looks like an age private identity, for input validation. */
    fun looksLikeIdentity(text: String): Boolean = text.trim().startsWith("AGE-SECRET-KEY-1", ignoreCase = true)
}
