package ir.mohammad.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.surfaces.SurfaceState
import ir.mohammad.vpn.surfaces.SurfaceStateMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * AC-W1 / AC-W3. The widget and the tile never see [TunnelState] directly: they consume
 * [SurfaceState]. That is the seam M4 plugs its ten-state machine into — extending the mapper is a
 * one-file change, and this test is the contract it must keep.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SurfaceStateMapperTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun map(
        state: TunnelState,
        hasProfile: Boolean = true,
        permissionGranted: Boolean = true,
    ) = SurfaceStateMapper.map(state, hasProfile, permissionGranted)

    @Test
    fun `every current state maps to exactly one surface state`() {
        val all = listOf(
            TunnelState.Disconnected,
            TunnelState.Connecting,
            TunnelState.Connected,
            TunnelState.Disconnecting,
            TunnelState.Failed(TunnelError.CoreStartFailed),
            TunnelState.Failed(TunnelError.NoProfileSelected),
        )
        val mapped = all.map { map(it) }
        assertEquals(all.size, mapped.size)
        assertTrue(mapped.all { it in SurfaceState.entries })
    }

    @Test
    fun `disconnected is idle and tappable`() {
        val s = map(TunnelState.Disconnected)
        assertEquals(SurfaceState.IDLE, s)
        assertTrue(s.canToggle)
        assertFalse(s.tileActive)
        assertTrue(s.tileAvailable)
    }

    @Test
    fun `connecting is a transient state that cannot be toggled`() {
        val s = map(TunnelState.Connecting)
        assertEquals(SurfaceState.CONNECTING, s)
        assertFalse("a second tap during connect must not start a second tunnel", s.canToggle)
        assertFalse(s.tileActive)
    }

    @Test
    fun `connected is the only active surface state`() {
        val s = map(TunnelState.Connected)
        assertEquals(SurfaceState.CONNECTED, s)
        assertTrue(s.tileActive)
        assertTrue(s.canToggle)
        assertEquals(1, SurfaceState.entries.count { it.tileActive })
    }

    @Test
    fun `disconnecting is not tappable`() {
        val s = map(TunnelState.Disconnecting)
        assertEquals(SurfaceState.DISCONNECTING, s)
        assertFalse(s.canToggle)
    }

    @Test
    fun `any failure maps to failed and stays tappable for a retry`() {
        TunnelError.entries.forEach { error ->
            val s = map(TunnelState.Failed(error))
            assertEquals("failure $error must map to FAILED", SurfaceState.FAILED, s)
            assertTrue("the user must be able to retry from the widget", s.canToggle)
        }
    }

    @Test
    fun `no profile means the user must open the app`() {
        val s = map(TunnelState.Disconnected, hasProfile = false)
        assertEquals(SurfaceState.NEEDS_APP, s)
        assertFalse("there is nothing to toggle without a profile", s.canToggle)
        assertFalse("the tile must not pretend it can connect", s.tileAvailable)
    }

    @Test
    fun `missing vpn permission is distinguishable from idle`() {
        val s = map(TunnelState.Disconnected, permissionGranted = false)
        assertEquals(SurfaceState.PERMISSION_NEEDED, s)
        assertFalse(s.canToggle)
        assertFalse(s.tileAvailable)
        assertNotEquals(map(TunnelState.Disconnected), s)
    }

    @Test
    fun `no profile wins over missing permission so the message is actionable`() {
        val s = map(TunnelState.Disconnected, hasProfile = false, permissionGranted = false)
        assertEquals(SurfaceState.NEEDS_APP, s)
    }

    @Test
    fun `labels are short enough for a 2x1 widget`() {
        SurfaceState.entries.forEach {
            val title = it.title(context)
            val subtitle = it.subtitle(context)
            assertTrue("${it.name} title is too long: '$title'", title.length <= 24)
            assertTrue("${it.name} subtitle is too long: '$subtitle'", subtitle.length <= 32)
            assertTrue("${it.name} title must not be blank", title.isNotBlank())
            assertTrue("${it.name} action must not be blank", it.action(context).isNotBlank())
        }
    }

    @Test
    fun `every surface state has a distinct icon and colour role`() {
        val icons = SurfaceState.entries.map { it.iconRes }
        assertEquals("icons must be distinguishable", icons.size, icons.toSet().size)
        SurfaceState.entries.forEach { assertTrue("${it.name} needs a colour role", it.colorRole.name.isNotEmpty()) }
    }

    @Test
    fun `every surface state has a content description for accessibility`() {
        SurfaceState.entries.forEach {
            val description = it.description(context)
            assertTrue("${it.name} needs a description", description.isNotBlank())
            assertTrue("${it.name} description must differ from its title", description != it.title(context))
        }
    }
}
