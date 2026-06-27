/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import kage.crypto.scrypt.ScryptIdentity
import kage.crypto.scrypt.ScryptRecipient
import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Arrays

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
     * kage's default scrypt work factor (log2 of the iteration count). Now a named public constant on
     * [ScryptRecipient] in the kage fork, so we surface it instead of relying on the single-arg
     * constructor's implicit default.
     */
    val DEFAULT_WORK_FACTOR: Int = ScryptRecipient.DEFAULT_WORK_FACTOR

    /**
     * Lowest work factor the UI offers. kage accepts down to 1, but anything that low is pointless for
     * a real passphrase, so we keep the slider in a sane range.
     */
    const val MIN_WORK_FACTOR: Int = 10

    /**
     * Highest work factor the UI offers. kage's decrypt-side ceiling ([ScryptIdentity]'s default
     * maxWorkFactor) is 22, and [identity] decrypts with that default — so a file encrypted above 22
     * could never be reopened in Mage. We cap here to never produce a self-undecryptable file.
     */
    const val MAX_WORK_FACTOR: Int = 22

    /**
     * Build a passphrase recipient for encryption at [workFactor] (defaults to [DEFAULT_WORK_FACTOR]).
     * kage validates the range (1..30) and throws on out-of-range values.
     */
    fun recipient(
        passphrase: CharArray,
        workFactor: Int = DEFAULT_WORK_FACTOR,
    ): ScryptRecipient {
        val bytes = toUtf8(passphrase)
        return ScryptRecipient(bytes, workFactor)
    }

    /** Build a passphrase identity for decryption. */
    fun identity(passphrase: CharArray): ScryptIdentity = ScryptIdentity(toUtf8(passphrase))

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
