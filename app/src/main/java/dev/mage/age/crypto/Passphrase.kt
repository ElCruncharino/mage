/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays
import kage.crypto.scrypt.ScryptIdentity
import kage.crypto.scrypt.ScryptRecipient

/**
 * Passphrase (scrypt) recipients and identities. A passphrase recipient must be the only recipient
 * on a file (age format rule, enforced by kage).
 *
 * Passphrases are taken as [CharArray] rather than [String] so the caller can zero them after use.
 *
 * IMPORTANT: kage's [ScryptRecipient]/[ScryptIdentity] retain the password byte[] *by reference* and
 * only read it later, at wrap/unwrap time during encrypt/decrypt. So we must NOT zero the bytes we
 * hand them — doing so silently turns every passphrase into all-zeros (verified: a "wrong"
 * passphrase would then decrypt). The array is left for the GC once the operation completes.
 */
object Passphrase {

    /**
     * Build a passphrase recipient for encryption. With [workFactor] null, kage's default scrypt
     * work factor is used (its `DEFAULT_WORK_FACTOR` lives in an internal companion, so it cannot be
     * named from here — the single-arg constructor applies it).
     */
    fun recipient(passphrase: CharArray, workFactor: Int? = null): ScryptRecipient {
        val bytes = toUtf8(passphrase)
        return if (workFactor != null) ScryptRecipient(bytes, workFactor) else ScryptRecipient(bytes)
    }

    /** Build a passphrase identity for decryption. */
    fun identity(passphrase: CharArray): ScryptIdentity {
        return ScryptIdentity(toUtf8(passphrase))
    }

    /** Encode a char[] as UTF-8 bytes without leaking an intermediate String. */
    private fun toUtf8(chars: CharArray): ByteArray {
        // CharBuffer.wrap shares [chars]; we don't wipe it here since the caller owns that array.
        val byteBuffer = StandardCharsets.UTF_8.encode(CharBuffer.wrap(chars))
        val bytes = ByteArray(byteBuffer.remaining())
        byteBuffer.get(bytes)
        // Best-effort wipe of the encoder's transient backing array.
        if (byteBuffer.hasArray()) Arrays.fill(byteBuffer.array(), 0.toByte())
        return bytes
    }
}
