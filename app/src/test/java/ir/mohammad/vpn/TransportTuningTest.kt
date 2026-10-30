package ir.mohammad.vpn

import ir.mohammad.vpn.core.TransportTuning
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportTuningTest {

    private fun profile(): Profile {
        val xray = JSONObject(
            """
            {"protocol":"vless","settings":{"vnext":[{"address":"h.example","port":443,
            "users":[{"id":"00000000-0000-0000-0000-000000000000","encryption":"none"}]}]},
            "streamSettings":{"network":"ws","security":"none","wsSettings":{"path":"/"}}}
            """.trimIndent(),
        )
        return Profile("p1", "A", "VLESS", "XRAY", "h.example", 443, xray, null)
    }

    @Test
    fun `fragment is absent by default`() {
        val json = JSONObject(XrayConfig.render(profile(), 1500, listOf("8.8.8.8")))
        val stream = json.getJSONArray("outbounds").getJSONObject(0).optJSONObject("streamSettings")
        assertFalse("no finalmask unless asked", stream?.has("finalmask") == true)
    }

    @Test
    fun `fragment is rendered when enabled`() {
        val tuning = TransportTuning(fragment = true, fragmentPackets = "tlshello")
        val json = JSONObject(XrayConfig.render(profile(), 1500, listOf("8.8.8.8"), tuning = tuning))
        val mask = json.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getJSONObject("finalmask")
        val fragment = mask.getJSONArray("tcp").getJSONObject(0)
        assertEquals("fragment", fragment.getString("type"))
        assertEquals("tlshello", fragment.getJSONObject("settings").getString("packets"))
        assertTrue(fragment.getJSONObject("settings").has("lengths"))
        assertTrue(fragment.getJSONObject("settings").has("delays"))
    }

    @Test
    fun `stream slicing mode keeps the write indices`() {
        val tuning = TransportTuning(fragment = true, fragmentPackets = "1-3")
        val json = JSONObject(XrayConfig.render(profile(), 1500, listOf("8.8.8.8"), tuning = tuning))
        val fragment = json.getJSONArray("outbounds").getJSONObject(0)
            .getJSONObject("streamSettings").getJSONObject("finalmask")
            .getJSONArray("tcp").getJSONObject(0)
        assertEquals("1-3", fragment.getJSONObject("settings").getString("packets"))
    }

    @Test
    fun `mux is absent by default`() {
        val json = JSONObject(XrayConfig.render(profile(), 1500, listOf("8.8.8.8")))
        assertFalse(json.getJSONArray("outbounds").getJSONObject(0).has("mux"))
    }

    @Test
    fun `mux is rendered with bounded concurrency`() {
        val tuning = TransportTuning(mux = true, muxConcurrency = 8, muxXudpConcurrency = 16)
        val json = JSONObject(XrayConfig.render(profile(), 1500, listOf("8.8.8.8"), tuning = tuning))
        val mux = json.getJSONArray("outbounds").getJSONObject(0).getJSONObject("mux")
        assertTrue(mux.getBoolean("enabled"))
        assertEquals(8, mux.getInt("concurrency"))
        assertEquals(16, mux.getInt("xudpConcurrency"))
        assertEquals("reject", mux.getString("xudpProxyUDP443"))
    }

    @Test
    fun `tuning never mutates the caller profile`() {
        val source = profile()
        val before = source.xray.toString()
        XrayConfig.render(source, 1500, listOf("8.8.8.8"), tuning = TransportTuning(fragment = true, mux = true))
        assertEquals(before, source.xray.toString())
    }
}
