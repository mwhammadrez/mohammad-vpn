package ir.mohammad.vpn

import android.app.Application
import ir.mohammad.vpn.surfaces.SurfaceUpdater

/**
 * Starts the widget/tile state pump once per process.
 *
 * Nothing else happens here: the bundle is still loaded by the Activity, so a cold process can show
 * a surface without starting a tunnel or reading a secret.
 */
class MohammadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SurfaceUpdater.start(this)
    }
}
