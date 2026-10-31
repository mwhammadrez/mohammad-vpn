package ir.mohammad.vpn

import ir.mohammad.vpn.core.RealityRules
import ir.mohammad.vpn.core.TransportTuning
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.BundleException
import ir.mohammad.vpn.data.OperatorBundleLoader
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC TLS-1, TLS-3, TLS-4, TLS-5 — AC-8 and AC-10.
 *
 * The validation rules are not invented: they mirror what the pinned core enforces when it builds a
 * REALITY config (`infra/conf/transport_security.go`, xray-core v26.10.29): publicKey is base64url
 * without padding and must decode to 32 bytes, shortId is hex of at most 16 characters, spiderX must
 * start with "/". The public key and the short id are never echoed in an error message.
 */
class RealityConfigTest {

    private val publicKey = "Q2H2h5jZW54Zg-MNKwBn8NOPEcZFMC2t2sAs_3vjKlg"
    private val shortId = "ee3e25522b558543"

    private fun bundle(
        reality: String = """{"serverName":"a.example","publicKey":"$publicKey","shortId":"$shortId","fingerprint":"chrome"}""",
        security: String = "reality",
        extraStream: String = "",
        extraProfile: String = "",
    ): String = """
        {"schemaVersion":1,"profiles":[
          {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
           $extraProfile
           "xray":{"protocol":"vless","settings":{"vnext":[{"address":"h.example","port":443,
             "users":[{"id":"0722d93a-024a-489d-be2a-a2e83a1f39b7","encryption":"none"}]}]},
             "streamSettings":{"network":"tcp","security":"$security",
               "realitySettings":$reality$extraStream}}}
        ]}
    """.trimIndent()

    private fun parse(json: String) = OperatorBundleLoader.parse(json)

    private fun failure(json: String): BundleException? =
        runCatching { parse(json) }.exceptionOrNull() as? BundleException

    // --- happy path -----------------------------------------------------------------

    @Test
    fun `a complete reality profile parses`() {
        val profile = parse(bundle()).profiles.single()
        assertEquals("a.example", profile.xray!!.getJSONObject("streamSettings")
            .getJSONObject("realitySettings").getString("serverName"))
    }

    @Test
    fun `the rendered config carries every reality field`() {
        val profile = parse(bundle(extraProfile = "\"fingerprint\":\"chrome\",")).profiles.single()
        val stream = proxyStream(profile)
        val reality = stream.getJSONObject("realitySettings")

        assertEquals("a.example", reality.getString("serverName"))
        assertEquals(publicKey, reality.getString("publicKey"))
        assertEquals(shortId, reality.getString("shortId"))
        assertEquals("chrome", reality.getString("fingerprint"))
        assertEquals("reality", stream.getString("security"))
    }

    @Test
    fun `a completely different server is accepted unchanged`() {
        // D-78/SRC-3: validation is about shape, not about this operator's values.
        val otherKey = "0MxEDeM4M-TnvFkSSt2PvBBbSLDiVeYZSDJZ91sitAw"
        val otherId = "96da3c3fa8193e34"
        val json = bundle(
            reality = """{"serverName":"cdn.example.net","publicKey":"$otherKey","shortId":"$otherId","fingerprint":"firefox"}""",
            extraProfile = "\"flow\":\"xtls-rprx-vision\",",
        )
        val profile = parse(json).profiles.single()
        val reality = proxyStream(profile).getJSONObject("realitySettings")

        assertEquals("cdn.example.net", reality.getString("serverName"))
        assertEquals(otherKey, reality.getString("publicKey"))
        assertEquals(otherId, reality.getString("shortId"))
        assertEquals("firefox", reality.getString("fingerprint"))
    }

    // --- serverName -----------------------------------------------------------------

    @Test
    fun `a missing serverName is rejected`() {
        val f = failure(bundle(reality = """{"publicKey":"$publicKey","shortId":"$shortId","fingerprint":"chrome"}"""))
        assertTrue("expected BundleException, got $f", f != null)
        assertTrue(f!!.message!!.contains("serverName"))
    }

    @Test
    fun `a serverName with a port or scheme is rejected`() {
        listOf("a.example:443", "https://a.example", "a.example/path", "a example").forEach { bad ->
            val f = failure(bundle(reality = """{"serverName":"$bad","publicKey":"$publicKey","shortId":"$shortId"}"""))
            assertTrue("$bad must be rejected", f != null)
        }
    }

    // --- publicKey ------------------------------------------------------------------

    @Test
    fun `a missing publicKey is rejected`() {
        val f = failure(bundle(reality = """{"serverName":"a.example","shortId":"$shortId","fingerprint":"chrome"}"""))
        assertTrue("expected BundleException, got $f", f != null)
        assertTrue(f!!.message!!.contains("publicKey"))
    }

    @Test
    fun `a publicKey that is not 32 bytes of base64url is rejected`() {
        listOf("short", "$publicKey=", "not base64!", "AAAA").forEach { bad ->
            val f = failure(bundle(reality = """{"serverName":"a.example","publicKey":"$bad","shortId":"$shortId"}"""))
            assertTrue("$bad must be rejected", f != null)
        }
    }

