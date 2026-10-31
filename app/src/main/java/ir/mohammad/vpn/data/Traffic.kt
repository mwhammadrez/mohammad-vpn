package ir.mohammad.vpn.data

import java.util.Locale

/**
 * One telemetry sample.
 *
 * SPEC FR-N7…FR-N10. [available] is a first-class value, not a sentinel: "zero bytes" and "we cannot
 * read the counters" are different claims, and 0.14.x showed the second as if it were the first.
 */
data class TrafficSnapshot(
    val up: Long = 0L,
    val down: Long = 0L,
    val available: Boolean = false,
) {
    companion object {
        val UNAVAILABLE = TrafficSnapshot(0L, 0L, available = false)
        val ZERO = TrafficSnapshot(0L, 0L, available = true)
    }
}

/**
 * Byte formatting with the user's locale and a unit that fits the magnitude.
 *
 * The 0.14.x bug was a fixed `%.2f GB`: 5 MB rendered as "0.00 GB", which reads as "nothing happened".
 * The rounding rule is fixed (1 decimal below 1 GiB, 2 above) so the number does not jitter between
 * ticks.
 */
object ByteFormat {

    private const val KB = 1024.0
    private const val MB = 1024.0 * 1024.0
    private const val GB = 1024.0 * 1024.0 * 1024.0

    /** "1.0 KB", "1.0 MB", "1.20 GB", "512 B". Never negative. */
    fun format(bytes: Long, locale: Locale = Locale.US): String {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024 -> "$safe B"
            safe < MB -> String.format(locale, "%.1f KB", safe / KB)
            safe < GB -> String.format(locale, "%.1f MB", safe / MB)
            else -> String.format(locale, "%.2f GB", safe / GB)
        }
    }

    /** "—" when the source is unavailable; never a fabricated zero. */
    fun format(snapshot: TrafficSnapshot?, downlink: Boolean, locale: Locale = Locale.US): String {
        if (snapshot == null || !snapshot.available) return "—"
        return format(if (downlink) snapshot.down else snapshot.up, locale)
    }

    /** Unit split for a two-part display (value + small unit label). */
    fun split(bytes: Long, locale: Locale = Locale.US): Pair<String, String> {
        val safe = bytes.coerceAtLeast(0L)
        return when {
            safe < 1024 -> "$safe" to "B"
            safe < MB -> String.format(locale, "%.1f", safe / KB) to "KB"
            safe < GB -> String.format(locale, "%.1f", safe / MB) to "MB"
            else -> String.format(locale, "%.2f", safe / GB) to "GB"
        }
    }

    /** Bar fill, saturating rather than overflowing, for a 0…[cap] scale. */
    fun fraction(bytes: Long, cap: Long): Float =
        if (cap <= 0L) 0f else (bytes.coerceAtLeast(0L).toDouble() / cap).coerceIn(0.0, 1.0).toFloat()
}

/**
 * Turns cumulative counters into per-interval deltas.
 *
 * Counters restart when the core restarts, so a decrease is a reset, not negative traffic. Clamping at
 * zero is what stops the first sample after a reconnect from spiking.
 */
class TelemetryCounter {

    private var lastUp = 0L
    private var lastDown = 0L
    private var primed = false

    fun reset() {
        lastUp = 0L
        lastDown = 0L
        primed = false
    }

    /** Returns (upDelta, downDelta) since the previous call; (0,0) on the first call after reset. */
    fun delta(up: Long, down: Long): Pair<Long, Long> {
        if (!primed) {
            primed = true
            lastUp = up
            lastDown = down
            return 0L to 0L
        }
        val dUp = (up - lastUp).coerceAtLeast(0L)
        val dDown = (down - lastDown).coerceAtLeast(0L)
        lastUp = up
        lastDown = down
        return dUp to dDown
    }
}
