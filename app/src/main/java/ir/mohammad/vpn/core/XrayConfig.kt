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

    fun render(
        profile: Profile,
        mtu: Int,
        dnsServers: List<String>,
        logLevel: String = "warning",
        ipv6: Boolean = true,
        geo: Boolean = false,
        fakeDns: Boolean = true,
    ): String {
        val outbound = JSONObject(profile.xray.toString()).apply { put("tag", "proxy") }

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
        if (fakeDns) dnsServersJson.put("fakedns")
        dnsServers.forEach { dnsServersJson.put(it) }

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
                JSONObject()
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
