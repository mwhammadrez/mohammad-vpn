package ir.mohammad.vpn

import ir.mohammad.vpn.core.MtuPolicy
import ir.mohammad.vpn.core.MtuSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC MTU-1 / MTU-7. Pure policy, no Android types: the resolution order must be provable without
 * a device, because the device cannot tell us which value a bundle *would* have produced.
 */
class MtuPolicyTest {

    @Test
    fun `profile wins over bundle and default`() {
        val r = MtuPolicy.resolve(profileMtu = 1390, bundleMtu = 1450)
        assertEquals(1390, r.mtu)
        assertEquals(MtuSource.PROFILE, r.source)
    }

    @Test
    fun `bundle wins when the profile does not set one`() {
        val r = MtuPolicy.resolve(profileMtu = null, bundleMtu = 1350)
        assertEquals(1350, r.mtu)
        assertEquals(MtuSource.BUNDLE, r.source)
    }

    @Test
    fun `default is 1400 when nothing is configured`() {
        val r = MtuPolicy.resolve(profileMtu = null, bundleMtu = null)
        assertEquals(1400, r.mtu)
        assertEquals(MtuSource.DEFAULT, r.source)
        assertEquals(1400, MtuPolicy.DEFAULT_MTU)
    }

    @Test
    fun `the whole 1200 to 1500 range is valid and nothing outside it`() {
        assertTrue(MtuPolicy.isValid(1200))
        assertTrue(MtuPolicy.isValid(1400))
        assertTrue(MtuPolicy.isValid(1500))
        assertFalse(MtuPolicy.isValid(1199))
        assertFalse(MtuPolicy.isValid(1501))
        assertFalse(MtuPolicy.isValid(0))
        assertFalse(MtuPolicy.isValid(-1400))
    }

    @Test
    fun `an out of range value is refused rather than silently replaced`() {
        val failure = runCatching { MtuPolicy.resolve(profileMtu = 900, bundleMtu = 1400) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)

        val failure2 = runCatching { MtuPolicy.resolve(profileMtu = null, bundleMtu = 1501) }.exceptionOrNull()
        assertTrue("expected IllegalArgumentException, got $failure2", failure2 is IllegalArgumentException)
    }

    @Test
    fun `segment size is mtu minus headers capped at 1440`() {
        assertEquals(1360, MtuPolicy.segmentSize(1400))
        assertEquals(1440, MtuPolicy.segmentSize(1500))
        assertEquals(1160, MtuPolicy.segmentSize(1200))
    }

    @Test
    fun `diagnostic line names the source and leaks no endpoint`() {
        val line = MtuPolicy.diagnostic(MtuPolicy.resolve(profileMtu = 1390, bundleMtu = 1450))

        assertTrue("must start with the stable prefix", line.startsWith("mtu resolved: configured=1390"))
        assertTrue("must carry the source", line.contains("source=profile"))
        assertTrue("must carry the effective value", line.contains("effective=1390"))
        assertFalse("must not contain a host", line.contains("h.example"))
        assertFalse("must not contain an address", line.contains("10.10."))
    }

    @Test
    fun `diagnostic labels the default branch distinctly`() {
        val line = MtuPolicy.diagnostic(MtuPolicy.resolve(profileMtu = null, bundleMtu = null))
        assertEquals("mtu resolved: configured=1400 effective=1400 source=default", line)
    }
}
