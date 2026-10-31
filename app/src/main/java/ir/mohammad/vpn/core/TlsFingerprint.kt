package ir.mohammad.vpn.core

import ir.mohammad.vpn.data.Profile
import org.json.JSONObject

/**
 * SPEC TLS-2, TLS-7.
 *
 * The allow-list is **read from the pinned core**, not from documentation: it is exactly the set of
 * names `GetFingerprint` can resolve in xray-core v26.10.29
 * (`transport/internet/tls/tls.go`, commit 0afec66085a85191e9dd2a4a6646c7c9a94688d1):
 * `PresetFingerprints` + `ModernFingerprints` + `OtherFingerprints`.
 *
 * `unsafe` is excluded on purpose. It is a key of `PresetFingerprints` with a nil value that `init()`
 * never assigns, so `GetFingerprint("unsafe")` returns nil and the core fails the connection. Accepting
 * it here would only move that failure later.
 */
object TlsFingerprint {

    /** Every name the pinned core accepts. 62 entries. */
    val VALID: Set<String> = setOf(
        "360", "android", "chrome", "edge", "firefox", "hello360_11_0", "hello360_7_5",
        "hello360_auto", "helloandroid_11_okhttp", "hellochrome_100", "hellochrome_100_psk",
        "hellochrome_102", "hellochrome_106_shuffle", "hellochrome_112_psk_shuf",
        "hellochrome_114_padding_psk_shuf", "hellochrome_115_pq", "hellochrome_115_pq_psk",
        "hellochrome_120", "hellochrome_120_pq", "hellochrome_131", "hellochrome_133",
        "hellochrome_58", "hellochrome_62", "hellochrome_70", "hellochrome_72", "hellochrome_83",
        "hellochrome_87", "hellochrome_96", "hellochrome_auto", "helloedge_106", "helloedge_85",
        "helloedge_auto", "hellofirefox_102", "hellofirefox_105", "hellofirefox_120",
        "hellofirefox_148", "hellofirefox_55", "hellofirefox_56", "hellofirefox_63",
        "hellofirefox_65", "hellofirefox_99", "hellofirefox_auto", "hellogolang", "helloios_11_1",
        "helloios_12_1", "helloios_13", "helloios_14", "helloios_auto", "helloqq_11_1",
        "helloqq_auto", "hellorandomized", "hellorandomizedalpn", "hellorandomizednoalpn",
        "hellosafari_16_0", "hellosafari_26_3", "hellosafari_auto", "ios", "qq", "random",
        "randomized", "randomizednoalpn", "safari",
    )

    /**
     * Names that resemble a real browser. The rest are formally valid but openly fingerprintable
     * (`hellogolang` is Go's own stack — the exact signal this milestone removes).
     */
    val RECOMMENDED: Set<String> = setOf(
        "chrome", "firefox", "safari", "ios", "android", "edge", "random", "randomized",
        "randomizednoalpn",
    )

    fun isValid(name: String): Boolean = name in VALID

    fun isRecommended(name: String): Boolean = name in RECOMMENDED

    /** The fingerprint effectively in force for [profile], or null when security is `none`. */
    fun effectiveFingerprint(profile: Profile): String? {
        profile.fingerprint?.let { return it }
        val stream = profile.xray?.optJSONObject("streamSettings") ?: return null
        return when (stream.optString("security", "none").lowercase()) {
            "tls" -> stream.optJSONObject("tlsSettings")?.optString("fingerprint")?.takeIf { it.isNotBlank() }
            "reality" -> stream.optJSONObject("realitySettings")?.optString("fingerprint")?.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /** SPEC TLS-7: a name only, never a hash and never a header. */
    fun diagnostic(profile: Profile): String =
        "tls fingerprint=${effectiveFingerprint(profile) ?: "none"}"
}
