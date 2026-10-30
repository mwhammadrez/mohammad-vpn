package ir.mohammad.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import ir.mohammad.vpn.MainActivity
import ir.mohammad.vpn.R
import ir.mohammad.vpn.core.AwgConfig
import ir.mohammad.vpn.core.GeoAssets
import ir.mohammad.vpn.core.TunPlan
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import libmohammad.AwgController
import libmohammad.XrayController
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

        const val TAG = "VPN"
    }

    private enum class StartResult { OK, INVALID_PROFILE, FAILED }

    private var tun: ParcelFileDescriptor? = null
    private var xray: XrayController? = null
    private var awg: AwgController? = null

    private val lock = Any()
    private val worker = Executors.newSingleThreadExecutor()
    private val io = Executors.newSingleThreadExecutor()
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private val retryPolicy = RetryPolicy()

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
            index = Tunnel.tunIndex,
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
                updateNotification("Connected", profile.name)
                armHealthCheck()
                armStatsPoll()
            }
            StartResult.INVALID_PROFILE -> fail(TunnelError.InvalidProfile)
            StartResult.FAILED -> fail(TunnelError.CoreStartFailed)
        }
    }

    private fun startXray(profile: Profile, descriptor: ParcelFileDescriptor, plan: TunPlan): StartResult {
        val renderStart = SystemClock.elapsedRealtime()
        val geoReady = GeoAssets.isReady(this)
        if (!geoReady) ensureGeoAssetsAsync()
        val config = try {
            XrayConfig.render(
                profile = profile,
                tunAddress = plan.ipv4Address,
                mtu = plan.mtu,
                dnsServers = plan.dns,
                logLevel = "warning",
                ipv6 = plan.ipv6Address.isNotEmpty(),
                geo = geoReady,
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
            LogStore.add("D", TAG, "xray.start(fd=${descriptor.fd})")
            core.start(config, descriptor.fd.toLong())
            synchronized(lock) { xray = core }
            LogStore.add("I", TAG, "xray started in ${SystemClock.elapsedRealtime() - coreStart} ms")
            StartResult.OK
        } catch (t: Throwable) {
            LogStore.add(
                "E", TAG,
                "xray start failed after ${SystemClock.elapsedRealtime() - coreStart} ms: " +
                    "${t.javaClass.simpleName}: ${t.message}",
            )
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
        netMonitor.currentNetwork()?.let { network ->
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
            .onSuccess { traffic -> Tunnel.updateTraffic(traffic.uplink, traffic.downlink) }
            .onFailure { LogStore.add("W", TAG, "stats query failed: ${it.message}") }
        runCatching { core.statsDebug() }
            .onSuccess { Tunnel.statsSnapshot = it }
            .onFailure { Tunnel.statsSnapshot = "stats-debug-failed: ${it.message}" }
    }

    private fun checkHealth() {
        if (userStopped) return
        if (Tunnel.state.value !is TunnelState.Connected) return
        val alive = (xray?.isRunning ?: false) || (awg?.isRunning ?: false)
        if (alive) return

        LogStore.add("E", TAG, "engine stopped unexpectedly (watchdog)")
        synchronized(lock) {
            closeResourcesLocked()
            Tunnel.update(TunnelState.Failed(TunnelError.CoreCrashed))
        }
        scheduleRetry("engine crashed")
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
            LogStore.add("W", TAG, "retry skipped while offline ($reason)")
            stopForegroundCompat()
            stopSelf()
            return
        }
        attempt++
        val delay = retryPolicy.delayFor(attempt) ?: return
        val gen = generation
        LogStore.add("I", TAG, "retry $attempt/${retryPolicy.maxAttempts} in $delay ms ($reason)")
        updateNotification("Reconnecting…", "Attempt $attempt")
        try {
            scheduler.schedule({ submit { connect(gen) } }, delay, TimeUnit.MILLISECONDS)
        } catch (e: RejectedExecutionException) {
            LogStore.add("W", TAG, "scheduler rejected retry: ${e.message}")
        }
    }

    private fun fail(reason: TunnelError) {
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
        LogStore.add("I", TAG, "disconnected in ${SystemClock.elapsedRealtime() - started} ms")
        stopForegroundCompat()
    }

    private fun closeAll() {
        synchronized(lock) { closeResourcesLocked() }
    }

    private fun closeResourcesLocked() {
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
        synchronized(lock) {
            closeResourcesLocked()
            if (Tunnel.state.value !is TunnelState.Disconnected) {
                Tunnel.update(TunnelState.Disconnected)
            }
        }
        runCatching { netMonitor.stop() }
        worker.shutdownNow()
        io.shutdownNow()
        scheduler.shutdownNow()
        super.onDestroy()
    }

    // ----------------------------------------------------------- notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
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
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
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
