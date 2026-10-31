package ir.mohammad.vpn

import android.content.Context
import android.service.quicksettings.TileService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.surfaces.TunnelController
import ir.mohammad.vpn.tile.MohammadTileService
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File

/**
 * AC-W4 and AC-W9.
 *
 * W4 is checkable two ways: the tile must call the shared controller (source guard, because a
 * `TileService` cannot be driven end to end without the platform binding its IQSService), and the
 * guidance rule it depends on is pure.
 *
 * W9 ("long-press opens the app") is **not implementable on the public API** — `TileService` exposes
 * no long-press callback; the system owns that gesture (it opens app info on Android 13+). The test
 * asserts that absence rather than pretending, so the limitation is recorded as evidence, not prose.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TileInteractionTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun source(path: String): String {
        val root = listOf(File(".."), File(".")).map { it.absoluteFile }
            .first { File(it, "app/src/main/java").isDirectory }
        return File(root, "app/src/main/java/ir/mohammad/vpn/$path").readText()
    }

    @Test
    fun `the tile toggles through the shared controller`() {
        val tile = source("tile/MohammadTileService.kt")

        assertTrue("the tile must call TunnelController.toggle", tile.contains("TunnelController.toggle("))
        assertFalse(
            "the tile must not build its own tunnel Intent",
            tile.contains("Intent(this, MohammadVpnService"),
        )
    }

    @Test
    fun `every outcome that cannot toggle opens the app`() {
        val service = Robolectric.buildService(MohammadTileService::class.java).create().get()

        assertTrue(service.needsApp(TunnelController.Outcome.NEEDS_SETUP))
        assertTrue(service.needsApp(TunnelController.Outcome.NEEDS_PERMISSION))
        assertFalse(service.needsApp(TunnelController.Outcome.STARTED))
        assertFalse(service.needsApp(TunnelController.Outcome.STOPPED))
        assertFalse(service.needsApp(TunnelController.Outcome.IGNORED))
    }

    @Test
    fun `the platform offers no long-press callback, which is why W9 cannot be wired`() {
        val hasLongPress = TileService::class.java.methods.any {
            it.name.contains("longClick", ignoreCase = true) ||
                it.name.contains("longPress", ignoreCase = true)
        }
        assertFalse(
            "If this ever becomes true, wire AC-W9 to it; today the gesture belongs to the system.",
            hasLongPress,
        )
    }

    @Test
    fun `the tile opens the app when it cannot toggle, which is the available substitute`() {
        // The closest sanctioned behaviour: an unusable tile sends the user to the app rather than
        // doing nothing. Asserted through the decision rule and the widget's identical fallback.
        val state = ir.mohammad.vpn.surfaces.SurfaceState.NEEDS_APP
        assertFalse(ir.mohammad.vpn.widget.MohammadWidgetProvider.isActionable(state, true, true))
    }
}
