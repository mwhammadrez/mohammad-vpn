package ir.mohammad.vpn.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import ir.mohammad.vpn.MainActivity
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.SurfaceStore
import ir.mohammad.vpn.surfaces.TileStateMapper
import ir.mohammad.vpn.surfaces.TunnelController

/**
 * Quick-settings tile. AC-W3.
 *
 * Lifecycle discipline (the brief calls it out): a listener is considered active only between
 * `onStartListening` and `onStopListening`, and nothing outside the service keeps a reference to it.
 * `render()` is a no-op when the platform gave us no tile (unit tests, and briefly during boot).
 */
class MohammadTileService : TileService() {

    @Volatile
    private var listening = false

    override fun onStartListening() {
        listening = true
        render()
    }

    override fun onStopListening() {
        listening = false
    }

    override fun onTileAdded() {
        render()
    }

    override fun onTileRemoved() {
        listening = false
    }

    override fun onClick() {
        if (needsApp(TunnelController.toggle(this))) openApp()
        render()
    }

    /** The guidance rule, separated so it can be tested without a bound tile. */
    internal fun needsApp(outcome: TunnelController.Outcome): Boolean =
        outcome == TunnelController.Outcome.NEEDS_SETUP ||
            outcome == TunnelController.Outcome.NEEDS_PERMISSION

    /** Exposed for the lifecycle test; not part of the public surface. */
    internal fun isListening(): Boolean = listening

    internal fun render() {
        val tile = qsTile ?: return
        val state = SurfaceStore.currentState(this)
        tile.state = TileStateMapper.statusFor(state)
        tile.label = getString(ir.mohammad.vpn.R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = state.subtitle(this)
            tile.contentDescription = state.description(this)
        }
        tile.updateTile()
    }

    /**
     * `startActivityAndCollapse(PendingIntent)` only exists from API 34; minSdk is 26, so the
     * deprecated Intent overload is still required below it (D-82). Suppressed with that reason
     * rather than raising minSdk for a cosmetic API.
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

    companion object {
        /** Ask the platform to call [onStartListening] again, which re-renders the tile. */
        fun refresh(self: android.content.Context) {
            runCatching {
                requestListeningState(
                    self,
                    android.content.ComponentName(self, MohammadTileService::class.java),
                )
            }
        }
    }
}
