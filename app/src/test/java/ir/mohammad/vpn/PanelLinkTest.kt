package ir.mohammad.vpn

import ir.mohammad.vpn.data.ConfigUri
import ir.mohammad.vpn.data.ImportBatch
import ir.mohammad.vpn.data.PanelApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The panel's CONNECT button must not have its own dial path: it imports a share link and the app
 * connects that profile exactly as if the user had pasted it. These tests pin the link the panel
 * builds to the parser the app ships, so a change on either side fails here instead of on a phone.
 */
@RunWith(AndroidJUnit4::class)
class PanelLinkTest {

    private val user = PanelApi.User(
        id = 1,
        name = "ali",
        uuid = "0722d93a-024a-489d-be2a-a2e83a1f39b7",
        subToken = "sub-token",
        usedBytes = 0,
        limitBytes = 0,
        expiresAt = 0,
        enabled = true,
    )

    @Test
    fun `panel connect link parses as ws over tls`() {
        val parsed = ConfigUri.parse(PanelApi.vlessLink(user, "node.example.com"))
        val profile = parsed.profile

        assertEquals("VLESS", profile.protocol)
        assertEquals("XRAY", profile.core)
        assertEquals("node.example.com", profile.host)
        assertEquals(443, profile.port)
        assertEquals("chrome", profile.fingerprint)

        val stream = profile.xray!!.getJSONObject("streamSettings")
        assertEquals("ws", stream.getString("network"))
        assertEquals("tls", stream.getString("security"))

        val ws = stream.getJSONObject("wsSettings")
        assertEquals("/ws?ed=2048", ws.getString("path"))
        assertEquals("node.example.com", ws.getJSONObject("headers").getString("Host"))
        assertEquals("node.example.com", stream.getJSONObject("tlsSettings").getString("serverName"))
    }

    @Test
    fun `panel link carries the uuid and the user name`() {
        val parsed = ConfigUri.parse(PanelApi.vlessLink(user, "node.example.com"))
        val vnext = parsed.profile.xray!!
            .getJSONObject("settings")
            .getJSONArray("vnext")
            .getJSONObject(0)

        assertEquals("node.example.com", vnext.getString("address"))
        assertEquals(user.uuid, vnext.getJSONArray("users").getJSONObject(0).getString("id"))
        assertEquals("ali", parsed.profile.name)
    }

    @Test
    fun `panel link imports in one batch without failures`() {
        val outcome = ImportBatch.parse(PanelApi.vlessLink(user, "node.example.com"))
        assertEquals(1, outcome.profiles.size)
        assertTrue(outcome.failures.isEmpty())
        assertEquals(user.uuid, outcome.profiles.single().xray!!
            .getJSONObject("settings")
            .getJSONArray("vnext").getJSONObject(0)
            .getJSONArray("users").getJSONObject(0).getString("id"))
    }
}
