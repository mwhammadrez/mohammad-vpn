package ir.mohammad.vpn

import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoutingRulesTest {

    private fun profile(): Profile {
        val xray = JSONObject(
            """{"protocol":"vless","settings":{"vnext":[]},"streamSettings":{"network":"ws","security":"none"}}""",
        )
        return Profile("p1", "A", "VLESS", "XRAY", "h.example", 443, xray, null)
    }

    private fun render(): JSONObject = JSONObject(
        XrayConfig.render(profile(), "10.0.0.1", 1500, listOf("1.1.1.1"), logLevel = "warning", ipv6 = true),
    )

    @Test
    fun lanTrafficGoesDirect() {
        val rules = render().getJSONObject("routing").getJSONArray("rules")
        var found = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            if (rule.optString("outboundTag") != "direct") continue
            val ips = rule.optJSONArray("ip") ?: continue
            for (j in 0 until ips.length()) {
                if (ips.getString(j) == "geoip:private" || ips.getString(j).startsWith("192.168.")) found = true
            }
        }
        assertTrue("expected a direct rule for private address space", found)
    }

    @Test
    fun domainStrategySupportsDomainRouting() {
        val routing = render().getJSONObject("routing")
        assertTrue(
            "domainStrategy must enable domain matching",
            routing.getString("domainStrategy") in setOf("IPIfNonMatch", "IPOnDemand"),
        )
    }

    @Test
    fun dnsObjectMatchesTunDns() {
        val dns = render().getJSONObject("dns")
        val servers = dns.getJSONArray("servers")
        assertEquals("1.1.1.1", servers.getString(0))
    }

    @Test
    fun statsAndPolicyEnableCounters() {
        val root = render()
        assertTrue(root.has("stats"))
        val policy = root.getJSONObject("policy").getJSONObject("system")
        assertTrue(policy.getBoolean("statsOutboundUplink"))
        assertTrue(policy.getBoolean("statsOutboundDownlink"))
    }

    @Test
    fun logLevelIsRespected() {
        assertEquals("warning", render().getJSONObject("log").getString("loglevel"))
    }

    @Test
    fun geoModeBlocksAdsAndRoutesIranDirect() {
        val json = JSONObject(
            XrayConfig.render(profile(), "10.0.0.1", 1500, listOf("1.1.1.1"), logLevel = "warning", ipv6 = false, geo = true),
        )
        val rules = json.getJSONObject("routing").getJSONArray("rules")
        val tags = (0 until rules.length()).map { rules.getJSONObject(it).optString("outboundTag") }
        assertEquals("block", tags.first())
        assertTrue("expect a direct rule for iran/private", tags.count { it == "direct" } >= 2)
        assertEquals("proxy", tags.last())

        var iranFound = false
        var adsFound = false
        for (i in 0 until rules.length()) {
            val rule = rules.getJSONObject(i)
            val domains = rule.optJSONArray("domain") ?: continue
            for (j in 0 until domains.length()) {
                when (domains.getString(j)) {
                    "domain:ir" -> iranFound = true
                    "geosite:category-ads-all" -> adsFound = true
                }
            }
        }
        assertTrue("iran rule missing", iranFound)
        assertTrue("ads rule missing", adsFound)
    }

    @Test
    fun withoutGeoAssetsNoGeositeSelectorsAreEmitted() {
        val rules = render().getJSONObject("routing").getJSONArray("rules")
        for (i in 0 until rules.length()) {
            val domains = rules.getJSONObject(i).optJSONArray("domain") ?: continue
            for (j in 0 until domains.length()) {
                assertTrue(
                    "geosite selector without assets would break startup: ${domains.getString(j)}",
                    !domains.getString(j).startsWith("geosite:"),
                )
            }
        }
    }

    @Test
    fun catchAllRuleRemainsLast() {
        val rules = render().getJSONObject("routing").getJSONArray("rules")
        val last = rules.getJSONObject(rules.length() - 1)
        assertEquals("proxy", last.getString("outboundTag"))
        assertEquals("tcp,udp", last.getString("network"))
    }
}
