package ir.mohammad.vpn.net

import android.content.Context
import ir.mohammad.vpn.core.GeoAssets
import ir.mohammad.vpn.core.MtuPolicy
import ir.mohammad.vpn.core.XrayConfig
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.vpn.SocksProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import libmohammad.XrayController
import java.net.InetSocketAddress
import java.net.Socket

/**
 * "Real delay": the time a request needs to come back *through the proxy*, not the time a TCP
 * handshake to the server needs.
 *
 * A handshake to `host:port` succeeds for a blocked, expired or misconfigured account, and it
 * succeeds against a CDN edge that will never reach the origin. That is why the old number was
 * misleading: a node could show 40 ms and carry no traffic.
 *
 * Two paths:
 *  - the tunnel is up and the tested profile is the one carrying it: measure through the live core;
 *  - the tunnel is down: start a temporary core on [PROBE_PORT], measure, stop it.
 *
 * A second core beside a live one is refused on purpose (D-105): two instances share process-wide
 * state, and a phone is not the place to find out what breaks.
 */
object RealDelay {

    /** Separate from [XrayConfig.SOCKS_PORT] so a probe never binds over the tunnel's inbound. */
    const val PROBE_PORT = 11808

    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val CORE_START_TIMEOUT_MS = 4_000

    sealed interface Plan {
        data object ThroughLiveTunnel : Plan
        data object TemporaryCore : Plan
        data class Refused(val reason: String) : Plan
    }

    data class Result(val ms: Long?, val reason: String? = null) {
        val ok: Boolean get() = ms != null
    }

    /** Pure decision step, so the rules are testable without a device. */
    fun plan(connected: Boolean, selectedId: String?, profileId: String): Plan = when {
        !connected -> Plan.TemporaryCore
        selectedId == profileId -> Plan.ThroughLiveTunnel
        else -> Plan.Refused("disconnect first: another profile is carrying the tunnel")
    }

    suspend fun measure(context: Context, profile: Profile, timeoutMs: Int = CONNECT_TIMEOUT_MS): Result =
        withContext(Dispatchers.IO) {
            val connected = Tunnel.state.value is TunnelState.Connected
            when (val p = plan(connected, Tunnel.selected?.id, profile.id)) {
                is Plan.Refused -> Result(null, p.reason)
                Plan.ThroughLiveTunnel -> probe(XrayConfig.SOCKS_PORT, timeoutMs)
                Plan.TemporaryCore -> withTemporaryCore(context, profile) { port ->
                    probe(port, timeoutMs)
                }
            }
        }

    private fun probe(port: Int, timeoutMs: Int): Result {
        val started = System.nanoTime()
        var lastReason = "no answer"
        for (target in TARGETS) {
            val ms = SocksProbe.httpTimeToFirstByte(
                socksPort = port,
                host = target.first,
                port = target.second,
                timeoutMs = timeoutMs,
            )
            if (ms != null) return Result(ms)
            lastReason = "no answer from ${target.first}"
        }
        return Result(null, "$lastReason after ${(System.nanoTime() - started) / 1_000_000} ms")
    }

    /**
     * Targets are public no-content endpoints, not operator servers: they measure reachability from
     * the server's side of the world, which is where the user's traffic actually goes.
     */
    private val TARGETS = listOf(
        "cp.cloudflare.com" to 80,
        "www.gstatic.com" to 80,
    )

    private fun withTemporaryCore(
        context: Context,
        profile: Profile,
        block: (Int) -> Result,
    ): Result {
        if (profile.core != "XRAY") {
            return Result(null, "delay test is implemented for Xray profiles only")
        }
        val mtu = profile.mtu ?: Tunnel.defaultMtu.takeIf { it > 0 } ?: MtuPolicy.DEFAULT_MTU
        val config = runCatching {
            XrayConfig.render(
                profile = profile,
                mtu = mtu,
                dnsServers = Tunnel.defaultDns,
                logLevel = "error",
                geo = false,
                fakeDns = false,
                socksPort = PROBE_PORT,
            )
        }.getOrElse { return Result(null, "config invalid: ${it.message}") }

        val core = XrayController { level, message ->
            if (level >= 2) LogStore.add("W", "PROBE", message.trim())
        }
        GeoAssets.path(context)?.let { runCatching { core.setAssetDir(it) } }
        runCatching { core.setLogLevel(2L) }

        return try {
            core.start(config, 0L)
            if (!awaitPort(PROBE_PORT, CORE_START_TIMEOUT_MS)) {
                Result(null, "core did not open the probe port")
            } else {
                block(PROBE_PORT)
            }
        } catch (t: Throwable) {
            Result(null, "core refused the config: ${t.message ?: t.javaClass.simpleName}")
        } finally {
            runCatching { core.stop() }
        }
    }

    private fun awaitPort(port: Int, timeoutMs: Int): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 200) }
            }.isSuccess
            if (ok) return true
            Thread.sleep(40)
        }
        return false
    }
}
