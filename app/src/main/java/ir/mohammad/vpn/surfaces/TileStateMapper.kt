package ir.mohammad.vpn.surfaces

import android.service.quicksettings.Tile

/**
 * AC-W3. Same seam as [SurfaceStateMapper]: the tile only ever sees a [SurfaceState], so M4's ten
 * states arrive here through the mapper and this file does not change.
 */
object TileStateMapper {

    fun statusFor(state: SurfaceState): Int = when {
        !state.tileAvailable -> Tile.STATE_UNAVAILABLE
        state.tileActive -> Tile.STATE_ACTIVE
        else -> Tile.STATE_INACTIVE
    }
}
