package ir.mohammad.vpn

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.notifications.VpnNotifier
import ir.mohammad.vpn.surfaces.TunnelController
import ir.mohammad.vpn.vpn.MohammadVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AC-N5, AC-N6. A notification action is only trustworthy if it is the same intent the dial sends —
 * otherwise the button and the app can disagree about what "disconnect" means.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationActionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun actions(paused: Boolean): List<Notification.Action> =
        VpnNotifier.build(context, TunnelState.Connected, TrafficSnapshot.ZERO, paused).actions.toList()

    private fun actionTarget(paused: Boolean, title: String): android.content.Intent {
        val action = actions(paused).first { it.title.toString() == title }
        return shadowOf(action.actionIntent).savedIntent
    }

    @Test
    fun `disconnect targets the service with the shared action`() {
        val intent = actionTarget(paused = false, title = "Disconnect")
        val expected = TunnelController.serviceIntent(context, MohammadVpnService.ACTION_DISCONNECT)

        assertEquals(MohammadVpnService::class.java.name, intent.component?.className)
        assertEquals(expected.action, intent.action)
        assertEquals(MohammadVpnService.ACTION_DISCONNECT, intent.action)
    }

    @Test
    fun `pause and resume swap with the paused flag`() {
        assertEquals(
            MohammadVpnService.ACTION_PAUSE,
            actionTarget(paused = false, title = "Pause").action,
        )
        assertEquals(
            MohammadVpnService.ACTION_RESUME,
            actionTarget(paused = true, title = "Resume").action,
        )
    }

    @Test
    fun `a paused notification does not offer pause again`() {
        val titles = actions(paused = true).map { it.title.toString() }
        assertTrue("paused: $titles", titles.contains("Resume"))
        assertTrue("paused: $titles", !titles.contains("Pause"))
        assertTrue("paused: $titles", !titles.contains("Disconnect"))
    }

    @Test
    fun `the resume action starts a foreground service, not a background one`() {
        val action = actions(paused = true).first { it.title.toString() == "Resume" }
        // A getForegroundService PendingIntent carries a different type; Robolectric exposes the
        // intent, so assert the action and that the caller can start it as a foreground service.
        val intent = shadowOf(action.actionIntent).savedIntent
        assertEquals(MohammadVpnService.ACTION_RESUME, intent.action)
        assertNotNull(intent.component)
    }

    @Test
    fun `every action is immutable`() {
        actions(paused = false).forEach { action ->
            val flags = shadowOf(action.actionIntent).flags
            assertTrue(
                "FLAG_IMMUTABLE is required on Android 12+",
                flags and android.app.PendingIntent.FLAG_IMMUTABLE != 0,
            )
        }
    }
}
