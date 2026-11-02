package ir.mohammad.vpn.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import ir.mohammad.vpn.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import net.i2p.crypto.eddsa.EdDSAEngine
import net.i2p.crypto.eddsa.EdDSAPublicKey
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable
import net.i2p.crypto.eddsa.spec.EdDSAPublicKeySpec

/**
 * Profiles that come from the operator's own server, so adding a server never needs a new APK.
 *
 * The server sends a detached Ed25519 signature next to the payload, and the public key is compiled
 * into the app (`REMOTE_CONFIG_KEY`): a compromised host cannot inject profiles, and nothing is
 * trusted merely because it arrived over HTTPS.
 *
 * The signed envelope is cached on disk (encrypted with an Android Keystore key). Re-reading it at
 * startup costs a signature check, so a phone that is offline still has the profiles it last saw —
 * and one that was tampered with while cached is rejected exactly like a bad download.
 *
 * Format of `GET {REMOTE_CONFIG_URL}`:
 * ```
 * { "schemaVersion": 1, "configVersion": 7,
 *   "payload": "{\"profiles\":[...]}",     // exact bytes that were signed
 *   "signature": "base64url"               // Ed25519 over payload
 * }
 * ```
 */
object RemoteConfig {

    private const val TAG = "RC"
    private const val CACHE_FILE = "remote-config.bin"
    private const val PREF_FILE = "mohammad"
    private const val PREF_LAST_FETCH = "rc_last_fetch"
    private const val PREF_VERSION = "rc_version"

    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_BODY_BYTES = 256 * 1024
    private const val RETRY_LIMIT = 3
    // SPEC RC-9 asks for 24 h. One hour keeps the operator's "add a config, see it on the phones"
    // loop usable; the fetch is a single signed GET and applies only when the version is newer.
    private const val REFRESH_INTERVAL_MS = 60 * 60 * 1000L

    private const val SCHEMA_VERSION = 1

    data class Snapshot(val profiles: List<Profile>, val version: Long)

    class Invalid(message: String) : Exception(message)

    /** True when this build was given a server address and a key. An empty build never fetches. */
    fun isConfigured(): Boolean =
        BuildConfig.REMOTE_CONFIG_URL.isNotBlank() && BuildConfig.REMOTE_CONFIG_KEY.isNotBlank()

    /**
     * The build may carry more than one address, tried in order. The first is the operator's own
     * domain; the second is usually the same worker on workers.dev, so a blocked host is a delay
     * rather than a dead app.
     */
    private fun urls(): List<String> =
        BuildConfig.REMOTE_CONFIG_URL.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /* ─────────────────────────── public entry points ─────────────────────────── */

    /** The last accepted envelope, verified again on every read. Null when nothing is cached. */
    fun cached(context: Context): Snapshot? {
        if (!isConfigured()) return null
        val file = File(context.filesDir, CACHE_FILE)
        if (!file.isFile) return null
        return runCatching {
            val envelope = runCatching { SecretBox.open(context, file.readBytes()) }.getOrNull() ?: return null
            accept(String(envelope, Charsets.UTF_8), minVersion = 0)
        }.onFailure { Log.w(TAG, "cached config rejected: ${it.javaClass.simpleName}") }.getOrNull()
    }

    /** Fetches at most once per 24 h. Returns a snapshot only when something newer was applied. */
    fun refreshIfDue(context: Context): Snapshot? {
        if (!isConfigured()) return null
        val last = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getLong(PREF_LAST_FETCH, 0L)
        if (System.currentTimeMillis() - last < REFRESH_INTERVAL_MS) return null
        return refresh(context)
    }

