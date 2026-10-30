package ir.mohammad.vpn

import ir.mohammad.vpn.core.TunPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunPlanTest {

    @Test
    fun planCarriesBothFamiliesWithoutADefaultRoute() {
        val plan = TunPlan.build(mtu = 1500, dns = listOf("8.8.8.8"))
        assertEquals(1500, plan.mtu)
        assertEquals("10.10.10.1", plan.ipv4Address)
        assertEquals(30, plan.ipv4Prefix)
        assertFalse(
            "a default route would swallow LAN traffic",
            plan.ipv4Routes.any { it.first == "0.0.0.0" && it.second == 0 },
        )
        assertTrue("IPv6 address required", plan.ipv6Address.isNotBlank())
        assertTrue("IPv6 default route required", plan.ipv6Routes.any { it.first == "::" && it.second == 0 })
    }

    @Test
    fun privateAndReservedRangesStayOutsideTheTunnel() {
        val routes = TunPlan.build(1500, listOf("8.8.8.8")).ipv4Routes
        val excluded = listOf(
            "10.0.0.0" to 8,
            "127.0.0.0" to 8,
            "169.254.0.0" to 16,
            "172.16.0.0" to 12,
            "192.168.0.0" to 16,
            "224.0.0.0" to 4,
        )
        excluded.forEach { (network, prefix) ->
            assertFalse("$network/$prefix must not be routed into the tunnel", (network to prefix) in routes)
        }
        assertEquals(
            "duplicate routes waste table space",
            routes.size,
            routes.toSet().size,
        )
    }

    @Test
    fun publicSpaceIsCovered() {
        val routes = TunPlan.build(1500, listOf("8.8.8.8")).ipv4Routes
        listOf("1.1.1.1", "8.8.8.8", "93.184.216.34", "172.217.0.1", "203.0.113.9").forEach { address ->
            assertTrue("$address must be routed", covers(routes, address))
        }
    }

    @Test
    fun ipv6IsDisabledOnlyWhenAsked() {
        val plan = TunPlan.build(1500, listOf("8.8.8.8"), ipv6 = false)
        assertEquals("", plan.ipv6Address)
        assertTrue(plan.ipv6Routes.isEmpty())
    }

    @Test
    fun dnsIsCarriedThrough() {
        val plan = TunPlan.build(1500, listOf("8.8.8.8"))
        assertEquals(listOf("8.8.8.8"), plan.dns)
    }

    private fun covers(routes: List<Pair<String, Int>>, address: String): Boolean {
        val target = ipToLong(address)
        return routes.any { (network, prefix) ->
            val mask = if (prefix == 0) 0L else (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
            (ipToLong(network) and mask) == (target and mask)
        }
    }

    private fun ipToLong(address: String): Long =
        address.split(".").fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
}
