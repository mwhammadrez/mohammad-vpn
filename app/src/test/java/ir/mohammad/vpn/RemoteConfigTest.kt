package ir.mohammad.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.RemoteConfig
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The remote config is the one path where a third party could put a server of their choosing into
 * the app, so the signature check is tested against a real Ed25519 key pair rather than a mock.
 * The fixture is signed exactly the way the panel signs (detached signature over the payload bytes).
 */
@RunWith(AndroidJUnit4::class)
class RemoteConfigTest {

    private val key = "pWAsg2x3Bs4fQzUC8YO01kvFfv0ir-48wcbLQRj4IFo"

    private val payload = """{"schemaVersion":1,"profiles":[{"name":"Frankfurt","uri":"vless://11111111-2222-4333-8444-555555555555@de1.example.net:443?encryption=none&security=tls&sni=de1.example.net&fp=chrome&type=ws&host=de1.example.net&path=%2Fws#Frankfurt"},{"name":"Amsterdam","uri":"vless://66666666-7777-4888-8999-aaaaaaaaaaaa@nl1.example.net:443?encryption=none&security=tls&sni=nl1.example.net&fp=chrome&type=ws&host=nl1.example.net&path=%2Fws#Amsterdam"}]}"""

    private val signature = "66g_g1Bk81T4xtaTbZMEDOCaB4RPlHTlVyskG-YqnTVP9izBNjxTsr4f4I9Lm6yUyOBklKIv7__CuiHBvz6_Ag"

    private fun envelope(
        configVersion: Long = 7,
        body: String = payload,
        sig: String = signature,
    ): String = JSONObject()
        .put("schemaVersion", 1)
        .put("configVersion", configVersion)
        .put("payload", body)
        .put("signature", sig)
        .toString()

    @Test
    fun `a signed envelope becomes profiles`() {
        val snapshot = RemoteConfig.accept(envelope(), minVersion = 0, publicKey = key)
        assertEquals(7L, snapshot.version)
        assertEquals(2, snapshot.profiles.size)
        assertEquals(listOf("Frankfurt", "Amsterdam"), snapshot.profiles.map { it.name })
        assertEquals("de1.example.net", snapshot.profiles.first().host)
        assertEquals(443, snapshot.profiles.first().port)
    }

    @Test
    fun `an edited payload fails the signature`() {
        val edited = payload.replace("Frankfurt", "Tehran")
        assertThrows(RemoteConfig.Invalid::class.java) {
            RemoteConfig.accept(envelope(body = edited), minVersion = 0, publicKey = key)
        }
    }

    @Test
    fun `an edited signature is refused`() {
        val bytes = signature.toCharArray()
        bytes[0] = if (bytes[0] == 'A') 'B' else 'A'
        assertThrows(RemoteConfig.Invalid::class.java) {
            RemoteConfig.accept(envelope(sig = String(bytes)), minVersion = 0, publicKey = key)
        }
    }

    @Test
    fun `a version that is not newer is refused`() {
        // minVersion is the lowest acceptable value, and the caller passes active + 1 — so "equal to
        // the active version" (7 here, with minVersion 8) must be refused, not re-applied.
        assertEquals(7L, RemoteConfig.accept(envelope(), minVersion = 7, publicKey = key).version)
        assertThrows(RemoteConfig.Invalid::class.java) {
            RemoteConfig.accept(envelope(), minVersion = 8, publicKey = key)
        }
    }

    @Test
    fun `another key does not verify`() {
        val other = RemoteConfig.accept(envelope(), minVersion = 0, publicKey = key)
        assertTrue(other.profiles.isNotEmpty())
        assertThrows(RemoteConfig.Invalid::class.java) {
            RemoteConfig.accept(
                envelope(),
                minVersion = 0,
                publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            )
        }
    }

    @Test
    fun `a wrong schema version is refused`() {
        val wrong = JSONObject(envelope()).put("schemaVersion", 2).toString()
        assertThrows(RemoteConfig.Invalid::class.java) {
            RemoteConfig.accept(wrong, minVersion = 0, publicKey = key)
        }
    }
}
