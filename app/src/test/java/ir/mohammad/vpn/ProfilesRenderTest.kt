package ir.mohammad.vpn

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.MohammadScreen
import ir.mohammad.vpn.ui.ProfilePing
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The profile section gained a paste button and a per-row delay chip. This renders it, so a layout
 * that overflows or a chip that hides the name is visible here rather than on the operator's phone.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ProfilesRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun profile(id: String, name: String, protocol: String) = Profile(
        id, name, protocol, "XRAY", "h.example", 443,
        JSONObject("""{"protocol":"vless","settings":{"vnext":[]},"streamSettings":{"network":"ws"}}"""),
        null,
    )

    @Test
    fun profilesWithPasteAndDelayChips() {
        val operator = profile("p1", "Operator Node", "VLESS")
        val pasted = profile("user-1", "Pasted Trojan", "TROJAN")
        val pending = profile("user-2", "Freshly Pasted", "VLESS")

        compose.mainClock.autoAdvance = false
        compose.setContent {
            MohammadScreen(
                state = TunnelState.Disconnected,
                traffic = TrafficSnapshot.UNAVAILABLE,
                logs = emptyList(),
                profiles = listOf(operator, pasted, pending),
                selected = pasted,
                pingMs = 184,
                pingBusy = true,
                onToggle = {},
                onSelect = {},
                onPing = {},
                onPaste = {},
                onPingProfile = {},
                pingByProfile = mapOf(
                    operator.id to ProfilePing(ms = 61),
                    pasted.id to ProfilePing(ms = 184),
                    pending.id to ProfilePing(busy = true),
                ),
                importMessage = "imported 2, skipped 1 unrecognised",
                pingProgress = 2 to 3,
            )
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/reports/surfaces/profiles_paste.png")
        compose.onNodeWithText("TESTING 2/3…").assertExists()
    }
}
