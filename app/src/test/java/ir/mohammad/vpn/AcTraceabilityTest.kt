package ir.mohammad.vpn

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * D-58: an acceptance criterion must name a test that exists, or one that a named milestone commits
 * to creating. This test makes that a build failure instead of a promise.
 *
 * It reads the documents, not the code: SPEC.md declares the matrix, PLAN.md declares the pending
 * classes. A class named in SPEC.md but present neither in sources nor in PLAN.md is an invented
 * criterion, which is exactly the defect the v1.0 matrix shipped with (37 of 40 classes missing).
 */
class AcTraceabilityTest {

    private val root: File = listOf(File(".."), File("."), File(System.getProperty("user.dir")).parentFile)
        .map { it.absoluteFile }
        .firstOrNull { File(it, "SPEC.md").isFile }
        ?: error("SPEC.md not found relative to ${File(".").absolutePath}")

    private val spec: String = File(root, "SPEC.md").readText()
    private val plan: String = File(root, "PLAN.md").readText()

    private fun testSourceClassNames(): Set<String> {
        val candidates = listOf(File(root, "app/src/test/java"), File(root, "src/test/java"))
        val dir = candidates.firstOrNull { it.isDirectory } ?: return emptySet()
        return dir.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .map { it.nameWithoutExtension }
            .toSet()
    }

    @Test
    fun everyAcceptanceCriterionHasACommandAndANumberedRow() {
        val rows = Regex("""^\|\s*(AC-(?:[WN])?\d+)\s*\|""", RegexOption.MULTILINE)
            .findAll(spec).map { it.groupValues[1] }.toList()

        assertTrue("SPEC.md must declare at least 30 acceptance criteria", rows.size >= 30)
        assertTrue("acceptance criteria must be uniquely numbered", rows.size == rows.toSet().size)

        val withCommand = rows.count { id ->
            val line = spec.lines().first { it.startsWith("| $id ") }
            line.contains("--tests") || line.contains("gradlew") || line.contains("grep ") ||
                line.contains("scripts/")
        }
        assertTrue(
            "every AC must name a runnable command; ${rows.size - withCommand} do not",
            withCommand == rows.size,
        )
    }

    @Test
    fun everyReferencedTestClassExistsOrIsPlanned() {
        val referenced = Regex("""--tests\s+"\*(\w+)"""")
            .findAll(spec)
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("SPEC.md must reference test classes", referenced.isNotEmpty())

        val existing = testSourceClassNames()
        assertTrue("no test sources found", existing.isNotEmpty())

        val missing = referenced.filterNot { it in existing || plan.contains(it) }
        assertTrue(
            "AC references classes that exist neither in sources nor in PLAN.md: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun everyAcceptanceCriterionNamesAnOwningMilestone() {
        val milestoneColumn = Regex("""^\|\s*AC-(?:[WN])?\d+\s*\|.*\|\s*([A-Za-z0-9-]+)\s*\|$""", RegexOption.MULTILINE)
            .findAll(spec)
            .map { it.groupValues[1] }
            .toList()

        assertTrue("AC rows must end with a milestone column", milestoneColumn.isNotEmpty())
        val known = milestoneColumn.all {
            it == "every" || Regex("""^M-?[A-Za-z0-9-]+$""").matches(it)
        }
        assertTrue("unknown milestone token in the AC matrix: $milestoneColumn", known)
    }
}
