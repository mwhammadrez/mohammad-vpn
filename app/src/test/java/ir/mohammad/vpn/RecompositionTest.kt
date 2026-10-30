package ir.mohammad.vpn

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
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

/**
 * Counts how many times the screen body composes. An idle screen must not
 * recompose at all — that is the measurable half of "no wasted frames".
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class RecompositionTest {

    @get:Rule
    val compose = createComposeRule()

    private val counter = IntArray(1)

    @Composable
    private fun Counted(state: TunnelState) {
        SideEffect { counter[0]++ }
        MohammadScreen(
            state = state,
            traffic = 0L to 0L,
            logs = emptyList(),
            profiles = listOf(
                Profile(
                    "p1", "A", "VLESS", "XRAY", "h.example", 2095,
                    JSONObject("""{"protocol":"vless","settings":{"vnext":[]}}"""),
                    null,
                ),
            ),
            selected = null,
            pingMs = null,
            pingBusy = false,
            onToggle = {},
            onSelect = {},
            onPing = {},
        )
    }

    @Test
    fun `idle screen does not recompose while frames advance`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Counted(TunnelState.Disconnected) }
        compose.mainClock.advanceTimeByFrame()
        val afterFirst = counter[0]

        // Ten seconds of frames with no state change.
        repeat(60) { compose.mainClock.advanceTimeByFrame() }
        compose.mainClock.advanceTimeBy(10_000)

        assertEquals(
            "an idle screen must not recompose: first=$afterFirst final=${counter[0]}",
            afterFirst,
            counter[0],
        )
    }

    @Test
    fun `connecting state animates without recomposing the body`() {
        compose.mainClock.autoAdvance = false
        compose.setContent { Counted(TunnelState.Connecting) }
        compose.mainClock.advanceTimeByFrame()
        val afterFirst = counter[0]
        repeat(30) { compose.mainClock.advanceTimeByFrame() }
        // The dial's transition runs in the draw phase, so the body stays put.
        assertEquals(afterFirst, counter[0])
    }
}
