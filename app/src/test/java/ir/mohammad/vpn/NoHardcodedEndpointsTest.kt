package ir.mohammad.vpn

import ir.mohammad.vpn.core.TunPlan
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Operator constraint, 2026-10-31 (D-78): the app must serve *any* number of operator-supplied
 * servers, so no server-specific data may live in Kotlin source. Everything comes from the encrypted
 * bundle or the signed remote config.
 *
 * This scans string literals in `app/src/main/java` and fails on:
 *  - UUIDs (a profile id),
 *  - IPv4 addresses that are not local infrastructure or a route-table prefix,
 *  - hostnames that are not an allow-listed infrastructure host,
 *  - 43-character base64url strings (a REALITY public key shape),
 *  - 8..16 character hex strings (a REALITY shortId shape).
 *
 * Test sources are exempt: fixtures must contain *some* value, and D-78 restricts the rule to
 * production. The exemption is explicit here rather than implied by a path filter in a script.
 */
class NoHardcodedEndpointsTest {

    private val repoRoot: File = listOf(File(".."), File("."), File(System.getProperty("user.dir")).parentFile)
        .map { it.absoluteFile }
        .firstOrNull { File(it, "app/src/main/java").isDirectory }
        ?: error("repository root not found from ${File(".").absolutePath}")

    private val sourceRoot = File(repoRoot, "app/src/main/java")

    /** Local infrastructure, never a server: the TUN's own address, loopback, default resolvers. */
    private val allowedIps = setOf(
        "0.0.0.0",      // bind-any, and the AWG "no listen port" marker
        "10.10.10.1",   // TUN v4 address (TunPlan)
        "127.0.0.1",    // loopback SOCKS listener and probe target
        "1.1.1.1",      // default DNS
        "8.8.8.8",      // default DNS
        "198.18.0.0",   // fakedns pool base
    )

    /**
     * Infrastructure hosts, not operator servers: the rule-set download and the two public
     * no-content endpoints the delay probe measures through the tunnel. Neither is a server the app
     * connects to for carrying traffic, and neither is secret.
     */
    private val allowedHosts = setOf("github.com", "cp.cloudflare.com", "www.gstatic.com")

    private val uuid = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val ipv4 = Regex("""\b(?:\d{1,3}\.){3}\d{1,3}\b""")
    private val hostname = Regex("""^([a-z0-9]([a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,24}$""")
    private val base64Key = Regex("""^[A-Za-z0-9_-]{43}$""")
    private val hexShortId = Regex("""^[0-9a-fA-F]{8,16}$""")
    private val literal = Regex(""""(?:[^"\\]|\\.)*"""")

    /** `last-crash.txt` and `geosite.dat` are filenames, not hosts; a host TLD is never a file type. */
    private val fileExtensions = setOf(
        "txt", "dat", "json", "enc", "apk", "log", "db", "bin", "yml", "yaml", "xml",
        "kt", "java", "so", "aar", "jar", "md", "png", "jpg", "proto", "pb", "csv",
    )

    /** Every prefix that legitimately appears as a route or a reserved range. */
    private fun routePrefixes(): Set<String> {
        val fromPlan = TunPlan.SPLIT_ROUTES.map { it.first }.toSet()
        val privateRanges = setOf(
            "10.0.0.0", "172.16.0.0", "192.168.0.0", "127.0.0.0", "169.254.0.0",
            "224.0.0.0", "255.255.255.255",
        )
        return fromPlan + privateRanges
    }

    @Test
    fun `production sources carry no server data`() {
        val prefixes = routePrefixes()
        val violations = mutableListOf<String>()

        sourceRoot.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            file.readLines().forEachIndexed { index, line ->
                literal.findAll(line).forEach { match ->
                    val value = match.value.trim('"')
                    if (value.isEmpty() || value.length > 300) return@forEach

                    val complaint = when {
                        uuid.containsMatchIn(value) -> "UUID-shaped literal"
                        ipv4.containsMatchIn(value) && value.none { it.isLetter() } -> {
                            val found = ipv4.findAll(value).map { it.value }.toList()
                            val bad = found.filterNot { it in allowedIps || it in prefixes }
                            if (bad.isEmpty()) null else "IP literal ${bad.first()}"
                        }
                        base64Key.matches(value) -> "base64url key-shaped literal"
                        hexShortId.matches(value) -> "hex short-id-shaped literal"
                        hostname.matches(value) &&
                            value.substringAfterLast('.').lowercase() !in fileExtensions &&
                            value !in allowedHosts -> "hostname literal"
                        else -> null
                    }
                    if (complaint != null) {
                        violations += "${file.relativeTo(repoRoot)}:${index + 1} $complaint: $value"
                    }
                }
            }
        }

        assertTrue(
            "server-specific data must come from the bundle or remote config (D-78):\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `the scanner would catch a real leak`() {
        // Guards against a scanner that silently matches nothing: feed it the shapes it must reject
        // through the same predicates the scan uses.
        assertTrue(uuid.containsMatchIn("0722d93a-024a-489d-be2a-a2e83a1f39b7"))
        assertTrue(base64Key.matches("Q2H2h5jZW54Zg-MNKwBn8NOPEcZFMC2t2sAs_3vjKlg"))
        assertTrue(hexShortId.matches("ee3e25522b558543"))
        assertTrue(hostname.matches("vpn.example.net"))
        assertTrue(ipv4.containsMatchIn("203.0.113.9"))
    }
}
