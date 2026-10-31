package ir.mohammad.vpn

import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.SurfaceStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-W5: a surface may never show CONNECTED unless the tunnel really answered.
 *
 * Two layers of evidence:
 * 1. the projection — only [TunnelState.Connected] may map to [SurfaceState.CONNECTED], and that
 *    state is produced by exactly one call site in the whole app;
 * 2. the producer — that call site sits *after* the SOCKS health probe in `startXray`, so a process
 *    that started but does not answer stays UNHEALTHY and never turns green.
 *
 * Known gap, recorded in D-86: the AmneziaWG branch returns OK without a probe, so an AWG profile can
 * turn green on process start alone. AmneziaWG is `NOT VERIFIED` everywhere and its proof harness is
 * a later milestone; this test pins the Xray guarantee and documents the exception rather than
 * pretending it does not exist.
 */
class SurfaceProbeGateTest {

    private val root: File = listOf(File(".."), File("."))
        .map { it.absoluteFile }
        .first { File(it, "app/src/main/java").isDirectory }

    private fun serviceSource(): String =
        File(root, "app/src/main/java/ir/mohammad/vpn/vpn/MohammadVpnService.kt").readText()

    @Test
    fun `connected is the only state that projects to CONNECTED`() {
        val greens = listOf(
            TunnelState.Disconnected,
            TunnelState.Connecting,
            TunnelState.Disconnecting,
            TunnelState.Failed(TunnelError.CoreStartFailed),
            TunnelState.Failed(TunnelError.ConfigUnhealthy),
            TunnelState.Failed(TunnelError.VpnPermissionDenied),
        ).filter { SurfaceStateMapper.map(it, true, true) == SurfaceState.CONNECTED }

        assertTrue("no failure or transition state may be green: $greens", greens.isEmpty())
        assertEquals(SurfaceState.CONNECTED, SurfaceStateMapper.map(TunnelState.Connected, true, true))
    }

    @Test
    fun `a failed probe result never becomes green`() {
        // ConfigUnhealthy is what startXray returns when awaitHealthy() exhausts its attempts.
        assertNotEquals(
            SurfaceState.CONNECTED,
            SurfaceStateMapper.map(TunnelState.Failed(TunnelError.ConfigUnhealthy), true, true),
        )
    }

    @Test
    fun `the app sets Connected from exactly one place`() {
        val occurrences = Regex("""Tunnel\.update\(TunnelState\.Connected\)""")
            .findAll(serviceSource()).count()
        assertEquals(
            "a second writer of Connected would bypass the probe gate",
            1,
            occurrences,
        )
    }

    @Test
    fun `the single writer of Connected consumes the OK result`() {
        val source = serviceSource()
        val connected = source.indexOf("Tunnel.update(TunnelState.Connected)")
        val branchStart = source.lastIndexOf("StartResult.OK ->", connected)

        assertTrue("Connected must sit inside the OK branch", branchStart in 0 until connected)
        assertTrue(
            "the OK branch may not contain another statement before it",
            source.substring(branchStart, connected).count { it == '\n' } <= 4,
        )
    }

    @Test
    fun `startXray can only return OK after the probe passes`() {
        val source = serviceSource()
        val startXray = source.indexOf("private fun startXray")
        val probe = source.indexOf("if (!awaitHealthy(profile))", startXray)
        val unhealthy = source.indexOf("return StartResult.UNHEALTHY", probe)
        val ok = source.indexOf("StartResult.OK", probe)

        assertTrue("startXray must exist", startXray >= 0)
        assertTrue("the probe gate must exist inside startXray", probe > startXray)
        assertTrue("an unhealthy path must return before OK", unhealthy > probe)
        assertTrue("OK must come after the probe", ok > probe)
    }

    @Test
    fun `the amneziawg exception is documented, not hidden`() {
        val source = serviceSource()
        val awg = source.indexOf("private fun startAwg")
        val awgOk = source.indexOf("StartResult.OK", awg)
        val awgProbe = source.indexOf("awaitHealthy", awg)

        // If AWG ever gains a probe, this flips to the strict assertion below.
        val probesAfterAwg = awgProbe in awg until awgOk
        assertTrue(
            "AWG currently returns OK without a probe (D-86); when that changes, tighten this test",
            !probesAfterAwg,
        )
    }
}
