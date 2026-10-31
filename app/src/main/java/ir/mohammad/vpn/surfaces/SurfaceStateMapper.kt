package ir.mohammad.vpn.surfaces

import ir.mohammad.vpn.data.TunnelState

/**
 * The single place where connection state becomes something a widget or a tile can draw.
 *
 * AC-W1 / AC-W3, and the seam required by the M4-W brief: when the machine grows from five to ten
 * states, this function grows with it and nothing in `widget/` or `tile/` changes.
 *
 * Precedence is deliberate:
 * 1. no profile  -> the user must open the app; nothing else is actionable;
 * 2. no VPN consent -> ask for it, but only while disconnected (a connected tunnel implies consent);
 * 3. otherwise the tunnel state decides.
 *
 * `CONNECTED` is never produced optimistically: [TunnelState.Connected] is only set by the service
 * after a successful SOCKS probe (0.10.0), so the surfaces inherit that rule for free.
 */
object SurfaceStateMapper {

    fun map(
        state: TunnelState,
        hasProfile: Boolean,
        permissionGranted: Boolean,
    ): SurfaceState {
        if (!hasProfile) return SurfaceState.NEEDS_APP
        if (!permissionGranted && state !is TunnelState.Connected && state !is TunnelState.Connecting) {
            return SurfaceState.PERMISSION_NEEDED
        }
        return when (state) {
            TunnelState.Disconnected -> SurfaceState.IDLE
            TunnelState.Connecting -> SurfaceState.CONNECTING
            TunnelState.Connected -> SurfaceState.CONNECTED
            TunnelState.Disconnecting -> SurfaceState.DISCONNECTING
            is TunnelState.Failed -> SurfaceState.FAILED
        }
    }
}
