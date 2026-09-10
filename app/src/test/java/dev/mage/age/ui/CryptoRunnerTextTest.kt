/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 *
 * Pure-JVM test of CryptoRunner's text helpers, used by TextScreen (issue #26).
 */

package dev.mage.age.ui

import dev.mage.age.crypto.Identities
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class CryptoRunnerTextTest {
    @Test
    fun encryptText_decryptText_roundTrips() =
        runBlocking {
            val id = Identities.generate()
            val recipient = Identities.recipientOf(id)
            val message = "the quick brown fox jumps over the lazy dog"

            val armored = CryptoRunner.encryptText(listOf(recipient), message, armor = true)
            assertEquals(true, String(armored, Charsets.UTF_8).startsWith("-----BEGIN AGE ENCRYPTED FILE-----"))

            val plain = CryptoRunner.decryptText(listOf(id), String(armored, Charsets.UTF_8))
            assertArrayEquals(message.toByteArray(Charsets.UTF_8), plain)
        }

    @Test
    fun decryptText_wrongIdentity_fails() =
        runBlocking {
            val id = Identities.generate()
            val stranger = Identities.generate()
            val recipient = Identities.recipientOf(id)
            val armored = CryptoRunner.encryptText(listOf(recipient), "secret", armor = true)

            try {
                CryptoRunner.decryptText(listOf(stranger), String(armored, Charsets.UTF_8))
                org.junit.Assert.fail("expected decryption with the wrong identity to throw")
            } catch (expected: Exception) {
                // good
            }
        }
}
