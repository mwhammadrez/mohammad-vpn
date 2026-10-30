package ir.mohammad.vpn

import ir.mohammad.vpn.diagnostics.SelfTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The IPv6 probe must never report an IPv4 answer as an IPv6 egress. */
class SelfTestFamilyTest {

    @Test
    fun `ipv6 literals are recognised`() {
        assertTrue(SelfTest.isIpv6Literal("2606:4700:4700::1111"))
        assertTrue(SelfTest.isIpv6Literal("2001:db8::1"))
    }

    @Test
    fun `ipv4 answers are rejected`() {
        assertFalse(SelfTest.isIpv6Literal("95.135.125.40"))
        assertFalse(SelfTest.isIpv6Literal("1.1.1.1"))
    }

    @Test
    fun `ipv4-mapped addresses are not treated as native ipv6`() {
        assertFalse(SelfTest.isIpv6Literal("::ffff:95.135.125.40"))
    }
}
