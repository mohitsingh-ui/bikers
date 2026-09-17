package com.ridesync.app.domain.session

import android.content.Context
import com.ridesync.app.audio.BluetoothAudioManager
import com.ridesync.app.audio.DuckingController
import com.ridesync.app.audio.Haptics
import com.ridesync.app.audio.Tones
import com.ridesync.app.audio.VoiceEngine
import com.ridesync.app.core.BatteryReader
import com.ridesync.app.data.preferences.ProfileRepository
import com.ridesync.app.data.preferences.RecentRidesRepository
import com.ridesync.app.data.preferences.Settings
import com.ridesync.app.data.preferences.SettingsRepository
import com.ridesync.app.music.MusicController
import com.ridesync.app.networking.ClockSync
import com.ridesync.app.networking.NetworkMonitor

/**
 * Shared, process-wide dependencies a live session needs. Built once in the
 * application container and handed to whichever session (host/client) is
 * active, so engines aren't rebuilt on every screen.
 */
class SessionEnvironment(
    val appContext: Context,
    val settingsRepository: SettingsRepository,
    val profileRepository: ProfileRepository,
    val recentRidesRepository: RecentRidesRepository,
) {
    val networkMonitor = NetworkMonitor(appContext)
    val bluetooth = BluetoothAudioManager(appContext)
    val haptics = Haptics(appContext)
    val tones = Tones()
    val battery = BatteryReader(appContext)
    val ducking = DuckingController()
    val clock = ClockSync()

    @Volatile
    var settings: Settings = Settings()

    fun buildVoiceEngine(
        onFrame: (ByteArray, Int, Boolean, Int) -> Unit,
    ): VoiceEngine = VoiceEngine(onFrame)

    fun buildMusicController(): MusicController = MusicController(appContext, ducking)
}
