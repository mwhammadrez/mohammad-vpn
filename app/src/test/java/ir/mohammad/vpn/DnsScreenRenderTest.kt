package ir.mohammad.vpn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import ir.mohammad.vpn.data.LogEntry
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.ProxyState
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.ui.AppMode
import ir.mohammad.vpn.ui.DnsMode
import ir.mohammad.vpn.ui.DnsScreen
import ir.mohammad.vpn.ui.TopBar
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Mode 2 as it actually renders: mode switch, orb, proxy address, profile row, log tail. There is no
 * device here, so a screenshot is the only way to see that nothing overflows or hides behind the orb.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class DnsScreenRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun dnsProfile(name: String = "DNS Tunnel") = Profile(
        id = "user-dns",
        name = name,
        protocol = "DNS",
        core = "DNS",
        host = "v.example.com",
        port = 53,
        xray = null,
        awg = null,
        dns = JSONObject()
            .put("domain", "v.example.com")
            .put("key", "0011223344556677")
            .put("method", 3)
            .put("resolvers", JSONArray().put("1.1.1.1").put("8.8.8.8")),
    )

    private fun render(mode: DnsMode, proxy: ProxyState.Proxy, state: TunnelState, name: String) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            Box(Modifier.fillMaxSize().background(ir.mohammad.vpn.ui.theme.Palette.BgDeep)) {
                DnsScreen(
                    dnsProfiles = listOf(dnsProfile()),
                    selected = dnsProfile(),
                    dnsMode = mode,
                    tunnelState = state,
                    proxy = proxy,
                    logs = listOf(
                        LogEntry("00:00:01", "I", "DNS", "session initialized"),
                        LogEntry("00:00:02", "D", "DNS", "MTU upload 111 download 1500"),
                        LogEntry("00:00:03", "I", "DNS-PROXY", "proxy ready on 127.0.0.1:10809"),
                    ),
                    onPickDnsMode = {},
                    onSelectProfile = {},
                    onToggle = {},
                    onCopyAddress = {},
                    topBar = {
                        TopBar(
                            mode = AppMode.DNS,
                            version = "v0.15.0",
                            menuOpen = false,
                            onToggleMenu = {},
                            onPickMode = {},
                        )
                    },
                )
            }
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onRoot().captureRoboImage("build/reports/surfaces/$name.png")
    }

    @Test
    fun proxyRunning() = render(
        DnsMode.PROXY,
        ProxyState.Proxy(running = true, port = 10809, profileName = "DNS Tunnel"),
        TunnelState.Disconnected,
        "dns_proxy",
    )

    @Test
    fun fullTunnelDisconnected() = render(
        DnsMode.VPN,
        ProxyState.Proxy(),
        TunnelState.Disconnected,
        "dns_vpn",
    )
}
