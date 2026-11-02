package ir.mohammad.vpn.data

import android.content.Context
import androidx.core.content.edit

/**
 * Remembers which panel this device created.
 *
 * Stored: the panel address, the signed session token, and the panel password. The password is kept
 * because it is the only way back in when a seven-day session expires, and this file lives in the
 * app's private storage. The Cloudflare API token is never written here — it exists for the duration
 * of one provisioning call and is dropped.
 */
object PanelStore {

    private const val PREFS = "panel"
    private const val KEY_URL = "url"
    private const val KEY_BASE = "base"
    private const val KEY_NODE = "node"
    private const val KEY_WORKER = "worker"
    private const val KEY_SESSION = "session"
    private const val KEY_PASSWORD = "password"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun account(context: Context): PanelApi.Account? {
        val p = prefs(context)
        val base = p.getString(KEY_BASE, null) ?: return null
        val panelUrl = p.getString(KEY_URL, null) ?: return null
        return PanelApi.Account(
            panelUrl = panelUrl,
            baseUrl = base,
            nodeUrl = p.getString(KEY_NODE, null),
            password = p.getString(KEY_PASSWORD, null).orEmpty(),
            worker = p.getString(KEY_WORKER, null) ?: "",
        )
    }

    fun session(context: Context): String? = prefs(context).getString(KEY_SESSION, null)

    fun save(context: Context, account: PanelApi.Account, session: String) {
        prefs(context).edit {
            putString(KEY_URL, account.panelUrl)
            putString(KEY_BASE, account.baseUrl)
            putString(KEY_NODE, account.nodeUrl)
            putString(KEY_WORKER, account.worker)
            putString(KEY_SESSION, session)
            putString(KEY_PASSWORD, account.password)
        }
    }

    fun saveSession(context: Context, session: String) {
        prefs(context).edit { putString(KEY_SESSION, session) }
    }

    fun clear(context: Context) {
        prefs(context).edit { clear() }
    }
}
