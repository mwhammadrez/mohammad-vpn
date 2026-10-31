package ir.mohammad.vpn

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * AC-N13. The research pass is a deliverable, not a preamble: if the document is missing or has been
 * hollowed out, the milestone is not done.
 */
class ResearchDocTest {

    private val doc: File = listOf(File(".."), File("."), File(System.getProperty("user.dir")).parentFile)
        .map { it.absoluteFile }
        .map { File(it, "docs/RESEARCH_TILE_NOTIFICATION_TELEMETRY.md") }
        .first { it.isFile }

    @Test
    fun `the research document exists with at least fifteen findings`() {
        val text = doc.readText()
        val entries = Regex("""^### """, RegexOption.MULTILINE).findAll(text).count()

        assertTrue("expected at least 15 findings, found $entries", entries >= 15)
    }

    @Test
    fun `every finding records a source and a verdict`() {
        val text = doc.readText()
        val blocks = text.split(Regex("""^### """, RegexOption.MULTILINE)).drop(1)

        blocks.forEach { block ->
            val heading = block.lineSequence().first()
            assertTrue("$heading has no link", block.contains("**Link**") || block.contains("http"))
            assertTrue("$heading has no fit verdict", block.contains("**Fit**"))
        }
    }

    @Test
    fun `the document names the decision it drives`() {
        val text = doc.readText()
        assertTrue(text.contains("What this research changes in the plan"))
    }
}
