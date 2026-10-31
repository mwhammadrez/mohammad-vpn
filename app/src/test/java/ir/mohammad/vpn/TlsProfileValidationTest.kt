package ir.mohammad.vpn

import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.BundleException
import ir.mohammad.vpn.data.OperatorBundleLoader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC TLS-2, TLS-3, TLS-6, TLS-7.
 *
 * A profile that turns TLS on without naming a fingerprint silently inherits Go's default
 * ClientHello, which no browser produces — that is the signal this milestone exists to remove.
 */
class TlsProfileValidationTest {

    private fun bundle(stream: String): String = """
        {"schemaVersion":1,"profiles":[
          {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
           "xray":{"protocol":"vless","settings":{},"streamSettings":$stream}}
        ]}
    """.trimIndent()

    private fun parse(stream: String) = OperatorBundleLoader.parse(bundle(stream))

    @Test
    fun `security none needs no fingerprint`() {
        val bundle = parse("""{"network":"ws","security":"none"}""")
        assertEquals(1, bundle.profiles.size)
    }

    @Test
    fun `a profile without streamSettings is still security none`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h","serverPort":443,
               "xray":{"protocol":"vless","settings":{}}}
            ]}
        """.trimIndent()
        assertEquals(1, OperatorBundleLoader.parse(json).profiles.size)
    }

    @Test
    fun `tls without a fingerprint is rejected`() {
        val failure = runCatching { parse("""{"network":"tcp","security":"tls","tlsSettings":{}}""") }
            .exceptionOrNull()
        assertTrue("expected BundleException, got $failure", failure is BundleException)
        assertTrue(failure?.message!!.contains("fingerprint"))
    }

    @Test
    fun `reality without a fingerprint is rejected`() {
        val failure = runCatching {
            parse(
                """{"network":"tcp","security":"reality","realitySettings":
                   {"serverName":"a.example","publicKey":"Q2H2h5jZW54Zg-MNKwBn8NOPEcZFMC2t2sAs_3vjKlg","shortId":"ee3e25522b558543"}}""",
            )
        }.exceptionOrNull()
        assertTrue("expected BundleException, got $failure", failure is BundleException)
        assertTrue(failure?.message!!.contains("fingerprint"))
    }

    @Test
    fun `an unknown fingerprint is rejected`() {
        val failure = runCatching {
            parse("""{"network":"tcp","security":"tls","tlsSettings":{"fingerprint":"netscape"}}""")
        }.exceptionOrNull()
        assertTrue("expected BundleException, got $failure", failure is BundleException)
        assertTrue(failure?.message!!.contains("fingerprint"))
    }

    @Test
    fun `a known fingerprint is accepted for tls and reality`() {
        parse("""{"network":"tcp","security":"tls","tlsSettings":{"fingerprint":"chrome"}}""")
        parse(
            """{"network":"tcp","security":"reality","realitySettings":
               {"serverName":"a.example","publicKey":"Q2H2h5jZW54Zg-MNKwBn8NOPEcZFMC2t2sAs_3vjKlg","shortId":"ee3e25522b558543","fingerprint":"firefox"}}""",
        )
    }

    @Test
    fun `a profile level fingerprint overrides the settings block`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "fingerprint":"safari",
               "xray":{"protocol":"vless","settings":{},
                 "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{}}}}
            ]}
        """.trimIndent()
        val profile = OperatorBundleLoader.parse(json).profiles.single()
        assertEquals("safari", profile.fingerprint)
    }

    @Test
    fun `a profile level fingerprint satisfies the requirement on its own`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "fingerprint":"chrome",
               "xray":{"protocol":"vless","settings":{},
                 "streamSettings":{"network":"tcp","security":"tls"}}}
            ]}
        """.trimIndent()
        assertEquals(1, OperatorBundleLoader.parse(json).profiles.size)
    }

    @Test
    fun `a fingerprint without tls is rejected as meaningless`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "fingerprint":"chrome",
               "xray":{"protocol":"vless","settings":{},
                 "streamSettings":{"network":"ws","security":"none"}}}
            ]}
        """.trimIndent()
        val failure = runCatching { OperatorBundleLoader.parse(json) }.exceptionOrNull()
        assertTrue("expected BundleException, got $failure", failure is BundleException)
    }

    @Test
    fun `the rendered config carries the fingerprint in the right block`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "fingerprint":"safari",
               "xray":{"protocol":"vless","settings":{},
                 "streamSettings":{"network":"tcp","security":"tls","tlsSettings":{}}}}
            ]}
        """.trimIndent()
        val profile = OperatorBundleLoader.parse(json).profiles.single()
        val out = JSONObject(XrayConfig.render(profile, 1400, listOf("1.1.1.1")))
        val proxy = out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
        val tls = proxy.getJSONObject("streamSettings").getJSONObject("tlsSettings")

        assertEquals("safari", tls.getString("fingerprint"))
    }

    @Test
    fun `the rendered config puts a reality fingerprint in realitySettings`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "fingerprint":"firefox",
               "xray":{"protocol":"vless","settings":{},
                 "streamSettings":{"network":"tcp","security":"reality",
                   "realitySettings":{"serverName":"a.example",
                     "publicKey":"Q2H2h5jZW54Zg-MNKwBn8NOPEcZFMC2t2sAs_3vjKlg",
                     "shortId":"ee3e25522b558543"}}}}
            ]}
        """.trimIndent()
        val profile = OperatorBundleLoader.parse(json).profiles.single()
        val out = JSONObject(XrayConfig.render(profile, 1400, listOf("1.1.1.1")))
        val proxy = out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
        val reality = proxy.getJSONObject("streamSettings").getJSONObject("realitySettings")

        assertEquals("firefox", reality.getString("fingerprint"))
    }

    @Test
    fun `the diagnostic log names the profile fingerprint, not a raw ja3`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "fingerprint":"chrome",
               "xray":{"protocol":"vless","settings":{},
                 "streamSettings":{"network":"tcp","security":"tls"}}}
            ]}
        """.trimIndent()
        val profile = OperatorBundleLoader.parse(json).profiles.single()
        val line = ir.mohammad.vpn.core.TlsFingerprint.diagnostic(profile)
        assertTrue(line.contains("fingerprint=chrome"))
        assertTrue("must not contain a ja3 hash", !line.contains(":"))
    }
}
