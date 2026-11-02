package ir.mohammad.vpn.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Remembers every panel this device created.
 *
 * Stored per panel: the panel address, the signed session token, and the panel password. The password
 * is kept because it is the only way back in when a seven-day session expires, and this file lives in
 * the app's private storage. The Cloudflare API token is never written here — it exists for the
 * duration of one provisioning call and is dropped.
 *
 * One entry is "current": [account] and [session] resolve to it, so the rest of the app keeps working
 * as if there were a single panel.
 */
object PanelStore {

    private const val PREFS = "panel"
    private const val KEY_LIST = "panels"
    private const val KEY_CURRENT = "current"

    // Single-panel layout from before the list existed; read once, then removed.
    private const val KEY_URL = "url"
    private const val KEY_BASE = "base"
    private const val KEY_NODE = "node"
    private const val KEY_WORKER = "worker"
    private const val KEY_SESSION = "session"
    private const val KEY_PASSWORD = "password"

    private const val KEY_JSON_PANEL = "panelUrl"
    private const val KEY_JSON_BASE = "baseUrl"
    private const val KEY_JSON_NODE = "nodeUrl"
    private const val KEY_JSON_WORKER = "worker"
    private const val KEY_JSON_SESSION = "session"
    private const val KEY_JSON_PASSWORD = "password"

    data class Entry(
        val panelUrl: String,
        val baseUrl: String,
        val nodeUrl: String?,
        val password: String,
        val worker: String,
        val session: String?,
    ) {
        fun toAccount(): PanelApi.Account =
            PanelApi.Account(panelUrl = panelUrl, baseUrl = baseUrl, nodeUrl = nodeUrl, password = password, worker = worker)

        /** Worker name without the machine prefix; what the list shows. */
        val label: String get() = worker
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Every saved panel, newest first. */
    fun list(context: Context): List<Entry> = read(context).toList()

    fun current(context: Context): Entry? {
        val entries = read(context)
        if (entries.isEmpty()) return null
        val worker = prefs(context).getString(KEY_CURRENT, null)
        return entries.firstOrNull { it.worker == worker } ?: entries.first()
    }

    fun account(context: Context): PanelApi.Account? = current(context)?.toAccount()

    fun session(context: Context): String? = current(context)?.session

    fun save(context: Context, account: PanelApi.Account, session: String) {
        val entries = read(context)
        entries.removeAll { it.worker == account.worker }
        entries.add(
            0,
            Entry(
                panelUrl = account.panelUrl,
                baseUrl = account.baseUrl,
                nodeUrl = account.nodeUrl,
                password = account.password,
                worker = account.worker,
                session = session,
            ),
        )
        write(context, entries, account.worker)
    }

    fun saveSession(context: Context, session: String) {
        val entries = read(context)
        val worker = prefs(context).getString(KEY_CURRENT, null) ?: return
        val index = entries.indexOfFirst { it.worker == worker }
        if (index < 0) return
        entries[index] = entries[index].copy(session = session)
        write(context, entries, worker)
    }

    /** Makes [worker] the panel that [account] and [session] resolve to. */
    fun select(context: Context, worker: String): Boolean {
        val entries = read(context)
        if (entries.none { it.worker == worker }) return false
        prefs(context).edit { putString(KEY_CURRENT, worker) }
        return true
    }

    fun remove(context: Context, worker: String) {
        val entries = read(context)
        entries.removeAll { it.worker == worker }
        val current = prefs(context).getString(KEY_CURRENT, null)
        write(context, entries, if (current == worker) entries.firstOrNull()?.worker else current)
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }

    /* ───────────────────────── storage ───────────────────────── */

    private fun read(context: Context): MutableList<Entry> {
        val p = prefs(context)
        val raw = p.getString(KEY_LIST, null)
        if (raw.isNullOrBlank()) return migrate(context)

        val out = mutableListOf<Entry>()
        runCatching {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val worker = item.optString(KEY_JSON_WORKER)
                val baseUrl = item.optString(KEY_JSON_BASE)
                if (worker.isBlank() || baseUrl.isBlank()) continue
                out.add(
                    Entry(
                        panelUrl = item.optString(KEY_JSON_PANEL).ifBlank { "$baseUrl/panel" },
                        baseUrl = baseUrl,
                        nodeUrl = item.optString(KEY_JSON_NODE).takeIf { it.isNotBlank() },
                        password = item.optString(KEY_JSON_PASSWORD),
                        worker = worker,
                        session = item.optString(KEY_JSON_SESSION).takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return out
    }

    /** Turns the old single-panel prefs into the first list entry, once. */
    private fun migrate(context: Context): MutableList<Entry> {
        val p = prefs(context)
        val out = mutableListOf<Entry>()
        val base = p.getString(KEY_BASE, null)
        val panelUrl = p.getString(KEY_URL, null)
        val worker = p.getString(KEY_WORKER, null).orEmpty()

        if (!base.isNullOrBlank() && !panelUrl.isNullOrBlank() && worker.isNotBlank()) {
            out.add(
                Entry(
                    panelUrl = panelUrl,
                    baseUrl = base,
                    nodeUrl = p.getString(KEY_NODE, null),
                    password = p.getString(KEY_PASSWORD, null).orEmpty(),
                    worker = worker,
                    session = p.getString(KEY_SESSION, null),
                ),
            )
            write(context, out, worker)
            p.edit {
                remove(KEY_URL)
                remove(KEY_BASE)
                remove(KEY_NODE)
                remove(KEY_WORKER)
                remove(KEY_SESSION)
                remove(KEY_PASSWORD)
            }
        }
        return out
    }

    private fun write(context: Context, entries: List<Entry>, current: String?) {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put(KEY_JSON_PANEL, entry.panelUrl)
                    put(KEY_JSON_BASE, entry.baseUrl)
                    put(KEY_JSON_NODE, entry.nodeUrl ?: "")
                    put(KEY_JSON_WORKER, entry.worker)
                    put(KEY_JSON_SESSION, entry.session ?: "")
                    put(KEY_JSON_PASSWORD, entry.password)
                },
            )
        }
        prefs(context).edit {
            putString(KEY_LIST, array.toString())
            if (current.isNullOrBlank()) remove(KEY_CURRENT) else putString(KEY_CURRENT, current)
        }
    }
}
