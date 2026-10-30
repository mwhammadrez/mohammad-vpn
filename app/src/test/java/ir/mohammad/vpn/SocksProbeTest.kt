package ir.mohammad.vpn

import ir.mohammad.vpn.vpn.SocksProbe
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

class SocksProbeTest {

    private fun fakeSocks(reply: ByteArray?, closeImmediately: Boolean = false, silent: Boolean = false): Int {
        val server = ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { client ->
                    if (closeImmediately) return@use
                    val greeting = ByteArray(3)
                    client.getInputStream().read(greeting)
                    if (silent) {
                        Thread.sleep(3_000)
                        return@use
                    }
                    client.getOutputStream().write(reply ?: byteArrayOf(0x05, 0x00))
                    client.getOutputStream().flush()
                    val head = ByteArray(4)
                    client.getInputStream().read(head)
                    client.getOutputStream().write(
                        byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0),
                    )
                    client.getOutputStream().flush()
                }
            }
            server.close()
        }
        return server.localPort
    }

    @Test
    fun `successful connect reports a live path`() {
        val port = fakeSocks(null)
        assertTrue(SocksProbe.probe(port, "example.com", 443, 1_000))
    }

    @Test
    fun `rejected method reports a dead path`() {
        val port = fakeSocks(byteArrayOf(0x05, 0xFF.toByte()))
        assertFalse(SocksProbe.probe(port, "example.com", 443, 1_000))
    }

    @Test
    fun `closed socket reports a dead path`() {
        val port = fakeSocks(null, closeImmediately = true)
        assertFalse(SocksProbe.probe(port, "example.com", 443, 1_000))
    }

    @Test
    fun `no listener reports a dead path`() {
        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()
        assertFalse(SocksProbe.probe(port, "example.com", 443, 500))
    }

    @Test
    fun `silent peer times out as a dead path`() {
        val port = fakeSocks(null, silent = true)
        assertFalse(SocksProbe.probe(port, "example.com", 443, 400))
    }
}
