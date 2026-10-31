package ir.mohammad.vpn

import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.ui.AppMode
import ir.mohammad.vpn.ui.DnsMode
import ir.mohammad.vpn.ui.profilesFor
import ir.mohammad.vpn.ui.resolveSelection
import org.junit.Assert.assertEquals
import org.junit.Test

/** The mode is persisted as a string; an unknown or missing value must land on the safe default. */
class AppModeTest {

    @Test
    fun `an unknown stored mode falls back to Mode 1`() {
        assertEquals(AppMode.XRAY, AppMode.from(null))
        assertEquals(AppMode.XRAY, AppMode.from(""))
        assertEquals(AppMode.XRAY, AppMode.from("something-new"))
    }

    @Test
    fun `stored keys round-trip`() {
        AppMode.entries.forEach { assertEquals(it, AppMode.from(it.storageKey)) }
        DnsMode.entries.forEach { assertEquals(it, DnsMode.from(it.storageKey)) }
    }

    private fun profile(id: String, core: String) = Profile(
        id = id, name = id, protocol = if (core == "DNS") "DNS" else "VLESS", core = core,
        host = "h.example", port = 443, xray = null, awg = null,
    )

    @Test
    fun `each mode holds its own kind of profile`() {
        val all = listOf(profile("a", "XRAY"), profile("d", "DNS"), profile("b", "XRAY"))
        assertEquals(listOf("a", "b"), profilesFor(AppMode.XRAY, all).map { it.id })
        assertEquals(listOf("d"), profilesFor(AppMode.DNS, all).map { it.id })
    }

    @Test
    fun `selection follows the mode and falls back to the first usable profile`() {
        val all = listOf(profile("a", "XRAY"), profile("d", "DNS"))
        assertEquals("a", resolveSelection(AppMode.XRAY, all, null)?.id)
        assertEquals("d", resolveSelection(AppMode.DNS, all, null)?.id)
        // A selection from the other mode is ignored, not carried over.
        assertEquals("d", resolveSelection(AppMode.DNS, all, all[0])?.id)
        assertEquals(null, resolveSelection(AppMode.DNS, listOf(all[0]), all[0]))
    }

    @Test
    fun `an unknown dns mode falls back to proxy`() {
        assertEquals(DnsMode.PROXY, DnsMode.from(null))
        assertEquals(DnsMode.PROXY, DnsMode.from("carrier-pigeon"))
        assertEquals(DnsMode.VPN, DnsMode.from("vpn"))
    }
}
