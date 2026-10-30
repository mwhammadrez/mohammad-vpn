package ir.mohammad.vpn.data

import android.content.Context
import ir.mohammad.vpn.BuildConfig
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

    fun load(context: Context): OperatorBundle {
        val bytes = try {
            context.assets.open("operator/bundle.enc").use { it.readBytes() }
        } catch (e: Exception) {
            throw BundleException("operator bundle missing")
        }
        if (bytes.size <= HEADER_LEN || String(bytes, 0, 4, Charsets.US_ASCII) != MAGIC) {
            throw BundleException("operator bundle header invalid")
        }

        val salt = bytes.copyOfRange(4, 4 + SALT_LEN)
        val iv = bytes.copyOfRange(4 + SALT_LEN, HEADER_LEN)
        val cipherText = bytes.copyOfRange(HEADER_LEN, bytes.size)

        val password = BuildConfig.OPERATOR_PASSWORD
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

        var mtu = root.optInt("defaultMtu", 1500)
        if (mtu !in 1200..1500) mtu = 1500

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

            val xray = o.optJSONObject("xray")
            val awg = o.optJSONObject("awg")
            if (core == "XRAY") {
                if (xray == null) throw BundleException("xray outbound missing")
                validateXray(protocol, xray)
            }
            if (core == "AMNEZIAWG" && awg == null) throw BundleException("awg settings missing")

            result.add(Profile(id, name, protocol, core, host, port, xray, awg))
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
        "WIREGUARD", "AMNEZIAWG", "HYSTERIA2",
    )
}
