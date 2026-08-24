/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import kage.Identity
import kage.Recipient
import kage.crypto.mlkem.MlKem768X25519Identity
import kage.crypto.mlkem.MlKem768X25519Recipient
import kage.crypto.x25519.X25519Identity
import kage.crypto.x25519.X25519Recipient

// Identities are AGE-SECRET-KEY-1... (X25519) or AGE-SECRET-KEY-PQ-1... (post-quantum).
object Identities {
    fun generate(postQuantum: Boolean = false): Identity = if (postQuantum) MlKem768X25519Identity.`new`() else X25519Identity.`new`()

    fun parseIdentity(text: String): Identity {
        val t = text.trim()
        return if (t.startsWith("AGE-SECRET-KEY-PQ-", ignoreCase = true)) {
            MlKem768X25519Identity.decode(t)
        } else {
            X25519Identity.decode(t)
        }
    }

    fun recipientOf(identity: Identity): Recipient =
        when (identity) {
            is X25519Identity -> identity.recipient()
            is MlKem768X25519Identity -> identity.recipient()
            else -> error("Unsupported identity type: ${identity::class}")
        }

    fun encode(identity: Identity): String =
        when (identity) {
            is X25519Identity -> identity.encodeToString()
            is MlKem768X25519Identity -> identity.encodeToString()
            else -> error("Unsupported identity type: ${identity::class}")
        }

    fun encode(recipient: Recipient): String =
        when (recipient) {
            is X25519Recipient -> recipient.encodeToString()
            is MlKem768X25519Recipient -> recipient.encodeToString()
            else -> error("Unsupported recipient type: ${recipient::class}")
        }
}
