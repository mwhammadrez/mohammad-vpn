package ir.mohammad.vpn.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** A failure reported by Cloudflare itself, or a transport failure talking to it. */
class CfException(message: String, val status: Int = 0) : Exception(message)

/**
 * Direct client for `api.cloudflare.com/client/v4`.
 *
 * The app provisions the panel itself instead of asking a middle service to do it: the token goes
 * from the phone to Cloudflare and nowhere else, and there is no builder server to deploy or trust.
 * Every endpoint used here is a plain HTTPS call, so `HttpURLConnection` is enough — the app already
 * depends on `org.json` and needs no HTTP library for this.
 */
class CloudflareApi(private val token: String) {

    data class Account(val id: String, val name: String)

    private val base = "https://api.cloudflare.com/client/v4"

    /* ─────────────────────────── accounts ─────────────────────────── */

    fun accounts(): List<Account> {
        val array = call("GET", "/accounts").optJSONArray("result") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(Account(id = id, name = item.optString("name")))
            }
        }
    }

    /** The `workers.dev` subdomain, or null when the account never registered one. */
    fun workersSubdomain(accountId: String): String? =
        call("GET", "/accounts/$accountId/workers/subdomain")
            .optJSONObject("result")
            ?.optString("subdomain")
            ?.takeIf { it.isNotBlank() }

    /* ─────────────────────────── D1 ─────────────────────────── */

    fun createDatabase(accountId: String, name: String): String {
        val result = call("POST", "/accounts/$accountId/d1/database", JSONObject().put("name", name))
            .optJSONObject("result")
        return result?.optString("uuid")?.takeIf { it.isNotBlank() }
            ?: throw CfException("Cloudflare did not return a database id")
    }

    fun query(accountId: String, database: String, sql: String, params: List<Any?> = emptyList()): JSONObject {
        val body = JSONObject().apply {
            put("sql", sql)
            put("params", JSONArray().apply { params.forEach { put(it ?: JSONObject.NULL) } })
        }
        return call("POST", "/accounts/$accountId/d1/database/$database/query", body, readTimeoutMs = 60_000)
    }

    /** Names are not unique in D1; the panel's database is the one created as `<worker>-db`. */
    fun databases(accountId: String): List<Pair<String, String>> {
        val array = call("GET", "/accounts/$accountId/d1/database?per_page=100").optJSONArray("result") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val uuid = item.optString("uuid").takeIf { it.isNotBlank() } ?: continue
                add(item.optString("name") to uuid)
            }
        }
    }

    fun deleteDatabase(accountId: String, database: String) {
        call("DELETE", "/accounts/$accountId/d1/database/$database")
    }

    /* ─────────────────────────── Workers ─────────────────────────── */

    fun scriptExists(accountId: String, name: String): Boolean = try {
        call("GET", "/accounts/$accountId/workers/scripts/$name")
        true
    } catch (e: CfException) {
        if (e.status == 404) false else throw e
    }

    /**
     * Uploads an ES module. The API wants a multipart body with a `metadata` part (bindings and module
     * name) and the module itself under the same name as `main_module`.
     */
    fun uploadScript(accountId: String, name: String, source: String, bindings: JSONArray) {
        val boundary = "----mohammad" + UUID.randomUUID().toString().replace("-", "")
        val metadata = JSONObject().apply {
            put("main_module", "index.js")
            put("compatibility_date", COMPATIBILITY_DATE)
            put("bindings", bindings)
        }

        val body = ByteArrayOutputStream()
        fun part(field: String, filename: String, type: String, bytes: ByteArray) {
            body.write("--$boundary\r\n".toByteArray(Charsets.UTF_8))
            body.write(
                "Content-Disposition: form-data; name=\"$field\"; filename=\"$filename\"\r\n"
                    .toByteArray(Charsets.UTF_8),
            )
            body.write("Content-Type: $type\r\n\r\n".toByteArray(Charsets.UTF_8))
            body.write(bytes)
            body.write("\r\n".toByteArray(Charsets.UTF_8))
        }
        part("metadata", "metadata.json", "application/json", metadata.toString().toByteArray(Charsets.UTF_8))
        part("index.js", "index.js", "application/javascript+module", source.toByteArray(Charsets.UTF_8))
        body.write("--$boundary--\r\n".toByteArray(Charsets.UTF_8))

        call(
            method = "PUT",
            path = "/accounts/$accountId/workers/scripts/$name",
            body = null,
            rawBody = body.toByteArray(),
            contentType = "multipart/form-data; boundary=$boundary",
            readTimeoutMs = UPLOAD_TIMEOUT_MS,
        )
    }

    fun enableSubdomain(accountId: String, name: String) {
        call(
            "POST",
            "/accounts/$accountId/workers/scripts/$name/subdomain",
            JSONObject().put("enabled", true).put("previews_enabled", false),
        )
    }

    fun deleteScript(accountId: String, name: String) {
        call("DELETE", "/accounts/$accountId/workers/scripts/$name?force=true")
    }

    /* ────────────────────── zones, DNS and routes ────────────────────── */

    data class Zone(val id: String, val name: String)

    fun zones(): List<Zone> {
        val array = call("GET", "/zones?per_page=50").optJSONArray("result") ?: JSONArray()
        return buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                add(Zone(id = id, name = item.optString("name")))
            }
        }
    }

    /**
     * Points [host] at [script] inside [zoneId]: an AAAA `100::` record plus a worker route.
     *
     * Both are upserted rather than created blindly. A leftover record from an earlier attempt would
     * otherwise make the second run fail with "record already exists" — and the panel that failed
     * half-way is exactly the one the user is trying to replace.
     */
    fun attachWorkerHost(zoneId: String, host: String, script: String) {
        val records = call("GET", "/zones/$zoneId/dns_records?type=AAAA&per_page=100")
            .optJSONArray("result") ?: JSONArray()
        var recordId: String? = null
        for (i in 0 until records.length()) {
            val item = records.optJSONObject(i) ?: continue
            if (item.optString("name").equals(host, ignoreCase = true)) {
                recordId = item.optString("id")
                break
            }
        }
        if (recordId == null) {
            call(
                "POST",
                "/zones/$zoneId/dns_records",
                JSONObject()
                    .put("type", "AAAA")
                    .put("name", host)
                    .put("ttl", 1)
                    .put("content", "100::")
                    .put("proxied", true),
            )
        } else {
            // Type and name are immutable on an existing record; only the target is rewritten.
            call("PATCH", "/zones/$zoneId/dns_records/$recordId", JSONObject().put("content", "100::").put("proxied", true))
        }

        val pattern = "$host/*"
        val routes = call("GET", "/zones/$zoneId/workers/routes").optJSONArray("result") ?: JSONArray()
        for (i in 0 until routes.length()) {
            val item = routes.optJSONObject(i) ?: continue
            if (item.optString("pattern") != pattern) continue
            if (item.optString("script") != script) {
                call(
                    "PUT",
                    "/zones/$zoneId/workers/routes/${item.optString("id")}",
                    JSONObject().put("pattern", pattern).put("script", script),
                )
            }
            return
        }
        call("POST", "/zones/$zoneId/workers/routes", JSONObject().put("pattern", pattern).put("script", script))
    }

    /* ─────────────────────────── transport ─────────────────────────── */

    private fun call(
        method: String,
        path: String,
        body: JSONObject? = null,
        readTimeoutMs: Int = 60_000,
        rawBody: ByteArray? = null,
        contentType: String? = null,
    ): JSONObject {
        val connection = (URL(base + path).openConnection() as HttpURLConnection).apply {
            this.requestMethod = method
            this.connectTimeout = CONNECT_TIMEOUT_MS
            this.readTimeout = readTimeoutMs
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }

        val payload = rawBody ?: body?.toString()?.toByteArray(Charsets.UTF_8)
        val type = contentType ?: "application/json; charset=utf-8"

        try {
            if (payload != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", type)
                connection.outputStream.use { it.write(payload) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.let { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).use { it.readText() }
            }.orEmpty()

            val json = runCatching { JSONObject(text) }.getOrNull()
            if (status !in 200..299 || json?.optBoolean("success") == false) {
                throw CfException(messageOf(status, json), status)
            }
            return json ?: JSONObject()
        } catch (e: CfException) {
            throw e
        } catch (e: Exception) {
            throw CfException("Could not reach Cloudflare: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Cloudflare answers with `{success:false, errors:[{code, message}]}`. The codes that actually
     * happen here are worth naming: a bare "HTTP 403" sends the user looking in the wrong place.
     */
    private fun messageOf(status: Int, json: JSONObject?): String {
        val first = json?.optJSONArray("errors")?.optJSONObject(0)
        val code = first?.optInt("code", 0) ?: 0
        val message = first?.optString("message").orEmpty()

        return when {
            status == 401 || code == 10000 ->
                "Cloudflare rejected the token. Create a new one with Workers Scripts: Edit, D1: Edit and " +
                    "Account Settings: Read — plus Zone: Read, DNS: Edit and Workers Routes: Edit if you attach your own domain."
            code == 9109 || status == 403 ->
                "This token is missing a permission Cloudflare needs (code ${if (code != 0) code else status}). " +
                    "It must have Workers Scripts: Edit, D1: Edit and Account Settings: Read — plus Zone: Read, " +
                    "DNS: Edit and Workers Routes: Edit if you attach your own domain."
            code == 10016 -> "A script with that name already exists. Pick another panel name."
            code == 10021 || code == 1001 ->
                "Cloudflare refused the script: ${message.ifBlank { "syntax error" }}"
            status == 429 -> "Cloudflare is rate limiting these requests. Wait a minute and try again."
            message.isNotBlank() -> "$message (code ${if (code != 0) code else status})"
            else -> "Cloudflare error $status"
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 20_000
        const val UPLOAD_TIMEOUT_MS = 120_000

        /** Pinned: the worker templates are built against this date. */
        const val COMPATIBILITY_DATE = "2025-01-01"
    }
}
