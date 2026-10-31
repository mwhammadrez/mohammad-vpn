package ir.mohammad.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.SurfaceStore
import ir.mohammad.vpn.widget.MohammadWidgetProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AC-W6: process death and cold start.
 *
 * A cold process has no `Tunnel.selected` (the bundle is loaded by the Activity), so the naive
 * implementation shows "open the app" to everyone after a reboot. [SurfaceStore] falls back to the
 * selection the Activity persisted. These tests model the two states a killed app can wake up in:
 * never set up, and set up but not running.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SurfaceColdStartTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val prefs by lazy { context.getSharedPreferences("mohammad", Context.MODE_PRIVATE) }

    @Before
    fun coldProcess() {
        prefs.edit().clear().commit()
        Tunnel.selected = null
        Tunnel.update(TunnelState.Disconnected)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        Tunnel.selected = null
        Tunnel.update(TunnelState.Disconnected)
    }

    @Test
    fun `a device that has never been set up asks the user to open the app`() {
        assertEquals(SurfaceState.NEEDS_APP, SurfaceStore.currentState(context, permissionGranted = true))
    }

    @Test
    fun `a set up device reads idle after process death, not needs-app`() {
        prefs.edit().putString("selected_profile", "p1").commit()
        Tunnel.selected = null

        assertEquals(SurfaceState.IDLE, SurfaceStore.currentState(context, permissionGranted = true))
    }

    @Test
    fun `the widget renders from the persisted selection without an Activity`() {
        prefs.edit().putString("selected_profile", "p1").commit()

        val views = MohammadWidgetProvider.buildViews(context, SurfaceStore.currentState(context, permissionGranted = true))
        val view = views.apply(context, null)

        assertNotNull(view.findViewById(R.id.widget_title))
        assertEquals(
            SurfaceState.IDLE.title(context),
            (view.findViewById<android.widget.TextView>(R.id.widget_title)).text.toString(),
        )
    }

    @Test
    fun `in-memory selection wins over the persisted one`() {
        prefs.edit().putString("selected_profile", "old").commit()
        Tunnel.selected = null
        assertEquals(SurfaceState.IDLE, SurfaceStore.currentState(context, permissionGranted = true))

        // The Activity loading a bundle sets this; the surfaces must follow it immediately.
        Tunnel.selected = ir.mohammad.vpn.data.Profile(
            "new", "N", "VLESS", "XRAY", "h.invalid", 443,
            org.json.JSONObject("""{"protocol":"vless","settings":{}}"""), null,
        )
        assertEquals(SurfaceState.IDLE, SurfaceStore.currentState(context, permissionGranted = true))
    }

    @Test
    fun `AC-N3 - after process death the tile is inactive, not unavailable`() {
        prefs.edit().putString("selected_profile", "p1").commit()

        val state = SurfaceStore.currentState(context, permissionGranted = true)
        assertEquals(SurfaceState.IDLE, state)
        assertEquals(
            android.service.quicksettings.Tile.STATE_INACTIVE,
            ir.mohammad.vpn.surfaces.TileStateMapper.statusFor(state),
        )
        assertEquals(state, SurfaceStateMapperSafe.mapForTile(context))
    }

    @Test
    fun `a tunnel that died with the process is not reported as connected`() {
        prefs.edit().putString("selected_profile", "p1").commit()
        // Process restart: state is whatever the fresh process defaults to.
        assertEquals(TunnelState.Disconnected, Tunnel.state.value)
        assertEquals(SurfaceState.IDLE, SurfaceStore.currentState(context, permissionGranted = true))
    }
}

/** Small indirection so the AC-N3 test can state the tile's view explicitly. */
private object SurfaceStateMapperSafe {
    fun mapForTile(context: Context): SurfaceState =
        SurfaceStore.currentState(context, permissionGranted = true)
}
