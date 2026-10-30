package ir.mohammad.vpn.core

import android.content.Context
import ir.mohammad.vpn.data.LogStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * geoip.dat / geosite.dat provider.
 *
 * The pair is ~28 MB, too large to ship inside the APK, so it is downloaded once
 * into internal storage and reused. When the assets are absent the renderer
 * falls back to literal CIDR/suffix rules, so the app still connects.
 */
object GeoAssets {

    private const val MIN_VALID_BYTES = 1_000_000L
    private const val TIMEOUT_MS = 30_000
    private const val TAG = "GEO"

    fun dir(context: Context): File = File(context.filesDir, "xray").apply { mkdirs() }

    fun isReady(context: Context): Boolean =
        File(dir(context), "geosite.dat").length() > MIN_VALID_BYTES &&
            File(dir(context), "geoip.dat").length() > MIN_VALID_BYTES

    fun path(context: Context): String? = if (isReady(context)) dir(context).absolutePath else null

    /** Downloads only what is missing. Returns true when the pair is usable. */
    suspend fun ensure(context: Context, geositeUrl: String, geoipUrl: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = dir(context)
            val wanted = listOf("geosite.dat" to geositeUrl, "geoip.dat" to geoipUrl)
            for ((name, url) in wanted) {
                val file = File(target, name)
                if (file.length() > MIN_VALID_BYTES) continue
                if (url.isBlank()) {
                    LogStore.add("W", TAG, "$name missing and no URL configured")
                    return@withContext false
                }
                LogStore.add("I", TAG, "downloading $name")
                val ok = download(url, file)
                if (!ok) {
                    LogStore.add("E", TAG, "$name download failed; using literal rules")
                    return@withContext false
                }
                LogStore.add("I", TAG, "$name ready (${file.length() / 1024} KB)")
            }
            isReady(context)
        }

    private fun download(url: String, target: File): Boolean {
        val temp = File(target.parentFile, target.name + ".part")
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mohammad")
            }
            if (connection.responseCode != 200) return false
            temp.outputStream().use { out -> connection.inputStream.use { it.copyTo(out) } }
            if (temp.length() < MIN_VALID_BYTES) {
                temp.delete()
                return false
            }
            if (target.exists()) target.delete()
            temp.renameTo(target)
        } catch (e: Exception) {
            LogStore.add("W", TAG, "download error: ${e.javaClass.simpleName}: ${e.message}")
            temp.delete()
            false
        } finally {
            connection?.disconnect()
        }
    }
}
