package ir.mohammad.vpn

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.ConfigUri
import ir.mohammad.vpn.data.Profile
import ir.mohammad.vpn.data.ProfileStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Persistence for pasted profiles. One bad entry must not take the list down with it. */
@RunWith(AndroidJUnit4::class)
class ProfileStoreTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun clean() {
        ProfileStore.file(context).delete()
        java.io.File(context.filesDir, "imported_profiles.json.corrupt").delete()
    }

    private fun sample(name: String = "A", link: String = "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=none&type=ws#A") =
        ConfigUri.parse(link).profile.copy(name = name)

    @Test
    fun `round trip keeps every field`() {
        val profile = sample().copy(
            fingerprint = "chrome",
            flow = "xtls-rprx-vision",
            dialMode = "oob.0",
            cipherSuites = "TLS_AES_128_GCM_SHA256",
            finalMask = JSONObject().put("tcp", org.json.JSONArray().put(JSONObject().put("type", "merge"))),
        )
        ProfileStore.save(context, listOf(profile))
        val loaded = ProfileStore.load(context).single()

        assertEquals(profile.id, loaded.id)
        assertEquals(profile.host, loaded.host)
        assertEquals(profile.port, loaded.port)
        assertEquals("oob.0", loaded.dialMode)
        assertEquals("merge", loaded.finalMask!!.getJSONArray("tcp").getJSONObject(0).getString("type"))
        assertEquals(profile.xray.toString(), loaded.xray.toString())
    }

    @Test
    fun `pasting the same link twice replaces instead of duplicating`() {
        val link = "vless://11111111-2222-3333-4444-555555555555@a.example.com:443?security=none"
        ProfileStore.add(context, ConfigUri.parse(link).profile)
        val after = ProfileStore.add(context, ConfigUri.parse(link).profile.copy(name = "renamed"))
        assertEquals(1, after.size)
        assertEquals("renamed", ProfileStore.load(context).single().name)
    }

    @Test
    fun `remove drops exactly one entry`() {
        val a = sample("A")
        val b = sample("B", "trojan://pw@b.example.com:443#B")
        ProfileStore.save(context, listOf(a, b))
        val left = ProfileStore.remove(context, a.id)
        assertEquals(1, left.size)
        assertEquals(b.id, left.single().id)
    }

    @Test
    fun `a corrupt file yields an empty list instead of a crash`() {
        ProfileStore.file(context).writeText("{not json")
        assertEquals(emptyList<Profile>(), ProfileStore.load(context))
    }

    @Test
    fun `an invalid entry is dropped and the valid one survives`() {
        val good = sample()
        val bad = JSONObject()
            .put("id", "user-bad")
            .put("name", "bad")
            .put("protocol", "VLESS")
            .put("core", "XRAY")
            .put("host", "x.example.com")
            .put("port", 99999)
        ProfileStore.file(context).writeText(
            JSONObject().put("schema", 1).put(
                "profiles",
                org.json.JSONArray().put(ProfileStore.toJson(good)).put(bad),
            ).toString(),
        )
        val loaded = ProfileStore.load(context)
        assertEquals(1, loaded.size)
        assertEquals(good.id, loaded.single().id)
    }

    @Test
    fun `validation catches the structural problems`() {
        assertNull(ProfileStore.validate(sample()))
        assertNotNull(ProfileStore.validate(sample().copy(port = 0)))
        assertNotNull(ProfileStore.validate(sample().copy(host = "")))
        assertNotNull(ProfileStore.validate(sample().copy(xray = null)))
        assertNotNull(ProfileStore.validate(sample().copy(mtu = 42)))
        assertTrue(ProfileStore.validate(sample().copy(core = "AMNEZIAWG", awg = null)) != null)
    }

@Test
    fun `a corrupt file is quarantined instead of silently wiping the list`() {
        ProfileStore.file(context).writeText("{ not json")
        assertEquals(emptyList<Profile>(), ProfileStore.load(context))
        assertTrue(java.io.File(context.filesDir, "imported_profiles.json.corrupt").isFile)
        // A later save must start from a clean list, not overwrite what was quarantined.
        ProfileStore.add(context, sample("B"))
        assertEquals(listOf("B"), ProfileStore.load(context).map { it.name })
    }
}
