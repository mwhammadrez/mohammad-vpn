package ir.mohammad.vpn.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Installs a panel and its proxy node in the user's own Cloudflare account, from the phone.
 *
 * The two worker scripts live in `assets/panel/` as ES modules; this class only orchestrates the
 * Cloudflare calls, in the order the platform requires a binding to exist before it is referenced:
 * database → tables → password → node worker → panel worker → public subdomain.
 *
 * Everything is undone if a step fails, so a retry with the same name does not trip over a half-made
 * panel (that was a real failure mode: a leftover database makes the second attempt look broken).
 */
class PanelProvisioner(private val context: Context, token: String) {

    private val cf = CloudflareApi(token.trim())

    data class Created(val account: PanelApi.Account, val accountName: String, val database: String)

    /**
     * @param name     worker name; becomes `<name>` and `<name>-node`
     * @param password panel password, hashed into the database before the worker ever serves a request
     * @param onStep   called with the index of each step as it starts; the list is [STEPS]
     */
    fun provision(name: String, password: String, onStep: (Int) -> Unit = { }): Created {
        if (password.length < 6) throw CfException("The panel password needs at least 6 characters")

        onStep(0)
        val script = normalizeName(name)
        val accounts = cf.accounts()
        val account = accounts.firstOrNull()
            ?: throw CfException(
                "This token cannot see any Cloudflare account. It needs the Account Settings: Read permission.",
            )

        if (cf.scriptExists(account.id, script)) {
            throw CfException("A worker named \"$script\" already exists in this account. Choose another panel name.")
        }

        // Checked before anything is created: without it the workers have no address to live at.
        val subdomain = cf.workersSubdomain(account.id)
            ?: throw CfException(
                "This account has no workers.dev subdomain yet. Open Workers & Pages in the Cloudflare dashboard " +
                    "once and set a subdomain, then come back.",
            )

        var database: String? = null
        var node = false
        var panel = false

        try {
            onStep(1)
            database = cf.createDatabase(account.id, "$script-db")

            onStep(2)
            for (statement in statements(readAsset("panel/schema.sql"))) {
                cf.query(account.id, database, statement)
            }

            onStep(3)
            cf.query(
                account.id, database,
                "INSERT INTO settings (key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                listOf("password_hash", sha256Hex(password)),
            )
            cf.query(
                account.id, database,
                "INSERT INTO settings (key,value) VALUES (?,?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
                listOf("session_secret", randomHex(32)),
            )

            val nodeName = "$script-node"
            onStep(4)
            cf.uploadScript(account.id, nodeName, readAsset("panel/proxy-worker.js"), bindings(database))
            node = true
            cf.enableSubdomain(account.id, nodeName)

            onStep(5)
            cf.uploadScript(
                account.id, script, readAsset("panel/panel-worker.js"),
                bindings(database).put(
                    JSONObject()
                        .put("type", "plain_text")
                        .put("name", "PROXY_HOST")
                        .put("text", "$nodeName.$subdomain.workers.dev"),
                ),
            )
            panel = true

            onStep(6)
            cf.enableSubdomain(account.id, script)

            val base = "https://$script.$subdomain.workers.dev"
            return Created(
                account = PanelApi.Account(
                    panelUrl = "$base/panel",
                    baseUrl = base,
                    nodeUrl = "https://$nodeName.$subdomain.workers.dev",
                    password = password,
                    worker = script,
                ),
                accountName = account.name,
                database = database,
            )
        } catch (e: Exception) {
            if (panel) runCatching { cf.deleteScript(account.id, script) }
            if (node) runCatching { cf.deleteScript(account.id, "$script-node") }
            database?.let { runCatching { cf.deleteDatabase(account.id, it) } }
            throw e
        }
    }

    /**
     * Removes the panel from Cloudflare: both workers and the database. Requires the token again,
     * because the app deliberately does not keep one — deleting a panel is a rare, deliberate act.
     */
    fun delete(workerName: String): String {
        val account = cf.accounts().firstOrNull()
            ?: throw CfException("This token cannot see any Cloudflare account.")
        val name = normalizeName(workerName)

        for (script in listOf(name, "$name-node")) {
            if (cf.scriptExists(account.id, script)) cf.deleteScript(account.id, script)
        }
        val database = cf.databases(account.id).firstOrNull { it.first == "$name-db" }
        database?.let { cf.deleteDatabase(account.id, it.second) }

        return if (database != null) "$name + $name-node + database deleted" else "$name + $name-node deleted"
    }

    private fun bindings(database: String): JSONArray = JSONArray().put(
        JSONObject().put("type", "d1").put("name", "DB").put("id", database),
    )

    private fun readAsset(file: String): String =
        context.assets.open(file).bufferedReader().use { it.readText() }

    companion object {

        /** Shown one by one while provisioning runs; kept here so the UI and the work cannot drift. */
        val STEPS = listOf(
            "Verifying account",
            "Creating database",
            "Creating tables",
            "Setting the panel password",
            "Installing the proxy node",
            "Installing the panel",
            "Enabling the domain",
        )

        private val NAME_RE = Regex("^[a-z0-9]([a-z0-9-]{1,48})[a-z0-9]$")

        /** Cloudflare accepts lowercase letters, digits and dashes, 3–50 characters. */
        fun normalizeName(raw: String): String {
            val cleaned = raw.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "-")
                .replace(Regex("-{2,}"), "-")
                .replace(Regex("^-+|-+$"), "")
            if (NAME_RE.matches(cleaned)) return cleaned
            val fallback = "mohammad-" + randomHex(3)
            return if (NAME_RE.matches(fallback)) fallback else "mohammad-panel"
        }

        /**
         * D1 runs one statement per call, so the schema file is split here. `--` comments are dropped
         * first: a semicolon inside a comment would otherwise cut a statement in half.
         */
        fun statements(sql: String): List<String> =
            sql.lineSequence()
                .filterNot { it.trimStart().startsWith("--") }
                .joinToString("\n")
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }

        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }

        fun randomHex(bytes: Int): String {
            val buffer = ByteArray(bytes)
            SecureRandom().nextBytes(buffer)
            return buffer.joinToString("") { "%02x".format(it) }
        }

        /** 12 characters, no look-alikes, so it can be read off the screen and typed again. */
        fun suggestPassword(): String {
            val alphabet = "abcdefghjkmnpqrstuvwxyz23456789"
            val random = SecureRandom()
            return (1..12).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        }
    }
}
