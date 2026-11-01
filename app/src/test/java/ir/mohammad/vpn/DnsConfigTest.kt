package ir.mohammad.vpn

import ir.mohammad.vpn.core.DnsConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The DNS engine takes a TOML file and a resolver list. Both are rendered here, and every rule the
 * engine would only discover at session setup is checked before a core ever starts.
 */
class DnsConfigTest {

    private fun settings(
        domain: String = "v.example.com",
        key: String = "0011223344556677",
        method: Int = 3,
        resolvers: List<String> = listOf("1.1.1.1", "8.8.8.8:53"),
    ) = JSONObject()
        .put("domain", domain)
        .put("key", key)
        .put("method", method)
        .put("resolvers", JSONArray(resolvers))

    @Test
    fun `a valid profile renders the engine config`() {
        val toml = DnsConfig.render(settings(), DnsConfig.SOCKS_PORT)
        assertTrue(toml.contains("DOMAINS = [\"v.example.com\"]"))
        assertTrue(toml.contains("DATA_ENCRYPTION_METHOD = 3"))
        assertTrue(toml.contains("ENCRYPTION_KEY = \"0011223344556677\""))
        assertTrue(toml.contains("LISTEN_PORT = ${DnsConfig.SOCKS_PORT}"))
        assertTrue(toml.contains("PROTOCOL_TYPE = \"SOCKS5\""))
        assertTrue(toml.contains("LOG_LEVEL = \"INFO\""))
    }

    @Test
    fun `resolvers become one line each`() {
        assertEquals("1.1.1.1\n8.8.8.8:53\n", DnsConfig.renderResolvers(settings()))
    }

    @Test
    fun `the key is whatever the server handed out`() {
        // The client hashes the key (SHA-256 for AES-256/ChaCha20, MD5 for AES-128), so length is not
        // a client-side rule. Public profiles ship 64-character hex keys for AES-256-GCM.
        assertNull(DnsConfig.validate(settings(method = 3, key = "0011223344556677")))
        assertNull(DnsConfig.validate(settings(method = 5, key = "59619224cdcc24eb52a1f503b291e39ce6cba3c3e0f85a59d282c9bbbb85619c")))
        assertNull(DnsConfig.validate(settings(method = 5, key = "a".repeat(32))))
        // Only genuinely unusable keys are refused.
        assertTrue(DnsConfig.validate(settings(method = 5, key = "")) != null)
        assertTrue(DnsConfig.validate(settings(method = 5, key = "has space")) != null)
    }

    @Test
    fun `an unencrypted profile needs no key`() {
        assertNull(DnsConfig.validate(settings(method = 0, key = "")))
        val toml = DnsConfig.render(settings(method = 0, key = ""), 10809)
        assertTrue(!toml.contains("ENCRYPTION_KEY"))
    }

    @Test
    fun `the domain and the resolver list are checked`() {
        assertTrue(DnsConfig.validate(settings(domain = "")) != null)
        assertTrue(DnsConfig.validate(settings(domain = "nodot")) != null)
        assertTrue(DnsConfig.validate(settings(resolvers = emptyList())) != null)
        assertTrue(DnsConfig.validate(settings(resolvers = listOf("1.1.1.1:99999"))) != null)
        assertTrue(DnsConfig.validate(settings(resolvers = List(65) { "1.1.1.$it" })) != null)
    }

    @Test
    fun `quotes in a domain cannot break the file`() {
        val toml = DnsConfig.render(settings(domain = "v.exa\"mple.com"), 10809)
        assertTrue(toml.contains("DOMAINS = [\"v.exa\\\"mple.com\"]"))
    }

    @Test
    fun `the diagnostic never prints the key`() {
        val text = DnsConfig.diagnostic(settings())
        assertTrue(text.contains("aes-128-gcm"))
        assertTrue(!text.contains("0011223344556677"))
    }
}
