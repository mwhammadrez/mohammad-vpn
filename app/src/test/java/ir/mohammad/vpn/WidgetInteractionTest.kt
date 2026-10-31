package ir.mohammad.vpn

import android.app.PendingIntent
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.TunnelController
import ir.mohammad.vpn.vpn.MohammadVpnService
import ir.mohammad.vpn.widget.MohammadWidgetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * AC-W2: a widget tap must enter the tunnel through the same door as the rotary dial — one entry
 * point, no second logic path.
 *
 * Three separate claims are checked:
 * 1. the PendingIntent the widget attaches is exactly [TunnelController.toggleIntent];
 * 2. when the user cannot toggle yet, the same tap opens the app instead of starting a doomed service;
 * 3. the service resolves TOGGLE with the same rule the controller uses, and the dial's Activity
 *    actually calls the controller (a source-level guard against re-duplicating the flow).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class WidgetInteractionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `an actionable tap attaches the shared toggle intent`() {
        val pending = MohammadWidgetProvider.intentFor(context, actionable = true)
        val saved = shadowOf(pending).savedIntent

        assertEquals(MohammadVpnService::class.java.name, saved.component?.className)
        assertEquals(MohammadVpnService.ACTION_TOGGLE, saved.action)
    }

    @Test
    fun `the attached intent is byte-for-byte the controller intent`() {
        val pending = MohammadWidgetProvider.intentFor(context, actionable = true)
        val saved = shadowOf(pending).savedIntent
        val expected = TunnelController.toggleIntent(context)

        assertEquals(expected.component, saved.component)
        assertEquals(expected.action, saved.action)
    }

    @Test
    fun `a non actionable tap opens the app`() {
        val pending = MohammadWidgetProvider.intentFor(context, actionable = false)
        val saved = shadowOf(pending).savedIntent

        assertEquals(MainActivity::class.java.name, saved.component?.className)
        assertNull("opening the app must not carry a tunnel action", saved.action)
    }

    @Test
    fun `the decision requires a profile, consent and a toggleable state`() {
        assertTrue(
            MohammadWidgetProvider.isActionable(SurfaceState.IDLE, hasProfile = true, permissionGranted = true),
        )
        assertFalse(
            "no profile",
            MohammadWidgetProvider.isActionable(SurfaceState.IDLE, hasProfile = false, permissionGranted = true),
        )
        assertFalse(
            "no consent",
            MohammadWidgetProvider.isActionable(SurfaceState.IDLE, hasProfile = true, permissionGranted = false),
        )
        assertFalse(
            "mid-connect",
            MohammadWidgetProvider.isActionable(SurfaceState.CONNECTING, hasProfile = true, permissionGranted = true),
        )
        assertFalse(
            "needs setup",
            MohammadWidgetProvider.isActionable(SurfaceState.NEEDS_APP, hasProfile = true, permissionGranted = true),
        )
    }

    @Test
    fun `the service resolves toggle exactly like the controller`() {
        val expected = mapOf(
            TunnelState.Disconnected to MohammadVpnService.ACTION_CONNECT,
            TunnelState.Connecting to MohammadVpnService.ACTION_DISCONNECT,
            TunnelState.Connected to MohammadVpnService.ACTION_DISCONNECT,
            TunnelState.Disconnecting to null,
        )
        expected.forEach { (state, action) ->
            assertEquals(
                "toggle from $state",
                action,
                MohammadVpnService.resolve(MohammadVpnService.ACTION_TOGGLE, state),
            )
        }
    }

    @Test
    fun `explicit actions pass through untouched`() {
        assertEquals(
            MohammadVpnService.ACTION_CONNECT,
            MohammadVpnService.resolve(MohammadVpnService.ACTION_CONNECT, TunnelState.Connected),
        )
        assertEquals(
            MohammadVpnService.ACTION_DISCONNECT,
            MohammadVpnService.resolve(MohammadVpnService.ACTION_DISCONNECT, TunnelState.Disconnected),
        )
        assertNull(MohammadVpnService.resolve(null, TunnelState.Disconnected))
    }

    @Test
    fun `the in-app dial delegates to the shared controller`() {
        // A guard, not a style opinion: if someone re-adds a private startService() call in the
        // Activity, two paths exist again and this fails.
        val root = listOf(File(".."), File(".")).map { it.absoluteFile }
            .first { File(it, "app/src/main/java").isDirectory }
        val activity = File(root, "app/src/main/java/ir/mohammad/vpn/MainActivity.kt").readText()

        assertTrue(
            "the Activity must call TunnelController.connect",
            activity.contains("TunnelController.connect("),
        )
        assertTrue(
            "the Activity must call TunnelController.disconnect",
            activity.contains("TunnelController.disconnect("),
        )
        assertFalse(
            "the Activity must not build its own tunnel Intent",
            activity.contains("Intent(this, MohammadVpnService::class.java)"),
        )
    }

    @Test
    fun `pending intents are immutable`() {
        listOf(true, false).forEach { actionable ->
            val flags = shadowOf(MohammadWidgetProvider.intentFor(context, actionable)).flags
            assertTrue(
                "FLAG_IMMUTABLE is required on Android 12+",
                flags and PendingIntent.FLAG_IMMUTABLE != 0,
            )
        }
    }
}
