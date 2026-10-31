package ir.mohammad.vpn.surfaces

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.vpn.MohammadVpnService

/**
 * The one way into the tunnel.
 *
 * The in-app dial, the home-screen widget and the quick-settings tile all call these functions; none
 * of them builds its own Intent. That is what makes "same entry point, no second logic path"
 * checkable instead of aspirational (AC-W2).
 *
 * Everything here is safe to call with the process cold: the intent either starts the service (which
 * loads the bundle in `onCreate`) or, when consent is missing, reports [Outcome.NEEDS_PERMISSION] so
 * the caller opens the app instead of failing silently.
 */
object TunnelController {

    enum class Outcome { STARTED, STOPPED, NEEDS_SETUP, NEEDS_PERMISSION, IGNORED }

    fun hasProfile(): Boolean = Tunnel.selected != null

    fun permissionGranted(context: Context): Boolean = VpnService.prepare(context) == null

    /** Connect/disconnect depending on the real current state. */
    fun toggle(context: Context): Outcome = when (Tunnel.state.value) {
        is TunnelState.Connected, is TunnelState.Connecting -> disconnect(context)
        is TunnelState.Disconnecting -> Outcome.IGNORED
        else -> connect(context)
    }

    fun connect(context: Context): Outcome {
        if (!hasProfile()) return Outcome.NEEDS_SETUP
        if (!permissionGranted(context)) return Outcome.NEEDS_PERMISSION
        ContextCompat.startForegroundService(
            context,
            serviceIntent(context, MohammadVpnService.ACTION_CONNECT),
        )
        return Outcome.STARTED
    }

    fun disconnect(context: Context): Outcome {
        context.startService(serviceIntent(context, MohammadVpnService.ACTION_DISCONNECT))
        return Outcome.STOPPED
    }

    /** The only place a tunnel Intent is built. */
    fun serviceIntent(context: Context, action: String): Intent =
        Intent(context, MohammadVpnService::class.java).setAction(action)

    /** What a widget or tile tap should send: one toggle, resolved by the service. */
    fun toggleIntent(context: Context): Intent =
        serviceIntent(context, MohammadVpnService.ACTION_TOGGLE)
}
