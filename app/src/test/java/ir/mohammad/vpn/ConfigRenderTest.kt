package ir.mohammad.vpn

import ir.mohammad.vpn.core.AwgConfig
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRenderTest {

    private fun vlessProfile(): Profile {
        val xray = JSONObject(
            """{"protocol":"vless","settings":{"vnext":[{"address":"h.example","port":443,
                 "users":[{"id":"0722d93a-024a-489d-be2a-a2e83a1f39b7","encryption":"none"}]}]},
                 "streamSettings":{"network":"ws","security":"none",
                 "wsSettings":{"path":"/","headers":{"Host":"h.example"}}}}""",
        )
        return Profile("p1", "A", "VLESS", "XRAY", "h.example", 443, xray, null)
    }

    @Test
    fun xrayConfigHasTunInboundAndProxyOutbound() {
        val json = JSONObject(XrayConfig.render(vlessProfile(), 1400, listOf("1.1.1.1")))
        val inbound = json.getJSONArray("inbounds").getJSONObject(0)
        assertEquals("socks", inbound.getString("protocol"))
        assertEquals(10808, inbound.getInt("port"))
        assertEquals("127.0.0.1", inbound.getString("listen"))

        val outbounds = json.getJSONArray("outbounds")
        assertEquals("proxy", outbounds.getJSONObject(0).getString("tag"))
        assertEquals("vless", outbounds.getJSONObject(0).getString("protocol"))
        assertEquals("direct", outbounds.getJSONObject(1).getString("tag"))
        assertEquals("freedom", outbounds.getJSONObject(1).getString("protocol"))
        assertEquals("block", outbounds.getJSONObject(2).getString("tag"))
        assertEquals("blackhole", outbounds.getJSONObject(2).getString("protocol"))

        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val catchAll = rules.getJSONObject(rules.length() - 1)
        assertEquals("proxy", catchAll.getString("outboundTag"))
        assertEquals("tcp,udp", catchAll.getString("network"))
    }

    @Test
    fun awgConfigKeepsBase64KeysAndDefaults() {
        val awg = JSONObject(
            """{"privateKey":"cHJpdmF0ZQ==","peers":[{"publicKey":"cHVibGlj","endpoint":"h:51820",
                 "allowedIPs":["0.0.0.0/0"],"persistentKeepalive":25}],
                 "obf":{"jc":4,"jmin":40,"jmax":70}}""",
        )
        val profile = Profile("w1", "W", "AMNEZIAWG", "AMNEZIAWG", "h", 51820, null, awg)
        val out = JSONObject(AwgConfig.render(profile, 1400))
        assertEquals("cHJpdmF0ZQ==", out.getString("privateKey"))
        assertEquals(1400, out.getInt("mtu"))
        assertEquals(1, out.getJSONArray("peers").length())
        assertEquals(4, out.getJSONObject("obf").getInt("jc"))
    }

    @Test(expected = IllegalStateException::class)
    fun awgWithoutPeersFails() {
        val awg = JSONObject("""{"privateKey":"x","peers":[]}""")
        val profile = Profile("w1", "W", "AMNEZIAWG", "AMNEZIAWG", "h", 51820, null, awg)
        AwgConfig.render(profile, 1400)
    }

    @Test
    fun renderedXrayConfigIsValidJson() {
        val text = XrayConfig.render(vlessProfile(), 1400, listOf("1.1.1.1", "8.8.8.8"))
        assertTrue(text.startsWith("{"))
        JSONObject(text) // throws if malformed
    }
}
