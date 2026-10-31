package ir.mohammad.vpn.surfaces

import android.content.Context
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.tile.MohammadTileService
import ir.mohammad.vpn.widget.MohammadWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Pushes every connection-state change to the widget and the tile.
 *
 * Reactive, not polled: one collector on [Tunnel.state]. It lives for the life of the process, which
 * is exactly as long as a state change can matter — when the process dies the tunnel dies with it and
 * both surfaces correctly fall back to IDLE.
 */
object SurfaceUpdater {

    @Volatile
    private var scope: CoroutineScope? = null

    fun start(context: Context) {
        if (scope != null) return
        val app = context.applicationContext
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = newScope
        newScope.launch {
            Tunnel.state.collect {
                MohammadWidgetProvider.updateAll(app)
                // Immediate push when a tile is listening; a re-listen request otherwise.
                MohammadTileService.notifyStateChanged(app)
            }
        }
    }

    /** Test-only: stops the collector so a later test does not observe a previous one. */
    internal fun stopForTest() {
        scope?.cancel()
        scope = null
    }
}
