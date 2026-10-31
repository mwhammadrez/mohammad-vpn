package ir.mohammad.vpn

import ir.mohammad.vpn.core.TlsFingerprint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SPEC TLS-2 / catalog 65, 87.
 *
 * The allow-list is not copied from documentation: it is the set of names the pinned Xray-core
 * actually resolves in `transport/internet/tls/tls.go` (PresetFingerprints + ModernFingerprints +
 * OtherFingerprints), minus `unsafe`, whose value stays nil and is therefore rejected at runtime.
 * Pinned source: xray-core v26.10.29, commit 0afec66085a85191e9dd2a4a6646c7c9a94688d1.
 */
class TlsFingerprintTest {

    @Test
    fun `the allow list matches the pinned core`() {
        // 61 = 11 preset (12 minus unsafe) + 11 modern + 40 other, minus hellogolang
        // (accepted by the map, rejected by the config layer).
        assertTrue("expected 61 names, got ${TlsFingerprint.VALID.size}", TlsFingerprint.VALID.size == 61)
    }

    @Test
    fun `browser presets are accepted`() {
        listOf("chrome", "firefox", "safari", "ios", "android", "edge", "random", "randomized")
            .forEach { assertTrue("$it must be accepted", TlsFingerprint.isValid(it)) }
    }

    @Test
    fun `unsafe is refused because the core never assigns it a value`() {
        assertFalse(TlsFingerprint.isValid("unsafe"))
    }

    @Test
    fun `an unknown name is refused`() {
        assertFalse(TlsFingerprint.isValid("chrome_999"))
        assertFalse(TlsFingerprint.isValid(""))
        assertFalse(TlsFingerprint.isValid("CHROME"))
    }

    @Test
    fun `names the config layer rejects are refused`() {
        // hellogolang resolves in the map but `infra/conf/transport_security.go` rejects it outright.
        assertFalse(TlsFingerprint.isValid("hellogolang"))
        assertTrue("old versions are still valid", TlsFingerprint.isValid("hellofirefox_55"))
        assertFalse("not a browser", TlsFingerprint.isRecommended("hellofirefox_55"))
    }

    @Test
    fun `recommended set is a subset of the valid set`() {
        assertTrue(TlsFingerprint.VALID.containsAll(TlsFingerprint.RECOMMENDED))
        assertTrue(TlsFingerprint.RECOMMENDED.contains("chrome"))
    }
}
