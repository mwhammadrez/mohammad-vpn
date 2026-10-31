package ir.mohammad.vpn

import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.tile.TileSyncController
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * AC-N1. The 0.14.x bug was "VPN connects but the tile stays inactive": the tile only rendered from
 * `onStartListening`, so a change while the shade was closed was lost.
 *
 * `TileSyncController` is the fix, and this is its contract: one push per *distinct* state, an
 * unconditional push when the platform starts listening, and an explicit reset when it stops.
 */
class TileStateSyncTest {

    private class CountingSink : ir.mohammad.vpn.tile.TileSink {
        val shown = mutableListOf<SurfaceState>()
        override fun show(state: SurfaceState) {
            shown += state
        }
    }

    @Test
    fun `every transition is pushed exactly once`() {
        val sink = CountingSink()
        val sync = TileSyncController(sink)

        sync.onListening(SurfaceState.IDLE)
        sync.onState(SurfaceState.CONNECTING)
        sync.onState(SurfaceState.CONNECTED)
        sync.onState(SurfaceState.IDLE)

        assertEquals(
            listOf(
                SurfaceState.IDLE,
                SurfaceState.CONNECTING,
                SurfaceState.CONNECTED,
                SurfaceState.IDLE,
            ),
            sink.shown,
        )
    }

    @Test
    fun `a repeated state is not pushed twice`() {
        val sink = CountingSink()
        val sync = TileSyncController(sink)

        sync.onListening(SurfaceState.CONNECTED)
        sync.onState(SurfaceState.CONNECTED)
        sync.onState(SurfaceState.CONNECTED)

        assertEquals(1, sink.shown.size)
    }

    @Test
    fun `onStartListening always pushes, even when nothing changed`() {
        val sink = CountingSink()
        val sync = TileSyncController(sink)

        sync.onListening(SurfaceState.CONNECTED)
        sync.onListening(SurfaceState.CONNECTED)

        assertEquals("the platform may have discarded the earlier render", 2, sink.shown.size)
    }

    @Test
    fun `a state missed while stopped is pushed on the next listen`() {
        val sink = CountingSink()
        val sync = TileSyncController(sink)

        sync.onListening(SurfaceState.IDLE)
        sync.onState(SurfaceState.CONNECTED)
        sync.onStopped()

        // While stopped the collector is not delivering; the next listen must catch up.
        sync.onListening(SurfaceState.CONNECTED)
        assertEquals(SurfaceState.CONNECTED, sink.shown.last())
        assertEquals(listOf(SurfaceState.IDLE, SurfaceState.CONNECTED, SurfaceState.CONNECTED), sink.shown)
    }

    @Test
    fun `the staleness guard forces one refresh`() {
        val sink = CountingSink()
        val sync = TileSyncController(sink)

        sync.onListening(SurfaceState.CONNECTED)
        sync.forceRefresh(SurfaceState.CONNECTED)

        assertEquals(2, sink.shown.size)
    }

    @Test
    fun `lastShown tracks what the platform was told`() {
        val sync = TileSyncController(CountingSink())
        assertNull(sync.lastShown())

        sync.onState(SurfaceState.CONNECTING)
        assertEquals(SurfaceState.CONNECTING, sync.lastShown())

        sync.onStopped()
        assertNull("a stopped tile must not believe it is up to date", sync.lastShown())
    }

    @Test
    fun `the controller never invents a state`() {
        val sink = CountingSink()
        val sync = TileSyncController(sink)
        // Only states it was given can be shown; there is no default and no optimistic green.
        sync.onListening(SurfaceState.IDLE)
        assertEquals(setOf(SurfaceState.IDLE), sink.shown.toSet())
    }
}
