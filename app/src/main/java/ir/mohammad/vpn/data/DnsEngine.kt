package ir.mohammad.vpn.data

import android.content.Context
import ir.mohammad.vpn.core.DnsConfig
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File

/**
 * Runs the DNS-tunnel engine as a child process.
 *
 * It is a Go binary shipped as `libmdvpn_client.so` in `jniLibs`; `useLegacyPackaging = true` makes
 * Android extract it to `nativeLibraryDir`, which is the only place a process may be executed from.
 * No JNI and no gomobile: a process plus a config file, which is also how the reference app does it.
 *
 * Everything it writes lives in `cacheDir/dns-<port>/` so two instances (VPN + delay probe) can
 * coexist without touching each other's files.
 */
object DnsEngine {

    class Handle(
        val process: Process,
        val directory: File,
        val port: Int,
        private val drain: Thread,
    ) {
        fun stop() {
            runCatching { process.destroy() }
            runCatching { if (!process.waitFor(1, java.util.concurrent.TimeUnit.SECONDS)) process.destroyForcibly() }
            runCatching { drain.join(500) }
            directory.deleteRecursively()
        }
    }

    fun binary(context: Context): File = File(context.applicationInfo.nativeLibraryDir, DnsConfig.BINARY_NAME)

    fun start(
        context: Context,
        settings: JSONObject,
        port: Int,
        onLine: (String) -> Unit = {},
    ): Handle {
        val binary = binary(context)
        if (!binary.isFile) {
            throw IllegalStateException("DNS engine not packaged (${binary.name} missing)")
        }
        DnsConfig.validate(settings)?.let { throw IllegalArgumentException(it) }

        val dir = File(context.cacheDir, "dns-$port").apply {
            deleteRecursively()
            mkdirs()
        }
        val config = File(dir, "config.toml")
        val resolvers = File(dir, "resolvers.txt")
        config.writeText(DnsConfig.render(settings, port))
        resolvers.writeText(DnsConfig.renderResolvers(settings))

        val process = ProcessBuilder(
            binary.absolutePath,
            "-config", config.absolutePath,
            "-resolvers", resolvers.absolutePath,
        )
            .directory(dir)
            .redirectErrorStream(true)
            .start()

        val drain = Thread({
            runCatching {
                BufferedReader(process.inputStream.reader()).useLines { lines ->
                    lines.forEach { line -> onLine(line.take(400)) }
                }
            }
        }, "dns-engine-$port").apply {
            isDaemon = true
            start()
        }
        return Handle(process, dir, port, drain)
    }

    /** Waits until the engine's local SOCKS5 listener accepts a connection. */
    fun awaitPort(port: Int, timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            val ok = runCatching {
                java.net.Socket().use { it.connect(java.net.InetSocketAddress("127.0.0.1", port), 150) }
            }.isSuccess
            if (ok) return true
            Thread.sleep(40)
        }
        return false
    }
}
