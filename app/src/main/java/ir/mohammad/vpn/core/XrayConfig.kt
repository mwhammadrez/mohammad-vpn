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
    const val TCP_USER_TIMEOUT_MS = 30_000
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
        // REALITY's camouflage depends on the ClientHello arriving intact; fragmenting it defeats
        // the transport it is meant to protect (SPEC TLS-5).
        if (tuning.fragment && !RealityRules.fragmentBreaksReality(RealityRules.securityOf(outbound))) {
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
     * window.
     *
     * `tcpMaxSeg` is the only MSS clamp this app can express: it cannot run iptables,
     * and tun2socks does not rewrite the MSS option. Without it a 1500-byte segment can
     * be handed to a path that only carries 1400.
     *
     * `tcpUserTimeout` bounds how long unacknowledged data may sit before the socket
     * dies; the kernel default is minutes, which is how a dead path looks alive.
     *
     * Every field is merged, never overwritten: an explicit operator value wins.
     */
    /** Applies the profile's `dialMode` to the outbound's sockopt, if it has one. */
    private fun outboundDialMode(sockopt: JSONObject, mode: String?) {
        if (mode.isNullOrBlank()) return
        if (!sockopt.has("dialMode")) sockopt.put("dialMode", mode)
    }

    /**
     * `cipherSuites` is a TLS-level field. It is only meaningful with `security: tls`; a REALITY
     * handshake borrows the decoy site's suites, so writing it there would be a lie.
     */
    private fun applyCipherSuites(outbound: JSONObject, profile: Profile) {
        val suites = profile.cipherSuites ?: return
        val stream = outbound.optJSONObject("streamSettings") ?: return
        if (stream.optString("security", "none").lowercase() != "tls") return
        val tls = stream.optJSONObject("tlsSettings") ?: JSONObject().also { stream.put("tlsSettings", it) }
        if (!tls.has("cipherSuites")) tls.put("cipherSuites", suites)
    }

    /**
     * SPEC TLS-2. A profile-level fingerprint wins over whatever the settings block carries, so the
     * operator can change it in one place without editing the transport JSON.
     */
    private fun applyFingerprint(outbound: JSONObject, profile: Profile) {
        val fingerprint = profile.fingerprint ?: return
        val stream = outbound.optJSONObject("streamSettings") ?: return
        when (stream.optString("security", "none").lowercase()) {
            "tls" -> stream
                .optJSONObject("tlsSettings")
                ?.put("fingerprint", fingerprint)
            "reality" -> stream
                .optJSONObject("realitySettings")
                ?.put("fingerprint", fingerprint)
        }
    }

    /**
     * SPEC TLS-4. `flow` belongs on every VLESS user object in the outbound, not on the stream:
     * the core reads it per account.
     */
    private fun applyFlow(outbound: JSONObject, profile: Profile) {
        val flow = profile.flow ?: return
        val vnext = outbound.optJSONObject("settings")?.optJSONArray("vnext") ?: return
        for (i in 0 until vnext.length()) {
            val users = vnext.optJSONObject(i)?.optJSONArray("users") ?: continue
            for (j in 0 until users.length()) users.optJSONObject(j)?.put("flow", flow)
        }
    }

    /** `streamSettings.finalMask`: the profile's copy wins, the transport JSON is never rewritten. */
    private fun applyFinalMask(outbound: JSONObject, profile: Profile) {
        val mask = profile.finalMask ?: return
        val stream = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        if (!stream.has("finalMask")) stream.put("finalMask", JSONObject(mask.toString()))
    }

    private fun applySockopt(outbound: JSONObject, mtu: Int, profile: Profile) {
        val stream = outbound.optJSONObject("streamSettings")
            ?: JSONObject().also { outbound.put("streamSettings", it) }
        val sockopt = stream.optJSONObject("sockopt")
            ?: JSONObject().also { stream.put("sockopt", it) }
        outboundDialMode(sockopt, profile.dialMode)
        if (!sockopt.has("tcpMaxSeg")) sockopt.put("tcpMaxSeg", MtuPolicy.segmentSize(mtu))
        if (!sockopt.has("tcpUserTimeout")) sockopt.put("tcpUserTimeout", TCP_USER_TIMEOUT_MS)
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
        /** Overridable so the delay probe can run a temporary core beside the live one. */
        socksPort: Int = SOCKS_PORT,
    ): String {
        val outbound = JSONObject(profile.xray.toString()).apply { put("tag", "proxy") }
        applyTuning(outbound, tuning)
        applyFingerprint(outbound, profile)
        applyFlow(outbound, profile)
        applyCipherSuites(outbound, profile)
        applyFinalMask(outbound, profile)
        applySockopt(outbound, mtu, profile)

        val sniffOverride = JSONArray().put("http").put("tls")
        if (fakeDns) sniffOverride.put("fakedns")

        val socks = JSONObject()
            .put("tag", "socks")
            .put("listen", LOOPBACK)
            .put("port", socksPort)
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
