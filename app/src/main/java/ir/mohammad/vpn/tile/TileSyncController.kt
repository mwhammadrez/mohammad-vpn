package ir.mohammad.vpn.tile

import ir.mohammad.vpn.surfaces.SurfaceState

/** What a tile does with a state. Implemented by the live service; a fake counts calls in tests. */
fun interface TileSink {
    fun show(state: SurfaceState)
}

/**
 * SPEC FR-N1 / AC-N1.
 *
 * The 0.14.x tile updated only from `requestListeningState`, which the platform may defer or coalesce
 * — the reported symptom ("VPN connects, tile stays inactive"). This controller makes the rule
 * explicit and testable:
 *
 *  - a *changed* state is pushed exactly once (no duplicate `updateTile()` for the same value);
 *  - `onListening` always pushes, even when the state did not change, because the platform rebuilds
 *    the tile and may have missed everything that happened while the shade was closed;
 *  - `forceRefresh` exists for the staleness guard: one bounded re-push, never a poll loop.
 */
class TileSyncController(private val sink: TileSink) {

    private var lastShown: SurfaceState? = null

    /** Called from the state collector while the tile is listening. */
    fun onState(state: SurfaceState) {
        if (state == lastShown) return
        lastShown = state
        sink.show(state)
    }

    /** Called from `onStartListening`; always renders. */
    fun onListening(state: SurfaceState) {
        lastShown = state
        sink.show(state)
    }

    /** Called by the staleness guard when the last push is old enough to distrust. */
    fun forceRefresh(state: SurfaceState) {
        lastShown = state
        sink.show(state)
    }

    /** Called from `onStopListening`; the next listener starts from a clean slate. */
    fun onStopped() {
        lastShown = null
    }

    /** Test/diagnostic: the state last pushed to the platform. */
    fun lastShown(): SurfaceState? = lastShown
}
