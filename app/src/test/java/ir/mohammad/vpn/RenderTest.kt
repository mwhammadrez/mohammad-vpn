package ir.mohammad.vpn

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.MohammadScreen
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real Compose tree on the JVM (Robolectric + native graphics).
 * No device and no emulator: the screenshots are produced by the same code the
 * APK runs.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun profile() = Profile(
        "p1", "MrServerP3", "VLESS", "XRAY", "h.example", 2095,
        JSONObject("""{"protocol":"vless","settings":{"vnext":[]},"streamSettings":{"network":"ws"}}"""),
        null,
    )

    private fun render(state: TunnelState, name: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MohammadScreen(
                state = state,
                traffic = 12_345_678L to 98_765_432L,
                logs = emptyList(),
                profiles = listOf(profile()),
                selected = profile(),
                pingMs = 61,
                pingBusy = false,
                testRunning = false,
                testReport = null,
                onToggle = {},
                onSelect = {},
                onPing = {},
                onSelfTest = {},
            )
        }
        compose.mainClock.advanceTimeBy(64)
        compose.onRoot().captureRoboImage("build/reports/render-$name.png")
    }

    @Test
    fun rendersConnected() = render(TunnelState.Connected, "connected")

    @Test
    fun rendersDisconnected() = render(TunnelState.Disconnected, "disconnected")
}
