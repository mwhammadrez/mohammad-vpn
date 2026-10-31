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
import ir.mohammad.vpn.core.GeoAssets
import ir.mohammad.vpn.core.TransportTuning
import ir.mohammad.vpn.core.TunPlan
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.Profile
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

        const val CHANNEL_ID = "mohammad_tunnel"
        const val NOTIFICATION_ID = 1001
        const val TUN_ADDRESS = "10.10.14.1"
        const val TUN_PREFIX = 30

        const val CONNECT_TIMEOUT_MS = 15_000L
        const val HEALTH_INTERVAL_MS = 5_000L

        /** Probe the data path at most this often (health ticks x interval). */
        const val PROBE_EVERY_TICKS = 6
        const val PROBE_FAILURES_TO_RECONNECT = DataPathPolicy.FAILURES_TO_RECONNECT
        /** Literal address for the health probe: no DNS, no operator data in the config. */
        const val HEALTH_TARGET = "1.1.1.1"
        const val HEALTH_TARGET_PORT = 443

        const val TAG = "VPN"
    }

    private enum class StartResult { OK, INVALID_PROFILE, FAILED, UNHEALTHY }

    private var tun: ParcelFileDescriptor? = null
    private var xray: XrayController? = null
    private var awg: AwgController? = null
    private var bridge: TunBridge? = null

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
        when (intent?.action) {
            ACTION_CONNECT -> {
                createChannel()
                startForeground(NOTIFICATION_ID, buildNotification("Connecting…", "Starting VPN engine"))
                userStopped = false
                attempt = 0
                val gen = ++generation
                submit { connect(gen) }
            }

            ACTION_DISCONNECT -> {
                disconnectBlocking("disconnect requested")
                stopSelf()
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

    // ---------------------------------------------------------------- connect

    private fun connect(gen: Int) {
        val t0 = SystemClock.elapsedRealtime()
        Tunnel.connectTimeline = emptyList()
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

        val profile = Tunnel.selected
        if (profile == null) {
            LogStore.add("E", TAG, "no profile selected")
            synchronized(lock) { Tunnel.update(TunnelState.Failed(TunnelError.NoProfileSelected)) }
            stopForegroundCompat()
            stopSelf()
            return
        }

        val plan = TunPlan.build(
            mtu = Tunnel.defaultMtu,
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
        updateNotification("Connecting…", "Creating tunnel")
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
                updateNotification("Connected", profile.name)
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
                tuning = currentTuning(),
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
    private fun awaitHealthy(profile: Profile, attempts: Int = 3, gapMs: Long = 800): Boolean {
        repeat(attempts) { index ->
            if (SocksProbe.probe(XrayConfig.SOCKS_PORT, HEALTH_TARGET, HEALTH_TARGET_PORT)) {
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
                2_000L,
                2_000L,
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
        val core = xray ?: return
        runCatching { core.queryTraffic() }
            .onSuccess { traffic ->
                val previous = Tunnel.traffic.value
                if (traffic.uplink != previous.first || traffic.downlink != previous.second) {
                    lastTrafficAt = SystemClock.elapsedRealtime()
                }
                Tunnel.updateTraffic(traffic.uplink, traffic.downlink)
            }
            .onFailure { LogStore.add("W", TAG, "stats query failed: ${it.message}") }
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
        val alive = (xray?.isRunning ?: false) || (awg?.isRunning ?: false)
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

        // Probe a neutral address, never the server itself: asking the proxy to
        // connect back to its own port is rejected by many providers and produced
        // a false "dead path" every minute.
        val ok = SocksProbe.probe(XrayConfig.SOCKS_PORT, HEALTH_TARGET, HEALTH_TARGET_PORT)
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
                updateNotification("Network lost", "Waiting for connectivity")
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
                updateNotification("Reconnecting…", "Attempt ${action.attempt}")
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
                LogStore.add("W", "NET", "RECONNECT_FAILED (attempt budget exhausted)")
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
            updateNotification("Waiting for network", "Will reconnect automatically")
            return
        }
        attempt++
        val delay = retryPolicy.delayFor(attempt) ?: return
        val gen = generation
        LogStore.add("I", TAG, "retry $attempt/${retryPolicy.maxAttempts} in $delay ms ($reason)")
        updateNotification("Reconnecting…", "Attempt $attempt")
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
        disconnectBlocking("system revoke")
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
            if (Tunnel.state.value !is TunnelState.Disconnected) {
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
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN tunnel",
            NotificationManager.IMPORTANCE_LOW,
        ).apply { setShowBadge(false) }
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(title: String, text: String): Notification {
        val launch = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setOngoing(true)
            .setContentIntent(launch)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        runCatching {
            getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_ID, buildNotification(title, text))
        }
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
