/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 *
 * Pure-JVM tests of the recipient facade and the new SSH / scrypt-work-factor wiring. Runs on the
 * local JVM via `./gradlew :app:testDebugUnitTest` — no device needed.
 */

package dev.mage.age.crypto

import kage.crypto.ssh.SshKey
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecipientsTest {
    private val message = "the quick brown fox jumps over the lazy dog\n".toByteArray()

    // A throwaway, unencrypted ed25519 keypair generated solely for these tests.
    private val sshPublic =
        "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINnFMWcEk9hpN3yXjDqBbbPbQhY+pmZTxekeMxhZkHmV mage-test@example"
    private val sshPrivate =
        """
        -----BEGIN OPENSSH PRIVATE KEY-----
        b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
        QyNTUxOQAAACDZxTFnBJPYaTd8l4w6gW2z20IWPqZmU8XpHjMYWZB5lQAAAJjGB6YHxgem
        BwAAAAtzc2gtZWQyNTUxOQAAACDZxTFnBJPYaTd8l4w6gW2z20IWPqZmU8XpHjMYWZB5lQ
        AAAEB54OTlNuSR8EdjD0MyoFLufmLEFvYYiNspKCx2sW/t8dnFMWcEk9hpN3yXjDqBbbPb
        QhY+pmZTxekeMxhZkHmVAAAAEW1hZ2UtdGVzdEBleGFtcGxlAQIDBA==
        -----END OPENSSH PRIVATE KEY-----
        """.trimIndent()

    @Test
    fun kindOf_classifiesAgeSshAndJunk() {
        assertEquals(Recipients.Kind.AGE, Recipients.kindOf("age1abc"))
        assertEquals(Recipients.Kind.SSH, Recipients.kindOf(sshPublic))
        assertEquals(Recipients.Kind.SSH, Recipients.kindOf("ssh-rsa AAAAB3..."))
        assertNull(Recipients.kindOf("not a key"))
        assertNull(Recipients.kindOf("ssh-dss AAAA")) // unsupported type, not classified
        assertTrue(Recipients.looksLikeRecipient(sshPublic))
        assertFalse(Recipients.looksLikeRecipient(""))
    }

    @Test
    fun canonical_stripsSshCommentAndDedupes() {
        val withComment = Recipients.canonical(sshPublic)
        val noComment = Recipients.canonical("ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAINnFMWcEk9hpN3yXjDqBbbPbQhY+pmZTxekeMxhZkHmV")
        // Same key, different/absent comment → identical canonical form (so the address book de-dupes).
        assertEquals(noComment, withComment)
        assertEquals(2, withComment.split(" ").size) // "<type> <base64>", no trailing comment
    }

    @Test
    fun canonical_ageRoundTripsThroughBech32() {
        val id = Identities.generate()
        val encoded = Identities.encode(id.recipient())
        assertEquals(encoded, Recipients.canonical(encoded))
    }

    @Test
    fun parse_rejectsGarbage() {
        try {
            Recipients.parse("definitely not a key")
            fail("expected parse to reject non-key input")
        } catch (expected: IllegalArgumentException) {
            // good
        }
    }

    @Test
    fun encryptToSshRecipient_decryptsWithSshIdentity() {
        // Encrypt through Mage's facade to an SSH public key, then decrypt with the matching SSH
        // private key (via kage directly, since Mage does not yet store SSH identities).
        val recipient = Recipients.parse(sshPublic)
        val identity = SshKey.parseIdentity(sshPrivate)

        val ct = AgeCrypto.encryptBytes(listOf(recipient), message, armor = false)
        val pt = AgeCrypto.decryptBytes(listOf(identity), ct)

        assertArrayEquals(message, pt)
    }

    @Test
    fun mixedAgeAndSshRecipients_eitherCanDecrypt() {
        val ageId = Identities.generate()
        val recipients = listOf(Recipients.parse(Identities.encode(ageId.recipient())), Recipients.parse(sshPublic))

        val ct = AgeCrypto.encryptBytes(recipients, message, armor = false)

        assertArrayEquals("age identity decrypts", message, AgeCrypto.decryptBytes(listOf(ageId), ct))
        assertArrayEquals(
            "ssh identity decrypts",
            message,
            AgeCrypto.decryptBytes(listOf(SshKey.parseIdentity(sshPrivate)), ct),
        )
    }

    @Test
    fun workFactor_customValueRoundTrips() {
        val pass = "correct horse battery staple".toCharArray()
        val ct =
            AgeCrypto.encryptBytes(
                listOf(Passphrase.recipient(pass.copyOf(), workFactor = Passphrase.MIN_WORK_FACTOR)),
                message,
                armor = false,
            )
        val pt = AgeCrypto.decryptBytes(listOf(Passphrase.identity(pass.copyOf())), ct)
        assertArrayEquals(message, pt)
    }

    @Test
    fun workFactor_boundsAreSane() {
        assertEquals(18, Passphrase.DEFAULT_WORK_FACTOR)
        assertEquals(22, Passphrase.MAX_WORK_FACTOR)
        assertTrue(Passphrase.MIN_WORK_FACTOR < Passphrase.MAX_WORK_FACTOR)
        // The UI ceiling must stay within what Mage's own default-ceiling identity can decrypt.
        assertTrue(
            "max work factor must be decryptable by the default ScryptIdentity ceiling (22)",
            Passphrase.MAX_WORK_FACTOR <= 22,
        )
    }

    @Test
    fun workFactor_outOfRangeRejectedByKage() {
        try {
            Passphrase.recipient("x".toCharArray(), workFactor = 31) // 1 shl 31 overflows; kage rejects
            fail("expected an out-of-range work factor to be rejected")
        } catch (expected: IllegalArgumentException) {
            // good
        }
    }
}
