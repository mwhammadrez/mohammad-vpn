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

    fun parse(text: String): Outcome {
        val lines = text.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
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
