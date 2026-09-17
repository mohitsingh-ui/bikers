package com.ridesync.app.networking.transport

import com.ridesync.app.core.RLog
import com.ridesync.app.networking.protocol.Envelope
import com.ridesync.app.networking.protocol.Wire
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * One reliable control-plane connection: newline-delimited JSON envelopes over
 * TCP. Thread-safe writes; blocking reads intended to run on a dedicated
 * coroutine on Dispatchers.IO (or a thread).
 *
 * Read timeout acts as a dead-link watchdog: heartbeats flow every few
 * seconds, so a silent 15 s means the link is gone.
 */
class TcpJsonConnection(private val socket: Socket) {

    private val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8))
    private val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
    private val writeLock = Any()

    @Volatile
    var closed = false
        private set

    val remoteAddress: String =
        (socket.remoteSocketAddress as? InetSocketAddress)?.address?.hostAddress ?: "?"

    init {
        try {
            socket.tcpNoDelay = true
            socket.keepAlive = true
            socket.soTimeout = READ_TIMEOUT_MS
        } catch (e: IOException) {
            RLog.w(RLog.Cat.NETWORK, "socket option failed", e)
        }
    }

    /** Blocking write. Returns false when the link is dead. */
    fun send(envelope: Envelope): Boolean {
        if (closed) return false
        val line = Wire.encode(envelope)
        return try {
            synchronized(writeLock) {
                writer.write(line)
                writer.write("\n")
                writer.flush()
            }
            true
        } catch (e: IOException) {
            RLog.d(RLog.Cat.NETWORK, "send failed to $remoteAddress: ${e.message}")
            false
        }
    }

    /**
     * Blocking read of the next valid envelope. Skips malformed lines.
     * Returns null when the connection is closed, broken, or silent for
     * longer than the watchdog timeout.
     */
    fun readNext(): Envelope? {
        while (!closed) {
            val line = try {
                reader.readLine() ?: return null
            } catch (_: SocketTimeoutException) {
                return null
            } catch (_: IOException) {
                return null
            }
            val env = Wire.decode(line)
            if (env != null) return env
            RLog.w(RLog.Cat.NETWORK, "dropped malformed control line (${line.length} chars)")
        }
        return null
    }

    fun close() {
        closed = true
        runCatching { socket.close() }
    }

    companion object {
        const val CONNECT_TIMEOUT_MS = 4000
        const val READ_TIMEOUT_MS = 15000

        /** Blocking connect; returns null on failure. */
        fun connect(host: String, port: Int, timeoutMs: Int = CONNECT_TIMEOUT_MS): TcpJsonConnection? {
            return try {
                val socket = Socket()
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                TcpJsonConnection(socket)
            } catch (e: IOException) {
                RLog.d(RLog.Cat.NETWORK, "connect $host:$port failed: ${e.message}")
                null
            }
        }
    }
}
