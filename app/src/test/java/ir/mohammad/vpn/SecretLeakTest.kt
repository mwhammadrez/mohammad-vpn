package ir.mohammad.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.ByteFormat
import ir.mohammad.vpn.data.TrafficSnapshot
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.notifications.VpnNotifier
import ir.mohammad.vpn.surfaces.SurfaceState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * AC-N11. `NoHardcodedEndpointsTest` covers Kotlin literals and `SurfaceResourceScanTest` covers the
 * widget's resources; this one covers what the user actually reads: the notification (title, body,
 * action labels) and every surface string.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SecretLeakTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val forbidden = listOf(
        "IPv4" to Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
        "UUID" to Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""),
        "base64 key" to Regex("""\b[A-Za-z0-9_-]{43}\b"""),
        "hostname" to Regex("""\b[a-z0-9-]+\.(com|net|org|ir|io|xyz|top|site|online)\b"""),
    )

    private fun assertClean(where: String, text: String) {
        forbidden.forEach { (label, pattern) ->
            assertTrue("$where contains a $label: '$text'", !pattern.containsMatchIn(text))
        }
    }

    @Test
    fun `the notification carries no endpoint in any state`() {
        listOf(
            TunnelState.Disconnected,
            TunnelState.Connecting,
            TunnelState.Connected,
            TunnelState.Disconnecting,
            TunnelState.Failed(ir.mohammad.vpn.data.TunnelError.CoreStartFailed),
        ).forEach { state ->
            val notification = VpnNotifier.build(
                context,
                state,
                TrafficSnapshot(up = 1_048_576, down = 2_097_152, available = true),
                paused = false,
            )
            val shadow = shadowOf(notification)
            assertClean("$state title", shadow.contentTitle.toString())
            assertClean("$state text", shadow.contentText.toString())
            notification.actions?.forEach { assertClean("$state action", it.title.toString()) }
        }
    }

    @Test
    fun `every surface string is clean`() {
        SurfaceState.entries.forEach { state ->
            assertClean("${state.name} title", state.title(context))
            assertClean("${state.name} subtitle", state.subtitle(context))
            assertClean("${state.name} description", state.description(context))
            assertClean("${state.name} action", state.action(context))
        }
    }

    @Test
    fun `telemetry values are numbers and units only`() {
        listOf(0L, 512L, 1024L, 1_048_576L, 5_368_709_120L).forEach { bytes ->
            val text = ByteFormat.format(bytes)
            assertClean("formatted $bytes", text)
            assertTrue("must be short: '$text'", text.length <= 12)
        }
    }

    @Test
    fun `the strings file has no endpoint either`() {
        val root = listOf(File(".."), File(".")).map { it.absoluteFile }
            .first { File(it, "app/src/main").isDirectory }
        val strings = File(root, "app/src/main/res/values/strings.xml").readLines()

        strings.forEachIndexed { index, line ->
            if (line.contains("xmlns")) return@forEachIndexed
            assertClean("strings.xml:${index + 1}", line)
        }
    }
}
