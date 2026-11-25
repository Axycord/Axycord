package com.discord.plugins

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PluginIndexTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun entry(variant: String? = null, size: Long = 148_591_903) = PluginIndex.Entry(
        id = "axycord.emojireplace", packageName = "mmaarij.axycord.emojireplace",
        name = "Emoji Replace", versionCode = 1, versionName = "1.0.0", pluginApiMinimum = 3,
        apkUrl = "https://example.com/plugin.apk", sha256 = "a".repeat(64),
        signerSha256 = "b".repeat(64), size = size, variant = variant,
    )

    @Test fun `full quality packs are accepted but oversized catalogs are rejected`() {
        entry("full").validate()
        assertThrows(IllegalArgumentException::class.java) { entry("full", 200L * 1024 * 1024 + 1).validate() }
        assertThrows(IllegalArgumentException::class.java) { entry("../full").validate() }
    }

    @Test fun `variants share a plugin identity but use distinct catalog selection keys`() {
        val full = entry("full")
        val compressed = entry("compressed", 51_440_788)
        assertEquals(full.id, compressed.id)
        assertNotEquals(full.catalogKey, compressed.catalogKey)
        assertTrue(compressed.matchesInstalled(1, compressed.size))
        assertFalse(full.matchesInstalled(1, compressed.size))
        assertTrue(full.matchesInstalled(1, full.size))
        assertFalse(compressed.matchesInstalled(1, full.size))
    }

    @Test fun `legacy listings newer installs and unavailable size keep their existing behavior`() {
        assertTrue(entry().matchesInstalled(1, 1))
        assertTrue(entry("full").matchesInstalled(2, 1))
        assertTrue(entry("full").matchesInstalled(1, null))
        assertFalse(entry("full").matchesInstalled(0, 1))
        assertFalse(entry("full").matchesInstalled(null, null))
    }

    @Test fun `download streams in bounded chunks and verifies all bytes`() {
        val size = 3 * 1024 * 1024
        val chunk = ByteArray(64 * 1024) { 42 }
        val digest = MessageDigest.getInstance("SHA-256")
        repeat(size / chunk.size) { digest.update(chunk) }
        val expected = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        val input = object : InputStream() {
            var remaining = size
            override fun read(): Int = error("Must use bounded bulk reads")
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                assertTrue(length <= 64 * 1024)
                if (remaining == 0) return -1
                val count = minOf(length, remaining)
                buffer.fill(42, offset, offset + count)
                remaining -= count
                return count
            }
        }
        val file = temporary.newFile()
        writeVerifiedApk(input, file, size.toLong(), expected)
        assertEquals(size.toLong(), file.length())
    }

    @Test fun `truncated oversized and wrong digest downloads are deleted`() {
        for ((data, size) in listOf(byteArrayOf(1) to 2L, byteArrayOf(1, 2) to 1L, byteArrayOf(1) to 1L)) {
            val file = temporary.newFile()
            assertThrows(IllegalArgumentException::class.java) {
                writeVerifiedApk(ByteArrayInputStream(data), file, size, "0".repeat(64))
            }
            assertFalse(file.exists())
        }
    }

    @Test fun `transport failures discard the partially written apk`() {
        val file = temporary.newFile()
        val input = object : InputStream() {
            override fun read(): Int = throw IOException("Disconnected")
        }
        assertThrows(IOException::class.java) { writeVerifiedApk(input, file, 1, "0".repeat(64)) }
        assertFalse(file.exists())
    }
}