    /** One fetch attempt with the retry policy in SPEC RC-7. Never throws. */
    fun refresh(context: Context): Snapshot? =
        try {
            val body = download()
            val active = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).getLong(PREF_VERSION, 0L)
            val snapshot = accept(body, minVersion = active + 1)
            save(context, body, snapshot.version)
            snapshot
        } catch (e: Exception) {
            // One line, no endpoint and no host: a log that names the server is a log that leaks it.
            Log.w(TAG, "remote config not applied: ${e.javaClass.simpleName}: ${e.message?.take(120)}")
            null
        }

    /* ─────────────────────────── verification ─────────────────────────── */

    /**
     * Verifies and parses an envelope. Exposed for tests: the crypto is the part that must never be
     * "probably fine".
     */
    internal fun accept(
        body: String,
        minVersion: Long,
        publicKey: String = BuildConfig.REMOTE_CONFIG_KEY,
    ): Snapshot {
        if (body.length > MAX_BODY_BYTES) throw Invalid("oversize")
        val envelope = runCatching { JSONObject(body) }.getOrElse { throw Invalid("not json") }

        val schema = envelope.optInt("schemaVersion", -1)
        if (schema != SCHEMA_VERSION) throw Invalid("unsupported schemaVersion $schema")

        val payload = envelope.optString("payload", "")
        if (payload.isEmpty()) throw Invalid("missing payload")
        val signature = envelope.optString("signature", "")
        if (signature.isEmpty()) throw Invalid("missing signature")

        val key = decode(publicKey)
        if (key.size != 32) throw Invalid("public key must be 32 bytes, was ${key.size}")
        if (!verify(key, payload.toByteArray(Charsets.UTF_8), decode(signature))) {
            throw Invalid("bad signature")
        }

        // Everything below is read from the *signed* bytes. The envelope's own configVersion is
        // attacker-controlled: a replay of an old signed payload with a huge version would pin the
        // client forever (no later release could ever be "newer").
        val signed = runCatching { JSONObject(payload) }.getOrElse { throw Invalid("payload is not json") }
        val signedSchema = signed.optInt("schemaVersion", -1)
        if (signedSchema != SCHEMA_VERSION) throw Invalid("unsupported signed schemaVersion $signedSchema")
        val version = resolveVersion(signed, envelope.optLong("configVersion", 0L))
        if (version < minVersion) throw Invalid("stale configVersion $version < $minVersion")

        return Snapshot(profiles = parsePayload(payload), version = version)
    }

    /**
     * The signed version wins. Payloads written before the panel embedded `configVersion` fall back
     * to the envelope value, which keeps already-deployed panels working (their payloads are short
     * and their hosts are the operator's own).
     */
    internal fun resolveVersion(signed: JSONObject, envelopeVersion: Long): Long {
        val fromPayload = signed.optLong("configVersion", -1L)
        return if (fromPayload >= 0) fromPayload else envelopeVersion
    }

    internal fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        try {
            val spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519)
            val engine = EdDSAEngine(MessageDigest.getInstance(spec.hashAlgorithm))
            engine.initVerify(EdDSAPublicKey(EdDSAPublicKeySpec(publicKey, spec)))
            engine.update(message)
            engine.verify(signature)
        } catch (e: Exception) {
            false
        }

    /** Payload → profiles, through the same parser the paste box uses. One bad entry is skipped. */
    internal fun parsePayload(payload: String): List<Profile> {
        val root = runCatching { JSONObject(payload) }.getOrElse { throw Invalid("payload is not json") }
        val array = root.optJSONArray("profiles") ?: JSONArray()

        val out = LinkedHashMap<String, Profile>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val uri = item.optString("uri").trim()
            if (uri.isEmpty()) continue
            val profile = runCatching { ConfigUri.parse(uri).profile }.getOrNull() ?: continue
            val name = item.optString("name").trim()
            val named = if (name.isEmpty() || name == profile.name) profile else profile.copy(name = name)
            out[named.id] = named
        }
        return out.values.toList()
    }

    private fun decode(value: String): ByteArray =
        try {
            Base64.decode(value, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: IllegalArgumentException) {
            throw Invalid("not base64url")
        }

    /* ─────────────────────────── transport ─────────────────────────── */

    private fun download(): String {
        val candidates = urls()
        if (candidates.isEmpty()) throw Invalid("no address configured")
        candidates.forEach { if (!it.startsWith("https://")) throw Invalid("remote config must be https") }

        var last: Exception? = null
        for (url in candidates) {
            for (attempt in 0 until RETRY_LIMIT) {
                try {
                    return once(url)
                } catch (e: Invalid) {
                    // A signed answer that fails the checks is a real answer: stop, do not try the
                    // next host as if the operator's key might be different there.
                    throw e
                } catch (e: Exception) {
                    last = e
                    Thread.sleep((300L shl attempt) + (0..150).random())
                }
            }
        }
        throw last ?: Invalid("unreachable")
    }

    private fun once(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = connection.responseCode
            // Transport failures are retryable on the next host. Only a *signed* answer that fails
            // verification is final (see `download`), so these must not be [Invalid].
            if (code != 200) throw IOException("http $code")
            val type = connection.contentType.orEmpty()
            if (!type.contains("json", ignoreCase = true)) throw IOException("content type $type")

            val body = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    body.write(buffer, 0, read)
                    if (body.size() > MAX_BODY_BYTES) throw IOException("oversize")
                }
            }
            return String(body.toByteArray(), Charsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private fun save(context: Context, body: String, version: Long) {
        runCatching {
            File(context.filesDir, CACHE_FILE).writeBytes(SecretBox.seal(context, body.toByteArray(Charsets.UTF_8)))
        }
        context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE).edit()
            .putLong(PREF_LAST_FETCH, System.currentTimeMillis())
            .putLong(PREF_VERSION, version)
            .apply()
    }

    /**
     * AES-GCM with a key that never leaves the Android Keystore. The cache is not secret in a
     * meaningful sense (it is signed), but SPEC RC-8 asks for it and it costs one small class.
     */
    private object SecretBox {
        private const val KEY_ALIAS = "remote_config_cache"
        private const val KEYSTORE = "AndroidKeyStore"

        fun seal(context: Context, plain: ByteArray): ByteArray {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key(context))
            val iv = cipher.iv
            val body = cipher.doFinal(plain)
            return iv + body
        }

        fun open(context: Context, sealed: ByteArray): ByteArray {
            if (sealed.size < 13) throw Invalid("cache truncated")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                key(context),
                GCMParameterSpec(128, sealed, 0, 12),
            )
            return cipher.doFinal(sealed, 12, sealed.size - 12)
        }

        private fun key(context: Context): SecretKey {
            val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            return generator.generateKey()
        }
    }
}
