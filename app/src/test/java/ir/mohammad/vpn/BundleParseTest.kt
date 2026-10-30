package ir.mohammad.vpn

import ir.mohammad.vpn.data.BundleException
import ir.mohammad.vpn.data.OperatorBundleLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BundleParseTest {

    private val valid = """
        {"schemaVersion":1,"defaultProfileId":"p1","profiles":[
          {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h.example","serverPort":443,
           "xray":{"protocol":"vless","settings":{}}}
        ]}
    """.trimIndent()

    @Test
    fun parsesValidBundle() {
        val bundle = OperatorBundleLoader.parse(valid)
        val profiles = bundle.profiles
        assertEquals(listOf("1.1.1.1", "8.8.8.8"), bundle.dnsServers)
        assertEquals(1500, bundle.mtu)
        assertEquals(1, profiles.size)
        assertEquals("p1", profiles[0].id)
        assertEquals("XRAY", profiles[0].core)
        assertEquals(443, profiles[0].port)
    }

    @Test(expected = BundleException::class)
    fun rejectsWrongSchemaVersion() {
        OperatorBundleLoader.parse(valid.replace("\"schemaVersion\":1", "\"schemaVersion\":9"))
    }

    @Test(expected = BundleException::class)
    fun rejectsEmptyProfiles() {
        OperatorBundleLoader.parse("""{"schemaVersion":1,"profiles":[]}""")
    }

    @Test(expected = BundleException::class)
    fun rejectsBadPort() {
        OperatorBundleLoader.parse(valid.replace("\"serverPort\":443", "\"serverPort\":0"))
    }

    @Test(expected = BundleException::class)
    fun rejectsProtocolCoreMismatch() {
        OperatorBundleLoader.parse(
            valid.replace("\"core\":\"XRAY\"", "\"core\":\"AMNEZIAWG\"")
                .replace("\"xray\"", "\"awg\""),
        )
    }

    @Test(expected = BundleException::class)
    fun rejectsUnknownCore() {
        OperatorBundleLoader.parse(valid.replace("\"core\":\"XRAY\"", "\"core\":\"MAGIC\""))
    }

    @Test(expected = BundleException::class)
    fun rejectsDuplicateIds() {
        val single = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h","serverPort":443,"xray":{}},
              {"id":"p1","name":"B","protocol":"VLESS","core":"XRAY","serverHost":"h","serverPort":443,"xray":{}}
            ]}
        """.trimIndent()
        OperatorBundleLoader.parse(single)
    }

    @Test(expected = BundleException::class)
    fun rejectsMissingXrayForXrayCore() {
        val noXray = """
            {"schemaVersion":1,"profiles":[
              {"id":"p1","name":"A","protocol":"VLESS","core":"XRAY","serverHost":"h","serverPort":443}
            ]}
        """.trimIndent()
        OperatorBundleLoader.parse(noXray)
    }

    @Test
    fun parsesAwgProfile() {
        val awg = """
            {"schemaVersion":1,"profiles":[
              {"id":"w1","name":"W","protocol":"AMNEZIAWG","core":"AMNEZIAWG","serverHost":"h","serverPort":51820,
               "awg":{"privateKey":"x","peers":[]}}
            ]}
        """.trimIndent()
        val profiles = OperatorBundleLoader.parse(awg).profiles
        assertTrue(profiles[0].awg != null)
        assertEquals("AMNEZIAWG", profiles[0].core)
    }
}
