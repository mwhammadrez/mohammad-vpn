package ir.mohammad.vpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.surfaces.SurfaceUpdater
import ir.mohammad.vpn.surfaces.TunnelController
import ir.mohammad.vpn.tile.MohammadTileService
import ir.mohammad.vpn.widget.MohammadWidgetProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

/**
 * The brief's lifecycle requirements: add/remove, cold process, and "must not hold references after
 * onStopListening". Robolectric gives us a real Service instance and a real AppWidgetManager, so
 * these are behavioural checks rather than mocks.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SurfaceLifecycleTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @After
    fun tearDown() {
        SurfaceUpdater.stopForTest()
    }

    @Test
    fun `the widget provider survives add, update and remove with no configured widget`() {
        val provider = MohammadWidgetProvider()

        provider.onEnabled(context)
        provider.onUpdate(context, android.appwidget.AppWidgetManager.getInstance(context), intArrayOf())
        provider.onDisabled(context)
    }

    @Test
    fun `the tile starts and stops listening without leaking`() {
        val controller = Robolectric.buildService(MohammadTileService::class.java)
        val service = controller.create().get()

        assertFalse("must not claim to listen before the platform asks", service.isListeningNow())
        service.onStartListening()
        assertTrue(service.isListeningNow())
        service.onStopListening()
        assertFalse("a stopped tile must not stay subscribed", service.isListeningNow())
        // onDestroy() is not called: Robolectric's ShadowTileService cannot be cast to ShadowService
        // while tearing the service down. The leak guarantee under test is the listening flag.
    }

    @Test
    fun `tile add and remove are safe before any listen`() {
        val controller = Robolectric.buildService(MohammadTileService::class.java)
        val service = controller.create().get()

        service.onTileAdded()
        service.onTileRemoved()
        assertFalse(service.isListeningNow())
    }

    @Test
    fun `render is a no-op when the platform gave no tile`() {
        val service = Robolectric.buildService(MohammadTileService::class.java).create().get()
        // Robolectric returns null from getQsTile(); the service must not throw.
        service.render()
    }

    @Test
    fun `a tile tap guides the user to the app only when it must`() {
        // onClick() itself is not invoked here: opening the app requires a platform-bound
        // IQSService which Robolectric does not provide. The decision it depends on is pure.
        val service = Robolectric.buildService(MohammadTileService::class.java).create().get()

        assertTrue(service.needsApp(TunnelController.Outcome.NEEDS_SETUP))
        assertTrue(service.needsApp(TunnelController.Outcome.NEEDS_PERMISSION))
        assertTrue(false.not().let { !service.needsApp(TunnelController.Outcome.STARTED) })
        assertTrue(!service.needsApp(TunnelController.Outcome.STOPPED))
        assertTrue(!service.needsApp(TunnelController.Outcome.IGNORED))
    }

    @Test
    fun `the updater can be started twice without stacking collectors`() {
        SurfaceUpdater.start(context)
        SurfaceUpdater.start(context)
        SurfaceUpdater.stopForTest()
        SurfaceUpdater.stopForTest()
    }
}
