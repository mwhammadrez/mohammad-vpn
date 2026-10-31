package ir.mohammad.vpn.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ir.mohammad.vpn.MainActivity
import ir.mohammad.vpn.R
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.SurfaceStore
import ir.mohammad.vpn.surfaces.TileStateMapper
import ir.mohammad.vpn.surfaces.TunnelController
import java.lang.ref.WeakReference

/**
 * Quick-settings tile. AC-N1, AC-N2, AC-N3, AC-W3.
 *
 * Reliability rules, all of them learned from the 0.14.x symptom "VPN connected, tile stayed inactive":
 *
 *  1. state arrives from the shared collector, not from `onStartListening` alone;
 *  2. every distinct state is pushed exactly once ([TileSyncController]);
 *  3. `onStartListening` always pushes, because the platform may have discarded everything that
 *     happened while the shade was closed;
 *  4. a bounded staleness refresh (5 s) covers a missed callback without becoming a poll loop;
 *  5. the static reference to the live service is weak and cleared on stop, so the platform can
 *     destroy it freely.
 */
class MohammadTileService : TileService() {

    @Volatile
    private var listening = false

    private lateinit var sync: TileSyncController
    private val handler = Handler(Looper.getMainLooper())
    private val staleRefresh = Runnable {
        if (listening) sync.forceRefresh(SurfaceStore.currentState(this))
    }

    override fun onCreate() {
        super.onCreate()
        sync = TileSyncController(QsTileSink(this))
    }

    override fun onStartListening() {
        listening = true
        active.set(WeakReference(this))
        sync.onListening(SurfaceStore.currentState(this))
        handler.removeCallbacks(staleRefresh)
        handler.postDelayed(staleRefresh, STALE_REFRESH_MS)
    }

    override fun onStopListening() {
        listening = false
        handler.removeCallbacks(staleRefresh)
        sync.onStopped()
        active.set(null)
    }

    override fun onTileAdded() {
        active.set(WeakReference(this))
        sync.forceRefresh(SurfaceStore.currentState(this))
    }

    override fun onTileRemoved() {
        listening = false
        handler.removeCallbacks(staleRefresh)
        sync.onStopped()
        active.set(null)
    }

    override fun onClick() {
        if (needsApp(TunnelController.toggle(this))) openApp()
        // Immediate feedback; the collector will confirm with the real state.
        if (listening) sync.forceRefresh(SurfaceStore.currentState(this))
    }

    /** Called by [ir.mohammad.vpn.surfaces.SurfaceUpdater] on every state transition. */
    internal fun onStateChanged() {
        if (!listening) return
        sync.onState(SurfaceStore.currentState(this))
    }

    /** Test hook: was the platform told to listen? */
    internal fun isListeningNow(): Boolean = listening

    /** The guidance rule, separated so it can be tested without a bound tile. */
    internal fun needsApp(outcome: TunnelController.Outcome): Boolean =
        outcome == TunnelController.Outcome.NEEDS_SETUP ||
            outcome == TunnelController.Outcome.NEEDS_PERMISSION

    /** Kept for callers in tests and for the cold-start path. */
    internal fun render() {
        sync.forceRefresh(SurfaceStore.currentState(this))
    }

    /**
     * `startActivityAndCollapse(PendingIntent)` only exists from API 34; minSdk is 26, so the
     * deprecated Intent overload is still required below it (D-82).
     */
    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    /** Writes one [SurfaceState] onto the platform tile. Null-safe outside a listening window. */
    private class QsTileSink(private val service: TileService) : TileSink {
        override fun show(state: SurfaceState) {
            val tile = service.qsTile ?: return
            tile.state = TileStateMapper.statusFor(state)
            tile.label = service.getString(R.string.tile_label)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                tile.subtitle = state.subtitle(service)
                tile.contentDescription = state.description(service)
            }
            tile.updateTile()
        }
    }

    companion object {
        private const val STALE_REFRESH_MS = 5_000L

        /** Weak by design: holding a service reference here would outlive the platform's binding. */
        private val active = java.util.concurrent.atomic.AtomicReference<WeakReference<MohammadTileService>?>()

        /** Push immediately if a tile is currently listening; otherwise ask the platform to re-listen. */
        fun notifyStateChanged(context: Context) {
            active.get()?.get()?.onStateChanged()
            refresh(context)
        }

        /** Ask the platform to call [onStartListening] again, which re-renders the tile. */
        fun refresh(context: Context) {
            runCatching {
                requestListeningState(context, ComponentName(context, MohammadTileService::class.java))
            }
        }

        /** Test hook: true while a live service is registered. */
        internal fun hasActive(): Boolean = active.get()?.get() != null
    }
}
