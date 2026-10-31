package ir.mohammad.vpn.surfaces

import android.content.Context
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelState

/**
 * What a surface needs to know when the app process has just been created (widget tap after a
 * reboot, tile tap after Doze) and no Activity has run yet.
 *
 * The bundle itself is loaded by the Activity, so `Tunnel.selected` is null until the user opens the
 * app at least once. The surfaces therefore read the persisted selection written by
 * [ir.mohammad.vpn.MainActivity.selectProfile] to decide between "ready" and "open the app".
 */
object SurfaceStore {

    const val PREFS = "mohammad"
    private const val KEY_SELECTED = "selected_profile"

    fun hasProfile(context: Context): Boolean {
        if (Tunnel.selected != null) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SELECTED, null) != null
    }

    fun permissionGranted(context: Context): Boolean = TunnelController.permissionGranted(context)

    /** The state the surfaces should draw right now. */
    fun currentState(context: Context): SurfaceState =
        SurfaceStateMapper.map(
            state = Tunnel.state.value,
            hasProfile = hasProfile(context),
            permissionGranted = permissionGranted(context),
        )

    /** Convenience for callers that already hold a raw state. */
    fun surfaceOf(context: Context, state: TunnelState): SurfaceState =
        SurfaceStateMapper.map(state, hasProfile(context), permissionGranted(context))
}
