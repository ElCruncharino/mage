/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 *
 * Pure-JVM tests of passphrase autogeneration. Runs on the local JVM via
 * `./gradlew :app:testDebugUnitTest`, no device needed.
 */

package dev.mage.age.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PassphraseTest {
    private val message = "the quick brown fox jumps over the lazy dog\n".toByteArray()

    @Test
    fun wordlist_hasExactlyTwoThousandFortyEightUniqueWords() {
        assertEquals(Wordlist.SIZE, Wordlist.words.size)
        assertEquals(Wordlist.SIZE, Wordlist.words.toSet().size)
    }

    @Test
    fun generate_producesTenKnownWordsJoinedByHyphens() {
        val generated = String(Passphrase.generate())
        val words = generated.split("-")

        assertEquals(Passphrase.GENERATED_WORD_COUNT, words.size)
        words.forEach { assertTrue("'$it' is not in the wordlist", it in Wordlist.words) }
    }

    @Test
    fun generate_isNotConstant() {
        // Flags a broken RNG (e.g. an accidentally-fixed seed) without asserting anything about the
        // real distribution. 20 draws colliding by chance is astronomically unlikely (2048^10 space).
        val draws = (1..20).map { String(Passphrase.generate()) }
        assertTrue(draws.toSet().size > 1)
    }

    @Test
    fun generate_roundTripsAsARealPassphrase() {
        val generated = Passphrase.generate()
        val ciphertext = AgeCrypto.encryptBytes(listOf(Passphrase.recipient(generated)), message, armor = false)
        val plaintext = AgeCrypto.decryptBytes(listOf(Passphrase.identity(generated)), ciphertext)

        assertEquals(String(message, Charsets.UTF_8), String(plaintext, Charsets.UTF_8))
    }
}
