package ir.mohammad.vpn

import ir.mohammad.vpn.ui.AppMode
import ir.mohammad.vpn.ui.DnsMode
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

    @Test
    fun `an unknown dns mode falls back to proxy`() {
        assertEquals(DnsMode.PROXY, DnsMode.from(null))
        assertEquals(DnsMode.PROXY, DnsMode.from("carrier-pigeon"))
        assertEquals(DnsMode.VPN, DnsMode.from("vpn"))
    }
}
