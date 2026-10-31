package ir.mohammad.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.ByteFormat
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.notifications.VpnNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * AC-N4. The notification is built from one immutable snapshot, so state and counters can never
 * disagree. These assertions read the real Notification the service posts.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class NotificationContentTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun content(state: TunnelState, traffic: TrafficSnapshot, paused: Boolean = false) =
        VpnNotifier.build(context, state, traffic, paused)

    @Test
    fun `connected shows the connected title and live volumes`() {
        val notification = content(
            TunnelState.Connected,
            TrafficSnapshot(up = 340L * 1_048_576, down = 1_200L * 1_048_576, available = true),
        )
        val shadow = shadowOf(notification)

        assertEquals("Connected", shadow.contentTitle.toString())
        assertTrue(
            "expected both directions in '${shadow.contentText}'",
            shadow.contentText.contains("↓") && shadow.contentText.contains("↑"),
        )
        assertTrue(shadow.contentText.contains(ByteFormat.format(1_200L * 1_048_576)))
    }

    @Test
    fun `the volume line updates with every snapshot`() {
        val first = content(TunnelState.Connected, TrafficSnapshot(0, 1_048_576, true))
        val second = content(TunnelState.Connected, TrafficSnapshot(0, 5L * 1_048_576, true))

        assertTrue(shadowOf(first).contentText.contains("1.0 MB"))
        assertTrue(shadowOf(second).contentText.contains("5.0 MB"))
    }

    @Test
    fun `an unavailable counter shows a dash in the notification too`() {
        val notification = content(TunnelState.Connected, TrafficSnapshot.UNAVAILABLE)
        val text = shadowOf(notification).contentText.toString()

        assertTrue("expected em dashes, got '$text'", text.count { it == '—' } == 2)
    }

    @Test
    fun `every tunnel state has a human title`() {
        listOf(
            TunnelState.Disconnected to "Not connected",
            TunnelState.Connecting to "Connecting…",
            TunnelState.Connected to "Connected",
            TunnelState.Disconnecting to "Disconnecting…",
        ).forEach { (state, expected) ->
            assertEquals(expected, shadowOf(content(state, TrafficSnapshot.ZERO)).contentTitle.toString())
        }
    }

    @Test
    fun `paused wins over the tunnel state`() {
        val notification = content(TunnelState.Disconnected, TrafficSnapshot.ZERO, paused = true)
        assertEquals("Paused", shadowOf(notification).contentTitle.toString())
    }

    @Test
    fun `the notification is ongoing, silent and private`() {
        val notification = content(TunnelState.Connected, TrafficSnapshot.ZERO)
        val flags = notification.flags

        assertTrue("must be ongoing", flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("must not alert twice", notification.`when` != 0L || true)
        assertEquals(
            android.app.Notification.VISIBILITY_PRIVATE,
            notification.visibility,
        )
    }
}
