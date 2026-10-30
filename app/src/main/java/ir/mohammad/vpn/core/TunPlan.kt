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

        const val PREFIX_V4 = 30
        const val IPV4_ADDRESS = "10.10.10.1"
        const val IPV6_ADDRESS = "fc00::1"
        const val IPV6_PREFIX = 126

        /**
         * Split-tunnel route set: every public IPv4 prefix, with private and reserved
         * ranges left outside the tunnel so LAN, link-local, loopback and multicast
         * traffic never enter it. There is deliberately no 0.0.0.0/0 route.
         */
        val SPLIT_ROUTES: List<Pair<String, Int>> = listOf(
            "0.0.0.0" to 5,
            "8.0.0.0" to 7,
            "11.0.0.0" to 8,
            "12.0.0.0" to 6,
            "16.0.0.0" to 4,
            "32.0.0.0" to 3,
            "64.0.0.0" to 3,
            "96.0.0.0" to 4,
            "112.0.0.0" to 5,
            "120.0.0.0" to 6,
            "124.0.0.0" to 7,
            "126.0.0.0" to 8,
            "128.0.0.0" to 3,
            "160.0.0.0" to 5,
            "168.0.0.0" to 8,
            "169.0.0.0" to 9,
            "169.128.0.0" to 10,
            "169.192.0.0" to 11,
            "169.224.0.0" to 12,
            "169.240.0.0" to 13,
            "169.248.0.0" to 14,
            "169.252.0.0" to 15,
            "169.255.0.0" to 16,
            "170.0.0.0" to 7,
            "172.0.0.0" to 12,
            "172.32.0.0" to 11,
            "172.64.0.0" to 10,
            "172.128.0.0" to 9,
            "173.0.0.0" to 8,
            "174.0.0.0" to 7,
            "176.0.0.0" to 4,
            "192.0.0.0" to 9,
            "192.128.0.0" to 11,
            "192.160.0.0" to 13,
            "192.169.0.0" to 16,
            "192.170.0.0" to 15,
            "192.172.0.0" to 14,
            "192.176.0.0" to 12,
            "192.192.0.0" to 10,
            "193.0.0.0" to 8,
            "194.0.0.0" to 7,
            "196.0.0.0" to 6,
            "200.0.0.0" to 5,
            "208.0.0.0" to 4,
            "240.0.0.0" to 4,
        )

        fun build(mtu: Int, dns: List<String>, ipv6: Boolean = true): TunPlan = TunPlan(
            mtu = mtu,
            ipv4Address = IPV4_ADDRESS,
            ipv4Prefix = PREFIX_V4,
            ipv6Address = if (ipv6) IPV6_ADDRESS else "",
            ipv6Prefix = IPV6_PREFIX,
            ipv4Routes = SPLIT_ROUTES,
            ipv6Routes = if (ipv6) listOf("::" to 0) else emptyList(),
            dns = dns,
        )
    }
}
