package ir.mohammad.vpn.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * `streamSettings.finalMask` — the core's transport-shaping layer, exposed per profile so the operator
 * can set `merge` (coalesce the first outbound writes) or `fragment` (split them) without editing the
 * core.
 *
 * The core rejects an unknown mask type at startup with a plain error, which on a phone looks like
 * "the VPN will not start". Validating here turns that into a bundle error that names the field.
 *
 * Type sets mirror the pinned core's loaders (`infra/conf/transport_finalmask.go`); `merge` is our
 * addition (DEFECT-…? see DECISIONS.md D-101).
 */
object FinalMaskRules {

    val TCP_TYPES = setOf("filter", "header-custom", "fragment", "sudoku", "xmc", "merge")
    val UDP_TYPES = setOf(
        "filter", "header-custom", "mkcp-legacy", "noise", "salamander",
        "sudoku", "xdns", "xicmp", "realm", "udphop",
    )

    private const val MAX_TIMES = 100
    private const val MAX_DELAY_MS = 5000
    private const val MAX_MAX_BYTES = 1 shl 20

    /** Returns an error fragment, or null when [mask] is acceptable (including absent). */
    fun validate(mask: JSONObject?): String? {
        if (mask == null) return null
        val keys = mask.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (key != "tcp" && key != "udp") return "finalMask: unknown key '$key'"
        }
        validateList(mask.optJSONArray("tcp"), "tcp", TCP_TYPES)?.let { return it }
        validateList(mask.optJSONArray("udp"), "udp", UDP_TYPES)?.let { return it }
        return null
    }

    private fun validateList(list: JSONArray?, side: String, allowed: Set<String>): String? {
        if (list == null) return null
        for (i in 0 until list.length()) {
            val entry = list.optJSONObject(i) ?: return "finalMask.$side[$i] is not an object"
            val type = entry.optString("type")
            if (type !in allowed) return "finalMask.$side[$i]: unsupported type '$type'"
            if (type == "merge") validateMerge(entry.optJSONObject("settings"), side, i)?.let { return it }
        }
        return null
    }

    /** Merge only makes sense on `tcp`; the core's udp loader has no such mask. */
    private fun validateMerge(settings: JSONObject?, side: String, index: Int): String? {
        if (settings == null) return null
        val times = settings.optInt("times", 0)
        if (times < 0 || times > MAX_TIMES) return "finalMask.$side[$index]: times out of range"
        val delay = settings.optInt("delayMs", 0)
        if (delay < 0 || delay > MAX_DELAY_MS) return "finalMask.$side[$index]: delayMs out of range"
        val maxBytes = settings.optInt("maxBytes", 0)
        if (maxBytes < 0 || maxBytes > MAX_MAX_BYTES) {
            return "finalMask.$side[$index]: maxBytes out of range"
        }
        return null
    }
}
