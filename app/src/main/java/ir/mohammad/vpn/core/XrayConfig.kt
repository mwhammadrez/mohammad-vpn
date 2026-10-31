package ir.mohammad.vpn.core

import ir.mohammad.vpn.data.Profile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders the runtime JSON handed to Xray-core.
 *
 * Architecture: the TUN descriptor is consumed by the userspace tun2socks bridge
 * (`TunBridge`), which forwards TCP/UDP to the local SOCKS inbound below. Xray
 * never sees the descriptor itself.
 *
 *   TUN fd -> tun2socks -> 127.0.0.1:10808 (socks) -> outbound
 */
object XrayConfig {

    const val SOCKS_PORT = 10808
    const val LOOPBACK = "127.0.0.1"

    /** Documented FakeDNS pool; must not collide with real destinations. */
    /** CGNAT in Iran expires idle mappings around 30-60 s; stay well inside that. */
    const val KEEPALIVE_IDLE_S = 25
    const val KEEPALIVE_INTERVAL_S = 15

    const val FAKE_DNS_POOL = "198.18.0.0/15"
    const val FAKE_DNS_POOL_SIZE = 65535

    /** Private address space that must never leave through the proxy. */
    private val DIRECT_CIDRS = listOf(
        "10.0.0.0/8",
        "172.16.0.0/12",
        "192.168.0.0/16",
        "127.0.0.0/8",
        "169.254.0.0/16",
        "fc00::/7",
        "fe80::/10",
    )

    /** Local-only suffixes that never resolve on the public internet. */
    private val DIRECT_DOMAINS = listOf(
        "domain:ir",
        "domain:localhost",
        "domain:local",
        "domain:lan",
        "domain:home.arpa",
    )

    /**
     * Applies transport hardening to the outbound copy.
     *
     * `finalmask` is Xray's post-handshake camouflage layer; `fragment` splits the
     * first writes (or the TLS ClientHello) so a middlebox cannot match the
     * handshake against a known pattern.
     */
    private fun applyTuning(outbound: JSONObject, tuning: TransportTuning) {
        if (tuning.fragment) {
            val fragment = JSONObject()
                .put("packets", tuning.fragmentPackets)
                .put("lengths", JSONArray(tuning.fragmentLengths))
                .put("delays", JSONArray(tuning.fragmentDelays))
                .put("maxSplit", tuning.fragmentMaxSplit)
            val stream = outbound.optJSONObject("streamSettings")
                ?: JSONObject().also { outbound.put("streamSettings", it) }
            val mask = stream.optJSONObject("finalmask")
                ?: JSONObject().also { stream.put("finalmask", it) }
            val tcp = mask.optJSONArray("tcp") ?: JSONArray().also { mask.put("tcp", it) }
            tcp.put(JSONObject().put("type", "fragment").put("settings", fragment))
        }
        if (tuning.mux) {
            outbound.put(
                "mux",
                JSONObject()
                    .put("enabled", true)
                    .put("concurrency", tuning.muxConcurrency)
                    .put("xudpConcurrency", tuning.muxXudpConcurrency)
                    .put("xudpProxyUDP443", tuning.muxXudpProxyUDP443),
            )
        }
    }

    /**
     * Socket-level defaults that every Xray outbound benefits from.
     *
     * 25 s idle / 15 s interval: carrier-grade NAT in Iran drops idle mappings in
     * the 30-60 s range; Xray's Chrome-derived default of 45 s sits inside that
     * window. Merges into an existing `sockopt` block instead of replacing it.
     */
    private fun applySockopt(outbound: JSONObject) {
        val stream = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = stream.optJSONObject("sockopt")
            ?: JSONObject().also { stream.put("sockopt", it) }
        if (!sockopt.has("tcpKeepAliveIdle")) sockopt.put("tcpKeepAliveIdle", KEEPALIVE_IDLE_S)
        if (!sockopt.has("tcpKeepAliveInterval")) sockopt.put("tcpKeepAliveInterval", KEEPALIVE_INTERVAL_S)
    }

