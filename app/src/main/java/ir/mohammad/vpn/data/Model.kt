package ir.mohammad.vpn.data

import android.content.Context
import ir.mohammad.vpn.BuildConfig
import ir.mohammad.vpn.core.MtuPolicy
import ir.mohammad.vpn.core.DialModeRules
import ir.mohammad.vpn.core.FinalMaskRules
import ir.mohammad.vpn.core.RealityRules
import ir.mohammad.vpn.core.TlsFingerprint
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Default rule-set sources; overridable per bundle. */
const val DEFAULT_GEOSITE_URL =
    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geosite.dat"
const val DEFAULT_GEOIP_URL =
    "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/latest/download/geoip.dat"

/** A single operator-provided connection profile. */
data class Profile(
    val id: String,
    val name: String,
    val protocol: String,
    val core: String,
    val host: String,
    val port: Int,
    val xray: JSONObject?,
    val awg: JSONObject?,
    /** Optional per-profile override; null means "use the bundle default". SPEC MTU-1. */
    val mtu: Int? = null,
    /** uTLS ClientHello to present. Required whenever `security` is tls or reality. SPEC TLS-2. */
    val fingerprint: String? = null,
    /** XTLS flow; only `xtls-rprx-vision`, and only with REALITY. SPEC TLS-4. */
    val flow: String? = null,
    /** "oob.<b1>,<b2>,..." — TCP urgent-byte desync for the first writes. See DialModeRules. */
    val dialMode: String? = null,
    /** TLS cipher-suite list, colon separated; passed straight to the core's TLS settings. */
    val cipherSuites: String? = null,
    /** `streamSettings.finalMask`: transport shaping (`merge`, `fragment`, ...). Validated by FinalMaskRules. */
    val finalMask: JSONObject? = null,
    /** DNS-tunnel settings when [core] is `DNS`; see DnsConfig. */
    val dns: JSONObject? = null,
)

/** Decrypted operator bundle plus its defaults. */
data class OperatorBundle(
    val profiles: List<Profile>,
    val defaultProfileId: String?,
    val dnsServers: List<String>,
    val mtu: Int,
    val tunIpv6: Boolean = false,
    val tunAddressIndex: Int = 0,
    val geositeUrl: String = DEFAULT_GEOSITE_URL,
    val geoipUrl: String = DEFAULT_GEOIP_URL,
)

class BundleException(message: String) : Exception(message)

/**
 * Loads and decrypts the operator bundle shipped in assets.
 *
 * Asset format: "MHB1" | salt[16] | iv[12] | AES-256-GCM(ciphertext||tag)
 * Key: PBKDF2-HMAC-SHA256(password, salt, 210000, 32 bytes)
 */
object OperatorBundleLoader {

    private const val MAGIC = "MHB1"
    private const val ITERATIONS = 210_000
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val HEADER_LEN = 4 + SALT_LEN + IV_LEN

    fun load(context: Context): OperatorBundle =
        decrypt(readAsset(context), BuildConfig.OPERATOR_PASSWORD)

    /** Reads the encrypted asset. Separate so the missing-asset failure stays typed and testable. */
    internal fun readAsset(context: Context): ByteArray = try {
        context.assets.open("operator/bundle.enc").use { it.readBytes() }
    } catch (e: Exception) {
        throw BundleException("operator bundle missing")
    }

