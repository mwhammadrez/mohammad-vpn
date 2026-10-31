package ir.mohammad.vpn.ui

import ir.mohammad.vpn.data.Profile

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

/**
 * Which profiles belong to which mode, and what "selected" means in each.
 *
 * The two environments do not share a profile kind: a DNS tunnel has no meaning in Mode 1, and a
 * VLESS profile has none behind a DNS engine. Keeping the rule here — instead of inline filters in
 * the activity — is what makes it testable and keeps the two screens from disagreeing.
 */
fun profilesFor(mode: AppMode, all: List<Profile>): List<Profile> =
    all.filter { if (mode == AppMode.DNS) it.core == "DNS" else it.core != "DNS" }

/**
 * The profile the mode would use: the current selection when it belongs to this mode, otherwise the
 * first one that does, otherwise null.
 */
fun resolveSelection(mode: AppMode, all: List<Profile>, selected: Profile?): Profile? {
    val candidates = profilesFor(mode, all)
    if (selected != null && candidates.any { it.id == selected.id }) return selected
    return candidates.firstOrNull()
}

/** How Mode 2 is used: a proxy on localhost, or a tunnel for the whole device. */
enum class DnsMode(val storageKey: String, val label: String) {
    PROXY("proxy", "PROXY"),
    VPN("vpn", "FULL TUNNEL");

    companion object {
        fun from(value: String?): DnsMode = entries.firstOrNull { it.storageKey == value } ?: PROXY
    }
}
