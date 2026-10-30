package ir.mohammad.vpn.core

import ir.mohammad.vpn.data.Profile
import org.json.JSONArray
import org.json.JSONObject

/**
 * Renders the JSON handed to the AmneziaWG bridge.
 *
 * Keys stay base64 (wireguard canonical form); the Go side converts them to hex
 * for the UAPI configuration and validates each one is 32 bytes.
 */
object AwgConfig {

    fun render(profile: Profile, mtu: Int): String {
        val awg = profile.awg ?: throw IllegalStateException("profile has no awg settings")

        val out = JSONObject()
            .put("privateKey", awg.optString("privateKey"))
            .put("listenPort", awg.optInt("listenPort", 0))
            .put("mtu", awg.optInt("mtu", mtu))

        val peersIn = awg.optJSONArray("peers") ?: JSONArray()
        if (peersIn.length() == 0) throw IllegalStateException("awg profile has no peers")
        val peers = JSONArray()
        for (i in 0 until peersIn.length()) {
            val p = peersIn.getJSONObject(i)
            val peer = JSONObject()
                .put("publicKey", p.optString("publicKey"))
                .put("endpoint", p.optString("endpoint"))
                .put("persistentKeepalive", p.optInt("persistentKeepalive", 0))
            if (p.has("presharedKey")) peer.put("presharedKey", p.optString("presharedKey"))
            val allowed = p.optJSONArray("allowedIPs") ?: JSONArray().put("0.0.0.0/0")
            peer.put("allowedIPs", allowed)
            peers.put(peer)
        }
        out.put("peers", peers)

        awg.optJSONObject("obf")?.let { out.put("obf", it) }
        return out.toString()
    }
}
