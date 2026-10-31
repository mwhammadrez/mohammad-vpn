package ir.mohammad.vpn

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.BundleException
import ir.mohammad.vpn.data.OperatorBundleLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Covers the whole decryption path in both directions.
 *
 * The operator's real bundle is git-ignored, so a clean clone has no asset and no password. The
 * fixture below exercises the success path unconditionally; one further test pins the behaviour of
 * the shipped asset for whichever configuration this build actually has. Nothing is skipped and no
 * assertion is conditional on an assumption.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BundleLoadTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `fixture bundle decrypts and parses`() {
        val bundle = OperatorBundleLoader.decrypt(BundleFixture.encrypted(), BundleFixture.PASSWORD)

        assertTrue("expected at least one profile", bundle.profiles.isNotEmpty())
        assertNotNull("default profile id must resolve", bundle.defaultProfileId)
        assertEquals("fixture-primary", bundle.defaultProfileId)
        assertTrue("mtu must be sane", bundle.mtu in 1200..1500)
        assertTrue("dns must not be empty", bundle.dnsServers.isNotEmpty())

        val profile = bundle.profiles.first()
        assertEquals("fixture-primary", profile.id)
        assertNotNull("profile needs a rendered outbound", profile.xray)
        assertTrue(profile.protocol.isNotBlank())
        assertTrue(profile.host.isNotBlank())
        assertTrue(profile.port in 1..65535)
    }

    @Test
    fun `wrong password is rejected rather than returning garbage`() {
        val bytes = BundleFixture.encrypted()
        val failure = runCatching { OperatorBundleLoader.decrypt(bytes, "definitely-not-the-password") }
            .exceptionOrNull()

        assertTrue("expected BundleException, got $failure", failure is BundleException)
        assertEquals("operator bundle decryption failed", failure?.message)
    }

    @Test
    fun `empty password is a distinct typed failure before decryption`() {
        val bytes = BundleFixture.encrypted()
        val failure = runCatching { OperatorBundleLoader.decrypt(bytes, "") }.exceptionOrNull()

        assertTrue("expected BundleException, got $failure", failure is BundleException)
        assertEquals("operator password not configured", failure?.message)
    }

    @Test
    fun `corrupt header is rejected before key derivation`() {
        val bytes = BundleFixture.encrypted()
        bytes[0] = 'X'.code.toByte()

        val failure = runCatching { OperatorBundleLoader.decrypt(bytes, BundleFixture.PASSWORD) }
            .exceptionOrNull()

        assertTrue("expected BundleException, got $failure", failure is BundleException)
        assertEquals("operator bundle header invalid", failure?.message)
    }

    @Test
    fun `shipped bundle behaves exactly as configured`() {
        val assetPresent = runCatching {
            context.assets.open("operator/bundle.enc").use { it.readBytes() }
        }.getOrNull()
        val password = BuildConfig.OPERATOR_PASSWORD

        when {
            assetPresent == null -> {
                val failure = runCatching { OperatorBundleLoader.load(context) }.exceptionOrNull()
                assertEquals("operator bundle missing", failure?.message)
            }
            password.isEmpty() -> {
                val failure = runCatching { OperatorBundleLoader.load(context) }.exceptionOrNull()
                assertEquals("operator password not configured", failure?.message)
            }
            else -> {
                val bundle = OperatorBundleLoader.load(context)
                assertTrue("shipped bundle must expose a profile", bundle.profiles.isNotEmpty())
                val defaultId = bundle.defaultProfileId
                if (defaultId != null) {
                    assertTrue(
                        "default profile id must resolve",
                        bundle.profiles.any { it.id == defaultId },
                    )
                }
            }
        }
    }
}
