package ir.mohammad.vpn

import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Iranian behaviour that must survive every refactor: domestic traffic never leaves
 * the country, QUIC is blocked, ads are blocked, everything else proxied.
 */
class IranRoutingTest {

    private fun profile() = Profile(
        "p1", "A", "VLESS", "XRAY", "h.example", 2095,
        JSONObject("""{"protocol":"vless","settings":{"vnext":[]},"streamSettings":{"network":"ws"}}"""),
        null,
    )

    private fun render(geo: Boolean) =
        JSONObject(XrayConfig.render(profile(), 1500, listOf("8.8.8.8"), geo = geo))

    private fun rules(json: JSONObject) = json.getJSONObject("routing").getJSONArray("rules")

    /** DNS entries are either a bare string or an object with an address. */
    private fun dnsEntries(json: JSONObject): List<JSONObject?> {
        val servers = json.getJSONObject("dns").getJSONArray("servers")
        return (0 until servers.length()).map { servers.optJSONObject(it) }
    }

    @Test
    fun `domestic domains resolve through the phone resolver`() {
        val domestic = dnsEntries(render(true))
            .firstOrNull { it?.optString("address") == "localhost" }
        assertNotNull("a domain:ir-scoped resolver must exist", domestic)
        assertEquals("domain:ir", domestic!!.getJSONArray("domains").getString(0))
        assertTrue(domestic.getBoolean("skipFallback"))
    }

    @Test
    fun `domestic resolver is absent without geo assets`() {
        val hasLocalhost = dnsEntries(render(false))
            .any { it?.optString("address") == "localhost" }
        assertFalse("localhost is only meaningful with geosite data", hasLocalhost)
    }

    @Test
    fun `iran is routed direct before the catch-all`() {
        val rules = rules(render(true))
        val directIr = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .firstOrNull { rule ->
                rule.optString("outboundTag") == "direct" &&
                    rule.optJSONArray("domain")?.let { d ->
                        (0 until d.length()).any { d.getString(it) == "domain:ir" }
                    } == true
            }
        assertNotNull("Iranian domains must go direct", directIr)
        assertEquals("geoip:ir", directIr!!.getJSONArray("ip").getString(0))

        val irIndex = (0 until rules.length()).indexOfFirst { rules.getJSONObject(it) === directIr }
        val catchAll = (0 until rules.length()).last { rules.getJSONObject(it).optString("outboundTag") == "proxy" }
        assertTrue("direct rule must precede the proxy catch-all", irIndex < catchAll)
    }

    @Test
    fun `quic is blocked first`() {
        val first = rules(render(true)).getJSONObject(0)
        assertEquals("block", first.getString("outboundTag"))
        assertEquals("udp", first.getString("network"))
        assertEquals("443", first.getString("port"))
    }

    @Test
    fun `without geo assets the fallback still sends iranian domains direct`() {
        val rules = rules(render(false))
        val direct = (0 until rules.length())
            .map { rules.getJSONObject(it) }
            .first { it.optString("outboundTag") == "direct" }
        val domains = direct.getJSONArray("domain")
        assertTrue((0 until domains.length()).any { domains.getString(it) == "domain:ir" })
    }
}
