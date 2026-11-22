/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 *
 * Pure-JVM tests of identity parsing from pasted text (bare keys, `age-keygen` output files, BOM and
 * CRLF from Windows editors). Runs via `./gradlew :app:testDebugUnitTest` — no device needed.
 */

package dev.mage.age.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class IdentitiesTest {
    private val secret = Identities.encode(Identities.generate())
    private val publicKey = Identities.encode(Identities.recipientOf(Identities.parseIdentity(secret)))

    @Test
    fun parseIdentity_acceptsBareKey() {
        assertEquals(secret, Identities.encode(Identities.parseIdentity(secret)))
    }

    @Test
    fun parseIdentity_acceptsSurroundingWhitespace() {
        assertEquals(secret, Identities.encode(Identities.parseIdentity("  $secret\n")))
    }

    @Test
    fun parseIdentity_acceptsAgeKeygenFileWithComments() {
        val file = "# created: 2026-11-21T09:00:00Z\n# public key: $publicKey\n$secret\n"
        assertEquals(secret, Identities.encode(Identities.parseIdentity(file)))
    }

    @Test
    fun parseIdentity_acceptsCrlfLineEndings() {
        val file = "# created: 2026-11-21T09:00:00Z\r\n# public key: $publicKey\r\n$secret\r\n"
        assertEquals(secret, Identities.encode(Identities.parseIdentity(file)))
    }

    @Test
    fun parseIdentity_stripsByteOrderMark() {
        assertEquals(secret, Identities.encode(Identities.parseIdentity("﻿$secret")))
        assertEquals(secret, Identities.encode(Identities.parseIdentity("﻿# public key: $publicKey\n$secret")))
    }

    @Test
    fun parseIdentity_rejectsFileWithNoSecret() {
        assertThrows(Exception::class.java) {
            Identities.parseIdentity("# created: 2026-11-21T09:00:00Z\n# public key: $publicKey\n")
        }
    }

    @Test
    fun parseIdentity_rejectsGarbage() {
        assertThrows(Exception::class.java) { Identities.parseIdentity("not a key") }
    }
}
