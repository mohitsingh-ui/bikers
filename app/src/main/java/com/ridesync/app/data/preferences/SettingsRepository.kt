package com.ridesync.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ridesync.app.domain.model.CommMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class AppTheme { DARK, LIGHT, SYSTEM }

enum class AccentChoice { EMBER, AMBER, COBALT }

enum class VoiceCodecChoice { OPUS, PCM }

/** Every user-tunable setting, with riding-safe defaults. */
data class Settings(
    // Communication
    val commMode: CommMode = CommMode.PUSH_TO_TALK,
    val voiceVolume: Float = 1.0f,
    val micSensitivity: Float = 1.0f,
    val noiseSuppression: Boolean = true,
    val echoCancellation: Boolean = true,
    val autoGainControl: Boolean = true,
    val vadThreshold: Float = 0.05f,
    val voiceCodec: VoiceCodecChoice = VoiceCodecChoice.OPUS,
    // Music
    val syncEnabled: Boolean = true,
    val duckingEnabled: Boolean = true,
    val duckLevel: Float = 0.25f,
    val duckFadeMs: Int = 1500,
    val duckHoldMs: Int = 1200,
    val hostOnlyMusic: Boolean = true,
    val driftSoftMs: Int = 120,
    val driftHardMs: Int = 300,
    val musicVolume: Float = 1.0f,
    // Ride
    val defaultRideName: String = "",
    val maxRiders: Int = 4,
    val hostMigrationEnabled: Boolean = true,
    val autoReconnect: Boolean = true,
    // Safety
    val rideModeAutoOn: Boolean = false,
    val emergencyEnabled: Boolean = true,
    val locationSharing: Boolean = false,
    val quickAlertsEnabled: Boolean = true,
    val spokenAlerts: Boolean = true,
    val hapticsEnabled: Boolean = true,
    // Appearance
    val theme: AppTheme = AppTheme.DARK,
    val accent: AccentChoice = AccentChoice.EMBER,
)

