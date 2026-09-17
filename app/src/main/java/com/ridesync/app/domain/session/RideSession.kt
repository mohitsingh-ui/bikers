package com.ridesync.app.domain.session

import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.ConnectionState
import com.ridesync.app.domain.model.MusicUiState
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.model.RideInfo
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.SessionEvent
import com.ridesync.app.domain.model.SessionRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Live, observable state of the whole ride, independent of host/client role. */
data class RideState(
    val role: SessionRole = SessionRole.CLIENT,
    val phase: RidePhase = RidePhase.LOBBY,
    val ride: RideInfo? = null,
    val riders: List<Rider> = emptyList(),
    val selfId: String = "",
    val commMode: CommMode = CommMode.PUSH_TO_TALK,
    val isTransmitting: Boolean = false,
    val talkingRiderNames: List<String> = emptyList(),
    val connection: ConnectionState = ConnectionState(),
    val music: MusicUiState = MusicUiState(),
    val musicDucked: Boolean = false,
    val elapsedMs: Long = 0L,
    val rideModeActive: Boolean = false,
    val phoneAudioSharing: Boolean = false,
) {
    val self: Rider? get() = riders.firstOrNull { it.id == selfId }
    val isHost: Boolean get() = role == SessionRole.HOST
    val connectedCount: Int get() = riders.count { it.state == com.ridesync.app.domain.model.RiderState.CONNECTED }
}

/**
 * The single interface the UI/ViewModel talks to, whether this device is
 * hosting, joined as a client, or running the on-device simulator. Keeps the
 * UI identical across all three.
 */
interface RideSession {
    val state: StateFlow<RideState>
    val events: Flow<SessionEvent>

    // Voice
    fun setPushToTalk(pressed: Boolean)
    fun setCommMode(mode: CommMode)

    // Music (no-ops or requests when the caller can't control)
    fun musicPlay()
    fun musicPause()
    fun musicNext()
    fun musicPrevious()
    fun musicResync()
    fun musicSelect(trackId: String)
    fun setMusicVolume(volume: Float)

    // Per-rider local mix
    fun setRiderMuted(riderId: String, muted: Boolean)
    fun setRiderVolume(riderId: String, volume: Float)

    // Safety
    fun sendQuickAlert(kind: QuickAlertKind)
    fun triggerEmergency()
    fun setRideMode(active: Boolean)

    // Lifecycle
    fun startRide()
    fun endRide()
    fun leave()
}
