package ir.mohammad.vpn.ui

/**
 * The two environments the app presents.
 *
 * Mode 1 is the Xray/REALITY path that has been the app so far. Mode 2 is the DNS tunnel: a different
 * engine, a different failure mode (slow but survives "only DNS is open"), and two ways to use it —
 * a local SOCKS proxy for apps that accept one, or a full tunnel for everything.
 *
 * Kept separate from the transport enum on purpose: a mode is a UI/product choice, not a protocol.
 */
enum class AppMode(val storageKey: String, val label: String, val short: String) {
    XRAY("xray", "Mode 1", "XRAY"),
    DNS("dns", "Mode 2", "DNS TUNNEL");

    companion object {
        fun from(value: String?): AppMode = entries.firstOrNull { it.storageKey == value } ?: XRAY
    }
}

/** How Mode 2 is used: a proxy on localhost, or a tunnel for the whole device. */
enum class DnsMode(val storageKey: String, val label: String) {
    PROXY("proxy", "PROXY"),
    VPN("vpn", "FULL TUNNEL");

    companion object {
        fun from(value: String?): DnsMode = entries.firstOrNull { it.storageKey == value } ?: PROXY
    }
}
