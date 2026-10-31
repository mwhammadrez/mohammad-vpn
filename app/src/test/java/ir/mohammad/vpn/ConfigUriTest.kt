package ir.mohammad.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.ConfigUri
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Base64

/**
 * Share-link import. Fixtures use example domains, which is allowed: D-78 restricts *production*
 * source, and a test needs some value to parse.
 */
@RunWith(AndroidJUnit4::class)
class ConfigUriTest {

    private fun b64(text: String): String = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun `vless plaintext over websocket`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@edge.example.com:8080" +
                "?type=ws&path=%2Fws&host=cdn.example.com&security=none#My%20Node",
        )
        val p = parsed.profile
        assertEquals("VLESS", p.protocol)
        assertEquals("XRAY", p.core)
        assertEquals("edge.example.com", p.host)
        assertEquals(8080, p.port)
        assertEquals("My Node", p.name)
        assertNull(p.fingerprint)

        val stream = p.xray!!.getJSONObject("streamSettings")
        assertEquals("ws", stream.getString("network"))
        assertEquals("none", stream.getString("security"))
        assertEquals("/ws", stream.getJSONObject("wsSettings").getString("path"))
        assertEquals(
            "cdn.example.com",
            stream.getJSONObject("wsSettings").getJSONObject("headers").getString("Host"),
        )
        val user = p.xray.getJSONObject("settings").getJSONArray("vnext")
            .getJSONObject(0).getJSONArray("users").getJSONObject(0)
        assertEquals("11111111-2222-3333-4444-555555555555", user.getString("id"))
        assertEquals("none", user.getString("encryption"))
    }

    @Test
    fun `vless over tls keeps sni and alpn and defaults the fingerprint`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@a.example.com:443" +
                "?security=tls&sni=www.example.com&alpn=h2,http%2F1.1&type=grpc&serviceName=svc",
        )
        val stream = parsed.profile.xray!!.getJSONObject("streamSettings")
        val tls = stream.getJSONObject("tlsSettings")
        assertEquals("www.example.com", tls.getString("serverName"))
        assertEquals("h2", tls.getJSONArray("alpn").getString(0))
        assertEquals("svc", stream.getJSONObject("grpcSettings").getString("serviceName"))
        assertEquals("chrome", parsed.profile.fingerprint)
        assertTrue(parsed.warnings.any { it.contains("fingerprint") })
    }

    @Test
    fun `vless reality carries key short id and flow`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@r.example.com:443" +
                "?security=reality&sni=decoy.example.com&pbk=${"A".repeat(43)}&sid=ab12&fp=firefox" +
                "&flow=xtls-rprx-vision&spx=%2F",
        )
        val p = parsed.profile
        assertEquals("firefox", p.fingerprint)
        assertEquals("xtls-rprx-vision", p.flow)
        val reality = p.xray!!.getJSONObject("streamSettings").getJSONObject("realitySettings")
        assertEquals("decoy.example.com", reality.getString("serverName"))
        assertEquals("A".repeat(43), reality.getString("publicKey"))
        assertEquals("ab12", reality.getString("shortId"))
        assertEquals("/", reality.getString("spiderX"))
    }

    @Test
    fun `reality without a public key is refused with a reason`() {
        val failure = runCatching {
            ConfigUri.parse(
                "vless://11111111-2222-3333-4444-555555555555@r.example.com:443?security=reality&sni=a.example.com",
            )
        }.exceptionOrNull()
        assertTrue(failure is ConfigUri.InvalidConfig)
        assertTrue(failure!!.message!!.contains("pbk"))
    }

    @Test
    fun `vmess base64 payload`() {
        val payload = JSONObject()
            .put("v", "2")
            .put("ps", "VM Node")
            .put("add", "v.example.com")
            .put("port", "443")
            .put("id", "11111111-2222-3333-4444-555555555555")
            .put("aid", "0")
            .put("scy", "auto")
            .put("net", "ws")
            .put("path", "/x")
            .put("host", "cdn.example.com")
            .put("tls", "tls")
            .put("sni", "s.example.com")
            .toString()
        val parsed = ConfigUri.parse("vmess://" + b64(payload))
        assertEquals("VMESS", parsed.profile.protocol)
        assertEquals("VM Node", parsed.profile.name)
        assertEquals("v.example.com", parsed.profile.host)
        assertEquals("chrome", parsed.profile.fingerprint)
        val stream = parsed.profile.xray!!.getJSONObject("streamSettings")
        assertEquals("tls", stream.getString("security"))
        assertEquals("s.example.com", stream.getJSONObject("tlsSettings").getString("serverName"))
    }

    @Test
    fun `trojan defaults to tls`() {
        val parsed = ConfigUri.parse("trojan://secret@t.example.com:443?sni=t.example.com#Trojan")
        assertEquals("TROJAN", parsed.profile.protocol)
        val stream = parsed.profile.xray!!.getJSONObject("streamSettings")
        assertEquals("tls", stream.getString("security"))
        val server = parsed.profile.xray.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("secret", server.getString("password"))
    }

    @Test
    fun `shadowsocks in both common shapes`() {
        val a = ConfigUri.parse("ss://${b64("aes-128-gcm:pw")}@s.example.com:8388#SS%20A")
        assertEquals("SHADOWSOCKS", a.profile.protocol)
        assertEquals("SS A", a.profile.name)
        val server = a.profile.xray!!.getJSONObject("settings").getJSONArray("servers").getJSONObject(0)
        assertEquals("aes-128-gcm", server.getString("method"))
        assertEquals("pw", server.getString("password"))

        val b = ConfigUri.parse("ss://${b64("aes-128-gcm:pw@s.example.com:8388")}#SS B")
        assertEquals("s.example.com", b.profile.host)
        assertEquals(8388, b.profile.port)
        assertEquals("SS B", b.profile.name)
    }

    @Test
    fun `vless post-quantum encryption is carried, not replaced with none`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@pq.example.com:443" +
                "?encryption=mlkem768x25519plus.native.0rtt.${"P".repeat(43)}&security=tls&sni=pq.example.com",
        )
        val user = parsed.profile.xray!!.getJSONObject("settings").getJSONArray("vnext")
            .getJSONObject(0).getJSONArray("users").getJSONObject(0)
        assertEquals("mlkem768x25519plus.native.0rtt.${"P".repeat(43)}", user.getString("encryption"))
    }

    @Test
    fun `httpupgrade gets its own settings block, not websocket`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@up.example.com:443" +
                "?type=httpupgrade&host=edge.example.com&path=%2Fmirror&security=tls",
        )
        val stream = parsed.profile.xray!!.getJSONObject("streamSettings")
        assertEquals("httpupgrade", stream.getString("network"))
        assertFalse(stream.has("wsSettings"))
        val up = stream.getJSONObject("httpupgradeSettings")
        assertEquals("/mirror", up.getString("path"))
        assertEquals("edge.example.com", up.getString("host"))
    }

    @Test
    fun `fm becomes the finalMask`() {
        val mask = """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello","length":"104-1"}}]}"""
        val encoded = java.net.URLEncoder.encode(mask, "UTF-8")
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@f.example.com:443?security=tls&fm=$encoded",
        )
        val tcp = parsed.profile.finalMask!!.getJSONArray("tcp").getJSONObject(0)
        assertEquals("fragment", tcp.getString("type"))
        assertEquals("tlshello", tcp.getJSONObject("settings").getString("packets"))
    }

    @Test
    fun `an unusable fm is reported and dropped, the link still imports`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@f.example.com:443?security=tls&fm=not-json",
        )
        assertNull(parsed.profile.finalMask)
        assertTrue(parsed.warnings.any { it.contains("fm") })
    }

    @Test
    fun `xhttp gets its own block`() {
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@x.example.com:443" +
                "?type=xhttp&path=%2Fapi&host=edge.example.com&security=tls",
        )
        val stream = parsed.profile.xray!!.getJSONObject("streamSettings")
        assertEquals("xhttp", stream.getString("network"))
        assertEquals("/api", stream.getJSONObject("xhttpSettings").getString("path"))
        assertEquals("edge.example.com", stream.getJSONObject("xhttpSettings").getString("host"))
    }

    @Test
    fun `a fragment mask is dropped for reality instead of breaking the handshake`() {
        val mask = """{"tcp":[{"type":"fragment","settings":{"packets":"tlshello"}}]}"""
        val encoded = java.net.URLEncoder.encode(mask, "UTF-8")
        val parsed = ConfigUri.parse(
            "vless://11111111-2222-3333-4444-555555555555@r.example.com:443" +
                "?security=reality&sni=a.example.com&pbk=${"A".repeat(43)}&sid=ab12&fm=$encoded",
        )
        assertNull(parsed.profile.finalMask)
        assertTrue(parsed.warnings.any { it.contains("reality") })
    }

    @Test
    fun `trojan links carry fm too`() {
        val mask = """{"tcp":[{"type":"merge","settings":{"times":1}}]}"""
        val encoded = java.net.URLEncoder.encode(mask, "UTF-8")
        val parsed = ConfigUri.parse("trojan://pw@t.example.com:443?security=tls&fm=$encoded")
        assertEquals("merge", parsed.profile.finalMask!!.getJSONArray("tcp").getJSONObject(0).getString("type"))
    }

    @Test
    fun `a dns tunnel link becomes a DNS profile`() {
        val parsed = ConfigUri.parse(
            "dns://v.example.com?key=0011223344556677&method=3&resolvers=1.1.1.1,8.8.8.8:53#Tunnel",
        )
        val p = parsed.profile
        assertEquals("DNS", p.core)
        assertEquals("DNS", p.protocol)
        assertEquals("v.example.com", p.host)
        assertEquals("Tunnel", p.name)
        assertEquals(2, p.dns!!.getJSONArray("resolvers").length())
        assertEquals(3, p.dns!!.getInt("method"))
    }

    @Test
    fun `a dns link with a bad key is refused`() {
        val failure = runCatching {
            ConfigUri.parse("dns://v.example.com?key=short&method=3&resolvers=1.1.1.1")
        }.exceptionOrNull()
        assertTrue(failure is ConfigUri.InvalidConfig)
    }

    @Test
    fun `the same link always yields the same id`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@x.example.com:443?security=none"
        assertEquals(ConfigUri.parse(link).profile.id, ConfigUri.parse(link).profile.id)
        assertTrue(ConfigUri.parse(link).profile.id.startsWith("user-"))
    }

    @Test
    fun `bad input is refused by name`() {
        assertTrue(runCatching { ConfigUri.parse("http://example.com") }.exceptionOrNull()
            .let { it is ConfigUri.InvalidConfig && it.message!!.contains("unsupported") })
        assertTrue(runCatching { ConfigUri.parse("hello world") }.exceptionOrNull() is ConfigUri.InvalidConfig)
        assertTrue(runCatching { ConfigUri.parse("vless://x.example.com:443") }.exceptionOrNull() is ConfigUri.InvalidConfig)
        assertTrue(runCatching { ConfigUri.parse("vmess://notbase64!!!") }.exceptionOrNull() is ConfigUri.InvalidConfig)
        assertNotNull(runCatching { ConfigUri.parse("") }.exceptionOrNull())
    }

    @Test
    fun `looksLikeConfig pre-filters the clipboard`() {
        assertTrue(ConfigUri.looksLikeConfig("vless://a@b:1"))
        assertTrue(ConfigUri.looksLikeConfig("  ss://x"))
        assertTrue(!ConfigUri.looksLikeConfig("hello"))
        assertTrue(!ConfigUri.looksLikeConfig("https://example.com"))
    }
}
