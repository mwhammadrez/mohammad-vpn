package ir.mohammad.vpn.data

import android.net.Uri
import ir.mohammad.vpn.core.DnsConfig
import ir.mohammad.vpn.core.FinalMaskRules
import ir.mohammad.vpn.core.MtuPolicy
import ir.mohammad.vpn.core.RealityRules
import ir.mohammad.vpn.core.TlsFingerprint
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64

/**
 * Turns a share link into a [Profile].
 *
 * Supported: `vless://`, `vmess://`, `trojan://`, `ss://`. Everything else is refused by name, not
 * silently ignored, because "I pasted it and nothing happened" is the worst possible answer.
 *
 * Imported profiles are user data, not operator data, so they are *not* subject to the bundle's
 * D-78 rule (which forbids server data in source). They are still validated: anything the core would
 * refuse at startup is refused here, at paste time, with the reason shown.
 *
 * Where a share link omits information the core needs, this fills a documented default rather than
 * failing: a TLS profile with no `fp` gets `chrome` (every uTLS client does this; D-104).
 */
object ConfigUri {

    const val DEFAULT_FINGERPRINT = "chrome"

    private val SUPPORTED = setOf("vless", "vmess", "trojan", "ss", "dns", "masterdns", "cottendns", "soar")

    class InvalidConfig(message: String) : Exception(message)

    data class Parsed(val profile: Profile, val warnings: List<String> = emptyList())

    /** True when [text] starts with a scheme this parser claims, so the UI can pre-filter. */
    fun looksLikeConfig(text: String): Boolean {
        val scheme = text.trim().substringBefore("://", "").lowercase()
        return scheme in SUPPORTED
    }

    fun parse(raw: String): Parsed {
        val text = raw.trim()
        if (text.isEmpty()) throw InvalidConfig("nothing to import")
        val scheme = text.substringBefore("://", "").lowercase()
        return when (scheme) {
            "vless" -> parseVless(text)
            "vmess" -> parseVmess(text)
            "trojan" -> parseTrojan(text)
            "ss" -> parseShadowsocks(text)
            "dns" -> parseDns(text)
            "masterdns" -> parseWhiteDns(text, "masterdns")
            "cottendns" -> parseWhiteDns(text, "cottendns")
            "soar" -> parseWhiteDns(text, "soar")
            "" -> throw InvalidConfig("not a share link: expected scheme:// at the start")
            else -> throw InvalidConfig("unsupported scheme \"$scheme\" (supported: vless, vmess, trojan, ss)")
        }
    }

    /* ------------------------------------------------------------------ vless */

