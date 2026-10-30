package ir.mohammad.vpn

import ir.mohammad.vpn.data.LogStore
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactionTest {

    @Test
    fun redactsUuid() {
        val out = LogStore.redact("user 0722d93a-024a-489d-be2a-a2e83a1f39b7 connected")
        assertFalse(out.contains("0722d93a"))
        assertTrue(out.contains("***"))
    }

    @Test
    fun redactsEndpoint() {
        val out = LogStore.redact("dialing example.com:2095 now")
        assertFalse(out.contains("example.com:2095"))
    }

    @Test
    fun redactsLongKeyTokens() {
        val key = "u6AMJmEQZuRR9uUsj0VjhdtHZaa2yiJ_ygqu8rtp6qYsZEpeS5xaqonDz5XNvSa735"
        val out = LogStore.redact("encryption=$key")
        assertFalse(out.contains(key))
    }

    @Test
    fun keepsOrdinaryText() {
        assertEquals("tunnel connected", LogStore.redact("tunnel connected"))
    }

    private fun assertEquals(expected: String, actual: String) =
        org.junit.Assert.assertEquals(expected, actual)
}
