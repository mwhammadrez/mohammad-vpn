package ir.mohammad.vpn

import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC MTU-3 / MTU-4 / MTU-5 and catalog 105/106/115.
 *
 * The app cannot run `iptables`, so Xray's `sockopt` is the only place we can express an MSS clamp.
 * These assertions are the executable form of that decision.
 */
class XraySockoptTest {

    private fun profile(sockopt: JSONObject? = null): Profile {
        val stream = JSONObject().put("network", "ws").put("security", "none")
        if (sockopt != null) stream.put("sockopt", sockopt)
        return Profile(
            id = "p1",
            name = "A",
            protocol = "VLESS",
            core = "XRAY",
            host = "h.example",
            port = 443,
            xray = JSONObject()
                .put("protocol", "vless")
                .put("settings", JSONObject())
                .put("streamSettings", stream),
            awg = null,
        )
    }

    private fun sockoptOf(mtu: Int, profile: Profile = profile()): JSONObject {
        val root = JSONObject(XrayConfig.render(profile, mtu, listOf("1.1.1.1")))
        val outbound = root.getJSONArray("outbounds")
            .let { arr -> (0 until arr.length()).map { arr.getJSONObject(it) } }
            .first { it.optString("tag") == "proxy" }
        return outbound.getJSONObject("streamSettings").getJSONObject("sockopt")
    }

    @Test
    fun `tcpMaxSeg is mtu minus 40 capped at 1440`() {
        assertEquals(1360, sockoptOf(1400).getInt("tcpMaxSeg"))
        assertEquals(1440, sockoptOf(1500).getInt("tcpMaxSeg"))
        assertEquals(1160, sockoptOf(1200).getInt("tcpMaxSeg"))
    }

    @Test
    fun `tcpUserTimeout is bounded so a dead path is torn down`() {
        assertEquals(30_000, sockoptOf(1400).getInt("tcpUserTimeout"))
    }

    @Test
    fun `keepalive stays at 25 seconds idle and 15 seconds interval`() {
        val sockopt = sockoptOf(1400)
        assertEquals(25, sockopt.getInt("tcpKeepAliveIdle"))
        assertEquals(15, sockopt.getInt("tcpKeepAliveInterval"))
    }

    @Test
    fun `an explicit operator value is never overwritten`() {
        val explicit = JSONObject()
            .put("tcpMaxSeg", 1200)
            .put("tcpUserTimeout", 45_000)
            .put("tcpKeepAliveIdle", 60)
        val sockopt = sockoptOf(1400, profile(explicit))

        assertEquals(1200, sockopt.getInt("tcpMaxSeg"))
        assertEquals(45_000, sockopt.getInt("tcpUserTimeout"))
        assertEquals(60, sockopt.getInt("tcpKeepAliveIdle"))
        assertEquals("the untouched field still gets a default", 15, sockopt.getInt("tcpKeepAliveInterval"))
    }

    @Test
    fun `sockopt is emitted for every outbound protocol, not only vless`() {
        val trojan = Profile(
            id = "p2",
            name = "B",
            protocol = "TROJAN",
            core = "XRAY",
            host = "h.example",
            port = 443,
            xray = JSONObject().put("protocol", "trojan").put("settings", JSONObject()),
            awg = null,
        )
        val sockopt = sockoptOf(1400, trojan)
        assertEquals(1360, sockopt.getInt("tcpMaxSeg"))
        assertTrue(sockopt.has("tcpUserTimeout"))
    }
}
