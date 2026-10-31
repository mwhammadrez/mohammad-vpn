package ir.mohammad.vpn.data

/**
 * Turns the contents of a clipboard into profiles.
 *
 * Separate from storage and from the screen so the rules are testable without Android: which lines
 * are attempted, what happens when one of five links is broken, and what "the same link twice" means.
 */
object ImportBatch {

    data class Outcome(
        val profiles: List<Profile>,
        /** One message per line that looked like a link and still failed. */
        val failures: List<String> = emptyList(),
        /** Non-empty lines that were not a supported share link at all. */
        val ignored: Int = 0,
    ) {
        val message: String
            get() = when {
                profiles.isEmpty() && failures.isEmpty() -> "Clipboard has no vless/vmess/trojan/ss link"
                profiles.isEmpty() -> failures.first()
                failures.isEmpty() && ignored == 0 -> "imported ${profiles.size}"
                failures.isEmpty() -> "imported ${profiles.size}, skipped $ignored unrecognised"
                else -> "imported ${profiles.size}, ${failures.size} failed: ${failures.first()}"
            }
    }

    /**
     * Splits clipboard text into links. Newlines are the common case, but a channel that copies a
     * block can deliver links concatenated on one line (`…#name1vless://…`), so a scheme that starts
     * anywhere also begins a new link.
     */
    internal fun splitLinks(text: String): List<String> =
        text.split('\n', '\r')
            .flatMap { it.split(SCHEME_BOUNDARY) }
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    // The lookbehind is load-bearing: "vless://" contains "ss://" starting at its fifth letter, and
    // without it every vless link would be split into "vle" + a broken shadowsocks link.
    private val SCHEME_BOUNDARY = Regex("(?i)(?<![a-z])(?=(?:vless|vmess|trojan|ss)://)")

    fun parse(text: String): Outcome {
        val lines = splitLinks(text)
        val profiles = LinkedHashMap<String, Profile>()
        val failures = mutableListOf<String>()
        var ignored = 0

        lines.forEach { line ->
            if (!ConfigUri.looksLikeConfig(line)) {
                ignored++
                return@forEach
            }
            runCatching { ConfigUri.parse(line) }
                .onSuccess { profiles[it.profile.id] = it.profile }
                .onFailure { failures.add(it.message ?: "unparseable link") }
        }
        return Outcome(profiles.values.toList(), failures, ignored)
    }
}
