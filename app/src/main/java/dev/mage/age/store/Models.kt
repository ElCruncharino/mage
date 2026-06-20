/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.store

/**
 * A stored identity. The public [recipient] (`age1...`) is kept in the clear so it can be shown and
 * used as an encryption target without unlocking; the private key is sealed by [KeystoreVault] and
 * lives only as [sealedIvB64]/[sealedCtB64].
 */
data class VaultIdentity(
    val id: String,
    val label: String,
    val recipient: String,
    val createdAt: Long,
    val sealedIvB64: String,
    val sealedCtB64: String,
)

/** A saved public recipient (address-book entry). Public keys are not secret, so stored in clear. */
data class SavedRecipient(
    val id: String,
    val label: String,
    val recipient: String,
    val addedAt: Long,
)