    private fun parseVless(text: String): Parsed {
        val uri = Uri.parse(text)
        val uuid = uri.userInfo?.takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("vless: missing id before @")
        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("vless: missing server address")
        val port = uri.port.takeIf { it in 1..65535 }
            ?: throw InvalidConfig("vless: missing or invalid port")

        val q = query(uri)
        val security = param(q, "security")?.lowercase() ?: "none"
        val network = param(q, "type")?.lowercase()?.takeIf { it.isNotBlank() } ?: "tcp"
        val warnings = mutableListOf<String>()
        requireUuid(uuid, "vless")
        RealityRules.validateFlow(param(q, "flow"), security)?.let { throw InvalidConfig("vless: $it") }

        // `encryption` is not decoration. Modern servers use "mlkem768x25519plus.<mode>.<rtt>.<key>"
        // (VLESS post-quantum). Forcing "none" here produces a config the core accepts and the
        // server rejects — a handshake failure with no useful message.
        val encryption = param(q, "encryption")?.takeIf { it.isNotBlank() } ?: "none"
        val users = JSONArray().put(
            JSONObject()
                .put("id", uuid)
                .put("encryption", encryption)
                .apply { param(q, "flow")?.let { put("flow", it) } },
        )
        val xray = JSONObject()
            .put("protocol", "vless")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject().put("address", host).put("port", port).put("users", users),
                    ),
                ),
            )
            .put("streamSettings", streamSettings(network, security, q, warnings))

        val profile = Profile(
            id = idFor(text),
            name = nameOf(uri, "vless ${host.substringBefore('.')}"),
            protocol = "VLESS",
            core = "XRAY",
            host = host,
            port = port,
            xray = xray,
            awg = null,
            fingerprint = fingerprintFor(security, param(q, "fp"), warnings),
            flow = param(q, "flow"),
            finalMask = finalMask(param(q, "fm"), security, warnings),
            mtu = mtuParam(q),
        )
        return Parsed(profile, warnings)
    }

    /* ------------------------------------------------------------------ trojan */

    private fun parseTrojan(text: String): Parsed {
        val uri = Uri.parse(text)
        val password = uri.userInfo?.takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("trojan: missing password before @")
        val host = uri.host?.takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("trojan: missing server address")
        val port = uri.port.takeIf { it in 1..65535 }
            ?: throw InvalidConfig("trojan: missing or invalid port")

        val q = query(uri)
        val security = param(q, "security")?.lowercase() ?: "tls"
        val network = param(q, "type")?.lowercase()?.takeIf { it.isNotBlank() } ?: "tcp"
        val warnings = mutableListOf<String>()

        val xray = JSONObject()
            .put("protocol", "trojan")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject().put("address", host).put("port", port).put("password", password),
                    ),
                ),
            )
            .put("streamSettings", streamSettings(network, security, q, warnings))

        return Parsed(
            Profile(
                id = idFor(text),
                name = nameOf(uri, "trojan ${host.substringBefore('.')}"),
                protocol = "TROJAN",
                core = "XRAY",
                host = host,
                port = port,
                xray = xray,
                awg = null,
                fingerprint = fingerprintFor(security, param(q, "fp"), warnings),
                finalMask = finalMask(param(q, "fm"), security, warnings),
                mtu = mtuParam(q),
            ),
            warnings,
        )
    }

    /* ------------------------------------------------------------------ vmess */

    private fun parseVmess(text: String): Parsed {
        val body = text.removePrefix("vmess://").substringBefore('#')
        val json = runCatching { JSONObject(String(decodeBase64(body), Charsets.UTF_8)) }
            .getOrElse { throw InvalidConfig("vmess: body is not base64 JSON") }

        val host = json.optString("add").takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("vmess: missing \"add\"")
        val port = json.optString("port").toIntOrNull()?.takeIf { it in 1..65535 }
            ?: throw InvalidConfig("vmess: missing or invalid \"port\"")
        val uuid = json.optString("id").takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("vmess: missing \"id\"")

        val security = if (json.optString("tls").equals("tls", true)) "tls" else "none"
        val network = json.optString("net").takeIf { it.isNotBlank() } ?: "tcp"
        val q = mutableMapOf<String, String>()
        json.optString("sni").takeIf { it.isNotBlank() }?.let { q["sni"] = it }
        json.optString("host").takeIf { it.isNotBlank() }?.let { q["host"] = it }
        json.optString("path").takeIf { it.isNotBlank() }?.let { q["path"] = it }
        json.optString("fp").takeIf { it.isNotBlank() }?.let { q["fp"] = it }
        json.optString("alpn").takeIf { it.isNotBlank() }?.let { q["alpn"] = it }

        val alterId = json.optString("aid").toIntOrNull() ?: 0
        val cipher = json.optString("scy").takeIf { it.isNotBlank() } ?: "auto"
        val warnings = mutableListOf<String>()
        requireUuid(uuid, "vmess")
        // VMess carries the TCP header obfuscation in `type` ("http", "none"), not in a query key.
        json.optString("type").takeIf { it.isNotBlank() }?.let { q["headerType"] = it }

        val xray = JSONObject()
            .put("protocol", "vmess")
            .put(
                "settings",
                JSONObject().put(
                    "vnext",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put(
                                "users",
                                JSONArray().put(
                                    JSONObject()
                                        .put("id", uuid)
                                        .put("alterId", alterId)
                                        .put("security", cipher),
                                ),
                            ),
                    ),
                ),
            )
            .put("streamSettings", streamSettings(network, security, q, warnings))

        return Parsed(
            Profile(
                id = idFor(text),
                name = json.optString("ps").takeIf { it.isNotBlank() } ?: "vmess ${host.substringBefore('.')}",
                protocol = "VMESS",
                core = "XRAY",
                host = host,
                port = port,
                xray = xray,
                awg = null,
                fingerprint = fingerprintFor(security, param(q, "fp"), warnings),
            ),
            warnings,
        )
    }

    /* ------------------------------------------------------------------ dns */

    /**
     * `dns://v.example.com?key=<hex>&method=3&resolvers=1.1.1.1,8.8.8.8`
     *
     * The authority is the tunnel domain, not a server address: queries go to resolvers, and the
     * authoritative nameserver for that domain is the tunnel server (D-113).
     */
    private fun parseDns(text: String): Parsed {
        val uri = Uri.parse(text)
        val domain = uri.host?.takeIf { it.isNotBlank() }
            ?: throw InvalidConfig("dns: missing tunnel domain")
        val q = query(uri)
        val method = param(q, "method")?.toIntOrNull() ?: DnsConfig.DEFAULT_METHOD
        val warnings = mutableListOf<String>()

        val resolvers = (param(q, "resolvers") ?: "")
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val settings = JSONObject()
            .put("domain", domain)
            .put("method", method)
            .apply { param(q, "key")?.let { put("key", it.trim()) } }
            .put("resolvers", JSONArray(resolvers))
            .apply { param(q, "balancing")?.toIntOrNull()?.let { put("balancing", it) } }
            .apply { param(q, "duplication")?.toIntOrNull()?.let { put("duplication", it) } }

        DnsConfig.validate(settings)?.let { throw InvalidConfig(it) }
        if (resolvers.isEmpty()) warnings.add("no resolvers in the link; add them before connecting")

        return Parsed(
            Profile(
                id = idFor(text),
                name = nameOf(uri, "dns $domain"),
                protocol = "DNS",
                core = "DNS",
                host = domain,
                port = 53,
                xray = null,
                awg = null,
                dns = settings,
            ),
            warnings,
        )
    }

    /* ---------------------------------------------------------- whitedns links */

    /**
     * `masterdns://<base64 json>` and its siblings, as WhiteDNS exports them:
     * `{ profile: { engine, server: { domain, encryption_key, encryption_method }, resolvers } }`.
     *
     * Only `masterdns` maps onto the engine this app ships. `cottendns` and `soar` are different
     * engines with their own binaries; refusing them by name is better than importing a profile that
     * silently cannot connect.
     */
    private fun parseWhiteDns(text: String, scheme: String): Parsed {
        val body = text.substringAfter("://").trim()
        val json = runCatching {
            JSONObject(String(decodeBase64(body), Charsets.UTF_8))
        }.getOrElse { throw InvalidConfig("$scheme: body is not base64 JSON") }

        val profile = json.optJSONObject("profile") ?: throw InvalidConfig("$scheme: profile missing")
        val engine = profile.optString("engine").ifBlank { scheme }
        if (engine != "masterdns") {
            throw InvalidConfig(
                "$scheme links need the ${engine} engine, which this build does not include; " +
                    "only masterdns is bundled",
            )
        }

        val server = profile.optJSONObject("server")
            ?: throw InvalidConfig("$scheme: server missing")
        val domain = server.optString("domain").trim()
        if (domain.isBlank()) throw InvalidConfig("$scheme: server.domain missing")

        val key = server.optString("encryption_key").trim()
        val method = server.optInt("encryption_method", DnsConfig.DEFAULT_METHOD)

        val resolvers = mutableListOf<String>()
        profile.optJSONArray("resolvers")?.let { array ->
            for (i in 0 until array.length()) {
                array.optString(i).trim().takeIf { it.isNotEmpty() }?.let { resolvers.add(it) }
            }
        }
        val warnings = mutableListOf<String>()
        if (resolvers.isEmpty()) {
            val fallback = listOf("1.1.1.1", "8.8.8.8")
            resolvers.addAll(fallback)
            warnings.add("no resolvers in the link; using ${fallback.joinToString()}")
        }

        val settings = JSONObject()
            .put("domain", domain)
            .put("method", method)
            .put("key", key)
            .put("resolvers", JSONArray(resolvers))
        DnsConfig.validate(settings)?.let { throw InvalidConfig(it) }

        val name = profile.optString("name").takeIf { it.isNotBlank() } ?: "masterdns ${domain.take(18)}"
        return Parsed(
            Profile(
                id = idFor(text),
                name = name,
                protocol = "DNS",
                core = "DNS",
                host = domain,
                port = 53,
                xray = null,
                awg = null,
                dns = settings,
            ),
            warnings,
        )
    }

    /* ------------------------------------------------------------------ ss */

    private fun parseShadowsocks(text: String): Parsed {
        // Two shapes exist: ss://b64(method:pass)@host:port and ss://b64(method:pass@host:port).
        var body = text.removePrefix("ss://")
        val fragment = body.substringAfter('#', "")
        body = body.substringBefore('#')
        val queryPart = body.substringAfter('?', "")
        if (queryPart.isNotEmpty()) body = body.substringBefore('?')
        // The SIP003 plugin runs a separate binary this build does not ship. Importing the link as
        // plain Shadowsocks produces a profile that can never connect, so refuse it with a reason.
        if (queryPart.contains("plugin=")) {
            throw InvalidConfig("ss: SIP003 plugins are not supported by this build")
        }

        val method: String
        val password: String
        val host: String
        val port: Int

        if (body.contains('@')) {
            val credentials = body.substringBefore('@')
            val authority = body.substringAfter('@')
            val decoded = runCatching { String(decodeBase64(credentials), Charsets.UTF_8) }
                .getOrElse { throw InvalidConfig("ss: credentials are not base64") }
            val sep = decoded.indexOf(':')
            if (sep <= 0) throw InvalidConfig("ss: credentials are not method:password")
            method = decoded.substring(0, sep)
            password = decoded.substring(sep + 1)
            host = authority.substringBeforeLast(':').trim('[', ']')
            port = authority.substringAfterLast(':').toIntOrNull() ?: -1
        } else {
            val decoded = runCatching { String(decodeBase64(body), Charsets.UTF_8) }
                .getOrElse { throw InvalidConfig("ss: body is not base64") }
            val at = decoded.lastIndexOf('@')
            if (at <= 0) throw InvalidConfig("ss: body is not method:password@host:port")
            val credentials = decoded.substring(0, at)
            val authority = decoded.substring(at + 1)
            val sep = credentials.indexOf(':')
            if (sep <= 0) throw InvalidConfig("ss: credentials are not method:password")
            method = credentials.substring(0, sep)
            password = credentials.substring(sep + 1)
            host = authority.substringBeforeLast(':').trim('[', ']')
            port = authority.substringAfterLast(':').toIntOrNull() ?: -1
        }

        if (method.isBlank()) throw InvalidConfig("ss: missing method")
        if (port !in 1..65535) throw InvalidConfig("ss: missing or invalid port")
        if (host.isBlank()) throw InvalidConfig("ss: missing server address")

        val xray = JSONObject()
            .put("protocol", "shadowsocks")
            .put(
                "settings",
                JSONObject().put(
                    "servers",
                    JSONArray().put(
                        JSONObject()
                            .put("address", host)
                            .put("port", port)
                            .put("method", method)
                            .put("password", password),
                    ),
                ),
            )

        return Parsed(
            Profile(
                id = idFor(text),
                name = fragment.takeIf { it.isNotBlank() }?.let(::urlDecode) ?: "ss ${host.substringBefore('.')}",
                protocol = "SHADOWSOCKS",
                core = "XRAY",
                host = host,
                port = port,
                xray = xray,
                awg = null,
            ),
        )
    }

    /* ---------------------------------------------------------------- shared */

    private fun streamSettings(
        network: String,
        security: String,
        q: Map<String, String>,
        warnings: MutableList<String>,
    ): JSONObject {
        val stream = JSONObject().put("network", network).put("security", security)

        when (network) {
            "ws" -> stream.put(
                "wsSettings",
                JSONObject()
                    .put("path", param(q, "path") ?: "/")
                    .apply {
                        param(q, "host")?.let { put("headers", JSONObject().put("Host", it)) }
                    },
            )
            // HTTPUpgrade looks like WebSocket on the wire but has its own settings block, and its
            // host is a field, not a header. Writing wsSettings here silently produces a config that
            // cannot complete the upgrade.
            "httpupgrade" -> stream.put(
                "httpupgradeSettings",
                JSONObject()
                    .put("path", param(q, "path") ?: "/")
                    .apply { param(q, "host")?.let { put("host", it) } },
            )
            "grpc" -> stream.put("grpcSettings", JSONObject().put("serviceName", param(q, "serviceName") ?: ""))
            // XHTTP (formerly SplitHTTP) is what the core recommends over HTTPUpgrade now.
            "xhttp", "splithttp" -> stream.put(
                "xhttpSettings",
                JSONObject()
                    .put("path", param(q, "path") ?: "/")
                    .apply { param(q, "host")?.let { put("host", it) } },
            )
            "h2", "http" -> stream.put(
                "httpSettings",
                JSONObject().put("path", param(q, "path") ?: "/").apply {
                    param(q, "host")?.let { put("host", JSONArray().put(it)) }
                },
            )
            "tcp" -> if (param(q, "headerType").equals("http", true)) {
                stream.put("tcpSettings", JSONObject().put("header", JSONObject().put("type", "http")))
            }
            else -> Unit
        }

        when (security) {
            "tls" -> stream.put(
                "tlsSettings",
                JSONObject().apply {
                    (param(q, "sni") ?: param(q, "host"))?.let { put("serverName", it) }
                    param(q, "alpn")?.let { put("alpn", JSONArray(it.split(',').map { s -> s.trim() })) }
                },
            )
            "reality" -> {
                val pbk = param(q, "pbk")
                if (pbk.isNullOrBlank()) {
                    throw InvalidConfig("vless: reality needs pbk (public key)")
                }
                val sni = param(q, "sni") ?: ""
                val sid = param(q, "sid") ?: ""
                val spx = param(q, "spx")
                // The core rejects a bad REALITY block at startup with a message the UI never sees.
                // Validate here, where the reason can be shown next to the pasted link.
                RealityRules.validateServerName(sni)?.let { throw InvalidConfig("vless: $it") }
                RealityRules.validatePublicKey(pbk)?.let { throw InvalidConfig("vless: $it") }
                RealityRules.validateShortId(sid)?.let { throw InvalidConfig("vless: $it") }
                spx?.let { RealityRules.validateSpiderX(it)?.let { reason -> throw InvalidConfig("vless: $reason") } }
                stream.put(
                    "realitySettings",
                    JSONObject()
                        .put("serverName", sni)
                        .put("publicKey", pbk)
                        .put("shortId", sid)
                        .apply { spx?.let { put("spiderX", it) } },
                )
            }
            "none" -> Unit
            else -> {
                warnings.add("unknown security \"$security\" imported as none")
                stream.put("security", "none")
            }
        }
        return stream
    }

    /**
     * `fm=` carries `streamSettings.finalMask` as URL-encoded JSON — usually the fragment that splits
     * the ClientHello. Dropping it changes how the handshake looks on the wire, which is the whole
     * point of the link.
     */
    private fun finalMask(raw: String?, security: String, warnings: MutableList<String>): JSONObject? {
        if (raw.isNullOrBlank()) return null
        val mask = runCatching { JSONObject(raw) }.getOrElse {
            warnings.add("fm is not valid JSON; fragment setting ignored")
            return null
        }
        val problem = FinalMaskRules.validate(mask)
        if (problem != null) {
            warnings.add("$problem; fragment setting ignored")
            return null
        }
        // D-76: splitting the ClientHello breaks REALITY authentication. Drop the mask and keep the
        // profile usable instead of importing a config that cannot complete its handshake.
        if (security == "reality") {
            val tcp = mask.optJSONArray("tcp")
            val fragment = tcp != null && (0 until tcp.length())
                .any { tcp.optJSONObject(it)?.optString("type") == "fragment" }
            if (fragment) {
                warnings.add("reality cannot use the fragment mask; ignored")
                return null
            }
        }
        return mask
    }

    /** `mtu=` is our own link parameter; clients that do not know it ignore it. */
    private fun mtuParam(q: Map<String, String>): Int? =
        param(q, "mtu")?.toIntOrNull()?.takeIf { MtuPolicy.isValid(it) }

    private fun fingerprintFor(security: String, fp: String?, warnings: MutableList<String>): String? {
        if (security == "none") return null
        val value = fp?.lowercase()?.takeIf { it.isNotBlank() }
        if (value == null) {
            warnings.add("no fingerprint in the link; using $DEFAULT_FINGERPRINT")
            return DEFAULT_FINGERPRINT
        }
        if (!TlsFingerprint.isValid(value)) {
            warnings.add("unknown fingerprint \"$value\"; using $DEFAULT_FINGERPRINT")
            return DEFAULT_FINGERPRINT
        }
        return value
    }

    /**
     * A VLESS/VMess id the core accepts: a 36-character UUID or its 32-hex-character form. The core
     * refuses anything else at config load, which the UI reports only as "tunnel did not start".
     */
    private fun requireUuid(value: String, scheme: String) {
        val compact = value.replace("-", "")
        val hex = compact.length == 32 && compact.all { c ->
            c.isDigit() || c.lowercaseChar() in 'a'..'f'
        }
        if (!hex) throw InvalidConfig("$scheme: id is not a UUID")
    }

    /** Share links mix casing (`serviceName`, `headerType`); the core's keys are exact, so we compare
     *  case-insensitively here and emit the core's spelling. */
    private fun param(query: Map<String, String>, key: String): String? {
        query[key]?.let { return it }
        return query.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
    }

    private fun query(uri: Uri): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        uri.queryParameterNames.forEach { key ->
            val value = uri.getQueryParameter(key) ?: return@forEach
            out[key] = value
        }
        return out
    }

    private fun nameOf(uri: Uri, fallback: String): String {
        val raw = uri.fragment ?: return fallback
        val decoded = urlDecode(raw)
        return decoded.takeIf { it.isNotBlank() } ?: fallback
    }

    private fun urlDecode(value: String): String =
        runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private fun decodeBase64(text: String): ByteArray {
        val cleaned = text.trim().replace('-', '+').replace('_', '/').trimEnd('=')
        val padded = cleaned + "=".repeat((4 - cleaned.length % 4) % 4)
        return runCatching { Base64.getDecoder().decode(padded) }
            .getOrElse { throw InvalidConfig("invalid base64") }
    }

    /** Stable id, so pasting the same link twice updates instead of duplicating. */
    fun idFor(link: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(link.trim().toByteArray())
        return "user-" + digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
