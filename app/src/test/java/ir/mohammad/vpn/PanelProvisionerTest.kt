package ir.mohammad.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.PanelProvisioner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The pieces of provisioning that can be checked without a Cloudflare account: the name rules, the
 * schema splitter, and the password hash — the hash has to be byte-identical to the one the panel
 * worker computes, or every login fails with "bad password".
 */
@RunWith(AndroidJUnit4::class)
class PanelProvisionerTest {

    @Test
    fun `worker names are lowered and stripped to what Cloudflare accepts`() {
        assertEquals("my-panel", PanelProvisioner.normalizeName("My Panel!"))
        assertEquals("mohammad", PanelProvisioner.normalizeName("  mohammad  "))
        assertEquals("a-b-c", PanelProvisioner.normalizeName("a__b--c"))
    }

    @Test
    fun `a name too short to be valid still yields a valid one`() {
        val name = PanelProvisioner.normalizeName("ab")
        assertTrue(name.startsWith("mohammad-"))
        assertTrue(name.length in 3..50)
    }

    @Test
    fun `schema comments are dropped and every statement is separated`() {
        val sql = """
            -- a comment; with a semicolon inside
            CREATE TABLE users (id INTEGER);
            CREATE INDEX i ON users (id);
        """.trimIndent()

        val statements = PanelProvisioner.statements(sql)
        assertEquals(2, statements.size)
        assertTrue(statements[0].startsWith("CREATE TABLE"))
        assertTrue(statements[1].startsWith("CREATE INDEX"))
    }

    @Test
    fun `password hash matches the worker's sha256 hex`() {
        // Known SHA-256 of "test"; the worker hashes the same way before comparing.
        assertEquals(
            "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            PanelProvisioner.sha256Hex("test"),
        )
    }

    @Test
    fun `suggested passwords are long enough and avoid look-alikes`() {
        val password = PanelProvisioner.suggestPassword()
        assertEquals(12, password.length)
        assertTrue(password.none { it in "01loOI" })
    }
}