    /**
     * Decrypts [bytes] with [password] and parses the result.
     *
     * `internal` so unit tests can exercise both the success and the failure path with a generated
     * fixture: the operator's real bundle is deliberately absent from the repository. The behaviour
     * is identical to [load], which is the only production entry point.
     */
    internal fun decrypt(bytes: ByteArray, password: String): OperatorBundle {
        if (bytes.size <= HEADER_LEN || String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) {
            throw BundleException("operator bundle header invalid")
        }

        val salt = bytes.copyOfRange(4, 4 + SALT_LEN)
        val iv = bytes.copyOfRange(4 + SALT_LEN, HEADER_LEN)
        val cipherText = bytes.copyOfRange(HEADER_LEN, bytes.size)

        if (password.isEmpty()) throw BundleException("operator password not configured")

        val keyBytes = try {
            val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, 256)
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } catch (e: Exception) {
            throw BundleException("key derivation failed")
        }

        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(cipherText)
        } catch (e: Exception) {
            throw BundleException("operator bundle decryption failed")
        }

        return parse(String(plain, Charsets.UTF_8))
    }

    fun parse(json: String): OperatorBundle {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw BundleException("operator bundle is not valid JSON")
        }
        if (root.optInt("schemaVersion", -1) != 1) {
            throw BundleException("unsupported schema version")
        }
        val array = root.optJSONArray("profiles") ?: throw BundleException("profiles missing")
        if (array.length() == 0) throw BundleException("profiles empty")

        val defaults = root.optJSONArray("defaultDnsServers")
        val dns = ArrayList<String>()
        if (defaults != null) {
            for (i in 0 until defaults.length()) {
                val server = defaults.optString(i)
                if (!isValidIp(server)) throw BundleException("defaultDnsServers entry invalid")
                dns.add(server)
            }
        }
        if (dns.isEmpty()) dns.addAll(listOf("1.1.1.1", "8.8.8.8"))

        val mtu = if (root.has("defaultMtu") && !root.isNull("defaultMtu")) {
            val raw = root.opt("defaultMtu")
            if (raw !is Number) throw BundleException("defaultMtu invalid")
            val value = raw.toInt()
            if (!MtuPolicy.isValid(value)) throw BundleException("defaultMtu out of range")
            value
        } else {
            MtuPolicy.DEFAULT_MTU
        }

        val result = ArrayList<Profile>(array.length())
        val seen = HashSet<String>()
        for (i in 0 until array.length()) {
            val o = array.optJSONObject(i) ?: throw BundleException("profile #$i malformed")
            val id = o.optString("id")
            val name = o.optString("name")
            val protocol = o.optString("protocol").uppercase()
            val core = o.optString("core").uppercase()
            val host = o.optString("serverHost")
            val port = o.optInt("serverPort", -1)

            if (id.isBlank() || !seen.add(id)) throw BundleException("profile id invalid or duplicated")
            if (name.isBlank() || name.length > 64) throw BundleException("profile name invalid")
            if (host.isBlank()) throw BundleException("profile host missing")
            if (port !in 1..65535) throw BundleException("profile port invalid")
            if (core !in setOf("XRAY", "AMNEZIAWG")) throw BundleException("unknown core $core")
            if (protocol !in PROTOCOLS) throw BundleException("unsupported protocol $protocol")

            val expectedCore = if (protocol == "WIREGUARD" || protocol == "AMNEZIAWG") "AMNEZIAWG" else "XRAY"
            if (core != expectedCore) {
                throw BundleException("protocol $protocol cannot run on core $core")
            }

            val profileMtu = if (o.has("mtu") && !o.isNull("mtu")) {
                val raw = o.opt("mtu")
                if (raw !is Number) throw BundleException("profile mtu invalid")
                val value = raw.toInt()
                if (!MtuPolicy.isValid(value)) throw BundleException("profile mtu out of range")
                value
            } else {
                null
            }

            val profileFingerprint = o.optString("fingerprint").takeIf { it.isNotBlank() }
            val profileFlow = o.optString("flow").takeIf { it.isNotBlank() }
            val profileDialMode = o.optString("dialMode").takeIf { it.isNotBlank() }
            DialModeRules.validate(profileDialMode)?.let { throw BundleException("profile dialMode: $it") }
            val profileCipherSuites = o.optString("cipherSuites").takeIf { it.isNotBlank() }
            val profileFinalMask = o.optJSONObject("finalMask")
            val profileDns = o.optJSONObject("dns")
            FinalMaskRules.validate(profileFinalMask)?.let { throw BundleException("profile $it") }

            val xray = o.optJSONObject("xray")
            val awg = o.optJSONObject("awg")
            if (core == "XRAY") {
                if (xray == null) throw BundleException("xray outbound missing")
                validateXray(protocol, xray)
                validateTransport(xray, profileFingerprint, profileFlow, profileFinalMask)
            }
            if (core == "AMNEZIAWG" && awg == null) throw BundleException("awg settings missing")
            if (core == "DNS") {
                ir.mohammad.vpn.core.DnsConfig.validate(profileDns)?.let { throw BundleException(it) }
            } else if (profileDns != null) {
                throw BundleException("dns settings require core=DNS")
            }

            result.add(
                Profile(
                    id, name, protocol, core, host, port, xray, awg, profileMtu,
                    profileFingerprint, profileFlow, profileDialMode, profileCipherSuites,
                    profileFinalMask, profileDns,
                ),
            )
        }

        val defaultId = root.optString("defaultProfileId").takeIf { it.isNotBlank() }
        if (defaultId != null && result.none { it.id == defaultId }) {
            throw BundleException("defaultProfileId does not match any profile")
        }

        val tunIpv6 = root.optBoolean("tunIpv6", false)
        val tunIndex = root.optInt("tunAddressIndex", 0).coerceAtLeast(0)
        val geo = root.optJSONObject("geoAssets")
        val geositeUrl = geo?.optString("geosite")?.takeIf { it.isNotBlank() } ?: DEFAULT_GEOSITE_URL
        val geoipUrl = geo?.optString("geoip")?.takeIf { it.isNotBlank() } ?: DEFAULT_GEOIP_URL

        return OperatorBundle(result, defaultId, dns, mtu, tunIpv6, tunIndex, geositeUrl, geoipUrl)
    }

    /**
     * SPEC TLS-2, TLS-3, TLS-6.
     *
     * A profile that turns on TLS without naming a fingerprint silently inherits Go's default
     * ClientHello, which no browser produces. That is a validation error here rather than a silent
     * default, because a silent default is how the app ended up emitting the loud one.
     */
    private fun validateTransport(
        xray: JSONObject,
        profileFingerprint: String?,
        flow: String?,
        profileFinalMask: JSONObject?,
    ) {
        val stream = xray.optJSONObject("streamSettings")
        val security = stream?.optString("security", "none")?.lowercase() ?: "none"

        // Flow is independent of whether TLS is on: validate it before any early return.
        RealityRules.validateFlow(flow, security)?.let { throw BundleException(it) }

        if (security !in setOf("none", "tls", "reality")) {
            throw BundleException("unsupported security: $security")
        }
        if (security == "none") {
            if (profileFingerprint != null) {
                throw BundleException("fingerprint requires security tls or reality")
            }
            return
        }
        if (security == "reality") {
            validateReality(stream!!, profileFinalMask)
        }

        val fromSettings = when (security) {
            "tls" -> stream?.optJSONObject("tlsSettings")?.optString("fingerprint")
            else -> stream?.optJSONObject("realitySettings")?.optString("fingerprint")
        }?.takeIf { it.isNotBlank() }

        val effective = profileFingerprint ?: fromSettings
            ?: throw BundleException("fingerprint missing for security=$security")
        if (!TlsFingerprint.isValid(effective)) {
            throw BundleException("unsupported fingerprint: $effective")
        }
        if (security == "reality" && TlsFingerprint.isDegraded(effective)) {
            throw BundleException("fingerprint $effective is rejected by the core with security=reality")
        }
    }

    /** SPEC TLS-3, TLS-5. Messages never echo the public key or the short id (NFR-5). */
    private fun validateReality(stream: JSONObject, profileMask: JSONObject?) {
        val reality = stream.optJSONObject("realitySettings")
            ?: throw BundleException("realitySettings missing for security=reality")

        RealityRules.validateServerName(reality.optString("serverName"))
            ?.let { throw BundleException("reality $it") }
        RealityRules.validatePublicKey(reality.optString("publicKey"))
            ?.let { throw BundleException("reality $it") }
        if (!reality.has("shortId")) throw BundleException("reality shortId missing")
        RealityRules.validateShortId(reality.optString("shortId"))
            ?.let { throw BundleException("reality $it") }
        if (reality.has("spiderX")) {
            RealityRules.validateSpiderX(reality.optString("spiderX"))
                ?.let { throw BundleException("reality $it") }
        }
        // D-76: fragmenting the ClientHello breaks REALITY's authentication. `merge` coalesces and
        // leaves the bytes alone, so it stays allowed.
        val streamMask = stream.optJSONObject("finalMask") ?: stream.optJSONObject("finalmask")
        if (hasMaskType(streamMask, "fragment") || hasMaskType(profileMask, "fragment")) {
            throw BundleException("reality cannot be combined with finalmask fragment")
        }
    }

    private fun hasMaskType(mask: JSONObject?, type: String): Boolean {
        val tcp = mask?.optJSONArray("tcp") ?: return false
        return (0 until tcp.length()).any { tcp.optJSONObject(it)?.optString("type") == type }
    }

    /** Only the Xray-accepted subset is allowed for Hysteria2 (SPEC FR-29). */
    private fun validateXray(protocol: String, xray: JSONObject) {
        val allowed = setOf("protocol", "settings", "streamSettings", "tag")
        val keys = xray.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key !in allowed) throw BundleException("unsupported xray key: $key")
        }
        val declared = xray.optString("protocol").lowercase()
        if (declared.isBlank()) throw BundleException("xray protocol missing")
        if (declared == "hysteria" || declared == "hysteria2") {
            val settings = xray.optJSONObject("settings")
            if (settings != null) {
                val sk = settings.keys()
                while (sk.hasNext()) {
                    val key = sk.next()
                    if (key !in setOf("server", "users")) {
                        throw BundleException("hysteria2 key not supported: $key")
                    }
                }
            }
        }
    }

    private fun isValidIp(value: String): Boolean {
        if (value.isBlank()) return false
        if (value.contains(':')) return true // IPv6 literal; core validates further
        val parts = value.split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    private val PROTOCOLS = setOf(
        "VLESS", "VMESS", "TROJAN", "SHADOWSOCKS", "SOCKS", "HTTP",
        "WIREGUARD", "AMNEZIAWG", "HYSTERIA2", "DNS",
    )
}
