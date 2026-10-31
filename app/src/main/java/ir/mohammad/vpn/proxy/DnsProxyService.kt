package ir.mohammad.vpn.proxy

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import ir.mohammad.vpn.R
import ir.mohammad.vpn.core.DnsConfig
import ir.mohammad.vpn.data.DnsEngine
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.ProxyState
import ir.mohammad.vpn.data.Tunnel

/**
 * Mode 2 in proxy mode: the DNS engine runs, publishes SOCKS5 on 127.0.0.1, and nothing else changes.
 *
 * No VpnService, no routes, no consent dialog — apps that accept a proxy use it directly. It still
 * needs a foreground service, because a child process without one dies the moment the user leaves.
 *
 * Type `specialUse` with the existing FOREGROUND_SERVICE_SPECIAL_USE permission: the app's permission
 * list does not grow for this feature.
 */
class DnsProxyService : Service() {

    private var engine: DnsEngine.Handle? = null
    @Volatile private var stopping = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopEngine("requested")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val profile = Tunnel.profiles.firstOrNull { it.id == intent?.getStringExtra(EXTRA_PROFILE_ID) }
            ?: Tunnel.selected?.takeIf { it.core == "DNS" }
        if (profile?.dns == null) {
            LogStore.add("E", TAG, "no DNS profile to run")
            ProxyState.failed("select a DNS profile first")
            stopSelf()
            return START_NOT_STICKY
        }

        if (engine != null) {
            LogStore.add("D", TAG, "start ignored: an engine is already running")
            return START_STICKY
        }

        startForegroundCompat(profile)
        ProxyState.starting(profile.id, profile.name, DnsConfig.SOCKS_PORT)
        return try {
            engine = DnsEngine.start(this, profile.dns, DnsConfig.SOCKS_PORT) { line ->
                if (line.contains("[ERROR]", true) || line.contains("[WARN]", true)) {
                    LogStore.add("W", "DNS", line)
                } else {
                    LogStore.add("D", "DNS", line)
                }
            }
            if (!DnsEngine.awaitPort(DnsConfig.SOCKS_PORT, 15_000)) {
                throw IllegalStateException("engine did not open its SOCKS port")
            }
            ProxyState.running(profile.id, profile.name, DnsConfig.SOCKS_PORT)
            LogStore.add("I", TAG, "proxy ready on 127.0.0.1:${DnsConfig.SOCKS_PORT}")
            START_STICKY
        } catch (t: Throwable) {
            LogStore.add("E", TAG, "proxy start failed: ${t.javaClass.simpleName}: ${t.message}")
            ProxyState.failed(t.message ?: "engine start failed")
            stopEngine("start failed")
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        stopEngine("service destroyed")
        super.onDestroy()
    }

    private fun stopEngine(reason: String) {
        if (stopping) return
        stopping = true
        engine?.let {
            runCatching { it.stop() }
                .onSuccess { LogStore.add("D", TAG, "engine stopped ($reason)") }
                .onFailure { e -> LogStore.add("E", TAG, "engine stop failed: ${e.message}") }
        }
        engine = null
        ProxyState.stopped()
    }

    private fun startForegroundCompat(profile: Profile) {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "DNS proxy", NotificationManager.IMPORTANCE_LOW).apply {
                    setShowBadge(false)
                },
            )
        }

        val open = android.app.PendingIntent.getActivity(
            this, 0,
            Intent(this, Class.forName("ir.mohammad.vpn.MainActivity")),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = android.app.PendingIntent.getService(
            this, 1,
            Intent(this, DnsProxyService::class.java).setAction(ACTION_STOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification: Notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle("DNS proxy · ${profile.name}")
            .setContentText("127.0.0.1:${DnsConfig.SOCKS_PORT}")
            .setOngoing(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "STOP", stop).build())
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val TAG = "DNS-PROXY"
        const val CHANNEL = "dns_proxy"
        const val NOTIFICATION_ID = 4711
        const val ACTION_START = "ir.mohammad.vpn.DNS_PROXY_START"
        const val ACTION_STOP = "ir.mohammad.vpn.DNS_PROXY_STOP"
        const val EXTRA_PROFILE_ID = "profile_id"

        fun start(context: Context, profile: Profile) {
            val intent = Intent(context, DnsProxyService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_PROFILE_ID, profile.id)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * `stopService` rather than an ACTION_STOP intent: stopping is allowed from the background,
         * while starting a service from a widget or tile callback is not (Android 8+ restrictions).
         */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DnsProxyService::class.java)) }
        }
    }
}
