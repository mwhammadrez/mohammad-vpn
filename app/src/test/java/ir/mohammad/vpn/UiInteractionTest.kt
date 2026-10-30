package ir.mohammad.vpn

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.MohammadScreen
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives the real screen: taps must reach the callbacks with the right values. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class UiInteractionTest {

    @get:Rule
    val compose = createComposeRule()

    private fun profile(id: String = "p1", name: String = "A") = Profile(
        id, name, "VLESS", "XRAY", "h.example", 2095,
        JSONObject("""{"protocol":"vless","settings":{"vnext":[]},"streamSettings":{"network":"ws"}}"""),
        null,
    )

    private fun setScreen(
        state: TunnelState = TunnelState.Disconnected,
        onToggle: () -> Unit = {},
        onSelect: (Profile) -> Unit = {},
        onFragment: (Boolean) -> Unit = {},
        onMux: (Boolean) -> Unit = {},
    ) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MohammadScreen(
                state = state,
                traffic = 0L to 0L,
                logs = emptyList(),
                profiles = listOf(profile(), profile("p2", "B")),
                selected = profile(),
                pingMs = null,
                pingBusy = false,
                testRunning = false,
                testReport = null,
                onToggle = onToggle,
                onSelect = onSelect,
                onPing = {},
                onSelfTest = {},
                onFragment = onFragment,
                onMux = onMux,
            )
        }
        compose.mainClock.advanceTimeBy(32)
    }

    @Test
    fun `dial reports Connect when idle`() {
        setScreen()
        compose.onNodeWithContentDescription("Connect").assertIsDisplayed()
    }

    @Test
    fun `dial reports Disconnect when connected`() {
        setScreen(state = TunnelState.Connected)
        compose.onNodeWithContentDescription("Disconnect").assertIsDisplayed()
    }

    @Test
    fun `tapping the dial fires the toggle`() {
        var taps = 0
        setScreen(onToggle = { taps++ })
        compose.onNodeWithContentDescription("Connect").performClick()
        compose.mainClock.advanceTimeBy(32)
        assertEquals(1, taps)
    }

    @Test
    fun `fragment toggle reports on`() {
        var value: Boolean? = null
        setScreen(onFragment = { value = it })
        compose.onNodeWithContentDescription("FRAGMENT off").performClick()
        compose.mainClock.advanceTimeBy(32)
        assertEquals(true, value)
    }

    @Test
    fun `mux toggle reports on`() {
        var value: Boolean? = null
        setScreen(onMux = { value = it })
        compose.onNodeWithContentDescription("MUX off").performClick()
        compose.mainClock.advanceTimeBy(32)
        assertEquals(true, value)
    }

    @Test
    fun `selecting a profile reports that profile`() {
        var picked: Profile? = null
        setScreen(onSelect = { picked = it })
        compose.onNodeWithText("B").performClick()
        compose.mainClock.advanceTimeBy(32)
        assertEquals("p2", picked?.id)
    }

    @Test
    fun `crash card is shown only when a report exists`() {
        setScreen()
        assertEquals(0, compose.onAllNodesWithText("LAST RUN CRASHED").fetchSemanticsNodes().size)
    }
}
