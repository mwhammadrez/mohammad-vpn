package ir.mohammad.vpn.data

import android.content.Context
import ir.mohammad.vpn.core.MtuPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Imported profiles, kept next to the app's own files.
 *
 * The operator bundle stays the source of truth for defaults and for the profiles it ships; this
 * store only holds what the user pasted. Everything here is user data, never bundled into the APK.
 *
 * A file that fails to read, or an entry that fails [validate], is dropped rather than fatal: one bad
 * pasted link must not make the app unstartable.
 */
object ProfileStore {

    private const val FILE_NAME = "imported_profiles.json"
    private const val SCHEMA = 1

    fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    fun load(context: Context): List<Profile> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        val root = runCatching { JSONObject(f.readText()) }.getOrNull() ?: return emptyList()
        val array = root.optJSONArray("profiles") ?: return emptyList()
        val out = mutableListOf<Profile>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val profile = runCatching { fromJson(obj) }.getOrNull() ?: continue
            if (validate(profile) == null) out.add(profile)
        }
        return out
    }

    fun save(context: Context, profiles: List<Profile>) {
        val root = JSONObject().put("schema", SCHEMA).put(
            "profiles",
            JSONArray().apply { profiles.forEach { put(toJson(it)) } },
        )
        val target = file(context)
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        temp.writeText(root.toString())
        if (!temp.renameTo(target)) {
            target.writeText(root.toString())
            temp.delete()
        }
    }

    /** Adds or replaces by id, so pasting the same link twice does not duplicate it. */
    fun add(context: Context, profile: Profile): List<Profile> {
        val existing = load(context).filterNot { it.id == profile.id } + profile
        save(context, existing)
        return existing
    }

    fun remove(context: Context, id: String): List<Profile> {
        val existing = load(context).filterNot { it.id == id }
        save(context, existing)
        return existing
    }

    /** Structural check, not a policy check: the parser already produced a valid profile. */
    fun validate(profile: Profile): String? {
        if (profile.id.isBlank()) return "id missing"
        if (profile.name.isBlank()) return "name missing"
        if (profile.host.isBlank()) return "host missing"
        if (profile.port !in 1..65535) return "port out of range"
        if (profile.core == "XRAY" && profile.xray == null) return "xray settings missing"
        if (profile.core == "AMNEZIAWG" && profile.awg == null) return "awg settings missing"
        profile.mtu?.let { if (!MtuPolicy.isValid(it)) return "mtu out of range" }
        return null
    }

    fun toJson(profile: Profile): JSONObject = JSONObject()
        .put("id", profile.id)
        .put("name", profile.name)
        .put("protocol", profile.protocol)
        .put("core", profile.core)
        .put("host", profile.host)
        .put("port", profile.port)
        .apply {
            profile.xray?.let { put("xray", JSONObject(it.toString())) }
            profile.awg?.let { put("awg", JSONObject(it.toString())) }
            profile.mtu?.let { put("mtu", it) }
            profile.fingerprint?.let { put("fingerprint", it) }
            profile.flow?.let { put("flow", it) }
            profile.dialMode?.let { put("dialMode", it) }
            profile.cipherSuites?.let { put("cipherSuites", it) }
            profile.finalMask?.let { put("finalMask", JSONObject(it.toString())) }
        }

    fun fromJson(obj: JSONObject): Profile = Profile(
        id = obj.getString("id"),
        name = obj.getString("name"),
        protocol = obj.getString("protocol"),
        core = obj.optString("core", "XRAY"),
        host = obj.getString("host"),
        port = obj.getInt("port"),
        xray = obj.optJSONObject("xray"),
        awg = obj.optJSONObject("awg"),
        mtu = if (obj.has("mtu")) obj.getInt("mtu") else null,
        fingerprint = obj.optString("fingerprint").takeIf { it.isNotBlank() },
        flow = obj.optString("flow").takeIf { it.isNotBlank() },
        dialMode = obj.optString("dialMode").takeIf { it.isNotBlank() },
        cipherSuites = obj.optString("cipherSuites").takeIf { it.isNotBlank() },
        finalMask = obj.optJSONObject("finalMask"),
    )
}