    @Test
    fun `the publicKey is never echoed in the error message`() {
        val bad = "A".repeat(40)
        val f = failure(bundle(reality = """{"serverName":"a.example","publicKey":"$bad","shortId":"$shortId"}"""))
        assertTrue("expected BundleException, got $f", f != null)
        assertFalse("the key must not appear in the message", f!!.message!!.contains(bad))
    }

    // --- shortId --------------------------------------------------------------------

    @Test
    fun `a missing shortId is rejected`() {
        val f = failure(bundle(reality = """{"serverName":"a.example","publicKey":"$publicKey","fingerprint":"chrome"}"""))
        assertTrue("expected BundleException, got $f", f != null)
        assertTrue(f!!.message!!.contains("shortId"))
    }

    @Test
    fun `a shortId that is not hex is rejected`() {
        listOf("zz", "abc", "ee3e25522b55854g").forEach { bad ->
            val f = failure(bundle(reality = """{"serverName":"a.example","publicKey":"$publicKey","shortId":"$bad"}"""))
            assertTrue("$bad must be rejected", f != null)
        }
    }

    @Test
    fun `a shortId longer than 16 characters is rejected`() {
        val f = failure(bundle(reality = """{"serverName":"a.example","publicKey":"$publicKey","shortId":"${"ab".repeat(9)}"}"""))
        assertTrue("expected BundleException, got $f", f != null)
    }

    @Test
    fun `an empty shortId is accepted because the core accepts it`() {
        val profile = parse(bundle(reality = """{"serverName":"a.example","publicKey":"$publicKey","shortId":"","fingerprint":"chrome"}"""))
        assertEquals(1, profile.profiles.size)
    }

    // --- spiderX --------------------------------------------------------------------

    @Test
    fun `a spiderX that does not start with a slash is rejected`() {
        val f = failure(
            bundle(reality = """{"serverName":"a.example","publicKey":"$publicKey","shortId":"$shortId","spiderX":"no","fingerprint":"chrome"}"""),
        )
        assertTrue("expected BundleException, got $f", f != null)
    }

    @Test
    fun `a well formed spiderX is accepted`() {
        parse(
            bundle(reality = """{"serverName":"a.example","publicKey":"$publicKey","shortId":"$shortId","spiderX":"/p=1-2","fingerprint":"chrome"}"""),
        )
    }

    // --- flow -----------------------------------------------------------------------

    @Test
    fun `vision is accepted with reality`() {
        val profile = parse(bundle(extraProfile = "\"flow\":\"xtls-rprx-vision\",")).profiles.single()
        assertEquals("xtls-rprx-vision", profile.flow)
    }

    @Test
    fun `vision is rejected without reality`() {
        val f = failure(bundle(security = "none", reality = "{}", extraProfile = "\"flow\":\"xtls-rprx-vision\","))
        assertTrue("expected BundleException, got $f", f != null)
        assertTrue(f!!.message!!.contains("flow"))
    }

    @Test
    fun `an unknown flow is rejected`() {
        val f = failure(bundle(extraProfile = "\"flow\":\"xtls-rprx-direct\","))
        assertTrue("expected BundleException, got $f", f != null)
    }

    @Test
    fun `the rendered outbound carries the flow on every user`() {
        val profile = parse(bundle(extraProfile = "\"flow\":\"xtls-rprx-vision\",")).profiles.single()
        val out = JSONObject(XrayConfig.render(profile, 1400, listOf("1.1.1.1")))
        val proxy = out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
        val user = proxy.getJSONObject("settings").getJSONArray("vnext")
            .getJSONObject(0).getJSONArray("users").getJSONObject(0)

        assertEquals("xtls-rprx-vision", user.getString("flow"))
    }

    // --- REALITY + fragment ---------------------------------------------------------

    @Test
    fun `reality together with a fragment mask is rejected`() {
        val extra = ""","finalmask":{"tcp":[{"type":"fragment","settings":{}}]}"""
        val f = failure(bundle(extraStream = extra))
        assertTrue("expected BundleException, got $f", f != null)
        assertTrue(f!!.message!!.contains("fragment"))
    }

    @Test
    fun `the renderer refuses to add a fragment to a reality profile`() {
        val profile = parse(bundle(extraProfile = "\"fingerprint\":\"chrome\",")).profiles.single()
        val stream = proxyStream(profile, TransportTuning(fragment = true))

        assertFalse("no finalmask may be emitted for reality", stream.has("finalmask"))
    }

    @Test
    fun `the renderer still adds a fragment to a non reality profile`() {
        val json = bundle(security = "none", reality = "{}", extraProfile = "\"fingerprint\":\"\",".replace("\"fingerprint\":\"\",", ""))
        val profile = parse(json).profiles.single()
        val stream = proxyStream(profile, TransportTuning(fragment = true))

        assertTrue("fragment must still work on plain profiles", stream.has("finalmask"))
    }

    @Test
    fun `the pure rule names the conflict`() {
        assertTrue(RealityRules.fragmentBreaksReality("reality"))
        assertFalse(RealityRules.fragmentBreaksReality("tls"))
        assertFalse(RealityRules.fragmentBreaksReality("none"))
    }

    private fun proxyStream(profile: ir.mohammad.vpn.data.Profile, tuning: TransportTuning = TransportTuning()): JSONObject {
        val out = JSONObject(XrayConfig.render(profile, 1400, listOf("1.1.1.1"), tuning = tuning))
        return out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
            .getJSONObject("streamSettings")
    }
}
