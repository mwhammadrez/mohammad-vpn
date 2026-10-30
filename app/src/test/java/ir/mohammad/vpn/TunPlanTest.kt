package ir.mohammad.vpn

import ir.mohammad.vpn.core.TunPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TunPlanTest {

    @Test
    fun planCarriesBothFamiliesAndDefaultRoutes() {
        val plan = TunPlan.build(mtu = 1500, dns = listOf("1.1.1.1"), index = 0)
        assertEquals(1500, plan.mtu)
        assertEquals("10.0.0.1", plan.ipv4Address)
        assertEquals(30, plan.ipv4Prefix)
        assertTrue(plan.ipv4Routes.any { it.first == "0.0.0.0" && it.second == 0 })
        assertTrue("IPv6 address required", plan.ipv6Address.isNotBlank())
        assertTrue("IPv6 default route required", plan.ipv6Routes.any { it.first == "::" && it.second == 0 })
    }

    @Test
    fun addressPoolIsDeterministicAndDistinct() {
        val a = TunPlan.build(1500, listOf("1.1.1.1"), 0)
        val b = TunPlan.build(1500, listOf("1.1.1.1"), 1)
        assertEquals(a.ipv4Address, TunPlan.build(1500, listOf("1.1.1.1"), 0).ipv4Address)
        assertTrue(a.ipv4Address != b.ipv4Address)
    }

    @Test
    fun indexWrapsInsteadOfCrashing() {
        val plan = TunPlan.build(1500, listOf("1.1.1.1"), 99)
        assertTrue(plan.ipv4Address.isNotBlank())
    }

    @Test
    fun ipv6IsDisabledOnlyWhenAsked() {
        val plan = TunPlan.build(1500, listOf("1.1.1.1"), 0, ipv6 = false)
        assertEquals("", plan.ipv6Address)
        assertTrue(plan.ipv6Routes.isEmpty())
    }

    @Test
    fun dnsIsCarriedThrough() {
        val plan = TunPlan.build(1500, listOf("9.9.9.9", "1.1.1.1"), 0)
        assertEquals(listOf("9.9.9.9", "1.1.1.1"), plan.dns)
    }
}
