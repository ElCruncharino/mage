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
        val t = extractSecret(text)
        return if (t.startsWith("AGE-SECRET-KEY-PQ-", ignoreCase = true)) {
            MlKem768X25519Identity.decode(t)
        } else {
            X25519Identity.decode(t)
        }
    }

    /**
     * Pull the secret key out of pasted text. Accepts a bare key, or a whole `age-keygen` output file
     * (with `# created:` / `# public key:` comment lines), and tolerates a leading UTF-8 byte-order mark
     * (written by some Windows editors) and CRLF line endings. Falls back to the trimmed input so a
     * malformed value still fails in the decoder with its own error.
     */
    fun extractSecret(text: String): String {
        val cleaned = text.trim().removePrefix("﻿").trim()
        return cleaned
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
            ?: cleaned
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
