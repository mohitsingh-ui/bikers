package com.ridesync.app.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.util.ArrayDeque
import java.util.Date

/**
 * App-wide crash safety net. Goal: the app should never be killed by an
 * unexpected exception.
 *
 * Two layers:
 *
 *  1. Background threads (audio capture/playback, socket receive loops,
 *     coroutine dispatchers): an uncaught exception is logged and swallowed —
 *     that one worker thread ends, but the process keeps running. A dropped
 *     voice thread is far better than a closed app.
 *
 *  2. The main (UI) thread: we run the main Looper inside our own loop, so an
 *     exception thrown while handling a UI event or a Compose frame is caught
 *     and the UI simply keeps going instead of the app closing.
 *
 * Every crash is written to a small file under filesDir/crashes and mirrored
 * into the in-memory log, so a "Send crash report" action can hand it back for
 * a proper fix. If crashes come in a tight burst (a genuine unrecoverable
 * loop), we stop swallowing and let the system handle it rather than freeze the
 * device.
 */
object CrashGuard {

    @Volatile private var installed = false
    private var appContext: Context? = null

    private const val KEEP_FILES = 6
    private const val BURST_WINDOW_MS = 4000L
    private const val BURST_LIMIT = 12
    private val recentCrashes = ArrayDeque<Long>()

    fun install(context: Context) {
        if (installed) return
        installed = true
        appContext = context.applicationContext

        val system = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            safeRecord(thread, throwable)
            // Swallow by default so the app is never force-closed. The one
            // exception is a rapid burst on the main thread (a genuine
            // unrecoverable loop), which we hand to the system so the device
            // isn't left spinning.
            val isMain = thread === Looper.getMainLooper().thread
            if (isMain && isBursting()) {
                system?.uncaughtException(thread, throwable)
            }
            // Otherwise: a background thread ends, or a stray main-thread
            // coroutine failure is logged — either way the app lives on.
        }

        // Keep the UI alive across exceptions by owning the main message loop.
        Handler(Looper.getMainLooper()).post {
            while (true) {
                try {
                    Looper.loop()
                    return@post // normal quit
                } catch (t: Throwable) {
                    safeRecord(Looper.getMainLooper().thread, t)
                    if (isBursting()) {
                        // Unrecoverable rapid loop — stop swallowing.
                        throw t
                    }
                    // Otherwise resume the loop and carry on.
                }
            }
        }
    }

    private fun isBursting(): Boolean {
        val now = System.currentTimeMillis()
        synchronized(recentCrashes) {
            recentCrashes.addLast(now)
            while (recentCrashes.isNotEmpty() && now - recentCrashes.first() > BURST_WINDOW_MS) {
                recentCrashes.removeFirst()
            }
            return recentCrashes.size > BURST_LIMIT
        }
    }

    /** record() must itself never throw — it runs on the crash path. */
    private fun safeRecord(thread: Thread, t: Throwable) {
        try {
            val sw = StringWriter()
            t.printStackTrace(PrintWriter(sw))
            RLog.e(RLog.Cat.SESSION, "UNCAUGHT on '${thread.name}': ${t.javaClass.simpleName}: ${t.message}")
            val ctx = appContext ?: return
            val dir = File(ctx.filesDir, "crashes").apply { mkdirs() }
            val report = buildString {
                appendLine("RideSync crash report")
                appendLine("time  : ${Date()}")
                appendLine("thread: ${thread.name}")
                appendLine()
                append(sw.toString())
                appendLine()
                appendLine("--- recent logs ---")
                RLog.recent().takeLast(120).forEach { appendLine(it) }
            }
            File(dir, "crash-${System.currentTimeMillis()}.log").writeText(report)
            // Keep only the newest few reports.
            dir.listFiles()
                ?.sortedBy { it.name }
                ?.dropLast(KEEP_FILES)
                ?.forEach { runCatching { it.delete() } }
        } catch (_: Throwable) {
            // Never let the safety net itself crash.
        }
    }

    /** The most recent crash report text, if any — for a "Send crash report" action. */
    fun latestReport(context: Context): String? = try {
        File(context.filesDir, "crashes")
            .listFiles()
            ?.maxByOrNull { it.name }
            ?.readText()
    } catch (_: Throwable) {
        null
    }

    fun hasReport(context: Context): Boolean =
        runCatching { File(context.filesDir, "crashes").listFiles()?.isNotEmpty() == true }.getOrDefault(false)
}
