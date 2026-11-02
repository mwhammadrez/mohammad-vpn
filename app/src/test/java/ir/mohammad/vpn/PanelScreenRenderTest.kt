package ir.mohammad.vpn

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import ir.mohammad.vpn.ui.AppMode
import ir.mohammad.vpn.ui.PanelScreen
import ir.mohammad.vpn.ui.TopBar
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mode 3 as it renders with the mode sheet open. The header carries the only way back to Mode 1/2,
 * so it has to sit below the status bar — that was the bug this screenshot exists to catch.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class PanelScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun modeThreeShowsTheHeaderAndTheSheet() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize()) {
                PanelScreen(
                    onOpenUrl = {},
                    onConnect = { true },
                    topBar = {
                        TopBar(
                            mode = AppMode.PANEL,
                            version = "v0.15.4",
                            menuOpen = true,
                            onToggleMenu = {},
                            onPickMode = {},
                        )
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(500)
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/panel-mode3.png")
    }
}
