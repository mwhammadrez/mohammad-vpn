package ir.mohammad.vpn.core

/**
 * SPEC SRC-2 and the "oob" transport trick (see `docs/AUDIT_PATTNG.md`).
 *
 * `oob.<b1>,<b2>,...` appends one byte to each of the first writes on the dialed TCP connection and
 * sends that write with the TCP urgent flag. A conforming peer's kernel drops the urgent byte, while
 * a middlebox that mishandles the urgent pointer desynchronises on those segments — usually on the
 * TLS ClientHello. It is a transport trick, not an encryption shortcut: it works on REALITY too.
 *
 * Validated here so a typo fails at bundle load instead of producing a core that refuses to start.
 */
object DialModeRules {

    const val OOB_PREFIX = "oob."

    /** Returns an error fragment, or null when [value] is acceptable (including absent). */
    fun validate(value: String?): String? {
        if (value.isNullOrBlank()) return null
        if (!value.startsWith(OOB_PREFIX)) return "dialMode must start with $OOB_PREFIX"
        val list = value.removePrefix(OOB_PREFIX)
        if (list.isEmpty()) return "dialMode has no bytes"
        val parts = list.split(",")
        parts.forEach { part ->
            val n = part.trim().toIntOrNull() ?: return "dialMode byte '$part' is not a number"
            if (n !in 0..255) return "dialMode byte '$part' is outside 0..255"
        }
        return null
    }

    fun isOob(value: String?): Boolean = value != null && value.startsWith(OOB_PREFIX)
}
