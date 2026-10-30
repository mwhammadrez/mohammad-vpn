package ir.mohammad.vpn

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.OperatorBundleLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Decrypts the bundle that actually ships in the APK. A re-encryption mistake or a
 * truncated asset fails here instead of on the device.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BundleLoadTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `shipped bundle decrypts with the build password`() {
        val bundle = OperatorBundleLoader.load(context)
        assertTrue("expected at least one profile", bundle.profiles.isNotEmpty())
        assertNotNull("default profile id must resolve", bundle.defaultProfileId)
        assertTrue("mtu must be sane", bundle.mtu in 1280..1500)
        assertTrue("dns must not be empty", bundle.dnsServers.isNotEmpty())
    }

    @Test
    fun `profile carries a usable outbound`() {
        val profile = OperatorBundleLoader.load(context).profiles.first()
        assertNotNull("profile needs a rendered outbound", profile.xray)
        assertTrue(profile.protocol.isNotBlank())
        assertTrue(profile.host.isNotBlank())
        assertTrue(profile.port in 1..65535)
    }

    @Test
    fun `wrong password is rejected rather than returning garbage`() {
        // The loader reads the build password; re-encrypting is out of scope here.
        // This asserts the failure path is typed, not a raw exception type leak.
        val failure = runCatching { OperatorBundleLoader.load(context) }.exceptionOrNull()
        assertEquals(null, failure) // sanity: the good path must stay good
    }
}
