package ir.mohammad.vpn.core

import org.json.JSONObject

/**
 * DNS-tunnel profiles, rendered for the MIT engine (MasterDnsVPN) that ships in `jniLibs`.
 *
 * The engine is a separate executable with a TOML config, so everything here is plain text: no
 * dependency on the app, and every rule below is testable on the JVM (D-113).
 *
 * Why a DNS path at all: it is the only thing left when a network permits nothing but DNS on
 * port 53. It is slow by nature — tens to hundreds of KB/s — so it is a fallback, never the default.
 */
object DnsConfig {

    /** The engine's local SOCKS5 listener in VPN mode. Distinct from Xray's 10808. */
    const val SOCKS_PORT = 10809

    /** Spare port for a temporary engine during a delay test. */
    const val PROBE_PORT = 11809

    const val BINARY_NAME = "libmdvpn_client.so"

    /** 0 none, 1 XOR, 2 ChaCha20, 3 AES-128-GCM, 4 AES-192-GCM, 5 AES-256-GCM. */
    const val DEFAULT_METHOD = 3

    val METHODS = mapOf(
        0 to "none",
        1 to "xor",
        2 to "chacha20",
        3 to "aes-128-gcm",
        4 to "aes-192-gcm",
        5 to "aes-256-gcm",
    )

    /**
     * The client hashes whatever key text it is given (`deriveKey`: SHA-256 for ChaCha20/AES-256,
     * MD5 for AES-128), so there is no required length on this side — the server's key file is the
     * one with a fixed size. Real public profiles hand out 64-character hex keys for AES-256-GCM;
     * demanding 32 here rejected every one of them (found by testing against a live server).
     *
     * The only rules that matter: a key exists when the method is not `none`, and it is a single
     * token (no spaces, quotes or newlines that would break the TOML line).
     */
    fun keyProblem(method: Int, key: String): String? {
        if (method == 0) return null
        if (key.isEmpty()) return "dns key missing"
        if (key.any { it.isWhitespace() }) return "dns key contains whitespace"
        if (key.length > 128) return "dns key is too long"
        return null
    }

    fun validate(settings: JSONObject?): String? {
        if (settings == null) return "dns settings missing"
        val domain = settings.optString("domain").trim()
        if (domain.isBlank()) return "dns domain missing"
        if (hasControl(domain)) return "dns domain contains a control character"
        if (domain.contains(' ') || !domain.contains('.')) return "dns domain is not a host name"
        if (domain.length > 200) return "dns domain is too long"

        val method = settings.optInt("method", DEFAULT_METHOD)
        if (method !in METHODS.keys) return "dns method must be 0..5"

        keyProblem(method, settings.optString("key"))?.let { return it }

        val resolvers = resolverList(settings)
        if (resolvers.isEmpty()) return "dns resolvers missing"
        if (resolvers.size > 64) return "dns resolvers: at most 64"
        resolvers.forEach { entry ->
            if (hasControl(entry)) return "dns resolver contains a control character"
            val (host, port) = splitResolver(entry)
            if (host.isBlank()) return "dns resolver \"$entry\" has no address"
            if (port == null || port !in 1..65535) return "dns resolver \"$entry\" has a bad port"
        }
        return null
    }

    /**
     * `1.1.1.1`, `1.1.1.1:53`, `[2001:4860::8888]:53` and a bare `2001:4860::8888` all occur in the
     * wild. Splitting on the last colon turns a bare IPv6 address into a bogus host:port pair.
     */
    internal fun splitResolver(entry: String): Pair<String, Int?> {
        val value = entry.trim()
        if (value.startsWith("[")) {
            val end = value.indexOf(']')
            if (end < 0) return value to null
            val host = value.substring(1, end)
            val rest = value.substring(end + 1)
            val port = if (rest.startsWith(":")) rest.drop(1).toIntOrNull() else 53
            return host to port
        }
        val colons = value.count { it == ':' }
        if (colons > 1) return value to 53
        if (colons == 1) return value.substringBefore(':') to value.substringAfter(':').toIntOrNull()
        return value to 53
    }

    private fun hasControl(value: String): Boolean = value.any { it.code < 0x20 || it.code == 0x7F }

    fun resolverList(settings: JSONObject?): List<String> {
        val array = settings?.optJSONArray("resolvers") ?: return emptyList()
        val out = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotEmpty()) out.add(value)
        }
        return out
    }

    /** One resolver per line, exactly as the engine's `-resolvers` file expects. */
    fun renderResolvers(settings: JSONObject): String =
        resolverList(settings).joinToString("\n", postfix = "\n")

    fun render(settings: JSONObject, listenPort: Int): String {
        val method = settings.optInt("method", DEFAULT_METHOD)
        val domain = settings.optString("domain").trim()
        val key = settings.optString("key")
        val balancing = settings.optInt("balancing", 3).coerceIn(1, 8)
        val duplication = settings.optInt("duplication", 2).coerceIn(1, 10)

        return buildString {
            appendLine("# generated by Mohammad — do not edit on device")
            appendLine("DOMAINS = [\"${escape(domain)}\"]")
            appendLine("DATA_ENCRYPTION_METHOD = $method")
            if (method != 0) appendLine("ENCRYPTION_KEY = \"${escape(key)}\"")
            appendLine("PROTOCOL_TYPE = \"SOCKS5\"")
            appendLine("LISTEN_IP = \"127.0.0.1\"")
            appendLine("LISTEN_PORT = $listenPort")
            appendLine("SOCKS5_AUTH = false")
            appendLine("LOCAL_DNS_ENABLED = false")
            appendLine("RESOLVER_BALANCING_STRATEGY = $balancing")
            appendLine("PACKET_DUPLICATION_COUNT = $duplication")
            appendLine("SETUP_PACKET_DUPLICATION_COUNT = ${(duplication + 1).coerceAtMost(12)}")
            appendLine("MIN_UPLOAD_MTU = ${settings.optInt("minUploadMtu", 38)}")
            appendLine("MIN_DOWNLOAD_MTU = ${settings.optInt("minDownloadMtu", 200)}")
            appendLine("MAX_UPLOAD_MTU = ${settings.optInt("maxUploadMtu", 150)}")
            appendLine("MAX_DOWNLOAD_MTU = ${settings.optInt("maxDownloadMtu", 4000)}")
            appendLine("MTU_TEST_RETRIES = 2")
            appendLine("MTU_TEST_TIMEOUT = 2.0")
            appendLine("MTU_TEST_PARALLELISM = 8")
            appendLine("LOG_LEVEL = \"${escape(settings.optString("logLevel", "INFO"))}\"")
        }.trimEnd()
    }

    private fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

    /** Human-readable summary for the log: never the key. */
    fun diagnostic(settings: JSONObject): String {
        val method = settings.optInt("method", DEFAULT_METHOD)
        return "dns domain=${settings.optString("domain")} " +
            "cipher=${METHODS[method] ?: method} resolvers=${resolverList(settings).size}"
    }
}
