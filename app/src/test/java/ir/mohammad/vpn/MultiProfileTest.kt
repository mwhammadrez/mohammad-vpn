package ir.mohammad.vpn

import ir.mohammad.vpn.data.OperatorBundleLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SRC-1 / SRC-5 (D-78): any number of operator profiles, no single-server assumption.
 *
 * Parsing is the layer that decides whether N profiles even exist for the rest of the app, so it is
 * tested explicitly for 1, 2 and 5 profiles, mixed cores, and the reserved-id cases.
 */
class MultiProfileTest {

    private fun profile(id: String, protocol: String = "VLESS", core: String = "XRAY"): String =
        if (core == "XRAY") {
            """
            {"id":"$id","name":"$id","protocol":"$protocol","core":"XRAY",
             "serverHost":"$id.invalid","serverPort":443,
             "xray":{"protocol":"vless","settings":{}}}
            """.trimIndent()
        } else {
            """
            {"id":"$id","name":"$id","protocol":"AMNEZIAWG","core":"AMNEZIAWG",
             "serverHost":"$id.invalid","serverPort":51820,
             "awg":{"privateKey":"fixture","peers":[]}}
            """.trimIndent()
        }

    private fun bundle(vararg entries: String, default: String? = null): String {
        val id = default?.let { ""","defaultProfileId":"$it"""" } ?: ""
        return """{"schemaVersion":1$id,"profiles":[${entries.joinToString(",")}]}"""
    }

    @Test
    fun `a single profile bundle still works`() {
        val parsed = OperatorBundleLoader.parse(bundle(profile("only")))
        assertEquals(1, parsed.profiles.size)
        assertEquals("only", parsed.profiles.single().id)
    }

    @Test
    fun `two profiles keep their order and identity`() {
        val parsed = OperatorBundleLoader.parse(bundle(profile("a"), profile("b"), default = "b"))
        assertEquals(listOf("a", "b"), parsed.profiles.map { it.id })
        assertEquals("b", parsed.defaultProfileId)
    }

    @Test
    fun `five mixed profiles parse without losing or reordering any`() {
        val parsed = OperatorBundleLoader.parse(
            bundle(
                profile("s1"),
                profile("s2", protocol = "VMESS"),
                profile("s3", protocol = "TROJAN"),
                profile("w1", core = "AMNEZIAWG"),
                profile("s4", protocol = "SHADOWSOCKS"),
                default = "s3",
            ),
        )
        assertEquals(5, parsed.profiles.size)
        assertEquals(listOf("s1", "s2", "s3", "w1", "s4"), parsed.profiles.map { it.id })
        assertEquals("s3", parsed.defaultProfileId)
        assertEquals(1, parsed.profiles.count { it.core == "AMNEZIAWG" })
    }

    @Test
    fun `every profile keeps its own host so paths cannot collide`() {
        val parsed = OperatorBundleLoader.parse(bundle(profile("s1"), profile("s2"), profile("s3")))
        assertEquals(
            listOf("s1.invalid", "s2.invalid", "s3.invalid"),
            parsed.profiles.map { it.host },
        )
    }

    @Test
    fun `a default that names a missing profile is rejected`() {
        val failure = runCatching {
            OperatorBundleLoader.parse(bundle(profile("s1"), profile("s2"), default = "nope"))
        }.exceptionOrNull()
        assertTrue("expected a BundleException, got $failure", failure != null)
    }

    @Test
    fun `duplicates are rejected at any position, not only at index zero`() {
        val failure = runCatching {
            OperatorBundleLoader.parse(bundle(profile("s1"), profile("s2"), profile("s1")))
        }.exceptionOrNull()
        assertTrue("expected a BundleException, got $failure", failure != null)
    }

    @Test
    fun `an empty profile list is rejected`() {
        val failure = runCatching { OperatorBundleLoader.parse("""{"schemaVersion":1,"profiles":[]}""") }
            .exceptionOrNull()
        assertTrue("expected a BundleException, got $failure", failure != null)
    }
}
