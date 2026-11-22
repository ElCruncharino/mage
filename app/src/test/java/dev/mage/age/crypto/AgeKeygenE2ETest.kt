/*
 * Mage — a modern Android GUI for age file encryption.
 * Copyright (c) 2026 Nick Haghiri
 *
 * End-to-end check against the real `age` CLI. The fixture directory is produced outside the test
 * (`age-keygen -o key.txt`, `age -r <pub> -o cipher.age plain.txt`) and named by MAGE_E2E_DIR. Without
 * that variable the test is skipped, so the normal unit run is unaffected.
 */

package dev.mage.age.crypto

import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class AgeKeygenE2ETest {
    private val dir: File? = System.getenv("MAGE_E2E_DIR")?.let(::File)

    @Test
    fun importsAgeKeygenFile_andDecryptsAgeCliOutput() {
        assumeTrue(dir != null && dir.isDirectory)
        dir!!

        // Paste the whole age-keygen file, exactly as a user would copy it from a PC.
        val keyFile = File(dir, "key.txt").readText()
        val identity = Identities.parseIdentity(keyFile)
        val expectedPublic = File(dir, "pub.txt").readText().trim()
        assertEquals(expectedPublic, Identities.encode(Identities.recipientOf(identity)))

        // Direction 1: age CLI -> Mage.
        val plain = File(dir, "plain.txt").readBytes()
        val decrypted = AgeCrypto.decryptBytes(listOf(identity), File(dir, "cipher.age").readBytes())
        assertEquals(String(plain, Charsets.UTF_8), String(decrypted, Charsets.UTF_8))

        // Direction 2: Mage -> age CLI. Written out for the shell to decrypt with `age -d`.
        val message = "hello from mage\n".toByteArray()
        val recipient = Identities.recipientOf(identity)
        val encrypted = AgeCrypto.encryptBytes(listOf(recipient), message, armor = false)
        File(dir, "mage.age").writeBytes(encrypted)
    }
}
