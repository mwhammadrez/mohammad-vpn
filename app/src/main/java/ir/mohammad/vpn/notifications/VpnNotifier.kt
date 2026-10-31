package ir.mohammad.vpn.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import ir.mohammad.vpn.MainActivity
import ir.mohammad.vpn.R
import ir.mohammad.vpn.data.ByteFormat
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.surfaces.TunnelController
import ir.mohammad.vpn.vpn.MohammadVpnService

/**
 * SPEC FR-N4…FR-N6 / AC-N4…AC-N6.
 *
 * One snapshot in, one immutable Notification out: state, counters and pause flag are read once so a
 * partially updated notification can never say "Connected" while showing the previous session's bytes
 * (catalog finding 2.5).
 *
 * No endpoint, profile name or key is rendered here — only a state word and two byte totals.
 */
object VpnNotifier {

    const val CHANNEL_ID = "mohammad_vpn_status"
    const val NOTIFICATION_ID = 1001

    private const val REQUEST_ACTIVITY = 10
    private const val REQUEST_DISCONNECT = 11
    private const val REQUEST_PAUSE = 12

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "VPN status",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Connection state and data usage while the tunnel is active"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun title(context: Context, state: TunnelState, paused: Boolean): String = when {
        paused -> context.getString(R.string.notif_title_paused)
        state is TunnelState.Connected -> context.getString(R.string.notif_title_connected)
        state is TunnelState.Connecting -> context.getString(R.string.notif_title_connecting)
        state is TunnelState.Disconnecting -> context.getString(R.string.notif_title_disconnecting)
        state is TunnelState.Failed -> context.getString(R.string.notif_title_failed)
        else -> context.getString(R.string.notif_title_idle)
    }

    /** "↓ 1.2 MB   ↑ 340 KB", or "↓ —   ↑ —" when the counters cannot be read. */
    fun volumeLine(traffic: TrafficSnapshot): String =
        "↓ " + ByteFormat.format(traffic, downlink = true) + "   ↑ " + ByteFormat.format(traffic, downlink = false)

    fun build(
        context: Context,
        state: TunnelState,
        traffic: TrafficSnapshot,
        paused: Boolean,
    ): Notification {
        ensureChannel(context)

        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vpn)
            .setContentTitle(title(context, state, paused))
            .setContentText(volumeLine(traffic))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setContentIntent(openApp(context))

        if (!paused) {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.notif_action_disconnect),
                    service(context, MohammadVpnService.ACTION_DISCONNECT, REQUEST_DISCONNECT),
                ).build(),
            )
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.notif_action_pause),
                    service(context, MohammadVpnService.ACTION_PAUSE, REQUEST_PAUSE),
                ).build(),
            )
        } else {
            builder.addAction(
                Notification.Action.Builder(
                    null,
                    context.getString(R.string.notif_action_resume),
                    service(context, MohammadVpnService.ACTION_RESUME, REQUEST_PAUSE),
                ).build(),
            )
        }

        return builder.build()
    }

    fun post(context: Context, notification: Notification) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                ?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_ACTIVITY,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Actions go through [TunnelController], so the notification cannot drift from the dial, the tile
     * or the widget (AC-N5).
     */
    private fun service(context: Context, action: String, request: Int): PendingIntent {
        val intent = TunnelController.serviceIntent(context, action)
        return if (action == MohammadVpnService.ACTION_CONNECT ||
            action == MohammadVpnService.ACTION_RESUME
        ) {
            PendingIntent.getForegroundService(context, request, intent, flags())
        } else {
            PendingIntent.getService(context, request, intent, flags())
        }
    }

    private fun flags(): Int = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

    /** Kept for callers that only have a Context and want the colour used by the surfaces. */
    fun accent(context: Context): Int = ContextCompat.getColor(context, R.color.surface_mint)
}
