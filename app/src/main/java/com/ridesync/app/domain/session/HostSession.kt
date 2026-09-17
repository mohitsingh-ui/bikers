package com.ridesync.app.domain.session

import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.ConnectionQuality
import com.ridesync.app.domain.model.ConnectionState
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.model.RideInfo
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.SessionEvent
import com.ridesync.app.domain.model.SessionRole
import com.ridesync.app.domain.model.TrackInfo
import com.ridesync.app.networking.discovery.HostAnnouncer
import com.ridesync.app.networking.host.HostServer
import com.ridesync.app.networking.host.RideConfig
import com.ridesync.app.networking.protocol.ControlMessage
import com.ridesync.app.networking.protocol.Emergency
import com.ridesync.app.networking.protocol.MusicCommand
import com.ridesync.app.networking.protocol.PlaybackSync
import com.ridesync.app.networking.protocol.Ports
import com.ridesync.app.networking.protocol.QuickAlertMsg
import com.ridesync.app.networking.protocol.RideEnded
import com.ridesync.app.networking.protocol.RideStarted
import com.ridesync.app.networking.protocol.VoicePackets
import com.ridesync.app.networking.protocol.VoiceStart
import com.ridesync.app.networking.protocol.VoiceStop
import com.ridesync.app.networking.protocol.toDomain
import com.ridesync.app.networking.protocol.toDto
import java.util.concurrent.atomic.AtomicLong
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
 * Host-side session orchestrator.
 *
 * Wires together:
 *  - [HostServer]      : accepts clients, relays voice, owns the roster
 *  - [HostAnnouncer]   : advertises the ride for discovery
 *  - VoiceEngine       : local mic capture -> relay to clients; playback of clients
 *  - MusicController    : authoritative playback, publishes PlaybackSync
 *  - Ducking           : local speaking OR any remote speaking -> duck music
 *
 * Implements the same [RideSession] interface the UI uses for clients, so the
 * ride screen is identical on host and client (the host just has extra powers).
 */
