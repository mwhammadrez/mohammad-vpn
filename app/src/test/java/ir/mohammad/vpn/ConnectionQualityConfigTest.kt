package ir.mohammad.vpn

import android.net.VpnService
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.vpn.MohammadVpnService
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ConnectionQualityConfigTest {

    private fun profile(host: String = "h.example") = Profile(
        "p1", "A", "VLESS", "XRAY", host, 443,
        JSONObject("""{"protocol":"vless","settings":{"vnext":[]},"streamSettings":{"network":"ws"}}"""),
        null,
    )

    private fun outbound(host: String = "h.example", hosts: Map<String, String> = emptyMap()) =
        JSONObject(XrayConfig.render(profile(host), 1500, listOf("8.8.8.8"), serverHosts = hosts))
            .getJSONArray("outbounds").getJSONObject(0)

    @Test
    fun `outbound carries keepalive tuned for cgnat`() {
        val sockopt = outbound().getJSONObject("streamSettings").getJSONObject("sockopt")
        assertEquals(25, sockopt.getInt("tcpKeepAliveIdle"))
        assertEquals(15, sockopt.getInt("tcpKeepAliveInterval"))
    }

    @Test
    fun `dns hosts entry is emitted for a resolved server domain`() {
        val json = JSONObject(
            XrayConfig.render(
                profile("node.example"),
                1500,
                listOf("8.8.8.8"),
                serverHosts = mapOf("node.example" to "203.0.113.7"),
            ),
        )
        val hosts = json.getJSONObject("dns").getJSONObject("hosts")
        assertEquals("203.0.113.7", hosts.getString("node.example"))
    }

    @Test
    fun `dns hosts is absent when nothing was resolved`() {
        val json = JSONObject(XrayConfig.render(profile("1.2.3.4"), 1500, listOf("8.8.8.8")))
        assertFalse(json.getJSONObject("dns").has("hosts"))
    }

    @Test
    fun `setMetered reaches the builder`() {
        val service = Robolectric.buildService(MohammadVpnService::class.java).create().get()
        val builder = service.newBuilder()
        service.applyMetering(builder, true)

        // Android 14 keeps the flag on the internal VpnConfig, not on the builder.
        val configField = VpnService.Builder::class.java.getDeclaredField("mConfig")
            .apply { isAccessible = true }
        val config = configField.get(builder)!!
        val metered = config.javaClass.getField("isMetered").getBoolean(config)
        assertTrue("setMetered(true) must reach the config", metered)
    }
}
