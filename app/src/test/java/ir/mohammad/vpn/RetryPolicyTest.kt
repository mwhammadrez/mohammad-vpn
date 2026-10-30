package ir.mohammad.vpn

import ir.mohammad.vpn.vpn.RetryPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetryPolicyTest {

    private val policy = RetryPolicy(maxAttempts = 3, baseDelayMs = 3_000, maxDelayMs = 30_000)

    @Test
    fun backoffGrowsExponentially() {
        assertEquals(3_000L, policy.delayFor(1))
        assertEquals(6_000L, policy.delayFor(2))
        assertEquals(12_000L, policy.delayFor(3))
    }

    @Test
    fun delayIsCapped() {
        val wide = RetryPolicy(maxAttempts = 10, baseDelayMs = 10_000, maxDelayMs = 25_000)
        assertEquals(25_000L, wide.delayFor(5))
    }

    @Test
    fun exhaustedAttemptsReturnNull() {
        assertNull(policy.delayFor(4))
        assertNull(policy.delayFor(0))
    }

    @Test
    fun attemptsLeftBoundary() {
        assertTrue(policy.hasAttemptsLeft(3))
        assertFalse(policy.hasAttemptsLeft(4))
    }
}
