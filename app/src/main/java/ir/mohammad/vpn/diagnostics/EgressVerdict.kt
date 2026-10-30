package ir.mohammad.vpn.diagnostics

/**
 * Compares the carrier egress IP (captured before connecting) with the egress IP
 * observed while the tunnel is up.
 *
 * This is the only device-side signal that proves traffic really leaves through
 * the server: if the public IP is unchanged, the tunnel is not carrying traffic.
 */
data class EgressVerdict(
    val changed: Boolean,
    val conclusion: String,
    val possibleIpv6Leak: Boolean,
) {
    companion object {
        fun of(
            baseline: String?,
            current: String?,
            ipv6Egress: String? = null,
            ipv4SameAsBaseline: Boolean = false,
        ): EgressVerdict {
            if (baseline.isNullOrBlank() || current.isNullOrBlank()) {
                return EgressVerdict(false, "inconclusive", false)
            }
            val changed = baseline != current
            val leak = !ipv6Egress.isNullOrBlank() && ipv4SameAsBaseline
            return EgressVerdict(
                changed = changed,
                conclusion = if (changed) "tunnel" else "no-tunnel",
                possibleIpv6Leak = leak,
            )
        }
    }
}
