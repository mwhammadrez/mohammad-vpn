package ir.mohammad.vpn.core

/**
 * A pure description of the TUN interface to create.
 *
 * Kept free of Android types so the address pool, route set and IPv6 policy are
 * unit-testable without a device.
 */
data class TunPlan(
    val mtu: Int,
    val ipv4Address: String,
    val ipv4Prefix: Int,
    val ipv6Address: String,
    val ipv6Prefix: Int,
    val ipv4Routes: List<Pair<String, Int>>,
    val ipv6Routes: List<Pair<String, Int>>,
    val dns: List<String>,
) {
    companion object {
        const val TAG = "TUN"

        /**
         * Address pools used to avoid colliding with a real LAN. A user whose
         * home network is 10.0.0.0/24 must not receive 10.0.0.1 from us.
         */
        private val POOL = listOf(
            "10.0.0.1",
            "10.1.0.1",
            "10.10.10.1",
            "10.10.14.1",
            "172.16.0.1",
            "172.20.0.1",
            "192.168.199.1",
            "10.20.0.1",
        )

        const val PREFIX_V4 = 30
        const val IPV6_ADDRESS = "fc00::1"
        const val IPV6_PREFIX = 126

        fun build(mtu: Int, dns: List<String>, index: Int, ipv6: Boolean = true): TunPlan {
            val safeIndex = if (POOL.isEmpty()) 0 else Math.floorMod(index, POOL.size)
            return TunPlan(
                mtu = mtu,
                ipv4Address = POOL[safeIndex],
                ipv4Prefix = PREFIX_V4,
                ipv6Address = if (ipv6) IPV6_ADDRESS else "",
                ipv6Prefix = IPV6_PREFIX,
                ipv4Routes = listOf("0.0.0.0" to 0),
                ipv6Routes = if (ipv6) listOf("::" to 0) else emptyList(),
                dns = dns,
            )
        }
    }
}
