package ir.mohammad.vpn

import ir.mohammad.vpn.vpn.DataPathPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The watchdog must never tear down a tunnel that is carrying traffic, and must not
 * act on a single probe failure.
 */
class DataPathPolicyTest {

    @Test
    fun `one or two failures never reconnect`() {
        assertFalse(DataPathPolicy.shouldReconnect(failures = 1, msSinceTraffic = 120_000))
        assertFalse(DataPathPolicy.shouldReconnect(failures = 2, msSinceTraffic = 120_000))
    }

    @Test
    fun `three failures with recent traffic still do not reconnect`() {
        assertFalse(DataPathPolicy.shouldReconnect(failures = 3, msSinceTraffic = 5_000))
    }

    @Test
    fun `three failures and a long silence reconnect`() {
        assertTrue(DataPathPolicy.shouldReconnect(failures = 3, msSinceTraffic = 120_000))
    }

    @Test
    fun `probe targets a neutral address, never the server itself`() {
        assertTrue("probing the server through its own tunnel is a bug", DataPathPolicy.probeTargetIsServer("node.example", "node.example"))
        assertFalse(DataPathPolicy.probeTargetIsServer("1.1.1.1", "node.example"))
    }
}
