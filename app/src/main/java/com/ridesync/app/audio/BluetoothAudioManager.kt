package com.ridesync.app.audio

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import com.ridesync.app.core.RLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** What the app knows about the current audio output route. */
data class AudioRouteState(
    val outputName: String = "Phone Speaker",
    val isBluetooth: Boolean = false,
    val hasBluetoothMic: Boolean = false,
    val outputType: OutputType = OutputType.SPEAKER,
) {
    enum class OutputType { SPEAKER, BLUETOOTH_A2DP, BLUETOOTH_SCO, WIRED }
}

/**
 * Bluetooth / audio routing.
 *
 * Important real-world limitation the spec calls out: many Bluetooth headsets
 * cannot do high-quality A2DP music **and** microphone at the same time —
 * enabling the mic (HFP/SCO) collapses both directions to narrowband. RideSync
 * handles this honestly:
 *
 *  - If the headset exposes a BT mic, we use `MODE_IN_COMMUNICATION` + SCO so
 *    voice works, accepting narrowband audio for music while connected.
 *  - We surface [hasBluetoothMic] so the UI can warn when only A2DP is present
 *    (music-only earbuds): voice will fall back to the phone mic.
 *
 * We do NOT claim every headset supports simultaneous hi-fi music + mic.
 */
class BluetoothAudioManager(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val _route = MutableStateFlow(AudioRouteState())
    val route: StateFlow<AudioRouteState> = _route

    @Volatile
    private var scoStarted = false

    /** Recompute the current route from the device list. */
    fun refresh() {
        val devices = try {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        } catch (e: Exception) {
            RLog.w(RLog.Cat.BT, "device query failed", e)
            emptyArray()
        }

        var state = AudioRouteState()
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> {
                    state = state.copy(
                        outputName = deviceName(device) ?: "Bluetooth Audio",
                        isBluetooth = true,
                        outputType = AudioRouteState.OutputType.BLUETOOTH_A2DP,
                    )
                }

                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> {
                    state = state.copy(
                        outputName = deviceName(device) ?: "Bluetooth Headset",
                        isBluetooth = true,
                        hasBluetoothMic = true,
                        outputType = AudioRouteState.OutputType.BLUETOOTH_SCO,
                    )
                }

                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> {
                    if (!state.isBluetooth) {
                        state = state.copy(
                            outputName = deviceName(device) ?: "Wired Headset",
                            outputType = AudioRouteState.OutputType.WIRED,
                            hasBluetoothMic = device.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                                device.type == AudioDeviceInfo.TYPE_USB_HEADSET,
                        )
                    }
                }
            }
        }

        // A device advertising a BT input mic means HFP is available.
        if (state.isBluetooth && !state.hasBluetoothMic) {
            state = state.copy(hasBluetoothMic = hasBluetoothInput())
        }

        _route.value = state
        RLog.d(RLog.Cat.BT, "route: ${state.outputName} bt=${state.isBluetooth} mic=${state.hasBluetoothMic}")
    }

    fun hasBluetoothOutput(): Boolean = _route.value.isBluetooth

    fun hasHeadsetForRiding(): Boolean {
        val r = _route.value
        return r.isBluetooth || r.outputType == AudioRouteState.OutputType.WIRED
    }

    /**
     * Configure the audio session for two-way voice. Starts Bluetooth SCO when
     * a BT mic is present. Returns the mode actually established.
     */
    fun startCommunicationMode(): Boolean {
        return try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            val route = _route.value
            if (route.hasBluetoothMic && route.isBluetooth) {
                startBluetoothSco()
            }
            true
        } catch (e: Exception) {
            RLog.w(RLog.Cat.BT, "communication mode failed", e)
            false
        }
    }

    fun stopCommunicationMode() {
        try {
            stopBluetoothSco()
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (e: Exception) {
            RLog.w(RLog.Cat.BT, "reset audio mode failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun startBluetoothSco() {
        if (scoStarted) return
        try {
            audioManager.isBluetoothScoOn = true
            audioManager.startBluetoothSco()
            scoStarted = true
            RLog.i(RLog.Cat.BT, "Bluetooth SCO started (voice)")
        } catch (e: Exception) {
            RLog.w(RLog.Cat.BT, "SCO start failed", e)
        }
    }

    @Suppress("DEPRECATION")
    private fun stopBluetoothSco() {
        if (!scoStarted) return
        try {
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
        } catch (_: Exception) {
        } finally {
            scoStarted = false
        }
    }

    private fun hasBluetoothInput(): Boolean = try {
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    } catch (_: Exception) {
        false
    }

    private fun deviceName(device: AudioDeviceInfo): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) device.productName?.toString() else null
}
