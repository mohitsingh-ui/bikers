package com.ridesync.app.networking.transport

import com.ridesync.app.core.RLog
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketException

/**
 * Thin UDP socket wrapper for the voice/clock/discovery planes.
 *
 * Receive runs on a dedicated thread (voice frames arrive every 20 ms; a
 * plain thread beats coroutine dispatch latency and never suspends). The
 * receive callback gets the raw buffer + length, valid only during the call —
 * decode immediately, don't retain.
 */
class UdpChannel(bindPort: Int = 0, enableBroadcast: Boolean = false) {

    private val socket: DatagramSocket = DatagramSocket(bindPort).apply {
        reuseAddress = true
        if (enableBroadcast) broadcast = true
        receiveBufferSize = 1 shl 17
        sendBufferSize = 1 shl 16
    }

    @Volatile
    private var receiveThread: Thread? = null

    @Volatile
    var closed = false
        private set

    val localPort: Int get() = socket.localPort

    /** Fire-and-forget send; UDP loss is handled by the codec/jitter layers. */
    fun send(data: ByteArray, length: Int, target: InetSocketAddress) {
        if (closed) return
        try {
            socket.send(DatagramPacket(data, length, target))
        } catch (e: IOException) {
            RLog.d(RLog.Cat.NETWORK, "udp send failed: ${e.message}")
        }
    }

    fun send(data: ByteArray, target: InetSocketAddress) = send(data, data.size, target)

    fun sendBroadcast(data: ByteArray, port: Int) {
        try {
            send(data, data.size, InetSocketAddress(InetAddress.getByName("255.255.255.255"), port))
        } catch (e: IOException) {
            RLog.d(RLog.Cat.NETWORK, "broadcast failed: ${e.message}")
        }
    }

    /** Starts the receive loop. Call at most once. */
    fun startReceiving(name: String, onPacket: (data: ByteArray, length: Int, from: InetSocketAddress) -> Unit) {
        check(receiveThread == null) { "receive loop already running" }
        val thread = Thread({
            val buffer = ByteArray(MAX_DATAGRAM)
            val packet = DatagramPacket(buffer, buffer.size)
            while (!closed) {
                try {
                    packet.setData(buffer, 0, buffer.size)
                    socket.receive(packet)
                    val from = packet.socketAddress as? InetSocketAddress ?: continue
                    onPacket(buffer, packet.length, from)
                } catch (_: SocketException) {
                    // closed
                    return@Thread
                } catch (e: IOException) {
                    if (!closed) RLog.d(RLog.Cat.NETWORK, "udp recv error: ${e.message}")
                } catch (e: Exception) {
                    // A handler bug must not kill the receive loop.
                    RLog.e(RLog.Cat.NETWORK, "udp handler error", e)
                }
            }
        }, "RideSync-Udp-$name")
        thread.isDaemon = true
        thread.priority = Thread.MAX_PRIORITY - 1
        receiveThread = thread
        thread.start()
    }

    fun close() {
        closed = true
        runCatching { socket.close() }
    }

    companion object {
        const val MAX_DATAGRAM = 2048
    }
}