/**
 * DataStore-backed settings. Everything is exposed as one [Settings] flow so
 * engines (ducking, sync, voice) can react to changes live, mid-ride.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val COMM_MODE = stringPreferencesKey("comm_mode")
        val VOICE_VOLUME = floatPreferencesKey("voice_volume")
        val MIC_SENSITIVITY = floatPreferencesKey("mic_sensitivity")
        val NOISE_SUPPRESSION = booleanPreferencesKey("noise_suppression")
        val ECHO_CANCELLATION = booleanPreferencesKey("echo_cancellation")
        val AUTO_GAIN = booleanPreferencesKey("auto_gain")
        val VAD_THRESHOLD = floatPreferencesKey("vad_threshold")
        val VOICE_CODEC = stringPreferencesKey("voice_codec")
        val SYNC_ENABLED = booleanPreferencesKey("sync_enabled")
        val DUCKING_ENABLED = booleanPreferencesKey("ducking_enabled")
        val DUCK_LEVEL = floatPreferencesKey("duck_level")
        val DUCK_FADE_MS = intPreferencesKey("duck_fade_ms")
        val DUCK_HOLD_MS = intPreferencesKey("duck_hold_ms")
        val HOST_ONLY_MUSIC = booleanPreferencesKey("host_only_music")
        val DRIFT_SOFT_MS = intPreferencesKey("drift_soft_ms")
        val DRIFT_HARD_MS = intPreferencesKey("drift_hard_ms")
        val MUSIC_VOLUME = floatPreferencesKey("music_volume")
        val DEFAULT_RIDE_NAME = stringPreferencesKey("default_ride_name")
        val MAX_RIDERS = intPreferencesKey("max_riders")
        val HOST_MIGRATION = booleanPreferencesKey("host_migration")
        val AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val RIDE_MODE_AUTO = booleanPreferencesKey("ride_mode_auto")
        val EMERGENCY_ENABLED = booleanPreferencesKey("emergency_enabled")
        val LOCATION_SHARING = booleanPreferencesKey("location_sharing")
        val QUICK_ALERTS = booleanPreferencesKey("quick_alerts")
        val SPOKEN_ALERTS = booleanPreferencesKey("spoken_alerts")
        val HAPTICS = booleanPreferencesKey("haptics")
        val THEME = stringPreferencesKey("theme")
        val ACCENT = stringPreferencesKey("accent")
    }

    val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            val defaults = Settings()
            Settings(
                commMode = p[Keys.COMM_MODE]?.let { runCatching { CommMode.valueOf(it) }.getOrNull() }
                    ?: defaults.commMode,
                voiceVolume = p[Keys.VOICE_VOLUME] ?: defaults.voiceVolume,
                micSensitivity = p[Keys.MIC_SENSITIVITY] ?: defaults.micSensitivity,
                noiseSuppression = p[Keys.NOISE_SUPPRESSION] ?: defaults.noiseSuppression,
                echoCancellation = p[Keys.ECHO_CANCELLATION] ?: defaults.echoCancellation,
                autoGainControl = p[Keys.AUTO_GAIN] ?: defaults.autoGainControl,
                vadThreshold = p[Keys.VAD_THRESHOLD] ?: defaults.vadThreshold,
                voiceCodec = p[Keys.VOICE_CODEC]?.let { runCatching { VoiceCodecChoice.valueOf(it) }.getOrNull() }
                    ?: defaults.voiceCodec,
                syncEnabled = p[Keys.SYNC_ENABLED] ?: defaults.syncEnabled,
                duckingEnabled = p[Keys.DUCKING_ENABLED] ?: defaults.duckingEnabled,
                duckLevel = p[Keys.DUCK_LEVEL] ?: defaults.duckLevel,
                duckFadeMs = p[Keys.DUCK_FADE_MS] ?: defaults.duckFadeMs,
                duckHoldMs = p[Keys.DUCK_HOLD_MS] ?: defaults.duckHoldMs,
                hostOnlyMusic = p[Keys.HOST_ONLY_MUSIC] ?: defaults.hostOnlyMusic,
                driftSoftMs = p[Keys.DRIFT_SOFT_MS] ?: defaults.driftSoftMs,
                driftHardMs = p[Keys.DRIFT_HARD_MS] ?: defaults.driftHardMs,
                musicVolume = p[Keys.MUSIC_VOLUME] ?: defaults.musicVolume,
                defaultRideName = p[Keys.DEFAULT_RIDE_NAME] ?: defaults.defaultRideName,
                maxRiders = p[Keys.MAX_RIDERS] ?: defaults.maxRiders,
                hostMigrationEnabled = p[Keys.HOST_MIGRATION] ?: defaults.hostMigrationEnabled,
                autoReconnect = p[Keys.AUTO_RECONNECT] ?: defaults.autoReconnect,
                rideModeAutoOn = p[Keys.RIDE_MODE_AUTO] ?: defaults.rideModeAutoOn,
                emergencyEnabled = p[Keys.EMERGENCY_ENABLED] ?: defaults.emergencyEnabled,
                locationSharing = p[Keys.LOCATION_SHARING] ?: defaults.locationSharing,
                quickAlertsEnabled = p[Keys.QUICK_ALERTS] ?: defaults.quickAlertsEnabled,
                spokenAlerts = p[Keys.SPOKEN_ALERTS] ?: defaults.spokenAlerts,
                hapticsEnabled = p[Keys.HAPTICS] ?: defaults.hapticsEnabled,
                theme = p[Keys.THEME]?.let { runCatching { AppTheme.valueOf(it) }.getOrNull() }
                    ?: defaults.theme,
                accent = p[Keys.ACCENT]?.let { runCatching { AccentChoice.valueOf(it) }.getOrNull() }
                    ?: defaults.accent,
            )
        }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    suspend fun setCommMode(v: CommMode) = edit { it[Keys.COMM_MODE] = v.name }
    suspend fun setVoiceVolume(v: Float) = edit { it[Keys.VOICE_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setMicSensitivity(v: Float) = edit { it[Keys.MIC_SENSITIVITY] = v.coerceIn(0.5f, 2f) }
    suspend fun setNoiseSuppression(v: Boolean) = edit { it[Keys.NOISE_SUPPRESSION] = v }
    suspend fun setEchoCancellation(v: Boolean) = edit { it[Keys.ECHO_CANCELLATION] = v }
    suspend fun setAutoGainControl(v: Boolean) = edit { it[Keys.AUTO_GAIN] = v }
    suspend fun setVadThreshold(v: Float) = edit { it[Keys.VAD_THRESHOLD] = v.coerceIn(0.01f, 0.5f) }
    suspend fun setVoiceCodec(v: VoiceCodecChoice) = edit { it[Keys.VOICE_CODEC] = v.name }
    suspend fun setSyncEnabled(v: Boolean) = edit { it[Keys.SYNC_ENABLED] = v }
    suspend fun setDuckingEnabled(v: Boolean) = edit { it[Keys.DUCKING_ENABLED] = v }
    suspend fun setDuckLevel(v: Float) = edit { it[Keys.DUCK_LEVEL] = v.coerceIn(0f, 0.8f) }
    suspend fun setDuckFadeMs(v: Int) = edit { it[Keys.DUCK_FADE_MS] = v.coerceIn(200, 5000) }
    suspend fun setDuckHoldMs(v: Int) = edit { it[Keys.DUCK_HOLD_MS] = v.coerceIn(0, 5000) }
    suspend fun setHostOnlyMusic(v: Boolean) = edit { it[Keys.HOST_ONLY_MUSIC] = v }
    suspend fun setDriftSoftMs(v: Int) = edit { it[Keys.DRIFT_SOFT_MS] = v.coerceIn(40, 1000) }
    suspend fun setDriftHardMs(v: Int) = edit { it[Keys.DRIFT_HARD_MS] = v.coerceIn(100, 3000) }
    suspend fun setMusicVolume(v: Float) = edit { it[Keys.MUSIC_VOLUME] = v.coerceIn(0f, 1f) }
    suspend fun setDefaultRideName(v: String) = edit { it[Keys.DEFAULT_RIDE_NAME] = v.take(40) }
    suspend fun setMaxRiders(v: Int) = edit { it[Keys.MAX_RIDERS] = v.coerceIn(2, 8) }
    suspend fun setHostMigrationEnabled(v: Boolean) = edit { it[Keys.HOST_MIGRATION] = v }
    suspend fun setAutoReconnect(v: Boolean) = edit { it[Keys.AUTO_RECONNECT] = v }
    suspend fun setRideModeAutoOn(v: Boolean) = edit { it[Keys.RIDE_MODE_AUTO] = v }
    suspend fun setEmergencyEnabled(v: Boolean) = edit { it[Keys.EMERGENCY_ENABLED] = v }
    suspend fun setLocationSharing(v: Boolean) = edit { it[Keys.LOCATION_SHARING] = v }
    suspend fun setQuickAlertsEnabled(v: Boolean) = edit { it[Keys.QUICK_ALERTS] = v }
    suspend fun setSpokenAlerts(v: Boolean) = edit { it[Keys.SPOKEN_ALERTS] = v }
    suspend fun setHapticsEnabled(v: Boolean) = edit { it[Keys.HAPTICS] = v }
    suspend fun setTheme(v: AppTheme) = edit { it[Keys.THEME] = v.name }
    suspend fun setAccent(v: AccentChoice) = edit { it[Keys.ACCENT] = v.name }
}
