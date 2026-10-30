package ir.mohammad.vpn

import ir.mohammad.vpn.diagnostics.EgressVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EgressVerdictTest {

    @Test
    fun changedEgressMeansTunnelCarriesTraffic() {
        val v = EgressVerdict.of(baseline = "5.6.7.8", current = "1.2.3.4")
        assertTrue(v.changed)
        assertEquals("tunnel", v.conclusion)
    }

    @Test
    fun identicalEgressMeansNoTunnel() {
        val v = EgressVerdict.of("5.6.7.8", "5.6.7.8")
        assertFalse(v.changed)
        assertEquals("no-tunnel", v.conclusion)
    }

    @Test
    fun missingBaselineIsInconclusive() {
        val v = EgressVerdict.of(null, "1.2.3.4")
        assertFalse(v.changed)
        assertEquals("inconclusive", v.conclusion)
    }

    @Test
    fun failedFetchIsInconclusive() {
        val v = EgressVerdict.of("5.6.7.8", null)
        assertFalse(v.changed)
        assertEquals("inconclusive", v.conclusion)
    }

    @Test
    fun ipv6OnlyEgressFlaggedAsPotentialLeak() {
        val v = EgressVerdict.of("5.6.7.8", "1.2.3.4", ipv6Egress = "2001:db8::1", ipv4SameAsBaseline = true)
        assertTrue(v.possibleIpv6Leak)
    }
}
