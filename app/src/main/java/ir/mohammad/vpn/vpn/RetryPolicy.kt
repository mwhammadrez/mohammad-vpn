package ir.mohammad.vpn.vpn

/**
 * Bounded exponential backoff for automatic reconnection.
 *
 * Deliberately pure so it can be unit-tested without Android.
 */
class RetryPolicy(
    val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 3_000L,
    private val maxDelayMs: Long = 30_000L,
) {

    /** Returns the delay before [attempt] (1-based), or null when attempts are exhausted. */
    fun delayFor(attempt: Int): Long? {
        if (attempt < 1 || attempt > maxAttempts) return null
        var delay = baseDelayMs
        repeat(attempt - 1) {
            delay *= 2
            if (delay >= maxDelayMs) return maxDelayMs
        }
        return minOf(delay, maxDelayMs)
    }

    fun hasAttemptsLeft(attempt: Int): Boolean = attempt <= maxAttempts
}
