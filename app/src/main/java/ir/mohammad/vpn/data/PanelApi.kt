package ir.mohammad.vpn.data

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client for the panel worker itself (`/api/login`, `/api/users`, `/sub`).
 *
 * Creating the panel is a separate job — [PanelProvisioner] talks to the Cloudflare API directly, so
 * no builder service sits between this app and the account.
 *
 * Everything runs on HttpURLConnection on purpose: the app already ships `org.json` and has no HTTP
 * dependency, and the payloads are tiny.
 */
object PanelApi {

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 30_000

    data class Account(
        val panelUrl: String,
        val baseUrl: String,
        val nodeUrl: String?,
        val password: String,
        val worker: String,
    )

    data class User(
        val id: Int,
        val name: String,
        val uuid: String,
        val subToken: String,
        val usedBytes: Long,
        val limitBytes: Long,
        val expiresAt: Long,
        val enabled: Boolean,
    ) {
        val blocked: Boolean
            get() = !enabled || (expiresAt > 0 && expiresAt < System.currentTimeMillis()) ||
                (limitBytes > 0 && usedBytes >= limitBytes)
    }

    class PanelException(message: String, val code: Int = 0) : Exception(message)

    private fun open(
        method: String,
        url: String,
        body: JSONObject?,
        bearer: String?,
        readTimeoutMs: Int,
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            this.requestMethod = method
            this.connectTimeout = CONNECT_TIMEOUT_MS
            this.readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
        }

        try {
            body?.let { payload ->
                connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            val json = runCatching { JSONObject(text) }.getOrNull()
            if (code !in 200..299) {
                val message = json?.optString("error")?.takeIf { it.isNotBlank() }
                    ?: when (code) {
                        401 -> "Invalid password"
                        429 -> "Too many requests, try again later"
                        else -> "Server error ($code)"
                    }
                throw PanelException(message, code)
            }
            return json ?: throw PanelException("Malformed response from server")
        } finally {
            connection.disconnect()
        }
    }

    fun login(baseUrl: String, password: String): String {
        val json = open("POST", baseUrl.trimEnd('/') + "/api/login", JSONObject().put("password", password), null, READ_TIMEOUT_MS)
        return json.optString("token").takeIf { it.isNotBlank() }
            ?: throw PanelException("Login did not return a session")
    }

    /**
     * Signs in to a panel that was created moments ago.
     *
     * Cloudflare publishes a fresh workers.dev hostname — and a fresh custom-domain route — within a
     * few seconds, so the first request can still land on 404. Retrying here turns that window into a
     * short wait instead of a failure that leaves a working panel on Cloudflare that this phone never
     * saved; the next attempt then hit "worker already exists" with no way forward.
     *
     * Returns null when the panel is still not answering; callers keep the account and ask for the
     * password later rather than losing it.
     */
    fun loginWhenReady(baseUrl: String, password: String, attempts: Int = 6, pauseMs: Long = 2_500L): String? {
        for (attempt in 0 until attempts) {
            try {
                return login(baseUrl, password)
            } catch (e: Exception) {
                if (attempt == attempts - 1) return null
                try {
                    Thread.sleep(pauseMs)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
            }
        }
        return null
    }

    fun users(baseUrl: String, session: String): List<User> {
        val json = open("GET", baseUrl.trimEnd('/') + "/api/users", null, session, READ_TIMEOUT_MS)
        val array = json.optJSONArray("users") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    User(
                        id = item.optInt("id"),
                        name = item.optString("name"),
                        uuid = item.optString("uuid"),
                        subToken = item.optString("sub_token"),
                        usedBytes = item.optLong("used_bytes"),
                        limitBytes = item.optLong("limit_bytes"),
                        expiresAt = item.optLong("expires_at"),
                        enabled = item.optInt("enabled", 1) == 1,
                    ),
                )
            }
        }
    }

    fun addUser(baseUrl: String, session: String, name: String, limitGb: Double, days: Int) {
        val payload = JSONObject().apply {
            put("name", name)
            put("limit_gb", limitGb)
            put("days", days)
        }
        open("POST", baseUrl.trimEnd('/') + "/api/users", payload, session, READ_TIMEOUT_MS)
    }

    fun setEnabled(baseUrl: String, session: String, id: Int, enabled: Boolean) {
        val payload = JSONObject().put("enabled", enabled)
        open("PATCH", baseUrl.trimEnd('/') + "/api/users/$id", payload, session, READ_TIMEOUT_MS)
    }

    fun resetUsage(baseUrl: String, session: String, id: Int) {
        open("POST", baseUrl.trimEnd('/') + "/api/users/$id/reset", JSONObject(), session, READ_TIMEOUT_MS)
    }

    fun deleteUser(baseUrl: String, session: String, id: Int) {
        open("DELETE", baseUrl.trimEnd('/') + "/api/users/$id", null, session, READ_TIMEOUT_MS)
    }

    fun subLink(baseUrl: String, subToken: String): String =
        baseUrl.trimEnd('/') + "/sub/" + URLEncoder.encode(subToken, "UTF-8")

    /**
     * The vless:// link the app imports when the user taps CONNECT on a profile.
     * Mirrors what the panel puts inside its own subscription.
     */
    fun vlessLink(user: User, nodeHost: String): String {
        val query = listOf(
            "encryption=none",
            "security=tls",
            "sni=$nodeHost",
            "fp=chrome",
            "alpn=http%2F1.1",
            "type=ws",
            "host=$nodeHost",
            "path=%2Fws%3Fed%3D2048",
        ).joinToString("&")
        val label = URLEncoder.encode(user.name.ifBlank { "profile" }, "UTF-8")
        return "vless://${user.uuid}@$nodeHost:443?$query#$label"
    }
}
