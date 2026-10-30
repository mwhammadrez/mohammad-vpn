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

                val host = targetHost.toByteArray(Charsets.US_ASCII)
                if (host.size > 255) return false
                val request = java.io.ByteArrayOutputStream()
                request.write(byteArrayOf(0x05, 0x01, 0x00, 0x03, host.size.toByte()))
                request.write(host)
                request.write((targetPort ushr 8) and 0xFF)
                request.write(targetPort and 0xFF)
                out.write(request.toByteArray())
                out.flush()

                val head = ByteArray(4)
                if (!readFully(input, head) || head[0] != 0x05.toByte() || head[1] != 0x00.toByte()) {
                    return false
                }
                when (head[3].toInt() and 0xFF) {
                    0x01 -> readFully(input, ByteArray(4 + 2))
                    0x04 -> readFully(input, ByteArray(16 + 2))
                    0x03 -> {
                        val len = ByteArray(1)
                        if (!readFully(input, len)) return false
                        readFully(input, ByteArray((len[0].toInt() and 0xFF) + 2))
                    }
                    else -> return false
                }
                true
            }
        }.getOrDefault(false)

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
