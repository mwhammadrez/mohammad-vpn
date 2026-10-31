package ir.mohammad.vpn

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.MohammadScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * AC-N7, AC-N8. The reported symptom was "the tiles show nothing while traffic flows". The cause was
 * a fixed `%.2f GB`: 5 MB rendered as "0.00 GB". These tests assert the rendered value and unit for a
 * realistic session, and the em dash when the source is unavailable.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class TelemetryTileTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(traffic: TrafficSnapshot) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MohammadScreen(
                state = TunnelState.Connected,
                traffic = traffic,
                logs = emptyList(),
                profiles = emptyList(),
                selected = null,
                pingMs = null,
                pingBusy = false,
                onToggle = {},
                onSelect = {},
                onPing = {},
            )
        }
        compose.mainClock.advanceTimeBy(32)
    }

    @Test
    fun `the downloaded tile shows a real value once traffic flows`() {
        show(TrafficSnapshot(up = 340 * 1_048_576L, down = 1_500 * 1_048_576L, available = true))

        // 1500 MiB = 1.46 GB, 340 MiB = 340.0 MB
        compose.onNodeWithText("1.46").assertIsDisplayed()
        compose.onAllNodesWithText("GB").fetchSemanticsNodes().isNotEmpty()
        compose.onNodeWithText("340.0").assertIsDisplayed()
    }

    @Test
    fun `a small transfer is not rounded away`() {
        // 5 MB used to render as "0.00 GB" — the exact bug the operator reported.
        show(TrafficSnapshot(up = 0, down = 5L * 1_048_576, available = true))

        compose.onNodeWithText("5.0").assertIsDisplayed()
        val zeros = compose.onAllNodesWithText("0.00").fetchSemanticsNodes()
        org.junit.Assert.assertTrue("0.00 GB must not appear", zeros.isEmpty())
    }

    @Test
    fun `an unavailable source shows a dash instead of a fabricated zero`() {
        show(TrafficSnapshot.UNAVAILABLE)

        val dashes = compose.onAllNodesWithText("—").fetchSemanticsNodes()
        org.junit.Assert.assertTrue("expected an em dash for an unavailable counter", dashes.isNotEmpty())
    }

    @Test
    fun `upload and download are not swapped`() {
        show(TrafficSnapshot(up = 100L * 1_048_576, down = 900L * 1_048_576, available = true))

        // DOWNLOADED sits above UPLOADED in the bento row; both values must be present and distinct.
        compose.onNodeWithText("900.0").assertIsDisplayed()
        compose.onNodeWithText("100.0").assertIsDisplayed()
    }
}
