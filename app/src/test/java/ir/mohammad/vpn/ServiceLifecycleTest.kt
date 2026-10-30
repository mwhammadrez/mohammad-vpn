package ir.mohammad.vpn

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.data.Tunnel
import ir.mohammad.vpn.data.TunnelError
import ir.mohammad.vpn.data.TunnelState
import ir.mohammad.vpn.vpn.MohammadVpnService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The service shares the app process, so any exception here closes the app.
 * These tests exercise the lifecycle paths that do not need the native core.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ServiceLifecycleTest {

    private fun start(action: String): MohammadVpnService {
        val controller = Robolectric.buildService(MohammadVpnService::class.java).create()
        val service = controller.get()
        service.onStartCommand(Intent(action).setClass(service, MohammadVpnService::class.java), 0, 1)
        return service
    }

    @Test
    fun `disconnect on an idle service does not throw`() {
        Tunnel.update(TunnelState.Disconnected)
        val service = start(MohammadVpnService.ACTION_DISCONNECT)
        assertEquals(TunnelState.Disconnected, Tunnel.state.value)
        service.onDestroy()
    }

    @Test
    fun `disconnect twice is still safe`() {
        Tunnel.update(TunnelState.Disconnected)
        val service = start(MohammadVpnService.ACTION_DISCONNECT)
        service.onStartCommand(
            Intent(MohammadVpnService.ACTION_DISCONNECT).setClass(service, MohammadVpnService::class.java),
            0,
            2,
        )
        assertEquals(TunnelState.Disconnected, Tunnel.state.value)
        service.onDestroy()
    }

    @Test
    fun `connect without a profile fails cleanly`() {
        Tunnel.selected = null
        Tunnel.update(TunnelState.Disconnected)
        val service = start(MohammadVpnService.ACTION_CONNECT)
        // connect() runs on the service worker, so wait for it to settle.
        val deadline = System.currentTimeMillis() + 3_000
        while (Tunnel.state.value !is TunnelState.Failed && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        val state = Tunnel.state.value
        assertTrue("expected Failed, got $state", state is TunnelState.Failed)
        assertEquals(TunnelError.NoProfileSelected, (state as TunnelState.Failed).reason)
        service.onDestroy()
    }

    @Test
    fun `destroy without a tunnel is safe`() {
        Tunnel.update(TunnelState.Disconnected)
        val controller = Robolectric.buildService(MohammadVpnService::class.java).create()
        controller.get().onDestroy()
        assertEquals(TunnelState.Disconnected, Tunnel.state.value)
    }
}
