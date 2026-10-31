package ir.mohammad.vpn

import ir.mohammad.vpn.data.ByteFormat
import ir.mohammad.vpn.data.TelemetryCounter
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.Tunnel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * AC-N9, AC-N10, and the formatting rules from the brief.
 *
 * The counters are cumulative in the core; everything the UI shows is a delta over an interval. The
 * two failure modes this pins down: swapped directions, and a negative first sample after a reconnect.
 */
class TelemetryCounterTest {

    @After
    fun tearDown() {
        Tunnel.trafficUnavailable()
    }

    @Test
    fun `the first sample is a baseline, not traffic`() {
        val c = TelemetryCounter()
        assertEquals(0L to 0L, c.delta(up = 10_000, down = 50_000))
    }

    @Test
    fun `deltas are separate and not swapped`() {
        val c = TelemetryCounter()
        c.delta(0, 0)

        assertEquals(1_000L to 5_000L, c.delta(up = 1_000, down = 5_000))
        assertEquals(500L to 0L, c.delta(up = 1_500, down = 5_000))
        assertEquals(0L to 2_500L, c.delta(up = 1_500, down = 7_500))
    }

    @Test
    fun `a counter reset never produces negative traffic`() {
        val c = TelemetryCounter()
        c.delta(1_000_000, 5_000_000)

        // The core restarted: counters are lower than before.
        assertEquals(0L to 0L, c.delta(up = 0, down = 0))
        assertEquals(400L to 800L, c.delta(up = 400, down = 800))
    }

    @Test
    fun `reset re-primes the baseline`() {
        val c = TelemetryCounter()
        c.delta(100, 200)
        c.reset()
        assertEquals("no spike after a reconnect", 0L to 0L, c.delta(up = 9_999, down = 9_999))
    }

    @Test
    fun `a new connection clears the totals and marks the source available`() {
        Tunnel.updateTraffic(up = 12_345, down = 67_890)
        val before = Tunnel.traffic.value
        assertTrue(before.available)
        assertEquals(12_345L, before.up)

        Tunnel.resetTraffic()
        val after = Tunnel.traffic.value
        assertTrue("a fresh session still has a live source", after.available)
        assertEquals(0L, after.up)
        assertEquals(0L, after.down)
    }

    @Test
    fun `an unavailable source is not reported as zero bytes`() {
        Tunnel.trafficUnavailable()
        val snapshot = Tunnel.traffic.value
        assertFalse(snapshot.available)
        assertEquals("—", ByteFormat.format(snapshot, downlink = true))
        assertEquals("—", ByteFormat.format(snapshot, downlink = false))
    }

    @Test
    fun `formatting picks a unit that fits the magnitude`() {
        assertEquals("512 B", ByteFormat.format(512, Locale.US))
        assertEquals("1.0 KB", ByteFormat.format(1024, Locale.US))
        assertEquals("1.0 MB", ByteFormat.format(1_048_576, Locale.US))
        assertEquals("1.20 GB", ByteFormat.format(1_288_490_188, Locale.US))
    }

    @Test
    fun `the value split keeps unit labels short`() {
        assertEquals("1.0" to "KB", ByteFormat.split(1024, Locale.US))
        assertEquals("340.0" to "MB", ByteFormat.split(340L * 1_048_576, Locale.US))
        assertEquals("0" to "B", ByteFormat.split(0, Locale.US))
    }

    @Test
    fun `a snapshot formats each direction from its own field`() {
        val snapshot = TrafficSnapshot(up = 1_048_576, down = 2_097_152, available = true)
        assertEquals("2.0 MB", ByteFormat.format(snapshot, downlink = true))
        assertEquals("1.0 MB", ByteFormat.format(snapshot, downlink = false))
    }
}
