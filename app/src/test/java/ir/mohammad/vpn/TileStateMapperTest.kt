package ir.mohammad.vpn

import android.service.quicksettings.Tile
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.TileStateMapper
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * AC-W3. The tile has exactly three platform states and every surface state must land on the right
 * one — in particular, nothing but CONNECTED may be ACTIVE.
 */
class TileStateMapperTest {

    @Test
    fun `only connected is active`() {
        val active = SurfaceState.entries.filter { TileStateMapper.statusFor(it) == Tile.STATE_ACTIVE }
        assertEquals(listOf(SurfaceState.CONNECTED), active)
    }

    @Test
    fun `states the user cannot act on are unavailable`() {
        assertEquals(Tile.STATE_UNAVAILABLE, TileStateMapper.statusFor(SurfaceState.NEEDS_APP))
        assertEquals(Tile.STATE_UNAVAILABLE, TileStateMapper.statusFor(SurfaceState.PERMISSION_NEEDED))
    }

    @Test
    fun `everything else is inactive`() {
        listOf(
            SurfaceState.IDLE,
            SurfaceState.CONNECTING,
            SurfaceState.DISCONNECTING,
            SurfaceState.FAILED,
        ).forEach {
            assertEquals("$it must be inactive", Tile.STATE_INACTIVE, TileStateMapper.statusFor(it))
        }
    }

    @Test
    fun `the mapping is total`() {
        SurfaceState.entries.forEach {
            val status = TileStateMapper.statusFor(it)
            assertEquals(
                "$it produced an unknown tile status",
                true,
                status == Tile.STATE_ACTIVE || status == Tile.STATE_INACTIVE || status == Tile.STATE_UNAVAILABLE,
            )
        }
    }
}
