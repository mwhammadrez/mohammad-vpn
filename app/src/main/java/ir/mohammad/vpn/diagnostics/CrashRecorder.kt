package ir.mohammad.vpn.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists the last uncaught exception so the next launch can show what killed the
 * process. A crash inside a native bridge takes the whole app down with it; without
 * this file the only evidence is a tombstone the operator cannot reach.
 */
object CrashRecorder {

    private const val FILE = "last-crash.txt"

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val body = buildString {
            appendLine("time: $stamp")
            appendLine("thread: ${thread.name}")
            appendLine("type: ${error.javaClass.name}")
            appendLine("message: ${error.message}")
            appendLine()
            appendLine(error.stackTraceToString().take(8_000))
        }
        File(context.filesDir, FILE).writeText(body)
    }

    /** Returns the recorded crash and clears it, or null when the last run was clean. */
    fun take(context: Context): String? {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull()
        runCatching { file.delete() }
        return text?.takeIf { it.isNotBlank() }
    }
}