    fun render(
        profile: Profile,
        mtu: Int,
        dnsServers: List<String>,
        logLevel: String = "warning",
        ipv6: Boolean = true,
        geo: Boolean = false,
        fakeDns: Boolean = true,
        tuning: TransportTuning = TransportTuning(),
        serverHosts: Map<String, String> = emptyMap(),
    ): String {
        val outbound = JSONObject(profile.xray.toString()).apply { put("tag", "proxy") }
        applyTuning(outbound, tuning)
        applySockopt(outbound)

        val sniffOverride = JSONArray().put("http").put("tls")
        if (fakeDns) sniffOverride.put("fakedns")

        val socks = JSONObject()
            .put("tag", "socks")
            .put("listen", LOOPBACK)
            .put("port", SOCKS_PORT)
            .put("protocol", "socks")
            .put(
                "settings",
                JSONObject()
                    .put("auth", "noauth")
                    .put("udp", true)
                    .put("userLevel", 0),
            )
            .put(
                "sniffing",
                JSONObject().put("enabled", true).put("destOverride", sniffOverride),
            )

        val dnsServersJson = JSONArray()
        if (fakeDns) {
            dnsServersJson.put(JSONObject().put("address", "fakedns"))
        }
        if (geo) {
            // Domestic names resolve through the phone's own resolver, so .ir sites get
            // their real addresses instead of a fake one and never leave the country.
            dnsServersJson.put(
                JSONObject()
                    .put("address", "localhost")
                    .put("domains", JSONArray().put("domain:ir"))
                    .put("skipFallback", true),
            )
        }
        dnsServers.forEach { dnsServersJson.put(it) }

        val dnsObject = JSONObject()
        if (serverHosts.isNotEmpty()) {
            // Saves one resolver round trip per connection: the server name is known
            // before the tunnel exists, so there is nothing to discover.
            val hosts = JSONObject()
            serverHosts.forEach { (name, address) -> hosts.put(name, address) }
            dnsObject.put("hosts", hosts)
        }

        val root = JSONObject()
            .put("log", JSONObject().put("loglevel", logLevel))
            .put("stats", JSONObject())
            .put(
                "policy",
                JSONObject()
                    .put(
                        "levels",
                        JSONObject().put(
                            "0",
                            JSONObject()
                                .put("handshake", 4)
                                .put("connIdle", 300)
                                .put("uplinkOnly", 1)
                                .put("downlinkOnly", 1),
                        ),
                    )
                    .put(
                        "system",
                        JSONObject()
                            .put("statsOutboundUplink", true)
                            .put("statsOutboundDownlink", true),
                    ),
            )
            .put(
                "dns",
                dnsObject
                    .put("servers", dnsServersJson)
                    .put("queryStrategy", if (ipv6) "UseIP" else "UseIPv4"),
            )

        if (fakeDns) {
            root.put(
                "fakedns",
                JSONObject()
                    .put("ipPool", FAKE_DNS_POOL)
                    .put("poolSize", FAKE_DNS_POOL_SIZE),
            )
        }

        val rules = JSONArray()
        // Reference priority: block QUIC first, then ads, then direct, then proxy.
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("outboundTag", "block")
                .put("network", "udp")
                .put("port", "443"),
        )
        if (geo) {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "block")
                    .put("domain", JSONArray().put("geosite:category-ads-all")),
            )
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "direct")
                    .put("ip", JSONArray().put("geoip:private"))
                    .put("domain", JSONArray().put("geosite:private")),
            )
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "direct")
                    .put("domain", JSONArray().put("domain:ir"))
                    .put("ip", JSONArray().put("geoip:ir")),
            )
        } else {
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "direct")
                    .put("ip", JSONArray(DIRECT_CIDRS))
                    .put("domain", JSONArray(DIRECT_DOMAINS)),
            )
        }
        rules.put(
            JSONObject()
                .put("type", "field")
                .put("network", "tcp,udp")
                .put("outboundTag", "proxy"),
        )

        return root
            .put("inbounds", JSONArray().put(socks))
            .put(
                "outbounds",
                JSONArray()
                    .put(outbound)
                    .put(JSONObject().put("tag", "direct").put("protocol", "freedom"))
                    .put(JSONObject().put("tag", "block").put("protocol", "blackhole")),
            )
            .put(
                "routing",
                JSONObject()
                    .put("domainStrategy", "IPIfNonMatch")
                    .put("rules", rules),
            )
            .toString()
    }
}
