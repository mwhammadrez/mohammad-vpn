package ir.mohammad.vpn.vpn

/** Pure rules for the data-path watchdog. */
object DataPathPolicy {

    const val FAILURES_TO_RECONNECT = 3

    /** Traffic seen inside this window means the tunnel works; ignore probe noise. */
    const val TRAFFIC_GRACE_MS = 60_000L

    fun shouldReconnect(failures: Int, msSinceTraffic: Long): Boolean =
        failures >= FAILURES_TO_RECONNECT && msSinceTraffic > TRAFFIC_GRACE_MS

    /** Guards against probing the server through its own tunnel. */
    fun probeTargetIsServer(target: String, server: String): Boolean =
        target.equals(server, ignoreCase = true)
}
