package ir.mohammad.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.ImportBatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Clipboard → profiles, including the awkward cases: several links, one broken, junk around them. */
@RunWith(AndroidJUnit4::class)
class ImportBatchTest {

    private val vless = "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=none#A"
    private val trojan = "trojan://pw@b.example.com:443#B"

    @Test
    fun `one link`() {
        val outcome = ImportBatch.parse(vless)
        assertEquals(1, outcome.profiles.size)
        assertEquals("A", outcome.profiles.single().name)
        assertEquals("imported 1", outcome.message)
    }

    @Test
    fun `several links, one per line`() {
        val outcome = ImportBatch.parse("$vless\n$trojan\n")
        assertEquals(2, outcome.profiles.size)
        assertEquals(setOf("VLESS", "TROJAN"), outcome.profiles.map { it.protocol }.toSet())
    }

    @Test
    fun `junk around the links is skipped, not fatal`() {
        val outcome = ImportBatch.parse("look at this:\n$vless\nthanks")
        assertEquals(1, outcome.profiles.size)
        assertEquals(2, outcome.ignored)
        assertEquals("imported 1, skipped 2 unrecognised", outcome.message)
    }

    @Test
    fun `one broken link does not discard the good ones`() {
        val broken = "vless://nohost?security=none"
        val outcome = ImportBatch.parse("$vless\n$broken")
        assertEquals(1, outcome.profiles.size)
        assertEquals(1, outcome.failures.size)
        assertTrue(outcome.message.startsWith("imported 1, 1 failed"))
    }

    @Test
    fun `a vless link is not split at the ss inside it`() {
        val outcome = ImportBatch.parse(vless)
        assertEquals(1, outcome.profiles.size)
        assertEquals("VLESS", outcome.profiles.single().protocol)
        assertEquals(0, outcome.failures.size)
    }

    @Test
    fun `links concatenated on one line are still three links`() {
        val outcome = ImportBatch.parse("$vless#$trojan#${vless.replace("a.example.com", "c.example.com")}")
        assertEquals(3, outcome.profiles.size) // three distinct links, three profiles
        assertEquals(0, outcome.failures.size)
    }

    @Test
    fun `the same link twice yields one profile`() {
        val outcome = ImportBatch.parse("$vless\n$vless")
        assertEquals(1, outcome.profiles.size)
    }

    @Test
    fun `nothing usable says so`() {
        val outcome = ImportBatch.parse("just some text")
        assertEquals(0, outcome.profiles.size)
        assertEquals("Clipboard has no vless/vmess/trojan/ss link", outcome.message)
    }
}
