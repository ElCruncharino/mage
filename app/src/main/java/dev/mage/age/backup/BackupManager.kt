/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.backup

import android.content.Context
import android.net.Uri
import dev.mage.age.AppContainer
import dev.mage.age.crypto.AgeCrypto
import dev.mage.age.crypto.Identities
import dev.mage.age.crypto.Passphrase
import dev.mage.age.io.SafIO
import dev.mage.age.ui.CryptoRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Encrypted identity backup/restore — dogfoods age itself: the backup file is the identity bundle
 * age-encrypted to a passphrase (scrypt). So a Mage backup is a normal `.age` file that the `age`
 * CLI (or any age tool) can also decrypt with the same passphrase.
 */
object BackupManager {
    data class ImportResult(
        val imported: Int,
        val skipped: Int,
    )

    /**
     * Export every stored identity to [output], encrypted to [passphrase]. Requires a valid auth
     * window (the caller should unlock first) because it reads private keys out of the vault.
     */
    suspend fun export(
        context: Context,
        container: AppContainer,
        passphrase: CharArray,
        armor: Boolean,
        output: Uri,
    ) = withContext(Dispatchers.IO) {
        val entries =
            container.identities.list().map { record ->
                BackupCodec.Entry(record.label, Identities.encode(container.identities.open(record)))
            }
        require(entries.isNotEmpty()) { "No identities to back up" }

        val bundle = BackupCodec.serialize(entries)
        val recipient = Passphrase.recipient(passphrase)
        SafIO.openOutput(context, output).use { out ->
            AgeCrypto.encrypt(listOf(recipient), bundle.inputStream(), out, armor)
        }
    }

    /**
     * Restore identities from an encrypted backup at [input] using [passphrase]. Identities whose
     * public key already exists are skipped. Returns counts. Does not require unlocking (it seals the
     * imported keys with the current vault key, which is a write).
     */
    suspend fun import(
        context: Context,
        container: AppContainer,
        passphrase: CharArray,
        input: Uri,
    ): ImportResult =
        withContext(Dispatchers.IO) {
            // Restore reads the whole backup into memory and kage's decrypt copies it again, so guard
            // against an oversized pick (the restore picker accepts any file) the same way DecryptScreen
            // does. Unknown size (null) is allowed — there is nothing cheap to check against.
            val size = SafIO.sizeBytes(context, input)
            val limit = CryptoRunner.maxDecryptInputBytes()
            require(size == null || size <= limit) {
                "This backup is too large to restore on this device"
            }
            val ciphertext = SafIO.openInput(context, input).use { it.readBytes() }
            val plaintext = AgeCrypto.decryptBytes(listOf(Passphrase.identity(passphrase)), ciphertext)
            val entries = BackupCodec.deserialize(plaintext)

            container.ensureVaultKey()
            val existing =
                container.identities
                    .list()
                    .map { it.recipient }
                    .toMutableSet()

            var imported = 0
            var skipped = 0
            entries.forEach { entry ->
                val identity = Identities.parseIdentity(entry.key)
                val recipient = Identities.encode(identity.recipient())
                if (recipient in existing) {
                    skipped++
                } else {
                    container.identities.add(entry.label, identity)
                    existing.add(recipient)
                    imported++
                }
            }
            ImportResult(imported, skipped)
        }
}
