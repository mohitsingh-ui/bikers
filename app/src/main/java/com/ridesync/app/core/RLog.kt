package com.ridesync.app.core

import android.util.Log
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Structured, category-tagged logging for RideSync.
 *
 * Rules:
 *  - Never log voice payloads or anything derived from microphone audio.
 *  - Keep messages short; these run on hot paths.
 *
 * A small in-memory ring buffer keeps the most recent lines so a debug screen
 * (or a bug report) can show them without touching disk.
 */
object RLog {

    enum class Cat(val tag: String) {
        NETWORK("RS/NETWORK"),
        DISCOVERY("RS/DISCOVERY"),
        VOICE("RS/VOICE"),
        AUDIO("RS/AUDIO"),
        MUSIC("RS/MUSIC"),
        BT("RS/BLUETOOTH"),
        CONN("RS/CONNECTION"),
        SESSION("RS/SESSION"),
        UI("RS/UI"),
    }

    private const val RING_CAPACITY = 400
    private val ring = ArrayDeque<String>(RING_CAPACITY)
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Toggled off in Ride Mode if needed; verbose logs are debug-only anyway. */
    @Volatile
    var verbose: Boolean = true

    fun d(cat: Cat, message: String) {
        if (verbose) Log.d(cat.tag, message)
        remember(cat, "D", message)
    }

    fun i(cat: Cat, message: String) {
        Log.i(cat.tag, message)
        remember(cat, "I", message)
    }

    fun w(cat: Cat, message: String, error: Throwable? = null) {
        if (error != null) Log.w(cat.tag, message, error) else Log.w(cat.tag, message)
        remember(cat, "W", message + (error?.let { " (${it.message})" } ?: ""))
    }

    fun e(cat: Cat, message: String, error: Throwable? = null) {
        if (error != null) Log.e(cat.tag, message, error) else Log.e(cat.tag, message)
        remember(cat, "E", message + (error?.let { " (${it.message})" } ?: ""))
    }

    private fun remember(cat: Cat, level: String, message: String) {
        val line = "${timeFormat.format(Date())} $level [${cat.name}] $message"
        synchronized(ring) {
            if (ring.size >= RING_CAPACITY) ring.removeFirst()
            ring.addLast(line)
        }
    }

    /** Snapshot of recent log lines, newest last. */
    fun recent(): List<String> = synchronized(ring) { ring.toList() }
}
