package ir.mohammad.vpn

import ir.mohammad.vpn.net.RealDelay
import ir.mohammad.vpn.vpn.SocksProbe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.DataInputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * The transport half of the delay measurement: SOCKS5 greeting, CONNECT, then an HTTP request, with
 * the clock stopped at the first response byte. A fake proxy here proves the bytes are what a real
 * core expects; it cannot prove a real server answers, which needs a device.
 */
class RealDelayTest {

    private class FakeSocks(
        private val replyStatus: Byte = 0x00,
        private val respondToHttp: Boolean = true,
    ) : AutoCloseable {
        val server = ServerSocket(0)
        val port: Int get() = server.localPort
        @Volatile var requestLine: String? = null
        @Volatile var hostHeader: String? = null
        private val worker = thread(isDaemon = true) {
            runCatching {
                server.accept().use { client -> serve(client) }
            }
        }

        private fun serve(client: Socket) {
            val input = DataInputStream(client.getInputStream())
            val output = client.getOutputStream()

            val greeting = ByteArray(3)
            input.readFully(greeting)
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            val head = ByteArray(4)
            input.readFully(head)
            when (head[3].toInt() and 0xFF) {
                0x01 -> input.readFully(ByteArray(4 + 2))
                0x03 -> {
                    val len = input.read()
                    input.readFully(ByteArray(len + 2))
                }
                0x04 -> input.readFully(ByteArray(16 + 2))
            }
            output.write(byteArrayOf(0x05, replyStatus, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            if (replyStatus.toInt() != 0 || !respondToHttp) {
                client.close()
                return
            }

            val reader = input.bufferedReader(Charsets.US_ASCII)
            requestLine = reader.readLine()
            hostHeader = reader.readLine()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isEmpty()) break
            }
            output.write("HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".toByteArray())
            output.flush()
        }

        override fun close() {
            runCatching { server.close() }
            runCatching { worker.join(500) }
        }
    }

    @Test
    fun `measures a request that comes back through the proxy`() {
        FakeSocks().use { proxy ->
            val ms = SocksProbe.httpTimeToFirstByte(proxy.port, "probe.example.com", 80, timeoutMs = 3_000)
            assertNotNull("expected a measurement", ms)
            assertTrue("expected >= 0, got $ms", ms!! >= 0)
            assertEquals("GET /generate_204 HTTP/1.1", proxy.requestLine)
            assertEquals("Host: probe.example.com", proxy.hostHeader)
        }
    }

    @Test
    fun `a refused CONNECT is a failure, not a number`() {
        FakeSocks(replyStatus = 0x05).use { proxy ->
            assertNull(SocksProbe.httpTimeToFirstByte(proxy.port, "probe.example.com", 80, timeoutMs = 3_000))
        }
    }

    @Test
    fun `no response at all is a failure`() {
        FakeSocks(respondToHttp = false).use { proxy ->
            assertNull(SocksProbe.httpTimeToFirstByte(proxy.port, "probe.example.com", 80, timeoutMs = 1_500))
        }
    }

    @Test
    fun `nothing listening is a failure`() {
        assertNull(SocksProbe.httpTimeToFirstByte(9, "probe.example.com", 80, timeoutMs = 500))
    }

    @Test
    fun `a second profile is measured directly, never with a second core`() {
        assertEquals(RealDelay.Plan.TemporaryCore, RealDelay.plan(false, "a", "b"))
        assertEquals(
            RealDelay.Plan.ThroughLive(10808),
            RealDelay.plan(true, "a", "a"),
        )
        // While another profile carries the tunnel, this one is measured by a plain handshake bound
        // to the underlying network: still no second core (D-105), but a real number.
        assertEquals(RealDelay.Plan.Direct, RealDelay.plan(true, "a", "b"))
    }

    @Test
    fun `a running DNS proxy is measured through its own port`() {
        assertEquals(
            RealDelay.Plan.ThroughLive(10809),
            RealDelay.plan(connected = false, selectedId = null, profileId = "d", proxyRunning = true, proxyProfileId = "d"),
        )
        // A different profile cannot borrow the live proxy; it gets its own direct measurement.
        assertEquals(
            RealDelay.Plan.Direct,
            RealDelay.plan(false, null, "other", proxyRunning = true, proxyProfileId = "d"),
        )
    }
}
