package dev.mage.age.backup

import dev.mage.age.crypto.Identities
import org.junit.Assert.assertEquals
import org.junit.Test

class RestoreParseTest {
    private val key = Identities.encode(Identities.generate())
    private val recipient = Identities.encode(Identities.recipientOf(Identities.parseIdentity(key)))

    private fun keyFile(eol: String) = "# created: 2026-11-22T00:00:00Z$eol# public key: $recipient$eol$key$eol"

    private fun assertRestores(bytes: ByteArray) {
        val entries = BackupCodec.parseRestored(bytes)
        assertEquals(1, entries.size)
        assertEquals(key, Identities.encode(Identities.parseIdentity(entries.single().key)))
    }

    @Test
    fun plainKeyFileLineEndings() {
        assertRestores(keyFile("\n").toByteArray())
        assertRestores(keyFile("\r\n").toByteArray())
        assertRestores(key.toByteArray())
    }

    @Test
    fun utf8ByteOrderMark() {
        assertRestores(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + keyFile("\r\n").toByteArray())
    }

    @Test
    fun utf16LeFromWindowsPowerShell() {
        assertRestores(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + keyFile("\r\n").toByteArray(Charsets.UTF_16LE))
        assertRestores(keyFile("\r\n").toByteArray(Charsets.UTF_16LE))
    }

    @Test
    fun utf16BeWithBom() {
        assertRestores(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + keyFile("\n").toByteArray(Charsets.UTF_16BE))
    }
}
