package ir.mohammad.vpn.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TunnelError {
    NoProfileSelected,
    VpnPermissionDenied,
    TunInterfaceFailed,
    CoreStartFailed,
    CoreCrashed,
    InvalidProfile,
    NetworkUnavailable,
    PingFailed,
}

sealed interface TunnelState {
    data object Disconnected : TunnelState
    data object Connecting : TunnelState
    data object Connected : TunnelState
    data object Disconnecting : TunnelState
    data class Failed(val reason: TunnelError) : TunnelState
}

/** Process-wide connection state and the currently selected profile. */
object Tunnel {
    private val _state = MutableStateFlow<TunnelState>(TunnelState.Disconnected)
    val state: StateFlow<TunnelState> = _state.asStateFlow()

    @Volatile
    var profiles: List<Profile> = emptyList()

    @Volatile
    var selected: Profile? = null

    /** Operator defaults from the bundle; used when a profile omits them. */
    @Volatile
    var defaultDns: List<String> = listOf("1.1.1.1", "8.8.8.8")

    @Volatile
    var defaultMtu: Int = 1500

    @Volatile
    var tunIndex: Int = 0

    @Volatile
    var ipv6Enabled: Boolean = false

    @Volatile
    var geositeUrl: String = ""

    @Volatile
    var geoipUrl: String = ""

    /** Last raw stats dump from the core, for diagnostics. */
    @Volatile
    var statsSnapshot: String = ""

    /** Stage timings for the most recent connect attempt: (stage, elapsed ms). */
    @Volatile
    var connectTimeline: List<Pair<String, Long>> = emptyList()

    /** Carrier egress IP captured before the first connect; null until known. */
    @Volatile
    var baselineEgressIp: String? = null

    private val _traffic = MutableStateFlow(0L to 0L)
    val traffic: StateFlow<Pair<Long, Long>> = _traffic.asStateFlow()

    fun updateTraffic(up: Long, down: Long) {
        _traffic.value = up to down
    }

    private val _permissionDenied = MutableStateFlow(false)
    val permissionDenied: StateFlow<Boolean> = _permissionDenied.asStateFlow()

    fun setPermissionDenied(value: Boolean) {
        _permissionDenied.value = value
    }

    fun update(next: TunnelState) {
        _state.value = next
    }

    val isBusy: Boolean
        get() = state.value is TunnelState.Connecting || state.value is TunnelState.Disconnecting
}

/**
 * Structured in-memory diagnostics log.
 *
 * Every entry carries a monotonic-ish timestamp, a severity, a subsystem tag and
 * a message. All messages pass through [redact] so that credentials never reach
 * the screen, logcat or a shared text.
 */
data class LogEntry(
    val time: String,
    val level: String,
    val tag: String,
    val message: String,
) {
    fun format(): String = "$time $level/$tag $message"
}

object LogStore {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private const val MAX_ENTRIES = 1000
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val uuidPattern =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val longTokenPattern = Regex("[A-Za-z0-9_\\-+/=]{40,}")
    private val endpointPattern = Regex("\\b(?:\\[[0-9a-fA-F:]+\\]|[a-zA-Z0-9._-]+\\.[a-zA-Z]{2,}):\\d{1,5}\\b")
    private val ipv6Pattern = Regex("\\b(?:[0-9a-fA-F]{1,4}:){2,7}[0-9a-fA-F]{0,4}\\b")
    private val b64Pattern = Regex("\\b[A-Za-z0-9+/]{16,}={0,2}\\b")

    fun add(level: String, message: String) = add(level, "APP", message)

    @Synchronized
    fun add(level: String, tag: String, message: String) {
        val entry = LogEntry(stamp.format(Date()), level, tag, redact(message))
        val next = _entries.value + entry
        _entries.value = if (next.size > MAX_ENTRIES) next.takeLast(MAX_ENTRIES) else next
    }

    fun redact(raw: String): String {
        var out = uuidPattern.replace(raw, "***")
        out = longTokenPattern.replace(out, "***")
        out = endpointPattern.replace(out, "***")
        out = ipv6Pattern.replace(out, "***")
        out = b64Pattern.replace(out, "***")
        return out
    }

    fun clear() {
        _entries.value = emptyList()
    }

    fun dump(): String = _entries.value.joinToString("\n") { it.format() }
}
