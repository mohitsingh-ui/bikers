package com.ridesync.app.domain.model

/** Which side of the star topology this device is on. */
enum class SessionRole { HOST, CLIENT }

/** Per-rider connection lifecycle as seen by the rest of the group. */
enum class RiderState { CONNECTED, RECONNECTING, DISCONNECTED }

/** Coarse link quality for the local device's connection to the ride. */
enum class ConnectionQuality { EXCELLENT, GOOD, WEAK, RECONNECTING, DISCONNECTED }

/** Voice communication mode. */
enum class CommMode { PUSH_TO_TALK, OPEN_INTERCOM }

/** Lifecycle of a ride session. */
enum class RidePhase { LOBBY, RIDING, ENDED }

/**
 * One rider in the group.
 *
 * @param id   Stable UUID for the rider's device (survives reconnects).
 * @param key  Compact integer id assigned by the host; used in binary UDP
 *             voice packets instead of the UUID to keep packets small.
 */
data class Rider(
    val id: String,
    val key: Int,
    val name: String,
    val isHost: Boolean,
    val state: RiderState = RiderState.CONNECTED,
    val batteryPercent: Int? = null,
    val isTalking: Boolean = false,
    val joinedAtMs: Long = 0L,
)

/** A ride as advertised/joined on the local network. */
data class RideInfo(
    val rideId: String,
    val name: String,
    val hostName: String,
    val pin: String,
    val maxRiders: Int,
    val riderCount: Int,
    val hostAddress: String?,
    val controlPort: Int,
    val hostOnlyMusic: Boolean = true,
)

enum class TrackSource { DEMO, LOCAL, EXTERNAL }

data class TrackInfo(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val source: TrackSource,
    val uri: String? = null,
)

/** Sync state shown on the music card. */
enum class SyncStatus { IDLE, SYNCED, RESYNCING, LOCAL_ONLY }

/** Everything the music UI needs, independent of host/client role. */
data class MusicUiState(
    val track: TrackInfo? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val syncStatus: SyncStatus = SyncStatus.IDLE,
    val canControl: Boolean = true,
    val playlist: List<TrackInfo> = emptyList(),
    val volume: Float = 0.8f,
    val trackAvailableLocally: Boolean = true,
)

/** One-tap alerts broadcast to the whole group. */
enum class QuickAlertKind(val emoji: String) {
    STOPPING("⚠️"),
    FUEL("⛽"),
    BREAK("☕"),
    SLOW_DOWN("🐢"),
    TURNING("↩️"),
    HAZARD("🚧"),
}

/** Local device's link to the ride network. */
data class ConnectionState(
    val quality: ConnectionQuality = ConnectionQuality.GOOD,
    val rttMs: Int? = null,
)

/** Shown when a ride ends; also stored in recent rides. */
data class RideSummary(
    val rideName: String,
    val startedAtMs: Long,
    val durationMs: Long,
    val riderCount: Int,
    val interruptions: Int,
    val musicSyncedMs: Long,
    val talkSeconds: Long,
)

/** Transient, user-visible things that happened in the session. */
sealed class SessionEvent {
    data class RiderJoined(val name: String) : SessionEvent()
    data class RiderLeft(val name: String) : SessionEvent()
    data class RiderReconnecting(val name: String) : SessionEvent()
    data class RiderReconnected(val name: String) : SessionEvent()
    data class HostChanged(val name: String) : SessionEvent()
    data object HostLost : SessionEvent()
    data class QuickAlertReceived(val kind: QuickAlertKind, val riderName: String) : SessionEvent()
    data class EmergencyReceived(
        val riderName: String,
        val atMs: Long,
        val latitude: Double? = null,
        val longitude: Double? = null,
    ) : SessionEvent()

    data object Resyncing : SessionEvent()
    data class RideEnded(val summary: RideSummary) : SessionEvent()
    data class Info(val message: String) : SessionEvent()
}
