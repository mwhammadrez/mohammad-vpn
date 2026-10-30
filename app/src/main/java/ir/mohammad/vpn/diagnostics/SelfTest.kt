package ir.mohammad.vpn.diagnostics

import android.content.Context
import android.os.Build
import ir.mohammad.vpn.BuildConfig
import ir.mohammad.vpn.data.LogStore
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelState
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collects everything needed to judge a device session without adb.
 *
 * The report is intentionally self-verifying: it captures the carrier egress IP
 * before connecting, the egress IP while connected, DNS timings, a 1 MiB
 * throughput delta from the core counters, and a redacted log tail.
 */
object SelfTest {

    private const val HTTP_TIMEOUT_MS = 8_000
    private const val PING_TIMEOUT_MS = 5_000
    private const val DOWNLOAD_BYTES = 1_048_576

    data class DnsResult(val host: String, val ok: Boolean, val ms: Long, val count: Int, val detail: String)
    data class Egress(val ip: String?, val ms: Long, val error: String?)
    data class Traffic(val up: Long, val down: Long, val deltaDown: Long)

    suspend fun run(context: Context): String = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        LogStore.add("I", "TEST", "self-test started")

        val state = Tunnel.state.value
        val connected = state is TunnelState.Connected
        val profile = Tunnel.selected

        val dns = listOf("example.com", "cloudflare.com", "wikipedia.org").map { resolve(it) }

        val egress = fetchEgress("https://ifconfig.co/json", "ip")
        val ipv6 = if (hasLocalIpv6()) fetchEgress("https://api6.ipify.org?format=json", "ip", forceIpv6 = true) else Egress(null, 0, "no local ipv6")
        val verdict = EgressVerdict.of(
            baseline = Tunnel.baselineEgressIp,
            current = egress.ip,
            ipv6Egress = ipv6.ip,
            ipv4SameAsBaseline = Tunnel.baselineEgressIp != null && Tunnel.baselineEgressIp == egress.ip,
        )

        val trafficBefore = Tunnel.traffic.value
        val download = download1MiB()
        kotlinx.coroutines.delay(3_000)
        val trafficAfter = Tunnel.traffic.value
        val delta = Traffic(
            up = trafficAfter.first,
            down = trafficAfter.second,
            deltaDown = (trafficAfter.second - trafficBefore.second).coerceAtLeast(0),
        )

        val ping = profile?.let { ping(it.host, it.port) } ?: -1L

        val report = JSONObject()
            .put("schema", 1)
            .put("generatedAt", java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US).format(java.util.Date()))
            .put("durationMs", System.currentTimeMillis() - started)
            .put(
                "app",
                JSONObject()
                    .put("version", BuildConfig.VERSION_NAME)
                    .put("code", BuildConfig.VERSION_CODE),
            )
            .put(
                "device",
                JSONObject()
                    .put("model", "${Build.MANUFACTURER} ${Build.MODEL}")
                    .put("android", Build.VERSION.RELEASE)
                    .put("sdk", Build.VERSION.SDK_INT)
                    .put("abis", JSONArray(Build.SUPPORTED_ABIS.toList())),
            )
            .put(
                "tunnel",
                JSONObject()
                    .put("state", state.javaClass.simpleName)
                    .put("connected", connected)
                    .put("core", profile?.core ?: "none")
                    .put("protocol", profile?.protocol ?: "none")
                    .put("mtu", Tunnel.defaultMtu)
                    .put("ipv6Enabled", Tunnel.ipv6Enabled)
                    .put("dnsServers", JSONArray(Tunnel.defaultDns)),
            )
            .put(
                "dns",
                JSONArray(dns.map {
                    JSONObject()
                        .put("host", it.host)
                        .put("ok", it.ok)
                        .put("ms", it.ms)
                        .put("addresses", it.count)
                        .put("detail", it.detail)
                }),
            )
            .put(
                "egress",
                JSONObject()
                    .put("baseline", Tunnel.baselineEgressIp ?: JSONObject.NULL)
                    .put("current", egress.ip ?: JSONObject.NULL)
                    .put("ms", egress.ms)
                    .put("error", egress.error ?: JSONObject.NULL)
                    .put("changed", verdict.changed)
                    .put("conclusion", verdict.conclusion),
            )
            .put(
                "ipv6",
                JSONObject()
                    .put("localAddressPresent", hasLocalIpv6())
                    .put("egress", ipv6.ip ?: JSONObject.NULL)
                    .put("possibleLeak", verdict.possibleIpv6Leak),
            )
            .put(
                "traffic",
                JSONObject()
                    .put("up", delta.up)
                    .put("down", delta.down)
                    .put("deltaDownBytes", delta.deltaDown)
                    .put("downloadResult", download),
            )
            .put("pingMs", ping)
            .put("log", JSONArray(LogStore.dump().lines().takeLast(200)))

        LogStore.add("I", "TEST", "self-test finished in ${System.currentTimeMillis() - started} ms")
        report.toString(2)
    }

    private fun resolve(host: String): DnsResult {
        val started = System.nanoTime()
        return try {
            val addresses = InetAddress.getAllByName(host)
            DnsResult(host, true, (System.nanoTime() - started) / 1_000_000, addresses.size, "")
        } catch (e: Exception) {
            DnsResult(host, false, (System.nanoTime() - started) / 1_000_000, 0, e.javaClass.simpleName)
        }
    }

    private fun fetchEgress(url: String, field: String, forceIpv6: Boolean = false): Egress {
        val started = System.nanoTime()
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Mohammad-SelfTest")
                setRequestProperty("Accept", "application/json")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val ip = JSONObject(body).optString(field).takeIf { it.isNotBlank() }
            Egress(ip, (System.nanoTime() - started) / 1_000_000, if (ip == null) "empty field" else null)
        } catch (e: Exception) {
            Egress(null, (System.nanoTime() - started) / 1_000_000, e.javaClass.simpleName + ": " + e.message)
        } finally {
            connection?.disconnect()
        }
    }

    private fun download1MiB(): String {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://speed.cloudflare.com/__down?bytes=$DOWNLOAD_BYTES").openConnection() as HttpURLConnection).apply {
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                setRequestProperty("User-Agent", "Mohammad-SelfTest")
            }
            val read = connection.inputStream.use { stream ->
                val buffer = ByteArray(16_384)
                var total = 0
                while (true) {
                    val n = stream.read(buffer)
                    if (n <= 0) break
                    total += n
                    if (total >= DOWNLOAD_BYTES * 2) break
                }
                total
            }
            "ok, read=$read"
        } catch (e: Exception) {
            "failed: ${e.javaClass.simpleName}"
        } finally {
            connection?.disconnect()
        }
    }

    private fun ping(host: String, port: Int): Long {
        val started = System.nanoTime()
        return try {
            Socket().use { it.connect(InetSocketAddress(host, port), PING_TIMEOUT_MS) }
            (System.nanoTime() - started) / 1_000_000
        } catch (e: Exception) {
            -1L
        }
    }

    private fun hasLocalIpv6(): Boolean = try {
        java.net.NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }
            .any { it is java.net.Inet6Address && !it.isLoopbackAddress && !it.isLinkLocalAddress }
    } catch (e: Exception) {
        false
    }
}
