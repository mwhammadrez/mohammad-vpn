package ir.mohammad.vpn

import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.Profile
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * SPEC TLS-6 / AC-11. M2 must not change a single byte of a `security:none` profile's config.
 *
 * The golden was captured from the M1 build (`scripts`-free capture, same JDK, same org.json) and is
 * stored verbatim at `app/src/test/resources/golden/security-none.json`.
 * Regenerating it is a deliberate act: change the profile or the renderer, capture, and justify the
 * diff in DECISIONS.md. It is not a snapshot to be updated to make a red build green.
 */
class GoldenConfigTest {

    private fun profile(): Profile {
        val xray = JSONObject(
            """{"protocol":"vless","settings":{"vnext":[{"address":"h.example","port":443,
                 "users":[{"id":"0722d93a-024a-489d-be2a-a2e83a1f39b7","encryption":"none"}]}]},
                 "streamSettings":{"network":"ws","security":"none",
                 "wsSettings":{"path":"/","headers":{"Host":"h.example"}}}}""",
        )
        return Profile("p1", "A", "VLESS", "XRAY", "h.example", 443, xray, null)
    }

    @Test
    fun `security none output is byte identical to the M1 build`() {
        val expected = javaClass.getResource("/golden/security-none.json")!!.readText()
        assertEquals(expected, XrayConfig.render(profile(), 1400, listOf("1.1.1.1")))
    }
}
