package ir.mohammad.vpn

import ir.mohammad.vpn.core.FinalMaskRules
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.BundleException
import ir.mohammad.vpn.data.OperatorBundleLoader
import ir.mohammad.vpn.data.Profile
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `finalMask` carries the `merge` mask added in `core-go/patches/0003`.
 *
 * The core accepts `merge` only on `tcp`, and an unknown type stops the core at startup. Every case
 * that would stop the core is rejected here instead, at bundle load, where the operator can see why.
 */
class FinalMaskConfigTest {

    private fun mergeMask(times: Int = 1, delayMs: Int = 30, maxBytes: Int = 16384) = JSONObject()
        .put("tcp", JSONArray().put(
            JSONObject().put("type", "merge").put(
                "settings",
                JSONObject().put("times", times).put("delayMs", delayMs).put("maxBytes", maxBytes),
            ),
        ))

    private fun profile(mask: JSONObject? = null, streamHasMask: Boolean = false): Profile {
        val stream = JSONObject().put("network", "ws").put("security", "none")
        if (streamHasMask) stream.put("finalMask", JSONObject().put("tcp", JSONArray()))
        return Profile(
            id = "p1", name = "A", protocol = "VLESS", core = "XRAY",
            host = "h.example", port = 443,
            xray = JSONObject()
                .put("protocol", "vless")
                .put("settings", JSONObject())
                .put("streamSettings", stream),
            awg = null,
            finalMask = mask,
        )
    }

    private fun stream(p: Profile): JSONObject {
        val out = JSONObject(XrayConfig.render(p, 1400, listOf("1.1.1.1")))
        return out.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
            .getJSONObject("streamSettings")
    }

    @Test
    fun `a merge mask reaches the core`() {
        val rendered = stream(profile(mergeMask())) .getJSONObject("finalMask")
        val tcp = rendered.getJSONArray("tcp").getJSONObject(0)
        assertEquals("merge", tcp.getString("type"))
        assertEquals(1, tcp.getJSONObject("settings").getInt("times"))
        assertEquals(30, tcp.getJSONObject("settings").getInt("delayMs"))
    }

    @Test
    fun `a mask already in the transport JSON is not overwritten`() {
        val rendered = stream(profile(mergeMask(), streamHasMask = true))
        assertEquals(0, rendered.getJSONObject("finalMask").getJSONArray("tcp").length())
    }

    @Test
    fun `no mask means no key`() {
        assertFalse(stream(profile()).has("finalMask"))
    }

    @Test
    fun `merge on udp is refused because the core has no such mask`() {
        val bad = JSONObject().put(
            "udp",
            JSONArray().put(JSONObject().put("type", "merge")),
        )
        assertTrue(FinalMaskRules.validate(bad) != null)
    }

    @Test
    fun `an unknown type is refused`() {
        val bad = JSONObject().put(
            "tcp",
            JSONArray().put(JSONObject().put("type", "teleport")),
        )
        assertTrue(FinalMaskRules.validate(bad) != null)
    }

    @Test
    fun `an unknown section is refused`() {
        assertTrue(FinalMaskRules.validate(JSONObject().put("sctp", JSONArray())) != null)
    }

    @Test
    fun `merge numbers are bounded`() {
        assertNull(FinalMaskRules.validate(mergeMask()))
        assertTrue(FinalMaskRules.validate(mergeMask(times = 101)) != null)
        assertTrue(FinalMaskRules.validate(mergeMask(times = -1)) != null)
        assertTrue(FinalMaskRules.validate(mergeMask(delayMs = 5001)) != null)
        assertTrue(FinalMaskRules.validate(mergeMask(delayMs = -1)) != null)
        assertTrue(FinalMaskRules.validate(mergeMask(maxBytes = (1 shl 20) + 1)) != null)
    }

    @Test
    fun `a stock mask type passes`() {
        val ok = JSONObject()
            .put("tcp", JSONArray().put(JSONObject().put("type", "fragment")))
            .put("udp", JSONArray().put(JSONObject().put("type", "noise")))
        assertNull(FinalMaskRules.validate(ok))
    }

    @Test
    fun `reality refuses a profile-level fragment but accepts merge`() {
        fun reality(fragment: Boolean): String {
            val mask = if (fragment) {
                JSONObject().put("tcp", JSONArray().put(JSONObject().put("type", "fragment")))
            } else {
                mergeMask()
            }
            return """
                {"schemaVersion":1,"profiles":[
                  {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
                   "fingerprint":"chrome","finalMask":$mask,
                   "xray":{"protocol":"vless","settings":{},
                     "streamSettings":{"network":"tcp","security":"reality",
                       "realitySettings":{"serverName":"a.example","publicKey":"${"A".repeat(43)}",
                         "shortId":"ab12","fingerprint":"chrome"}}}}
                ]}
            """.trimIndent()
        }
        val withFragment = runCatching { OperatorBundleLoader.parse(reality(true)) }.exceptionOrNull()
        assertTrue("expected BundleException, got $withFragment", withFragment is BundleException)

        val withMerge = runCatching { OperatorBundleLoader.parse(reality(false)) }.exceptionOrNull()
        assertNull("merge must be allowed with reality, got $withMerge", withMerge)
    }

    @Test
    fun `unsafe is accepted for tls and refused for reality`() {
        fun bundle(security: String, fingerprint: String): String {
            val settings = if (security == "reality") {
                ""","realitySettings":{"serverName":"a.example","publicKey":"${"A".repeat(43)}","shortId":"ab12","fingerprint":"$fingerprint"}"""
            } else {
                ""","tlsSettings":{"fingerprint":"$fingerprint"}"""
            }
            return """
                {"schemaVersion":1,"profiles":[
                  {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
                   "xray":{"protocol":"vless","settings":{},
                     "streamSettings":{"network":"tcp","security":"$security"$settings}}}
                ]}
            """.trimIndent()
        }
        assertNull(runCatching { OperatorBundleLoader.parse(bundle("tls", "unsafe")) }.exceptionOrNull())
        assertTrue(
            runCatching { OperatorBundleLoader.parse(bundle("reality", "unsafe")) }.exceptionOrNull()
                is BundleException,
        )
    }

    @Test
    fun `an invalid mask rejects the bundle`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "finalMask":{"tcp":[{"type":"nope"}]},
               "xray":{"protocol":"vless","settings":{}}}
            ]}
        """.trimIndent()
        val failure = runCatching { OperatorBundleLoader.parse(json) }.exceptionOrNull()
        assertTrue("expected BundleException, got $failure", failure is BundleException)
    }

    @Test
    fun `a valid mask survives parsing`() {
        val json = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
               "finalMask":{"tcp":[{"type":"merge","settings":{"times":2,"delayMs":20}}]},
               "xray":{"protocol":"vless","settings":{}}}
            ]}
        """.trimIndent()
        val mask = OperatorBundleLoader.parse(json).profiles.single().finalMask!!
        assertEquals("merge", mask.getJSONArray("tcp").getJSONObject(0).getString("type"))
    }
}
