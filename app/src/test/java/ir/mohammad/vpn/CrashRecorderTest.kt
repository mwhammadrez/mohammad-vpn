package ir.mohammad.vpn

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.mohammad.vpn.diagnostics.CrashRecorder
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CrashRecorderTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun `no crash yields no report`() {
        CrashRecorder.take(context)
        assertNull("clean run must not surface a card", CrashRecorder.take(context))
    }

    @Test
    fun `a crash is persisted and consumed once`() {
        val handler = Thread.getDefaultUncaughtExceptionHandler()
        val original = handler
        try {
            CrashRecorder.install(context)
            val crashHandler = Thread.getDefaultUncaughtExceptionHandler()
            assertNotNull("install must wrap the handler", crashHandler)
            // Invoke the wrapper directly instead of killing the test JVM.
            runCatching { crashHandler!!.uncaughtException(Thread.currentThread(), IllegalStateException("boom")) }
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
        val report = CrashRecorder.take(context)
        assertNotNull("crash must be recorded", report)
        assertTrue(report!!.contains("IllegalStateException"))
        assertNull("report is consumed by reading", CrashRecorder.take(context))
    }
}
