package ir.mohammad.vpn

import ir.mohammad.vpn.vpn.HandoverAction
import ir.mohammad.vpn.vpn.HandoverContext
import ir.mohammad.vpn.vpn.HandoverEvent
import ir.mohammad.vpn.vpn.HandoverPolicy
import ir.mohammad.vpn.vpn.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HandoverPolicyTest {

    private val policy = HandoverPolicy(RetryPolicy(maxAttempts = 3, baseDelayMs = 3_000L))

    private fun ctx(
        userStopped: Boolean = false,
        generationMatches: Boolean = true,
        connected: Boolean = true,
        online: Boolean = false,
        attemptsUsed: Int = 0,
    ) = HandoverContext(userStopped, generationMatches, connected, online, attemptsUsed)

    @Test
    fun `network loss holds the service instead of stopping it`() {
        val action = policy.onEvent(HandoverEvent.NETWORK_LOST, ctx(online = false))
        assertTrue(
            "loss must hold, got $action",
            action is HandoverAction.HoldForNetwork,
        )
    }

    @Test
    fun `network restoration resumes with the first bounded attempt`() {
        val action = policy.onEvent(HandoverEvent.NETWORK_RESTORED, ctx(online = true))
        assertEquals(HandoverAction.Resume(attempt = 1, delayMs = 3_000L), action)
    }

    @Test
    fun `user disconnect during the outage prevents auto reconnect`() {
        val action = policy.onEvent(
            HandoverEvent.NETWORK_RESTORED,
            ctx(userStopped = true, online = true),
        )
        assertEquals(HandoverAction.Ignore, action)
    }

    @Test
    fun `stale generation never reconnects`() {
        val action = policy.onEvent(
            HandoverEvent.NETWORK_RESTORED,
            ctx(generationMatches = false, online = true),
        )
        assertEquals(HandoverAction.Ignore, action)
    }

    @Test
    fun `reconnect attempts stay capped`() {
        val action = policy.onEvent(
            HandoverEvent.NETWORK_RESTORED,
            ctx(online = true, attemptsUsed = 3),
        )
        assertEquals(HandoverAction.Exhausted, action)
    }

    @Test
    fun `loss while disconnected is ignored`() {
        val action = policy.onEvent(
            HandoverEvent.NETWORK_LOST,
            ctx(connected = false, online = false),
        )
        assertEquals(HandoverAction.Ignore, action)
    }

    @Test
    fun `restoration while already online and unstopped still resumes`() {
        val action = policy.onEvent(
            HandoverEvent.NETWORK_RESTORED,
            ctx(online = true, attemptsUsed = 1),
        )
        assertEquals(HandoverAction.Resume(attempt = 2, delayMs = 6_000L), action)
    }
}
