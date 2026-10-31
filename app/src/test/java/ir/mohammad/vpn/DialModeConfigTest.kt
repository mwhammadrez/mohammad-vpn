package ir.mohammad.vpn

import ir.mohammad.vpn.core.DialModeRules
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.BundleException
import ir.mohammad.vpn.data.OperatorBundleLoader
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The `oob` transport trick, ported from `docs/AUDIT_PATTNG.md` §2.2 and implemented independently
 * in `core-go/patches/0002-oob-dialmode.patch`.
 *
 * What is proven here: the value reaches the core's `sockopt`, and a bad value is refused at bundle
 * load. What is *not* proven here: that it defeats a specific ISP — that needs a device.
 */
class DialModeConfigTest {

    private fun profile(dialMode: String? = null, cipherSuites: String? = null, security: String = "none"): Profile {
        val stream = JSONObject().put("network", "ws").put("security", security)
        if (security == "tls") stream.put("tlsSettings", JSONObject())
        return Profile(
            id = "p1", name = "A", protocol = "VLESS", core = "XRAY",
            host = "h.example", port = 443,
            xray = JSONObject()
                .put("protocol", "vless")
                .put("settings", JSONObject())
                .put("streamSettings", stream),
            awg = null,
            dialMode = dialMode,
            cipherSuites = cipherSuites,
        )
    }

    private fun sockopt(p: Profile): JSONObject {
        val out = JSONObject(XrayConfig.render(p, 1400, listOf("1.1.1.1")))
        val proxy = out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
        return proxy.getJSONObject("streamSettings").getJSONObject("sockopt")
    }

    @Test
    fun `a profile without a dialMode renders none`() {
        assertFalse(sockopt(profile()).has("dialMode"))
    }

    @Test
    fun `an oob dialMode reaches the core`() {
        assertEquals("oob.0", sockopt(profile(dialMode = "oob.0")).getString("dialMode"))
        assertEquals("oob.0,32,65", sockopt(profile(dialMode = "oob.0,32,65")).getString("dialMode"))
    }

    @Test
    fun `syntax is validated before a core ever sees it`() {
        assertNull(DialModeRules.validate(null))
        assertNull(DialModeRules.validate(""))
        assertNull(DialModeRules.validate("oob.0"))
        assertNull(DialModeRules.validate("oob.0,32,65"))
        assertNull(DialModeRules.validate("oob. 7 , 8 "))

        assertTrue(DialModeRules.validate("oob.") != null)
        assertTrue(DialModeRules.validate("oob") != null)
        assertTrue(DialModeRules.validate("oob.256") != null)
        assertTrue(DialModeRules.validate("oob.-1") != null)
        assertTrue(DialModeRules.validate("oob.x") != null)
        assertTrue(DialModeRules.validate("split") != null)
    }

    @Test
    fun `a malformed dialMode rejects the whole bundle`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "dialMode":"oob.999",
               "xray":{"protocol":"vless","settings":{}}}
            ]}
        """.trimIndent()
        val failure = runCatching { OperatorBundleLoader.parse(json) }.exceptionOrNull()
        assertTrue("expected BundleException, got $failure", failure is BundleException)
    }

    @Test
    fun `a valid dialMode survives parsing`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "dialMode":"oob.0,32",
               "xray":{"protocol":"vless","settings":{}}}
            ]}
        """.trimIndent()
        assertEquals("oob.0,32", OperatorBundleLoader.parse(json).profiles.single().dialMode)
    }

    @Test
    fun `cipher suites land in tlsSettings only when tls is on`() {
        val tls = profile(cipherSuites = "TLS_AES_128_GCM_SHA256", security = "tls")
        val out = JSONObject(XrayConfig.render(tls, 1400, listOf("1.1.1.1")))
        val proxy = out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
        val settings = proxy.getJSONObject("streamSettings").getJSONObject("tlsSettings")

        assertEquals("TLS_AES_128_GCM_SHA256", settings.getString("cipherSuites"))
    }

    @Test
    fun `cipher suites are ignored on a plaintext profile instead of being emitted`() {
        val plain = profile(cipherSuites = "TLS_AES_128_GCM_SHA256", security = "none")
        assertFalse(sockopt(plain).has("cipherSuites"))
    }
}
