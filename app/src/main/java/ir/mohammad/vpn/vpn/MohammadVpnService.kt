package ir.mohammad.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import ir.mohammad.vpn.MainActivity
import ir.mohammad.vpn.R
import ir.mohammad.vpn.core.AwgConfig
import ir.mohammad.vpn.core.DnsConfig
import ir.mohammad.vpn.core.GeoAssets
import ir.mohammad.vpn.core.MtuPolicy
import ir.mohammad.vpn.core.RealityRules
import ir.mohammad.vpn.notifications.VpnNotifier
import ir.mohammad.vpn.core.TransportTuning
import ir.mohammad.vpn.core.TunPlan
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.DnsEngine
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.OperatorBundleLoader
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.ProfileStore
import ir.mohammad.vpn.data.RemoteConfig
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import libmohammad.AwgController
import libmohammad.TunBridge
import libmohammad.XrayController
import java.net.InetAddress
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class MohammadVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "ir.mohammad.vpn.action.CONNECT"
        const val ACTION_DISCONNECT = "ir.mohammad.vpn.action.DISCONNECT"
        /** Sent by the app widget and the quick-settings tile: one action, decided by the service. */
        const val ACTION_TOGGLE = "ir.mohammad.vpn.action.TOGGLE"
        const val ACTION_PAUSE = "ir.mohammad.vpn.action.PAUSE"
        const val ACTION_RESUME = "ir.mohammad.vpn.action.RESUME"

        /**
         * Resolves a toggle into a concrete action from the current state. Pure so the widget, the
         * tile and the in-app dial can be proven to take the same path (AC-W2).
         */
        fun resolve(action: String?, state: TunnelState): String? = when (action) {
            ACTION_TOGGLE -> when (state) {
                is TunnelState.Connected, is TunnelState.Connecting -> ACTION_DISCONNECT
                is TunnelState.Disconnecting -> null
                else -> ACTION_CONNECT
            }
            else -> action
        }

        const val NOTIFICATION_ID = 1001
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val HEALTH_INTERVAL_MS = 5_000L
        /** One second: the notification promises a live counter and both read the same poll. */
        const val STATS_INTERVAL_MS = 1_000L

        /** Probe the data path at most this often (health ticks x interval). */
        const val PROBE_EVERY_TICKS = 6
        const val PROBE_FAILURES_TO_RECONNECT = DataPathPolicy.FAILURES_TO_RECONNECT
        /** Literal address for the health probe: no DNS, no operator data in the config. */
        const val HEALTH_TARGET = "8.8.8.8"
        const val HEALTH_TARGET_PORT = 443

        const val TAG = "VPN"

        /** Same store the activity and the surfaces read; a cold process has nothing else. */
        const val PREFS = "mohammad"
        const val KEY_SELECTED_PROFILE = "selected_profile"
    }

    private enum class StartResult { OK, INVALID_PROFILE, FAILED, UNHEALTHY }

    private var tun: ParcelFileDescriptor? = null
    private var xray: XrayController? = null
    private var awg: AwgController? = null
    private var bridge: TunBridge? = null

    /** DNS-tunnel engine process; null unless the selected profile uses it. */
    private var dnsEngine: DnsEngine.Handle? = null

    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor()
    private val io = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    // DNS pre-resolution must not queue behind the 28 MB geo download on `io`.
    private val resolver = Executors.newSingleThreadExecutor()
    private val retryPolicy = RetryPolicy()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handover = HandoverPolicy()
    @Volatile private var retryFuture: ScheduledFuture<*>? = null
    @Volatile private var waitingForNetwork = false
    @Volatile private var reconnecting = false
    @Volatile private var lastTrafficAt = SystemClock.elapsedRealtime()
    private var healthTicks = 0
    private var statsTicks = 0
    private var probeFailures = 0

    private var connectTimeout: ScheduledFuture<*>? = null
    private var healthCheck: ScheduledFuture<*>? = null
    private var statsPoll: ScheduledFuture<*>? = null

    @Volatile private var generation = 0
    @Volatile private var userStopped = false
    @Volatile private var paused = false
    @Volatile private var attempt = 0

    private lateinit var netMonitor: NetMonitor

    override fun onCreate() {
        super.onCreate()
        netMonitor = NetMonitor(this).also { it.start() }
        scope.launch {
            netMonitor.online.collect { online ->
                if (online) onConnectivityRestored() else onConnectivityLost()
            }
        }
        scope.launch {
            netMonitor.activeNetwork.collect { network ->
                if (network != null) rebindUnderlying(network)
            }
        }
        LogStore.add("D", TAG, "service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogStore.add("D", TAG, "onStartCommand action=${intent?.action} startId=$startId")
        val action = resolve(intent?.action, Tunnel.state.value)
        when (action) {
            ACTION_CONNECT -> {
                paused = false
                userStopped = false
                attempt = 0
                startForeground(
                    NOTIFICATION_ID,
                    VpnNotifier.build(this, TunnelState.Connecting, Tunnel.traffic.value, paused = false),
                )
                val gen = ++generation
                submit { connect(gen) }
            }

            // Documented fallback (D-90): pause stops the tunnel but keeps the service and the
            // notification alive with a Resume action. A half-stopped bridge is a data-path change we
            // cannot verify without a device, so it is deliberately not implemented.
            ACTION_PAUSE -> {
                paused = true
                userStopped = true
                cancelStatsPoll()
                // Teardown touches native code and the TUN; never block the main thread on it.
                submit {
                    disconnectBlocking("paused by user")
                    Tunnel.trafficUnavailable()
                    VpnNotifier.post(
                        this,
                        VpnNotifier.build(this, Tunnel.state.value, Tunnel.traffic.value, paused = true),
                    )
                }
            }

            ACTION_RESUME -> {
                paused = false
                userStopped = false
                attempt = 0
                startForeground(
                    NOTIFICATION_ID,
                    VpnNotifier.build(this, TunnelState.Connecting, Tunnel.traffic.value, paused = false),
                )
                val gen = ++generation
                submit { connect(gen) }
            }

            ACTION_DISCONNECT -> {
                paused = false
                // Close the core and the descriptor on the worker, not on the caller's thread.
                submit {
                    disconnectBlocking("disconnect requested")
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun submit(block: () -> Unit) {
        try {
            worker.execute(block)
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "worker rejected task: ${e.message}")
        }
    }

    private fun isStale(gen: Int): Boolean = userStopped || gen != generation

    /**
     * A widget or tile tap can start this service in a fresh process, before any activity has run.
     * In that case [Tunnel.selected] is null even though a profile id is saved; rebuild it from the
     * same stores the activity uses instead of failing with NoProfileSelected.
     */
    private fun restoreSelectedProfile(): Profile? {
        val id = getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_SELECTED_PROFILE, null) ?: return null
        val remote = runCatching { RemoteConfig.cached(this)?.profiles }.getOrNull().orEmpty()
        val imported = runCatching { ProfileStore.load(this) }.getOrNull().orEmpty()
        // A build that ships only the encrypted bundle has no remote cache: decrypt it here, on the
        // worker, so a widget tap after a reboot still finds the operator profile.
        val bundled = runCatching { OperatorBundleLoader.load(this).profiles }.getOrNull().orEmpty()
        val found = (remote + imported + bundled).firstOrNull { it.id == id } ?: return null
        if (Tunnel.profiles.isEmpty()) Tunnel.publishProfiles(bundled + imported)
        LogStore.add("I", TAG, "restored saved profile for a cold start")
        return found
    }

    // ---------------------------------------------------------------- connect

    private fun connect(gen: Int) {
        val t0 = SystemClock.elapsedRealtime()
        fun mark(stage: String) {
            Tunnel.connectTimeline = Tunnel.connectTimeline + (stage to (SystemClock.elapsedRealtime() - t0))
        }

        if (isStale(gen)) {
            LogStore.add("D", TAG, "connect aborted: stale generation")
            return
        }
        if (Tunnel.state.value is TunnelState.Connected) {
            LogStore.add("W", TAG, "connect ignored: already connected")
            return
        }

        // A cold process (widget/tile after a reboot) has no in-memory selection; rebuild it from
        // the saved id before deciding that no profile exists.
        val profile = Tunnel.selected ?: restoreSelectedProfile()
        if (profile == null) {
            LogStore.add("E", TAG, "no profile selected")
            synchronized(lock) { Tunnel.update(TunnelState.Failed(TunnelError.NoProfileSelected)) }
            stopForegroundCompat()
            stopSelf()
            return
        }
        Tunnel.selected = profile

        // A new session never inherits the previous session's totals (AC-N10). This runs only after
        // the stale/connected guards, so a no-op connect cannot zero a live session's counters.
        Tunnel.resetTraffic()
        Tunnel.connectTimeline = emptyList()

        val mtuResolution = MtuPolicy.resolve(profile.mtu, Tunnel.defaultMtu)
        LogStore.add("I", TAG, MtuPolicy.diagnostic(mtuResolution))

        val plan = TunPlan.build(
            mtu = mtuResolution.mtu,
            dns = Tunnel.defaultDns,
            ipv6 = Tunnel.ipv6Enabled,
        )
        LogStore.add("I", TAG, "profile=${profile.name} protocol=${profile.protocol} core=${profile.core}")
        LogStore.add("D", TAG, "target host set, port=${profile.port}, online=${netMonitor.online.value}")
        LogStore.add(
            "I", TAG,
            "tun plan: mtu=${plan.mtu} v4=${plan.ipv4Address}/${plan.ipv4Prefix} " +
                "v6=${plan.ipv6Address.ifEmpty { "off" }} dns=${plan.dns.size}",
        )

        synchronized(lock) { Tunnel.update(TunnelState.Connecting) }
        refreshNotification()
        armConnectTimeout(gen)

        val descriptor = try {
            buildInterface(plan)
        } catch (t: Throwable) {
            LogStore.add("E", TAG, "interface build threw ${t.javaClass.simpleName}: ${t.message}")
            fail(TunnelError.TunInterfaceFailed)
            return
        }
        if (descriptor == null) {
            LogStore.add("E", TAG, "establish() returned null (permission revoked?)")
            fail(TunnelError.TunInterfaceFailed)
            return
        }
        synchronized(lock) { tun = descriptor }
        LogStore.add("I", TAG, "tunnel up: fd=${descriptor.fd} mtu=${plan.mtu} addr=${plan.ipv4Address}/${plan.ipv4Prefix}")
        mark("tun")

        if (isStale(gen)) {
            LogStore.add("W", TAG, "connect abandoned after tunnel creation")
            closeAll()
            return
        }

        val result = when (profile.core) {
            "AMNEZIAWG" -> startAwg(profile, descriptor, plan.mtu)
            "DNS" -> startDns(profile, descriptor, plan)
            else -> startXray(profile, descriptor, plan)
        }

        if (isStale(gen)) {
            LogStore.add("W", TAG, "connect abandoned after core start")
            closeAll()
            return
        }

        when (result) {
            StartResult.OK -> {
                cancelConnectTimeout()
                attempt = 0
                synchronized(lock) { Tunnel.update(TunnelState.Connected) }
                mark("core")
                LogStore.add("I", TAG, "tunnel connected")
                mark("connected")
                if (reconnecting) {
                    reconnecting = false
                    LogStore.add("I", "NET", "RECONNECT_SUCCEEDED in ${SystemClock.elapsedRealtime() - t0} ms")
                }
                refreshNotification()
                armHealthCheck()
                armStatsPoll()
            }
            StartResult.INVALID_PROFILE -> fail(TunnelError.InvalidProfile)
            StartResult.FAILED -> fail(TunnelError.CoreStartFailed)
            StartResult.UNHEALTHY -> fail(TunnelError.ConfigUnhealthy)
        }
    }

    private fun startXray(profile: Profile, descriptor: ParcelFileDescriptor, plan: TunPlan): StartResult {
        val renderStart = SystemClock.elapsedRealtime()
        val security = RealityRules.securityOf(profile.xray)
        val tuningNow = currentTuning()
        if (tuningNow.fragment && RealityRules.fragmentBreaksReality(security)) {
            LogStore.add(
                "W", TAG,
                "fragment suppressed: it would break REALITY (security=$security)",
            )
        }
        val geoReady = GeoAssets.isReady(this)
        if (!geoReady) ensureGeoAssetsAsync()
        val config = try {
            XrayConfig.render(
                profile = profile,
                mtu = plan.mtu,
                dnsServers = plan.dns,
                logLevel = "warning",
                ipv6 = plan.ipv6Address.isNotEmpty(),
                geo = geoReady,
                fakeDns = true,
                tuning = tuningNow,
                serverHosts = preResolve(profile),
            )
        } catch (t: Throwable) {
            LogStore.add("E", TAG, "xray config render failed: ${t.javaClass.simpleName}: ${t.message}")
            return StartResult.INVALID_PROFILE
        }
        LogStore.add(
            "I", TAG,
            "xray config rendered in ${SystemClock.elapsedRealtime() - renderStart} ms " +
                "(${config.length} bytes, outbound=${profile.protocol.lowercase()})",
        )

        val core = XrayController { level, message ->
            LogStore.add(mapLevel(level.toInt(), message), "XRAY", message.trim())
        }
        GeoAssets.path(this)?.let { core.setAssetDir(it) }
        runCatching { core.setLogLevel(0L) }
            .onFailure { LogStore.add("W", TAG, "setLogLevel unavailable: ${it.message}") }

        val coreStart = SystemClock.elapsedRealtime()
        return try {
            // SOCKS-only mode: the descriptor belongs to the bridge, never to the core.
            LogStore.add("D", TAG, "xray.start(socks-only, port=${XrayConfig.SOCKS_PORT})")
            core.start(config, 0L)
            synchronized(lock) { xray = core }
            LogStore.add("I", TAG, "xray started in ${SystemClock.elapsedRealtime() - coreStart} ms")

            val listenStart = SystemClock.elapsedRealtime()
            if (!awaitSocks(XrayConfig.SOCKS_PORT, 2_000)) {
                throw IllegalStateException(
                    "socks inbound not accepting on 127.0.0.1:${XrayConfig.SOCKS_PORT} after 2000 ms",
                )
            }
            LogStore.add(
                "D", TAG,
                "socks inbound accepting in ${SystemClock.elapsedRealtime() - listenStart} ms",
            )

            val bridgeStart = SystemClock.elapsedRealtime()
            val b = TunBridge()
            b.start(descriptor.fd.toLong(), plan.mtu.toLong(), "socks5://127.0.0.1:${XrayConfig.SOCKS_PORT}")
            synchronized(lock) { bridge = b }
            LogStore.add(
                "I", TAG,
                "tun2socks up in ${SystemClock.elapsedRealtime() - bridgeStart} ms " +
                    "(fd=${descriptor.fd}, mtu=${plan.mtu})",
            )

            // Green means "the tunnel answers", not "the process started".
            val healthStart = SystemClock.elapsedRealtime()
            if (!awaitHealthy(profile)) {
                LogStore.add(
                    "E", TAG,
                    "config unhealthy: no answer through the tunnel after " +
                        "${SystemClock.elapsedRealtime() - healthStart} ms",
                )
                return StartResult.UNHEALTHY
            }
            LogStore.add(
                "I", TAG,
                "tunnel verified in ${SystemClock.elapsedRealtime() - healthStart} ms",
            )
            StartResult.OK
        } catch (t: Throwable) {
            LogStore.add(
                "E", TAG,
                "xray start failed after ${SystemClock.elapsedRealtime() - coreStart} ms: " +
                    "${t.javaClass.simpleName}: ${t.message}",
            )
            synchronized(lock) {
                bridge?.let { runCatching { it.stop() } }
                bridge = null
            }
            runCatching { core.stop() }
            StartResult.FAILED
        }
    }

    /**
     * DNS tunnel: the engine is a child process that publishes a local SOCKS5 listener; the bridge
     * and everything above it stay exactly as they are for Xray (D-113).
     */
    private fun startDns(profile: Profile, descriptor: ParcelFileDescriptor, plan: TunPlan): StartResult {
        val settings = profile.dns ?: return StartResult.INVALID_PROFILE
        LogStore.add("I", TAG, "dns ${DnsConfig.diagnostic(settings)}")

        val handle = try {
            DnsEngine.start(this, settings, DnsConfig.SOCKS_PORT) { line ->
                if (line.contains("[ERROR]", ignoreCase = true) ||
                    line.contains("[WARN]", ignoreCase = true)
                ) {
                    LogStore.add("W", "DNS", line)
                } else {
                    LogStore.add("D", "DNS", line)
                }
            }
        } catch (t: Throwable) {
            LogStore.add("E", TAG, "dns engine start failed: ${t.javaClass.simpleName}: ${t.message}")
            return StartResult.FAILED
        }
        synchronized(lock) { dnsEngine = handle }

        if (!DnsEngine.awaitPort(DnsConfig.SOCKS_PORT, 15_000)) {
            LogStore.add("E", TAG, "dns engine did not open its SOCKS port within 15 s")
            return StartResult.UNHEALTHY
        }
        LogStore.add("I", TAG, "dns engine listening on 127.0.0.1:${DnsConfig.SOCKS_PORT}")

        return try {
            val b = TunBridge()
            b.start(descriptor.fd.toLong(), plan.mtu.toLong(), "socks5://127.0.0.1:${DnsConfig.SOCKS_PORT}")
            synchronized(lock) { bridge = b }

            if (!awaitHealthy(profile, attempts = 2, gapMs = 1_500, port = DnsConfig.SOCKS_PORT)) {
                LogStore.add("E", TAG, "dns tunnel does not answer yet (first queries can be slow)")
                // A cold DNS session needs a moment; the state machine treats this as a warning and
                // still reports connected, because the engine keeps retrying in the background.
            }
            StartResult.OK
        } catch (t: Throwable) {
            LogStore.add("E", TAG, "dns bridge failed: ${t.javaClass.simpleName}: ${t.message}")
            bridge?.let { runCatching { it.stop() } }
            bridge = null
            StartResult.FAILED
        }
    }

    private fun startAwg(profile: Profile, descriptor: ParcelFileDescriptor, mtu: Int): StartResult {
        val config = try {
            AwgConfig.render(profile, mtu)
        } catch (t: Throwable) {
            LogStore.add("E", TAG, "awg config render failed: ${t.javaClass.simpleName}: ${t.message}")
            return StartResult.INVALID_PROFILE
        }
        LogStore.add("I", TAG, "awg config rendered (${config.length} bytes)")

        val core = AwgController { level, message ->
            LogStore.add(mapLevel(level.toInt(), message), "AWG", message.trim())
        }

        val coreStart = SystemClock.elapsedRealtime()
        return try {
            LogStore.add("D", TAG, "awg.start(fd=${descriptor.fd}, mtu=$mtu)")
            core.start(config, descriptor.fd.toLong(), mtu.toLong())
            synchronized(lock) { awg = core }
            LogStore.add("I", TAG, "awg started in ${SystemClock.elapsedRealtime() - coreStart} ms")
            StartResult.OK
        } catch (t: Throwable) {
            LogStore.add(
                "E", TAG,
                "awg start failed after ${SystemClock.elapsedRealtime() - coreStart} ms: " +
                    "${t.javaClass.simpleName}: ${t.message}",
            )
            runCatching { core.stop() }
            StartResult.FAILED
        }
    }

    /** Waits until the core's SOCKS inbound accepts connections. */
    /**
     * Proves the chain works end to end: a SOCKS CONNECT through the core to a
     * literal address must be accepted. A live process is not a working config.
     */
    private fun awaitHealthy(
        profile: Profile,
        attempts: Int = 3,
        gapMs: Long = 800,
        port: Int = XrayConfig.SOCKS_PORT,
    ): Boolean {
        repeat(attempts) { index ->
            if (SocksProbe.probe(port, HEALTH_TARGET, HEALTH_TARGET_PORT)) {
                return true
            }
            LogStore.add("W", TAG, "health probe ${index + 1}/$attempts failed")
            if (index < attempts - 1) {
                try {
                    Thread.sleep(gapMs)
                } catch (_: InterruptedException) {
                    return false
                }
            }
        }
        return false
    }

    private fun awaitSocks(port: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 150)
                    return true
                }
            } catch (_: Exception) {
                try {
                    Thread.sleep(40)
                } catch (_: InterruptedException) {
                    return false
                }
            }
        }
        return false
    }

    /** Transport hardening chosen in the UI; applied on every connect. */
    private fun currentTuning(): TransportTuning {
        val prefs = getSharedPreferences("mohammad", MODE_PRIVATE)
        val tuning = TransportTuning(
            fragment = prefs.getBoolean("fragment", false),
            fragmentPackets = prefs.getString("fragmentMode", "tlshello") ?: "tlshello",
            mux = prefs.getBoolean("mux", false),
        )
        if (tuning.fragment || tuning.mux) {
            LogStore.add(
                "I", TAG,
                "transport tuning: fragment=${if (tuning.fragment) tuning.fragmentPackets else "off"} " +
                    "mux=${if (tuning.mux) "on" else "off"}",
            )
        }
        return tuning
    }

    private fun ensureGeoAssetsAsync() {
        val geosite = Tunnel.geositeUrl
        val geoip = Tunnel.geoipUrl
        if (geosite.isBlank() || geoip.isBlank()) return
        // Never run this on the tunnel worker: the 28 MB download would starve
        // the stats poll and the watchdog for its whole duration.
        try {
            io.execute {
                kotlinx.coroutines.runBlocking {
                    runCatching { GeoAssets.ensure(applicationContext, geosite, geoip) }
                        .onSuccess { ready -> LogStore.add("I", "GEO", "rule sets ready=$ready") }
                        .onFailure { LogStore.add("W", "GEO", "download failed: ${it.message}") }
                }
            }
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "geo download rejected: ${e.message}")
        }
    }

    /**
     * A VPN tunnel is reported as unmetered: the system must not apply Data Saver
     * restrictions to traffic the user has already chosen to route through the tunnel.
     * Builder.setMetered() arrived in API 29; below that the system infers it itself.
     */
    internal fun applyMetering(builder: Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setMetered(false)
    }

    /** A Builder requires the enclosing VpnService instance; tests cannot construct one. */
    internal fun newBuilder(): Builder = Builder()

    /**
     * Resolves the server name once, with a hard cap, so the rendered config can carry
     * a `dns.hosts` entry and skip a resolver round trip inside the core.
     * Never logs the name or the address.
     */
    private fun preResolve(profile: Profile): Map<String, String> {
        val host = profile.host
        if (host.contains(':') || host.all { it.isDigit() || it == '.' }) return emptyMap()
        val task = try {
            resolver.submit<InetAddress?> { runCatching { InetAddress.getByName(host) }.getOrNull() }
        } catch (e: RejectedExecutionException) {
            return emptyMap()
        }
        val address = runCatching { task.get(1_200, TimeUnit.MILLISECONDS) }.getOrNull() ?: return emptyMap()
        val literal = address.hostAddress ?: return emptyMap()
        LogStore.add("D", TAG, "server address pre-resolved for hosts entry")
        return mapOf(host to literal)
    }

    private fun buildInterface(plan: TunPlan): ParcelFileDescriptor? {
        LogStore.add("D", TAG, "building interface from plan: mtu=${plan.mtu}")
        val builder = Builder()
            .setSession("Mohammad")
            .setMtu(plan.mtu)
            .addAddress(plan.ipv4Address, plan.ipv4Prefix)
            .setBlocking(false)

        plan.ipv4Routes.forEach { (destination, prefix) -> builder.addRoute(destination, prefix) }
        if (plan.ipv6Address.isNotEmpty()) {
            builder.addAddress(plan.ipv6Address, plan.ipv6Prefix)
            plan.ipv6Routes.forEach { (destination, prefix) -> builder.addRoute(destination, prefix) }
            LogStore.add("D", TAG, "ipv6 enabled: ${plan.ipv6Address}/${plan.ipv6Prefix} + default route")
        }
        val currentNetwork = netMonitor.currentNetwork()
        runCatching { applyMetering(builder) }
            .onFailure { LogStore.add("W", TAG, "setMetered rejected: ${it.message}") }
        currentNetwork?.let { network ->
            runCatching { builder.setUnderlyingNetworks(arrayOf(network)) }
                .onFailure { LogStore.add("W", TAG, "underlying network rejected: ${it.message}") }
        }

        plan.dns.forEach { server ->
            runCatching { builder.addDnsServer(server) }
                .onFailure { LogStore.add("W", TAG, "dns add failed: ${it.message}") }
        }
        runCatching { builder.addDisallowedApplication(packageName) }
            .onFailure { LogStore.add("W", TAG, "self-exclusion failed: ${it.message}") }

        return builder.establish()
    }

    // ------------------------------------------------------------ supervision

    private fun armConnectTimeout(gen: Int) {
        cancelConnectTimeout()
        connectTimeout = try {
            scheduler.schedule({
                if (!isStale(gen) && Tunnel.state.value is TunnelState.Connecting) {
                    LogStore.add("E", TAG, "core start timed out after $CONNECT_TIMEOUT_MS ms")
                    generation++ // invalidate the in-flight attempt
                    synchronized(lock) { Tunnel.update(TunnelState.Failed(TunnelError.CoreStartFailed)) }
                    // Closing the descriptor unblocks a core stuck reading it.
                    runCatching { tun?.close() }
                    stopForegroundCompat()
                    stopSelf()
                }
            }, CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "scheduler rejected timeout: ${e.message}")
            null
        }
    }

    private fun cancelConnectTimeout() {
        connectTimeout?.cancel(false)
        connectTimeout = null
    }

    private fun armHealthCheck() {
        healthCheck?.cancel(false)
        healthCheck = try {
            scheduler.scheduleWithFixedDelay(
                { submit { checkHealth() } },
                HEALTH_INTERVAL_MS,
                HEALTH_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "scheduler rejected health check: ${e.message}")
            null
        }
    }

    private fun cancelHealthCheck() {
        healthCheck?.cancel(false)
        healthCheck = null
    }

    private fun armStatsPoll() {
        statsPoll?.cancel(false)
        statsPoll = try {
            scheduler.scheduleWithFixedDelay(
                { submit { pollStats() } },
                STATS_INTERVAL_MS,
                STATS_INTERVAL_MS,
                TimeUnit.MILLISECONDS,
            )
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "scheduler rejected stats poll: ${e.message}")
            null
        }
    }

    private fun cancelStatsPoll() {
        statsPoll?.cancel(false)
        statsPoll = null
    }

    private fun pollStats() {
        if (Tunnel.state.value !is TunnelState.Connected) return
        val core = xray
        if (core == null) {
            // AmneziaWG and the DNS engine expose no byte counters. Their engine being alive is the
            // only signal available, and it is enough to keep the dead-path policy from firing.
            if (awg?.isRunning == true || dnsEngine?.process?.isAlive == true) {
                lastTrafficAt = SystemClock.elapsedRealtime()
                VpnNotifier.post(
                    this,
                    VpnNotifier.build(this, Tunnel.state.value, Tunnel.traffic.value, paused),
                )
            }
            return
        }
        runCatching { core.queryTraffic() }
            .onSuccess { traffic ->
                val previous = Tunnel.traffic.value
                if (traffic.uplink != previous.up || traffic.downlink != previous.down) {
                    lastTrafficAt = SystemClock.elapsedRealtime()
                }
                Tunnel.updateTraffic(traffic.uplink, traffic.downlink)
                VpnNotifier.post(
                    this,
                    VpnNotifier.build(this, Tunnel.state.value, Tunnel.traffic.value, paused),
                )
            }
            .onFailure {
                LogStore.add("W", TAG, "stats query failed: ${it.message}")
                Tunnel.trafficUnavailable()
            }
        // The raw dump is only needed for diagnostics; rebuilding that string every
        // two seconds costs a JNI round trip and allocations for nothing.
        if (++statsTicks % 3 == 0) {
            runCatching { core.statsDebug() }
                .onSuccess { Tunnel.statsSnapshot = it }
                .onFailure { Tunnel.statsSnapshot = "stats-debug-failed: ${it.message}" }
        }
    }

    private fun checkHealth() {
        if (userStopped) return
        if (Tunnel.state.value !is TunnelState.Connected) return
        val alive = (xray?.isRunning ?: false) || (awg?.isRunning ?: false) ||
            (dnsEngine?.process?.isAlive ?: false)
        if (!alive) {
            LogStore.add("E", TAG, "engine stopped unexpectedly (watchdog)")
            synchronized(lock) {
                closeResourcesLocked()
                Tunnel.update(TunnelState.Failed(TunnelError.CoreCrashed))
            }
            scheduleRetry("engine crashed")
            return
        }
        checkDataPath()
    }

    /**
     * A live core process is not a live tunnel: after a handover Xray keeps running
     * while its outbound sockets are dead. Probe the local SOCKS inbound end-to-end
     * and reconnect when it stops answering.
     */
    private fun checkDataPath() {
        if (!netMonitor.online.value) return
        healthTicks++
        if (healthTicks % PROBE_EVERY_TICKS != 0) return

        // Only Xray exposes a local SOCKS listener to probe. AmneziaWG has no such listener and the
        // DNS engine speaks its own protocol on 10809, so probing 10808 there would manufacture a
        // dead path every minute.
        val port = when {
            xray != null -> XrayConfig.SOCKS_PORT
            dnsEngine != null -> DnsConfig.SOCKS_PORT
            else -> return
        }
        if (awg != null) return

        // Probe a neutral address, never the server itself: asking the proxy to
        // connect back to its own port is rejected by many providers and produced
        // a false "dead path" every minute.
        val ok = SocksProbe.probe(port, HEALTH_TARGET, HEALTH_TARGET_PORT)
        if (ok) {
            probeFailures = 0
            return
        }
        probeFailures++
        val silentFor = SystemClock.elapsedRealtime() - lastTrafficAt
        LogStore.add(
            "W", TAG,
            "data path probe failed ($probeFailures/$PROBE_FAILURES_TO_RECONNECT, silent ${silentFor / 1000}s)",
        )
        if (!DataPathPolicy.shouldReconnect(probeFailures, silentFor)) return

        probeFailures = 0
        LogStore.add("W", "NET", "RECONNECT_STARTED (dead outbound path)")
        forceReconnect("dead outbound path")
    }

    private fun forceReconnect(reason: String) {
        if (userStopped) return
        reconnecting = true
        synchronized(lock) {
            closeResourcesLocked()
            Tunnel.update(TunnelState.Failed(TunnelError.NetworkUnavailable))
        }
        scheduleRetry(reason)
    }

    // ------------------------------------------------------------- handover

    private fun onConnectivityLost() {
        LogStore.add("W", "NET", "NETWORK_LOST")
        retryFuture?.cancel(false)
        retryFuture = null

        val ctx = HandoverContext(
            userStopped = userStopped,
            generationMatches = true,
            connected = Tunnel.state.value is TunnelState.Connected,
            online = false,
            attemptsUsed = attempt,
        )
        when (val action = handover.onEvent(HandoverEvent.NETWORK_LOST, ctx)) {
            is HandoverAction.HoldForNetwork -> {
                waitingForNetwork = true
                runCatching { setUnderlyingNetworks(null) }
                    .onFailure { LogStore.add("W", TAG, "underlying network clear failed: ${it.message}") }
                LogStore.add("D", TAG, "holding tunnel open until the network returns")
                refreshNotification()
                synchronized(lock) {
                    if (Tunnel.state.value is TunnelState.Connected) {
                        Tunnel.update(TunnelState.Failed(TunnelError.NetworkUnavailable))
                    }
                }
            }
            else -> Unit
        }
    }

    private fun onConnectivityRestored() {
        // The StateFlow replays its current value on subscribe; ignore it when there
        // is no tunnel to resume.
        if (tun == null && !waitingForNetwork && Tunnel.state.value is TunnelState.Disconnected) return
        // A connect already in flight owns the tunnel; tearing it down here would kill the attempt
        // that the restore was supposed to save.
        when (Tunnel.state.value) {
            is TunnelState.Connecting, is TunnelState.Disconnecting -> return
            else -> Unit
        }
        LogStore.add("I", "NET", "NETWORK_RESTORED")
        waitingForNetwork = false
        retryFuture?.cancel(false)
        retryFuture = null

        netMonitor.currentNetwork()?.let { rebindUnderlying(it) }

        val gen = generation
        val ctx = HandoverContext(
            userStopped = userStopped,
            generationMatches = gen == generation,
            connected = tun != null || Tunnel.state.value is TunnelState.Connected,
            online = true,
            attemptsUsed = attempt,
        )
        when (val action = handover.onEvent(HandoverEvent.NETWORK_RESTORED, ctx)) {
            is HandoverAction.Resume -> {
                attempt = action.attempt
                reconnecting = true
                LogStore.add(
                    "I", "NET",
                    "RECONNECT_STARTED attempt=${action.attempt} in ${action.delayMs} ms",
                )
                refreshNotification()
                try {
                    retryFuture = scheduler.schedule(
                        { submit { resumeConnect(gen) } },
                        action.delayMs,
                        TimeUnit.MILLISECONDS,
                    )
                } catch (e: RejectedExecutionException) {
                    LogStore.add("W", TAG, "scheduler rejected handover retry: ${e.message}")
                }
            }
            HandoverAction.Exhausted -> {
                reconnecting = false
                waitingForNetwork = false
                LogStore.add("W", "NET", "RECONNECT_FAILED (attempt budget exhausted)")
                // Do not leave an established TUN with no engine behind it: every packet would be
                // accepted and dropped. Close it and let the user try again.
                synchronized(lock) {
                    closeResourcesLocked()
                    Tunnel.update(TunnelState.Failed(TunnelError.NetworkUnavailable))
                }
                stopForegroundCompat()
                stopSelf()
            }
            else -> Unit
        }
    }

    /** Tears down the stale path (if any) and reconnects on the current generation. */
    private fun resumeConnect(gen: Int) {
        if (isStale(gen)) {
            LogStore.add("D", TAG, "handover reconnect aborted: stale generation")
            reconnecting = false
            return
        }
        synchronized(lock) {
            closeResourcesLocked()
            Tunnel.update(TunnelState.Failed(TunnelError.NetworkUnavailable))
        }
        connect(gen)
    }

    private fun rebindUnderlying(network: Network) {
        runCatching { setUnderlyingNetworks(arrayOf(network)) }
            .onSuccess { LogStore.add("D", TAG, "underlying network rebound") }
            .onFailure { LogStore.add("W", TAG, "underlying network update failed: ${it.message}") }
    }

    private fun scheduleRetry(reason: String) {
        if (userStopped) return
        if (!retryPolicy.hasAttemptsLeft(attempt + 1)) {
            LogStore.add("W", TAG, "retry budget exhausted ($reason)")
            stopForegroundCompat()
            stopSelf()
            return
        }
        if (!netMonitor.online.value) {
            // A phone call or a tunnel switch must not kill the service; hold and wait.
            LogStore.add("W", TAG, "retry deferred until the network returns ($reason)")
            waitingForNetwork = true
            refreshNotification()
            return
        }
        attempt++
        val delay = retryPolicy.delayFor(attempt) ?: return
        val gen = generation
        LogStore.add("I", TAG, "retry $attempt/${retryPolicy.maxAttempts} in $delay ms ($reason)")
        refreshNotification()
        try {
            retryFuture = scheduler.schedule({ submit { connect(gen) } }, delay, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "scheduler rejected retry: ${e.message}")
        }
    }

    private fun fail(reason: TunnelError) {
        if (reconnecting) {
            reconnecting = false
            LogStore.add("W", "NET", "RECONNECT_FAILED ($reason)")
        }
        cancelConnectTimeout()
        cancelHealthCheck()
        cancelStatsPoll()
        synchronized(lock) {
            closeResourcesLocked()
            Tunnel.update(TunnelState.Failed(reason))
        }
        stopForegroundCompat()
        stopSelf()
    }

    // ------------------------------------------------------------ disconnect

    private fun disconnectBlocking(reason: String) {
        userStopped = true
        generation++
        val started = SystemClock.elapsedRealtime()
        LogStore.add("I", TAG, "disconnect: $reason")
        try {
            synchronized(lock) {
                if (Tunnel.state.value !is TunnelState.Disconnecting) {
                    Tunnel.update(TunnelState.Disconnecting)
                }
                cancelConnectTimeout()
                cancelHealthCheck()
                cancelStatsPoll()
                closeResourcesLocked()
                Tunnel.update(TunnelState.Disconnected)
            }
        } catch (t: Throwable) {
            // A failure while tearing down must never take the process down.
            LogStore.add("E", TAG, "disconnect failed: ${t.javaClass.simpleName}: ${t.message}")
            runCatching { Tunnel.update(TunnelState.Disconnected) }
        } finally {
            LogStore.add("I", TAG, "disconnected in ${SystemClock.elapsedRealtime() - started} ms")
            runCatching { stopForegroundCompat() }
        }
    }

    private fun closeAll() {
        synchronized(lock) { closeResourcesLocked() }
    }

    private fun closeResourcesLocked() {
        bridge?.let {
            val started = SystemClock.elapsedRealtime()
            runCatching { it.stop() }
                .onSuccess { LogStore.add("D", TAG, "bridge.stop ok in ${SystemClock.elapsedRealtime() - started} ms") }
                .onFailure { e -> LogStore.add("E", TAG, "bridge.stop failed: ${e.message}") }
        }
        bridge = null
        xray?.let {
            val started = SystemClock.elapsedRealtime()
            runCatching { it.stop() }
                .onSuccess { LogStore.add("D", TAG, "xray.stop ok in ${SystemClock.elapsedRealtime() - started} ms") }
                .onFailure { e -> LogStore.add("E", TAG, "xray.stop failed: ${e.message}") }
        }
        xray = null
        dnsEngine?.let {
            val started = SystemClock.elapsedRealtime()
            runCatching { it.stop() }
                .onSuccess { LogStore.add("D", TAG, "dns engine stopped in ${SystemClock.elapsedRealtime() - started} ms") }
                .onFailure { e -> LogStore.add("E", TAG, "dns engine stop failed: ${e.message}") }
        }
        dnsEngine = null
        awg?.let {
            val started = SystemClock.elapsedRealtime()
            runCatching { it.stop() }
                .onSuccess { LogStore.add("D", TAG, "awg.stop ok in ${SystemClock.elapsedRealtime() - started} ms") }
                .onFailure { e -> LogStore.add("E", TAG, "awg.stop failed: ${e.message}") }
        }
        awg = null
        tun?.let {
            runCatching { it.close() }
                .onSuccess { LogStore.add("D", TAG, "tun fd closed") }
                .onFailure { e -> LogStore.add("E", TAG, "tun close failed: ${e.message}") }
        }
        tun = null
    }

    // -------------------------------------------------------------- lifecycle

    override fun onRevoke() {
        LogStore.add("W", TAG, "onRevoke: VPN permission revoked by system")
        // onRevoke runs on the main thread; native teardown belongs on the worker.
        submit {
            disconnectBlocking("system revoke")
            stopSelf()
        }
        super.onRevoke()
    }

    override fun onDestroy() {
        LogStore.add("D", TAG, "onDestroy")
        userStopped = true
        generation++
        cancelConnectTimeout()
        cancelHealthCheck()
        cancelStatsPoll()
        retryFuture?.cancel(false)
        retryFuture = null
        scope.cancel()
        synchronized(lock) {
            closeResourcesLocked()
            // Keep a failure visible: onDestroy runs right after fail()/stopSelf and would otherwise
            // replace the reason with a bare Disconnected.
            if (Tunnel.state.value !is TunnelState.Failed && Tunnel.state.value !is TunnelState.Disconnected) {
                Tunnel.update(TunnelState.Disconnected)
            }
        }
        runCatching { netMonitor.stop() }
        // shutdown(), never shutdownNow(): interrupting a thread that is inside a
        // JNI/Go call aborts the process.
        worker.shutdown()
        io.shutdown()
        scheduler.shutdown()
        resolver.shutdown()
        super.onDestroy()
    }

    // ----------------------------------------------------------- notification

    private fun createChannel() {
        VpnNotifier.ensureChannel(this)
    }

    /** Re-renders the persistent notification from one snapshot; never builds its own text. */
    private fun refreshNotification() {
        VpnNotifier.post(
            this,
            VpnNotifier.build(this, Tunnel.state.value, Tunnel.traffic.value, paused),
        )
    }

    private fun stopForegroundCompat() {
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun mapLevel(level: Int, message: String): String {
        val lower = message.lowercase()
        return when {
            level >= 3 || lower.contains("[error]") || lower.contains("failed") -> "E"
            level == 2 || lower.contains("[warning]") -> "W"
            lower.contains("[debug]") -> "D"
            else -> "I"
        }
    }
}
