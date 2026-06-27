/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 */

package dev.mage.age.crypto

import kage.Age
import kage.Identity
import kage.Recipient
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Thin, Android-free facade over kage's [Age] object. Kept free of Android types on purpose so it
 * can be exercised by plain-JVM unit tests (kage itself is pure JVM).
 *
 * Encryption is streaming: [encrypt] copies [InputStream] to [OutputStream] without holding the
 * whole payload in memory. Decryption is NOT — kage's [Age.decryptStream] buffers the entire
 * ciphertext internally before producing plaintext, so callers must size-guard decrypt inputs (see
 * CryptoRunner.maxDecryptInputBytes). The byte[] helpers exist only for small inputs (pasted text,
 * key material).
 *
 * Armor on decrypt is automatic: [Age.decryptStream] sniffs the
 * `-----BEGIN AGE ENCRYPTED FILE-----` header and de-armors transparently, so callers never pass an
 * armor flag when decrypting.
 */
object AgeCrypto {
    /** ASCII-armor header age writes; handy for UI hints and tests. */
    const val ARMOR_HEADER: String = "-----BEGIN AGE ENCRYPTED FILE-----"

    /**
     * Encrypt [input] to [output] for every recipient in [recipients].
     *
     * Pass more than one recipient to make a file any of them can open (e.g. a teammate's key plus
     * one of your own identities so you can still decrypt it later). A [kage.crypto.scrypt.ScryptRecipient]
     * (passphrase) must be the *only* recipient — that is an age format rule kage enforces.
     *
     * Both streams are closed by kage when this returns.
     */
    fun encrypt(
        recipients: List<Recipient>,
        input: InputStream,
        output: OutputStream,
        armor: Boolean,
    ) {
        require(recipients.isNotEmpty()) { "At least one recipient is required" }
        Age.encryptStream(recipients, input, output, armor)
    }

    /**
     * Decrypt [input] to [output], trying each identity in turn until one unwraps the file key.
     * Armored input is detected and handled automatically. Both streams are closed when this returns.
     *
     * @throws kage.errors.CryptoException (e.g. [kage.errors.NoIdentitiesException] /
     *   [kage.errors.IncorrectHMACException]) if none of the identities can open the file.
     */
    fun decrypt(
        identities: List<Identity>,
        input: InputStream,
        output: OutputStream,
    ) {
        require(identities.isNotEmpty()) { "At least one identity is required" }
        Age.decryptStream(identities, input, output)
    }

    /** Convenience for small payloads (e.g. pasted text). See [encrypt]. */
    fun encryptBytes(
        recipients: List<Recipient>,
        plaintext: ByteArray,
        armor: Boolean,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        encrypt(recipients, ByteArrayInputStream(plaintext), out, armor)
        return out.toByteArray()
    }

    /** Convenience for small payloads. See [decrypt]. */
    fun decryptBytes(
        identities: List<Identity>,
        ciphertext: ByteArray,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        decrypt(identities, ByteArrayInputStream(ciphertext), out)
        return out.toByteArray()
    }
}
