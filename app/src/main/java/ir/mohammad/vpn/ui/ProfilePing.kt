package ir.mohammad.vpn.ui

/** Per-profile result of a real-delay measurement, so the list can show each row's own number. */
data class ProfilePing(
    val ms: Long? = null,
    val busy: Boolean = false,
    val error: String? = null,
) {
    val label: String
        get() = when {
            busy -> "…"
            ms != null -> "${ms}ms"
            error != null -> "fail"
            else -> "—"
        }
}
