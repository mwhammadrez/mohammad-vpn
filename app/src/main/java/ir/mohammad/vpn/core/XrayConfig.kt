package ir.mohammad.vpn.core

import ir.mohammad.vpn.data.Profile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders the runtime JSON handed to Xray-core.
 *
 * The TUN inbound reads the file descriptor from the XRAY_TUN_FD environment
 * variable, which the Go bridge sets right before starting the core.
 */
object XrayConfig {

    const val TUN_NAME = "xray0"

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
        tunAddress: String,
        mtu: Int,
        dnsServers: List<String>,
        logLevel: String = "warning",
        ipv6: Boolean = true,
        geo: Boolean = false,
    ): String {
        val outbound = JSONObject(profile.xray.toString()).apply { put("tag", "proxy") }

        val inbound = JSONObject()
            .put("tag", "tun-in")
            .put("protocol", "tun")
            .put(
                "settings",
                JSONObject()
                    .put("name", TUN_NAME)
                    .put("mtu", mtu)
                    .put("gateway", JSONArray().put("$tunAddress/30"))
                    .put("dns", JSONArray(dnsServers)),
            )
            .put(
                "sniffing",
                JSONObject()
                    .put("enabled", true)
                    .put("destOverride", JSONArray().put("http").put("tls").put("quic")),
            )

        val rules = JSONArray()
        if (geo) {
            // Same shape as a mature client's default preset: ads and QUIC are
            // blocked, private space and the home country stay direct.
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "block")
                    .put("domain", JSONArray().put("geosite:category-ads-all")),
            )
            rules.put(
                JSONObject()
                    .put("type", "field")
                    .put("outboundTag", "block")
                    .put("network", "udp")
                    .put("port", "443"),
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

        val catchAll = JSONObject()
            .put("type", "field")
            .put("network", "tcp,udp")
            .put("outboundTag", "proxy")

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
                    .put("servers", JSONArray(dnsServers))
                    .put("queryStrategy", if (ipv6) "UseIP" else "UseIPv4"),
            )
            .put("inbounds", JSONArray().put(inbound))
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
                    // Domain rules only work when Xray is allowed to resolve.
                    .put("domainStrategy", "IPIfNonMatch")
                    .put("rules", rules.put(catchAll)),
            )
        return root.toString()
    }
}
