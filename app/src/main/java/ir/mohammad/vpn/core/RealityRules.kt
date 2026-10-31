package ir.mohammad.vpn.core

import org.json.JSONObject
import java.util.Base64

/**
 * SPEC TLS-3, TLS-4, TLS-5.
 *
 * These rules are not invented here: they are the checks the pinned core performs when it builds a
 * REALITY transport (`infra/conf/transport_security.go`, xray-core v26.10.29):
 *
 *  - `publicKey` is base64 **RawURL** (no padding) and must decode to exactly 32 bytes;
 *  - `shortId` is hex, at most 16 characters, decoded into 8 bytes (an empty value is accepted);
 *  - `spiderX`, when present, must start with `/`;
 *  - `xtls-rprx-vision` is the only supported flow.
 *
 * Every function returns a message fragment on failure and null on success, so the caller can wrap it
 * in a `BundleException` without this object depending on the data layer.
 */
object RealityRules {

    const val VISION = "xtls-rprx-vision"

    private val HOSTNAME = Regex(
        """^(?=.{1,253}$)(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\.)+[a-zA-Z]{2,63}$""",
    )
    private val HEX = Regex("^[0-9a-fA-F]*$")
    private val BASE64URL = Regex("^[A-Za-z0-9_-]+$")

    /** The `security` value of a transport block; `none` when absent. */
    fun securityOf(xray: JSONObject?): String =
        xray?.optJSONObject("streamSettings")?.optString("security", "none")?.lowercase() ?: "none"

    fun validateServerName(value: String): String? = when {
        value.isBlank() -> "serverName missing"
        value.length > 253 -> "serverName longer than 253 characters"
        !HOSTNAME.matches(value) -> "serverName is not a plain hostname"
        else -> null
    }

    fun validatePublicKey(value: String): String? {
        if (value.isBlank()) return "publicKey missing"
        if (value.contains('=')) return "publicKey must not be padded"
        if (!BASE64URL.matches(value)) return "publicKey is not base64url"
        val decoded = try {
            Base64.getUrlDecoder().decode(value)
        } catch (e: IllegalArgumentException) {
            return "publicKey is not base64url"
        }
        if (decoded.size != 32) return "publicKey must decode to 32 bytes"
        return null
    }

    fun validateShortId(value: String): String? = when {
        value.length > 16 -> "shortId longer than 16 characters"
        value.length % 2 != 0 -> "shortId must have an even number of hex characters"
        !HEX.matches(value) -> "shortId must be hexadecimal"
        else -> null
    }

    fun validateSpiderX(value: String): String? =
        if (value.startsWith("/")) null else "spiderX must start with /"

    /**
     * Vision splices TLS records and therefore needs an outer TLS 1.3 session. The core accepts it
     * with TLS or REALITY; this project allows it with REALITY only, per SPEC TLS-4.
     */
    fun validateFlow(flow: String?, security: String): String? {
        if (flow.isNullOrBlank()) return null
        if (flow != VISION) return "flow $flow is not supported"
        if (security != "reality") return "flow $VISION requires security=reality"
        return null
    }

    /**
     * `finalmask.fragment` splits the ClientHello; REALITY's camouflage depends on that ClientHello
     * reaching the server intact, so the combination is rejected rather than silently degraded.
     */
    fun fragmentBreaksReality(security: String): Boolean = security.equals("reality", ignoreCase = true)
}
