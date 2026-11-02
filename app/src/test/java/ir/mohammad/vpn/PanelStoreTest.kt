package ir.mohammad.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.PanelApi
import ir.mohammad.vpn.data.PanelStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "I closed the app and it asked for the token again" must never be true: the panel a device created
 * has to survive a process restart. This pins the round trip.
 */
@RunWith(AndroidJUnit4::class)
class PanelStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `a created panel survives a restart`() {
        PanelStore.clear(context)
        PanelStore.save(
            context,
            PanelApi.Account(
                panelUrl = "https://panel.example.com/panel",
                baseUrl = "https://panel.example.com",
                nodeUrl = "https://node.example.com",
                password = "secret",
                worker = "panel",
            ),
            "session-token",
        )

        val restored = PanelStore.account(context)
        assertNotNull("a saved panel must be readable after restart", restored)
        assertEquals("https://panel.example.com", restored!!.baseUrl)
        assertEquals("https://panel.example.com/panel", restored.panelUrl)
        assertEquals("https://node.example.com", restored.nodeUrl)
        assertEquals("panel", restored.worker)
        assertEquals("session-token", PanelStore.session(context))
    }

    @Test
    fun `clearing forgets the panel`() {
        PanelStore.save(
            context,
            PanelApi.Account("https://a/panel", "https://a", null, "p", "a"),
            "s",
        )
        PanelStore.clear(context)
        assertNull(PanelStore.account(context))
        assertNull(PanelStore.session(context))
    }
}
