/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 *
 * Pure-JVM tests of the crypto facade. kage is a JVM library, so these run on the local JVM via
 * `./gradlew :app:testDebugUnitTest` — no device or emulator needed.
 */

package dev.mage.age.crypto

import kage.Recipient
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AgeCryptoTest {
    private val message = "the quick brown fox jumps over the lazy dog\n".toByteArray()

    @Test
    fun x25519_roundTrip_binary() {
        val id = Identities.generate()
        val recipient = Identities.recipientOf(id)

        val ct = AgeCrypto.encryptBytes(listOf(recipient), message, armor = false)
        val pt = AgeCrypto.decryptBytes(listOf(id), ct)

        assertArrayEquals(message, pt)
    }

    @Test
    fun x25519_roundTrip_armored() {
        val id = Identities.generate()
        val ct = AgeCrypto.encryptBytes(listOf(Identities.recipientOf(id)), message, armor = true)

        // Armored output is ASCII and carries the age armor header.
        val text = String(ct, Charsets.US_ASCII)
        assertTrue("armor header present", text.startsWith(AgeCrypto.ARMOR_HEADER))

        // Decrypt auto-detects the armor.
        val pt = AgeCrypto.decryptBytes(listOf(id), ct)
        assertArrayEquals(message, pt)
    }

    @Test
    fun multiRecipient_eachCanDecrypt() {
        val alice = Identities.generate()
        val bob = Identities.generate()
        val recipients: List<Recipient> = listOf(Identities.recipientOf(alice), Identities.recipientOf(bob))

        val ct = AgeCrypto.encryptBytes(recipients, message, armor = false)

        assertArrayEquals("alice decrypts", message, AgeCrypto.decryptBytes(listOf(alice), ct))
        assertArrayEquals("bob decrypts", message, AgeCrypto.decryptBytes(listOf(bob), ct))
    }

    @Test
    fun wrongIdentity_failsToDecrypt() {
        val id = Identities.generate()
        val stranger = Identities.generate()
        val ct = AgeCrypto.encryptBytes(listOf(Identities.recipientOf(id)), message, armor = false)

        try {
            AgeCrypto.decryptBytes(listOf(stranger), ct)
            fail("expected decryption with the wrong identity to throw")
        } catch (expected: Exception) {
            // good — kage throws a CryptoException subclass when no identity matches.
        }
    }

    @Test
    fun passphrase_roundTrip() {
        val pass = "correct horse battery staple".toCharArray()
        val ct = AgeCrypto.encryptBytes(listOf(Passphrase.recipient(pass.copyOf())), message, armor = false)
        val pt = AgeCrypto.decryptBytes(listOf(Passphrase.identity(pass.copyOf())), ct)
        assertArrayEquals(message, pt)
    }

    @Test
    fun passphrase_wrongPassphrase_fails() {
        val ct =
            AgeCrypto.encryptBytes(
                listOf(Passphrase.recipient("hunter2".toCharArray())),
                message,
                armor = false,
            )
        try {
            AgeCrypto.decryptBytes(listOf(Passphrase.identity("nope".toCharArray())), ct)
            fail("expected wrong passphrase to throw")
        } catch (expected: Exception) {
            // good
        }
    }

    @Test
    fun identity_encodeDecode_isStable() {
        val id = Identities.generate()
        val encodedId = Identities.encode(id)
        val encodedRecipient = Identities.encode(Identities.recipientOf(id))

        // Re-parsing the identity yields one whose recipient encodes identically.
        val reparsed = Identities.parseIdentity(encodedId)
        assertEquals(encodedRecipient, Identities.encode(Identities.recipientOf(reparsed)))

        // A file encrypted to the re-parsed recipient decrypts with the original identity.
        val ct = AgeCrypto.encryptBytes(listOf(Recipients.parse(encodedRecipient)), message, false)
        assertArrayEquals(message, AgeCrypto.decryptBytes(listOf(id), ct))
    }

    @Test
    fun postQuantum_roundTrip() {
        val id = Identities.generate(postQuantum = true)
        val encodedId = Identities.encode(id)
        val encodedRecipient = Identities.encode(Identities.recipientOf(id))
        assertTrue(encodedId.startsWith("AGE-SECRET-KEY-PQ-"))
        assertTrue(encodedRecipient.startsWith("age1pq"))

        val ct = AgeCrypto.encryptBytes(listOf(Recipients.parse(encodedRecipient)), message, armor = false)
        val pt = AgeCrypto.decryptBytes(listOf(Identities.parseIdentity(encodedId)), ct)
        assertArrayEquals(message, pt)
    }

    @Test
    fun postQuantum_cannotMixWithClassicRecipient() {
        val pq = Identities.generate(postQuantum = true)
        val classic = Identities.generate()
        val recipients = listOf(Identities.recipientOf(pq), Identities.recipientOf(classic))

        try {
            AgeCrypto.encryptBytes(recipients, message, armor = false)
            fail("expected encrypting to a post-quantum and a classic recipient together to throw")
        } catch (expected: Exception) {
            // good — kage's "postquantum" label rejects the mix.
        }
    }

    /**
     * Regression test: a multi-chunk payload (> kage's 64 KiB STREAM chunk) decrypted through a
     * stream that returns short reads — exactly how file / SAF (`content://`) streams behave. This is
     * the real-world "encrypt on device, decrypt fails with 'error occurred while decrypting stream'"
     * bug: the chunk reader treated a mid-stream short read as the final chunk. In-memory byte-array
     * round-trips never hit it because ByteArrayInputStream always fills the buffer.
     */
    @Test
    fun multiChunk_throughPartialReadStream_roundTrips() {
        val id = Identities.generate()
        val big = ByteArray(200_000) { (it % 251).toByte() } // ~3 chunks
        val ct = AgeCrypto.encryptBytes(listOf(Identities.recipientOf(id)), big, armor = false)

        // A stream that never returns more than a handful of bytes per read.
        val drip =
            object : java.io.InputStream() {
                private val src = java.io.ByteArrayInputStream(ct)

                override fun read(): Int = src.read()

                override fun read(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ): Int = src.read(b, off, minOf(len, 7))
            }
        val out = java.io.ByteArrayOutputStream()
        AgeCrypto.decrypt(listOf(id), drip, out)

        assertArrayEquals(big, out.toByteArray())
    }

    // Regression test for #19: a 470 MB file used to be rejected by a pre-flight size guard that
    // assumed decrypt buffered the whole file. It doesn't (Age.decryptStream streams), so this
    // decrypts one under the 512 MB test heap and checks the old guard formula really would have
    // rejected it.
    @Test
    fun largeFile_decryptsUnderConstrainedHeap_regressionFor19() {
        val id = Identities.generate()
        val recipient = Identities.recipientOf(id)

        // Explicit disk-backed dir: the default java.io.tmpdir may be a RAM-backed tmpfs, and this
        // test writes several hundred MB of fixtures.
        val dir = java.io.File("build/tmp/largeFileTest").apply { mkdirs() }
        val plaintext = java.io.File.createTempFile("mage-big-plain", ".bin", dir)
        val ciphertext = java.io.File.createTempFile("mage-big-cipher", ".age", dir)
        val roundtrip = java.io.File.createTempFile("mage-big-out", ".bin", dir)
        try {
            val size = 470_000_000L
            val chunk = ByteArray(1 shl 20) // reused 1 MiB filler, so the fixture itself stays flat
            java.util.Random(42).nextBytes(chunk)
            java.io.FileOutputStream(plaintext).use { out ->
                var written = 0L
                while (written < size) {
                    val n = minOf(chunk.size.toLong(), size - written).toInt()
                    out.write(chunk, 0, n)
                    written += n
                }
            }

            java.io.FileInputStream(plaintext).use { src ->
                java.io.FileOutputStream(ciphertext).use { dst ->
                    AgeCrypto.encrypt(listOf(recipient), src, dst, armor = false)
                }
            }

            val oldGuardLimit = Runtime.getRuntime().maxMemory() / 4
            assertTrue(
                "old guard limit ($oldGuardLimit) should be smaller than the ciphertext " +
                    "(${ciphertext.length()}) for this to be a real regression test",
                oldGuardLimit < ciphertext.length(),
            )

            java.io.FileInputStream(ciphertext).use { src ->
                java.io.FileOutputStream(roundtrip).use { dst ->
                    AgeCrypto.decrypt(listOf(id), src, dst)
                }
            }

            assertEquals(plaintext.length(), roundtrip.length())
        } finally {
            plaintext.delete()
            ciphertext.delete()
            roundtrip.delete()
        }
    }
}