class HostSession(
    private val scope: CoroutineScope,
    private val env: SessionEnvironment,
    private val hostRiderId: String,
    config: RideConfig,
) : RideSession {

    private val _state = MutableStateFlow(
        RideState(
            role = SessionRole.HOST,
            phase = RidePhase.LOBBY,
            selfId = hostRiderId,
            commMode = env.settings.commMode,
            ride = RideInfo(
                rideId = config.rideId,
                name = config.name,
                hostName = config.hostName,
                pin = config.pin,
                maxRiders = config.maxRiders,
                riderCount = 1,
                hostAddress = env.networkMonitor.localIpAddress(),
                controlPort = config.controlPort,
                hostOnlyMusic = config.hostOnlyMusic,
            ),
        ),
    )
    override val state: StateFlow<RideState> = _state

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SessionEvent> = _events

    private val syncSeq = AtomicLong(0)
    private val stats = SessionStats(config.name)

    private val voiceEngine = env.buildVoiceEngine { payload, length, _, flags ->
        server.relayVoiceFromHost(payload, length, flags)
    }
    private val music = env.buildMusicController()

    // Phone-audio sharing (AudioPlaybackCapture). Present only while the host
    // is streaming its device audio to the riders.
    private var mediaCapture: com.ridesync.app.audio.MediaStreamCapture? = null
    private val mediaFrameBytes = ByteArray(com.ridesync.app.audio.VoiceFormat.FRAME_SAMPLES * 2)

    private val server = HostServer(
        scope = scope,
        hostRiderId = hostRiderId,
        config = config,
        clock = env.clock,
        listener = ServerListener(),
    )

    private val announcer = HostAnnouncer(env.appContext) { server.discoveryInfo() }

    private var tickJob: Job? = null
    private var duckJob: Job? = null

    fun startHosting(): Boolean {
        // Host is authoritative clock: offset 0.
        env.clock.reset()
        env.bluetooth.refresh()
        env.bluetooth.startCommunicationMode()

        if (!server.start()) {
            RLog.e(RLog.Cat.SESSION, "host server failed to start")
            return false
        }
        announcer.start()
        voiceEngine.start(env.settings)
        music.initialize(env.settings)
        music.setFollower(false)
        music.onHostSnapshot = { track, playing, position -> publishPlayback(track, playing, position) }

        startDuckWatcher()
        startTicker()
        RLog.i(RLog.Cat.SESSION, "hosting '${config().name}'")
        pushState()
        return true
    }

    private fun config() = server.config

    // --------------------------------------------------------- RideSession

    override fun setPushToTalk(pressed: Boolean) {
        if (_state.value.commMode == CommMode.OPEN_INTERCOM) return
        voiceEngine.setTransmitting(pressed)
        server.setHostTalking(pressed)
        if (pressed) {
            env.haptics.talkStart(); env.tones.talkStart()
            server.broadcast(VoiceStart(riderKey = 0, riderName = config().hostName))
        } else {
            env.haptics.talkEnd(); env.tones.talkEnd()
            server.broadcast(VoiceStop(riderKey = 0))
        }
        pushState()
    }

    override fun setCommMode(mode: CommMode) {
        scope.launch { env.settingsRepository.setCommMode(mode) }
        _state.value = _state.value.copy(commMode = mode)
        if (mode == CommMode.OPEN_INTERCOM) {
            voiceEngine.setTransmitting(false)
        }
    }

    override fun musicPlay() {
        music.hostPlay()
        stats.onMusicPlaying(true, System.currentTimeMillis())
    }

    override fun musicPause() {
        music.hostPause()
        stats.onMusicPlaying(false, System.currentTimeMillis())
    }

    override fun musicNext() = music.hostNext()
    override fun musicPrevious() = music.hostPrevious()
    override fun musicResync() {
        music.hostResync()
        _events.tryEmit(SessionEvent.Resyncing)
    }

    override fun musicSelect(trackId: String) = music.hostSelect(trackId)
    override fun setMusicVolume(volume: Float) {
        music.setBaseVolume(volume)
        scope.launch { env.settingsRepository.setMusicVolume(volume) }
    }

    override fun playLocalMusicFile(uri: String, title: String) {
        music.addAndPlayLocalFile(uri, title)
        stats.onMusicPlaying(true, System.currentTimeMillis())
    }

    override fun setRiderMuted(riderId: String, muted: Boolean) {
        keyFor(riderId)?.let { voiceEngine.setLocalMute(it, muted) }
    }

    override fun setRiderVolume(riderId: String, volume: Float) {
        keyFor(riderId)?.let { voiceEngine.setLocalVolume(it, volume) }
    }

    override fun sendQuickAlert(kind: QuickAlertKind) {
        runCatching {
            server.broadcast(QuickAlertMsg(kind.name, config().hostName))
            if (env.settings.spokenAlerts) env.tones.alert()
            env.announcer.announceOwnAlert(kind)
            env.haptics.alert()
            _events.tryEmit(SessionEvent.QuickAlertReceived(kind, config().hostName))
        }.onFailure { RLog.e(RLog.Cat.SESSION, "sendQuickAlert failed", it) }
    }

    override fun triggerEmergency() {
        runCatching {
            server.broadcast(Emergency(config().hostName, System.currentTimeMillis()))
            env.haptics.emergency(); env.tones.emergency()
            env.announcer.announceEmergency(config().hostName)
            _events.tryEmit(SessionEvent.EmergencyReceived(config().hostName, System.currentTimeMillis()))
        }.onFailure { RLog.e(RLog.Cat.SESSION, "triggerEmergency failed", it) }
    }

    override fun setRideMode(active: Boolean) {
        _state.value = _state.value.copy(rideModeActive = active)
    }

    /**
     * Start streaming the host phone's own audio output to the riders. Each
     * captured PCM frame is packed to bytes and relayed as a dedicated media
     * stream. Returns false if capture couldn't start (e.g. below Android 10).
     */
    fun startMediaShare(projection: android.media.projection.MediaProjection): Boolean {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return false
        stopMediaShare()
        val capture = com.ridesync.app.audio.MediaStreamCapture(projection) { pcm, samples ->
            var j = 0
            val n = samples.coerceAtMost(com.ridesync.app.audio.VoiceFormat.FRAME_SAMPLES)
            for (i in 0 until n) {
                val s = pcm[i].toInt()
                mediaFrameBytes[j++] = (s and 0xFF).toByte()
                mediaFrameBytes[j++] = (s shr 8 and 0xFF).toByte()
            }
            server.relayMediaFromHost(mediaFrameBytes, n * 2)
        }
        val ok = capture.start()
        if (ok) {
            mediaCapture = capture
            _state.value = _state.value.copy(phoneAudioSharing = true)
            _events.tryEmit(SessionEvent.Info("Sharing your phone’s audio"))
        }
        return ok
    }

    fun stopMediaShare() {
        mediaCapture?.stop()
        mediaCapture = null
        if (_state.value.phoneAudioSharing) {
            _state.value = _state.value.copy(phoneAudioSharing = false)
        }
    }

    override fun startRide() {
        server.markRideStarted()
        stats.onRideStarted(System.currentTimeMillis())
        server.broadcast(RideStarted(env.clock.hostNow()))
        _state.value = _state.value.copy(phase = RidePhase.RIDING)
        RLog.i(RLog.Cat.SESSION, "ride started")
        pushState()
    }

    override fun endRide() {
        val summary = stats.build(System.currentTimeMillis())
        server.broadcast(RideEnded(summary.toDto()))
        scope.launch { env.recentRidesRepository.add(summary) }
        _events.tryEmit(SessionEvent.RideEnded(summary))
        _state.value = _state.value.copy(phase = RidePhase.ENDED)
        shutdown(broadcastEnd = false)
    }

    override fun leave() {
        // Host leaving = ending the ride (migration handled client-side if it just drops).
        shutdown(broadcastEnd = true)
    }

    // ----------------------------------------------------- server callbacks

    private inner class ServerListener : HostServer.Listener {
        override fun onRosterChanged(roster: List<Rider>, succession: List<String>) {
            stats.onRiderCount(roster.count { it.state == com.ridesync.app.domain.model.RiderState.CONNECTED })
            _state.value = _state.value.copy(
                riders = roster,
                ride = _state.value.ride?.copy(riderCount = roster.count {
                    it.state == com.ridesync.app.domain.model.RiderState.CONNECTED
                }),
            )
            announcer.refresh()
        }

        override fun onClientMessage(fromRiderId: String, message: ControlMessage) {
            when (message) {
                is MusicCommand -> handleClientMusicCommand(message)
                is VoiceStart -> onSpeakingChanged()
                is VoiceStop -> onSpeakingChanged()
                is QuickAlertMsg -> runCatching {
                    val kind = QuickAlertKind.valueOf(message.kind)
                    if (env.settings.spokenAlerts) env.tones.alert()
                    env.announcer.announceAlert(kind, message.riderName)
                    env.haptics.alert()
                    _events.tryEmit(SessionEvent.QuickAlertReceived(kind, message.riderName))
                }.onFailure { RLog.w(RLog.Cat.SESSION, "handle alert failed", it) }.let {}

                is Emergency -> runCatching {
                    env.haptics.emergency(); env.tones.emergency()
                    env.announcer.announceEmergency(message.riderName)
                    _events.tryEmit(
                        SessionEvent.EmergencyReceived(
                            message.riderName, message.atMs, message.latitude, message.longitude,
                        ),
                    )
                }.onFailure { RLog.w(RLog.Cat.SESSION, "handle emergency failed", it) }.let {}

                else -> Unit
            }
        }

        override fun onClientJoined(rider: Rider, rejoin: Boolean) {
            env.haptics.riderChange()
            _events.tryEmit(
                if (rejoin) SessionEvent.RiderReconnected(rider.name)
                else SessionEvent.RiderJoined(rider.name),
            )
        }

        override fun onClientLost(rider: Rider) {
            stats.onInterruption()
            _events.tryEmit(SessionEvent.RiderReconnecting(rider.name))
        }

        override fun onClientLeft(rider: Rider) {
            voiceEngine.forgetRemote(rider.key)
            _events.tryEmit(SessionEvent.RiderLeft(rider.name))
        }

        override fun onVoiceFromClient(voice: VoicePackets.Datagram.Voice) {
            if (voice.isEnd || voice.payload.isEmpty()) return
            voiceEngine.onRemoteVoice(voice.senderKey, voice.seq, voice.isPcm, voice.payload)
        }
    }

    private fun handleClientMusicCommand(cmd: MusicCommand) {
        // Host-only music: ignore client transport requests when locked.
        if (config().hostOnlyMusic) return
        when (cmd.action) {
            MusicCommand.PLAY -> music.hostPlay()
            MusicCommand.PAUSE -> music.hostPause()
            MusicCommand.NEXT -> music.hostNext()
            MusicCommand.PREVIOUS -> music.hostPrevious()
            MusicCommand.SEEK -> cmd.seekToMs?.let { music.hostSeek(it) }
            MusicCommand.SELECT -> cmd.trackId?.let { music.hostSelect(it) }
            MusicCommand.RESYNC -> music.hostResync()
        }
    }

    // ------------------------------------------------------------- ducking

    private fun startDuckWatcher() {
        duckJob = scope.launch {
            voiceEngine.anySpeaking.collect { speaking ->
                music.setSpeaking(speaking)
                onSpeakingChanged()
            }
        }
    }

    private fun onSpeakingChanged() {
        val talkingNames = server.currentRoster().filter { it.isTalking }.map { it.name }
        _state.value = _state.value.copy(
            talkingRiderNames = talkingNames,
            musicDucked = voiceEngine.anySpeaking.value,
        )
    }

    // -------------------------------------------------------------- ticker

    private fun startTicker() {
        tickJob = scope.launch {
            music.state.collect { musicState ->
                _state.value = _state.value.copy(music = musicState)
            }
        }
        scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                if (_state.value.phase == RidePhase.RIDING) {
                    _state.value = _state.value.copy(elapsedMs = now - stats.startedAtMs)
                }
                server.setHostBattery(env.battery.currentPercent())
                _state.value = _state.value.copy(
                    connection = ConnectionState(ConnectionQuality.EXCELLENT, 0),
                )
                delay(1000)
            }
        }
    }

    private fun publishPlayback(track: TrackInfo?, playing: Boolean, position: Long) {
        server.broadcast(
            PlaybackSync(
                track = track?.toDto(),
                isPlaying = playing,
                positionMs = position,
                hostTimeMs = env.clock.hostNow(),
                syncSeq = syncSeq.incrementAndGet(),
            ),
        )
        stats.onMusicPlaying(playing, System.currentTimeMillis())
    }

    private fun keyFor(riderId: String): Int? =
        _state.value.riders.firstOrNull { it.id == riderId }?.key

    private fun pushState() {
        onSpeakingChanged()
    }

    private fun shutdown(broadcastEnd: Boolean) {
        tickJob?.cancel()
        duckJob?.cancel()
        stopMediaShare()
        announcer.stop()
        voiceEngine.stop()
        music.release()
        server.stop(broadcastEnd = broadcastEnd)
        env.bluetooth.stopCommunicationMode()
        RLog.i(RLog.Cat.SESSION, "host session shut down")
    }
}
