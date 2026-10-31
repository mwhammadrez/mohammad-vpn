package ir.mohammad.vpn

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-W7: no endpoint or secret in any widget/tile resource.
 *
 * `NoHardcodedEndpointsTest` covers Kotlin source. This one covers the places a launcher actually
 * reads: the widget layout, the widget metadata and the strings file. A hostname pasted into
 * `strings.xml` would never be caught by the Kotlin scan.
 */
class SurfaceResourceScanTest {

    private val root: File = listOf(File(".."), File("."), File(System.getProperty("user.dir")).parentFile)
        .map { it.absoluteFile }
        .firstOrNull { File(it, "app/src/main").isDirectory }
        ?: error("repository root not found")

    private val surfaces = listOf(
        "res/layout/widget_mohammad.xml",
        "res/xml/widget_mohammad_info.xml",
        "res/values/strings.xml",
    )

    private val forbidden = listOf(
        "IPv4" to Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b"""),
        "UUID" to Regex("""[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"""),
        "base64 key" to Regex("""\b[A-Za-z0-9_-]{43}\b"""),
        "hex shortId" to Regex("""\b[0-9a-fA-F]{8,16}\b"""),
        "hostname" to Regex("""\b[a-z0-9-]+\.(com|net|org|ir|io|xyz|top|site|online|dev|me|co)\b"""),
    )

    @Test
    fun `surface resources carry no endpoint-shaped value`() {
        val violations = mutableListOf<String>()

        surfaces.forEach { relative ->
            val file = File(root, "app/src/main/$relative")
            assertTrue("missing surface resource: $relative", file.isFile)

            file.readLines().forEachIndexed { index, line ->
                // Namespace declarations contain a schema URL; they are markup, not data.
                if (line.contains("xmlns:") || line.contains("schemas.android.com")) return@forEachIndexed
                forbidden.forEach { (label, pattern) ->
                    if (pattern.containsMatchIn(line)) {
                        violations += "$relative:${index + 1} $label -> ${line.trim()}"
                    }
                }
            }
        }

        assertTrue(
            "widget/tile resources must not contain server data (D-78):\n" + violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `the widget declares a layout and the provider points at it`() {
        val layout = File(root, "app/src/main/res/layout/widget_mohammad.xml").readText()
        assertTrue(layout.contains("@+id/widget_title"))
        assertTrue(layout.contains("@+id/widget_icon"))
        assertTrue(layout.contains("@+id/widget_action"))

        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.appwidget.provider"))
        assertTrue(manifest.contains("android.appwidget.action.APPWIDGET_UPDATE"))
    }
}
