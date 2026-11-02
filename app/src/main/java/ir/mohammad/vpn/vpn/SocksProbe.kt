package ir.mohammad.vpn.vpn

import java.net.InetSocketAddress
import java.net.Socket

/**
 * Verifies that the local SOCKS inbound can still reach the remote through the core.
 * A live core process is not proof of a live data path.
 */
object SocksProbe {

    /**
     * Performs a SOCKS5 greeting plus a CONNECT to [targetHost]:[targetPort] through
     * 127.0.0.1:[port]. Returns true only when the core accepts the request, which
     * means the outbound chain is alive end-to-end enough to answer.
     *
     * The target never leaves the process: it is not logged and not reported.
     */
    /**
     * Measures a *real* round trip through the proxy: SOCKS5 CONNECT to [host]:[port], then an HTTP
     * request, stopping the clock at the first byte of the response.
     *
     * This is deliberately not a TCP handshake to the server: a handshake proves the port is open,
     * not that the account, the transport and the route work. Returns milliseconds, or null when the
     * request fails, times out, or the proxy answers with a non-success SOCKS reply.
     */
    fun httpTimeToFirstByte(
        socksPort: Int,
        host: String,
        port: Int,
        path: String = "/generate_204",
        hostHeader: String = host,
        timeoutMs: Int = 8_000,
    ): Long? = runCatching {
        val started = System.nanoTime()
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress("127.0.0.1", socksPort), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = socket.getOutputStream()
            val input = socket.getInputStream()

            out.write(byteArrayOf(0x05, 0x01, 0x00))
            out.flush()
            val greeting = ByteArray(2)
            if (!readFully(input, greeting) || greeting[0] != 0x05.toByte() || greeting[1] != 0x00.toByte()) {
                return null
            }

            val request = java.io.ByteArrayOutputStream()
            request.write(byteArrayOf(0x05, 0x01, 0x00))
            val literal = parseIpv4(host)
            if (literal != null) {
                request.write(byteArrayOf(0x01))
                request.write(literal)
            } else {
                val name = host.toByteArray(Charsets.US_ASCII)
                if (name.size > 255) return null
                request.write(byteArrayOf(0x03, name.size.toByte()))
                request.write(name)
            }
            request.write((port ushr 8) and 0xFF)
            request.write(port and 0xFF)
            out.write(request.toByteArray())
            out.flush()

            val head = ByteArray(4)
            if (!readFully(input, head) || head[0] != 0x05.toByte() || head[1] != 0x00.toByte()) {
                return null
            }
            when (head[3].toInt() and 0xFF) {
                0x01 -> if (!readFully(input, ByteArray(4 + 2))) return null
                0x04 -> if (!readFully(input, ByteArray(16 + 2))) return null
                0x03 -> {
                    val len = ByteArray(1)
                    if (!readFully(input, len)) return null
                    if (!readFully(input, ByteArray((len[0].toInt() and 0xFF) + 2))) return null
                }
                else -> return null
            }

            val line = "GET $path HTTP/1.1\r\nHost: $hostHeader\r\nConnection: close\r\n" +
                "User-Agent: Mohammad-DelayProbe\r\nAccept: */*\r\n\r\n"
            out.write(line.toByteArray(Charsets.US_ASCII))
            out.flush()

            val first = input.read()
            if (first < 0) return null
            (System.nanoTime() - started) / 1_000_000
        }
    }.getOrNull()

    fun probe(port: Int, targetHost: String, targetPort: Int, timeoutMs: Int = 1_500): Boolean =
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress("127.0.0.1", port), timeoutMs)
                socket.soTimeout = timeoutMs
                val out = socket.getOutputStream()
                val input = socket.getInputStream()

                out.write(byteArrayOf(0x05, 0x01, 0x00)) // version 5, one method: no auth
                out.flush()
                val greeting = ByteArray(2)
                if (!readFully(input, greeting) || greeting[0] != 0x05.toByte() || greeting[1] != 0x00.toByte()) {
                    return false
                }

                val request = java.io.ByteArrayOutputStream()
                val literal = parseIpv4(targetHost)
                if (literal != null) {
                    // Address type 1: a literal address needs no resolution inside the core.
                    request.write(byteArrayOf(0x05, 0x01, 0x00, 0x01))
                    request.write(literal)
                } else {
                    val host = targetHost.toByteArray(Charsets.US_ASCII)
                    if (host.size > 255) return false
                    request.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()))
                    request.write(host)
                }
                request.write((targetPort ushr 8) and 0xFF)
                request.write(targetPort and 0xFF)
                out.write(request.toByteArray())
                out.flush()

                val head = ByteArray(4)
                if (!readFully(input, head) || head[0] != 0x05.toByte() || head[1] != 0x00.toByte()) {
                    return false
                }
                when (head[3].toInt() and 0xFF) {
                    0x01 -> if (!readFully(input, ByteArray(4 + 2))) return false
                    0x04 -> if (!readFully(input, ByteArray(16 + 2))) return false
                    0x03 -> {
                        val len = ByteArray(1)
                        if (!readFully(input, len)) return false
                        if (!readFully(input, ByteArray((len[0].toInt() and 0xFF) + 2))) return false
                    }
                    else -> return false
                }
                true
            }
        }.getOrDefault(false)

    /** Dotted-quad to four bytes, or null when the text is not an IPv4 literal. */
    internal fun parseIpv4(text: String): ByteArray? {
        val parts = text.split('.')
        if (parts.size != 4) return null
        val bytes = ByteArray(4)
        parts.forEachIndexed { i, part ->
            val value = part.toIntOrNull() ?: return null
            if (value !in 0..255) return null
            bytes[i] = value.toByte()
        }
        return bytes
    }

    private fun readFully(input: java.io.InputStream, buffer: ByteArray): Boolean {
        var read = 0
        while (read < buffer.size) {
            val n = input.read(buffer, read, buffer.size - read)
            if (n <= 0) return false
            read += n
        }
        return true
    }
}
