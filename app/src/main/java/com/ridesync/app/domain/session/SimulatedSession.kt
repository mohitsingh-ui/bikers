package com.ridesync.app.domain.session

import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.ConnectionQuality
import com.ridesync.app.domain.model.ConnectionState
import com.ridesync.app.domain.model.MusicUiState
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.model.RideInfo
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.RiderState
import com.ridesync.app.domain.model.SessionEvent
import com.ridesync.app.domain.model.SessionRole
import com.ridesync.app.domain.model.SyncStatus
import com.ridesync.app.domain.model.TrackInfo
import com.ridesync.app.domain.model.TrackSource
import com.ridesync.app.music.DemoTrackGenerator
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * A fully in-memory session with scripted simulated riders. Lets a single phone
 * demonstrate the entire ride experience — talking indicators, ducking, quick
 * alerts, drops/reconnects, music sync and host migration — with no network and
 * no other devices.
 *
 * Real audio engines are intentionally NOT started here (there's no one to talk
 * to); it drives the same [RideState] the live sessions produce, so every
 * screen renders identically. Which simulated riders are present is chosen up
 * front in Developer Mode.
 */
class SimulatedSession(
    private val scope: CoroutineScope,
    private val hostName: String,
    private val simulatedRiderNames: List<String>,
    private val asHost: Boolean = true,
) : RideSession {

    private val selfId = "sim-self"
    private val playlist: List<TrackInfo> = DemoTrackGenerator.playlistMetadata()

    private val allRiders: List<Rider> = buildList {
        add(Rider(selfId, 0, hostName, isHost = asHost, batteryPercent = 88, joinedAtMs = 0))
        simulatedRiderNames.forEachIndexed { i, name ->
            add(
                Rider(
                    id = "sim-$i",
                    key = i + 1,
                    name = name,
                    isHost = false,
                    batteryPercent = listOf(64, 51, 91, 77, 43).getOrElse(i) { 70 },
                    joinedAtMs = (i + 1) * 1000L,
                ),
            )
        }
    }

    private var trackIndex = 0
    private var musicPlaying = false
    private var musicPositionMs = 0L

    private val _state = MutableStateFlow(
        RideState(
            role = if (asHost) SessionRole.HOST else SessionRole.CLIENT,
            phase = RidePhase.LOBBY,
            selfId = selfId,
            commMode = CommMode.PUSH_TO_TALK,
            ride = RideInfo(
                rideId = "SIMULATION",
                name = "Simulated Ride",
                hostName = hostName,
                pin = "0000",
                maxRiders = allRiders.size.coerceAtLeast(4),
                riderCount = allRiders.size,
                hostAddress = "127.0.0.1",
                controlPort = 0,
            ),
            riders = allRiders,
            connection = ConnectionState(ConnectionQuality.EXCELLENT, 12),
            music = MusicUiState(
                track = playlist.firstOrNull(),
                playlist = playlist,
                durationMs = playlist.firstOrNull()?.durationMs ?: 0L,
                canControl = asHost,
                syncStatus = SyncStatus.SYNCED,
                trackAvailableLocally = false,
            ),
        ),
    )
    override val state: StateFlow<RideState> = _state

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SessionEvent> = _events

    private var scriptJob: Job? = null
    private var musicJob: Job? = null
    private var startedAt = 0L

    fun startSimulation() {
        startScript()
        startMusicClock()
    }

    // --------------------------------------------------------- RideSession

    override fun setPushToTalk(pressed: Boolean) {
        _state.value = _state.value.copy(
            isTransmitting = pressed,
            riders = _state.value.riders.map { if (it.id == selfId) it.copy(isTalking = pressed) else it },
            musicDucked = pressed || _state.value.talkingRiderNames.isNotEmpty(),
        )
        recomputeTalking()
    }

    override fun setCommMode(mode: CommMode) {
        _state.value = _state.value.copy(commMode = mode)
    }

    override fun musicPlay() = setMusicPlaying(true)
    override fun musicPause() = setMusicPlaying(false)

    override fun musicNext() {
        trackIndex = (trackIndex + 1) % playlist.size
        musicPositionMs = 0
        pushMusic()
    }

    override fun musicPrevious() {
        trackIndex = (trackIndex - 1 + playlist.size) % playlist.size
        musicPositionMs = 0
        pushMusic()
    }

    override fun musicResync() {
        _events.tryEmit(SessionEvent.Resyncing)
        _state.value = _state.value.copy(music = _state.value.music.copy(syncStatus = SyncStatus.RESYNCING))
        scope.launch {
            delay(900)
            _state.value = _state.value.copy(music = _state.value.music.copy(syncStatus = SyncStatus.SYNCED))
        }
    }

    override fun musicSelect(trackId: String) {
        val idx = playlist.indexOfFirst { it.id == trackId }
        if (idx >= 0) {
            trackIndex = idx
            musicPositionMs = 0
            pushMusic()
        }
    }

    override fun setMusicVolume(volume: Float) {
        _state.value = _state.value.copy(music = _state.value.music.copy(volume = volume))
    }

    override fun setRiderMuted(riderId: String, muted: Boolean) {
        // No audio in sim; reflected in UI only via mix screen state elsewhere.
    }

    override fun setRiderVolume(riderId: String, volume: Float) {}

    override fun sendQuickAlert(kind: QuickAlertKind) {
        _events.tryEmit(SessionEvent.QuickAlertReceived(kind, hostName))
    }

    override fun triggerEmergency() {
        _events.tryEmit(SessionEvent.EmergencyReceived(hostName, System.currentTimeMillis()))
    }

    override fun setRideMode(active: Boolean) {
        _state.value = _state.value.copy(rideModeActive = active)
    }

    override fun startRide() {
        startedAt = System.currentTimeMillis()
        _state.value = _state.value.copy(phase = RidePhase.RIDING)
    }

    override fun endRide() {
        val now = System.currentTimeMillis()
        val summary = com.ridesync.app.domain.model.RideSummary(
            rideName = _state.value.ride?.name ?: "Simulated Ride",
            startedAtMs = if (startedAt > 0) startedAt else now - 600_000,
            durationMs = if (startedAt > 0) now - startedAt else 600_000,
            riderCount = allRiders.size,
            interruptions = 2,
            musicSyncedMs = 480_000,
            talkSeconds = 95,
        )
        _events.tryEmit(SessionEvent.RideEnded(summary))
        _state.value = _state.value.copy(phase = RidePhase.ENDED)
        stop()
    }

    override fun leave() = stop()

    fun stop() {
        scriptJob?.cancel()
        musicJob?.cancel()
    }

    // ------------------------------------------------------------- scripting

    private fun setMusicPlaying(playing: Boolean) {
        musicPlaying = playing
        pushMusic()
    }

    private fun startMusicClock() {
        musicJob = scope.launch {
            while (isActive) {
                if (musicPlaying) {
                    musicPositionMs += 250
                    val dur = playlist[trackIndex].durationMs
                    if (musicPositionMs >= dur) {
                        trackIndex = (trackIndex + 1) % playlist.size
                        musicPositionMs = 0
                    }
                    pushMusic()
                }
                delay(250)
            }
        }
    }

    private fun pushMusic() {
        _state.value = _state.value.copy(
            music = _state.value.music.copy(
                track = playlist[trackIndex],
                isPlaying = musicPlaying,
                positionMs = musicPositionMs,
                durationMs = playlist[trackIndex].durationMs,
            ),
        )
    }

    /** A gentle loop of simulated group activity so the UI feels alive. */
    private fun startScript() {
        scriptJob = scope.launch {
            val rng = Random(42)
            delay(3000)
            while (isActive) {
                val others = _state.value.riders.filter { it.id != selfId && it.state == RiderState.CONNECTED }
                if (others.isEmpty()) {
                    delay(4000); continue
                }
                when (rng.nextInt(6)) {
                    0, 1 -> simulateTalk(others[rng.nextInt(others.size)].key, rng.nextLong(1200, 3200))
                    2 -> simulateQuickAlert(others[rng.nextInt(others.size)].name, rng)
                    3 -> simulateReconnect(others[rng.nextInt(others.size)].id)
                    4 -> simulateBatteryDrift()
                    else -> Unit
                }
                delay(rng.nextLong(4000, 9000))
            }
        }
    }

    private suspend fun simulateTalk(key: Int, durationMs: Long) {
        setSimTalking(key, true)
        delay(durationMs)
        setSimTalking(key, false)
    }

    private fun setSimTalking(key: Int, talking: Boolean) {
        val riders = _state.value.riders.map { if (it.key == key) it.copy(isTalking = talking) else it }
        _state.value = _state.value.copy(riders = riders)
        recomputeTalking()
    }

    private fun recomputeTalking() {
        val riders = _state.value.riders
        val names = riders.filter { it.isTalking }.map { it.name }
        _state.value = _state.value.copy(
            talkingRiderNames = names,
            musicDucked = names.isNotEmpty(),
        )
    }

    private fun simulateQuickAlert(name: String, rng: Random) {
        val kind = QuickAlertKind.entries[rng.nextInt(QuickAlertKind.entries.size)]
        _events.tryEmit(SessionEvent.QuickAlertReceived(kind, name))
    }

    private suspend fun simulateReconnect(riderId: String) {
        val name = _state.value.riders.firstOrNull { it.id == riderId }?.name ?: return
        updateRiderState(riderId, RiderState.RECONNECTING)
        _events.tryEmit(SessionEvent.RiderReconnecting(name))
        delay(2500)
        updateRiderState(riderId, RiderState.CONNECTED)
        _events.tryEmit(SessionEvent.RiderReconnected(name))
    }

    private fun simulateBatteryDrift() {
        _state.value = _state.value.copy(
            riders = _state.value.riders.map {
                val b = it.batteryPercent ?: return@map it
                it.copy(batteryPercent = (b - Random.nextInt(0, 2)).coerceAtLeast(1))
            },
        )
    }

    private fun updateRiderState(riderId: String, newState: RiderState) {
        _state.value = _state.value.copy(
            riders = _state.value.riders.map { if (it.id == riderId) it.copy(state = newState) else it },
            connection = if (riderId == selfId) _state.value.connection else _state.value.connection,
        )
    }
}
