package com.ridesync.app.domain.session

import com.ridesync.app.core.RLog
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.ConnectionQuality
import com.ridesync.app.domain.model.ConnectionState
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.RiderState
import com.ridesync.app.domain.model.SessionEvent
import com.ridesync.app.domain.model.SessionRole
import com.ridesync.app.networking.client.HostConnection
import com.ridesync.app.networking.client.HostEndpoint
import com.ridesync.app.networking.host.HostElection
import com.ridesync.app.networking.protocol.Bye
import com.ridesync.app.networking.protocol.ControlMessage
import com.ridesync.app.networking.protocol.Emergency
import com.ridesync.app.networking.protocol.HostTransfer
import com.ridesync.app.networking.protocol.JoinAccepted
import com.ridesync.app.networking.protocol.MusicCommand
import com.ridesync.app.networking.protocol.PlaybackSync
import com.ridesync.app.networking.protocol.QuickAlertMsg
import com.ridesync.app.networking.protocol.RideEnded
import com.ridesync.app.networking.protocol.RideStarted
import com.ridesync.app.networking.protocol.RosterUpdate
import com.ridesync.app.networking.protocol.VoicePackets
import com.ridesync.app.networking.protocol.VoiceStart
import com.ridesync.app.networking.protocol.VoiceStop
import com.ridesync.app.networking.protocol.toDomain
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
 * Client-side session orchestrator. Mirror image of [HostSession]:
 *  - [HostConnection] handles the resilient link + reconnection.
 *  - Voice frames captured locally are sent to the host for relay.
 *  - Roster / playback / alerts arrive as control messages.
 *  - Music runs in follower mode, drift-corrected against the host clock.
 *  - On host loss, participates in [HostElection]; either promotes itself to a
 *    new [HostSession] (host migration) or shows the reconnect fallback.
 */
class ClientSession(
    private val scope: CoroutineScope,
    private val env: SessionEnvironment,
    private val riderId: String,
    private val riderName: String,
    initialEndpoint: HostEndpoint,
    /** Called when this client promotes itself to host (migration). */
    private val onPromoteToHost: (rideId: String, rideName: String, pin: String) -> Unit,
) : RideSession {

    private val _state = MutableStateFlow(
        RideState(
            role = SessionRole.CLIENT,
            phase = RidePhase.LOBBY,
            selfId = riderId,
            commMode = env.settings.commMode,
            connection = ConnectionState(ConnectionQuality.RECONNECTING),
        ),
    )
    override val state: StateFlow<RideState> = _state

    private val _events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<SessionEvent> = _events

    private var currentEndpoint = initialEndpoint
    private var succession: List<String> = emptyList()
    private val stats = SessionStats("")

    /** Re-discovery used during host migration to find whoever became host. */
    private var migrationScanner: com.ridesync.app.networking.discovery.RideScanner? = null

    private val voiceEngine = env.buildVoiceEngine { payload, length, isPcm, flags ->
        connection.sendVoice(payload, length, flags)
    }
    private val music = env.buildMusicController()

    private val connection = HostConnection(
        scope = scope,
        riderId = riderId,
        riderName = riderName,
        clock = env.clock,
        autoReconnect = { env.settings.autoReconnect },
        batteryProvider = { env.battery.currentPercent() },
        listener = ConnectionListener(),
    )

    private var duckJob: Job? = null
    private var electionJob: Job? = null

    fun startClient() {
        env.bluetooth.refresh()
        env.bluetooth.startCommunicationMode()
        voiceEngine.start(env.settings)
        music.initialize(env.settings)
        music.setFollower(true)
        music.hostClockProvider = { env.clock.hostNow() }

        startDuckWatcher()
        startTicker()
        connection.start(currentEndpoint)
        RLog.i(RLog.Cat.SESSION, "client session started -> ${currentEndpoint.host}")
    }

    // --------------------------------------------------------- RideSession

    override fun setPushToTalk(pressed: Boolean) {
        if (_state.value.commMode == CommMode.OPEN_INTERCOM) return
        voiceEngine.setTransmitting(pressed)
        if (pressed) {
            env.haptics.talkStart(); env.tones.talkStart()
            connection.sendControl(VoiceStart(connection.myKey, riderName))
        } else {
            env.haptics.talkEnd(); env.tones.talkEnd()
            connection.sendControl(VoiceStop(connection.myKey))
        }
    }

    override fun setCommMode(mode: CommMode) {
        scope.launch { env.settingsRepository.setCommMode(mode) }
        _state.value = _state.value.copy(commMode = mode)
        if (mode == CommMode.OPEN_INTERCOM) voiceEngine.setTransmitting(false)
    }

    // Clients request; host decides. When host-only music is on these are no-ops server-side.
    override fun musicPlay() = connection.sendControl(MusicCommand(MusicCommand.PLAY))
    override fun musicPause() = connection.sendControl(MusicCommand(MusicCommand.PAUSE))
    override fun musicNext() = connection.sendControl(MusicCommand(MusicCommand.NEXT))
    override fun musicPrevious() = connection.sendControl(MusicCommand(MusicCommand.PREVIOUS))
    override fun musicResync() = connection.sendControl(MusicCommand(MusicCommand.RESYNC))
    override fun musicSelect(trackId: String) =
        connection.sendControl(MusicCommand(MusicCommand.SELECT, trackId = trackId))

    override fun setMusicVolume(volume: Float) {
        // Local monitor volume only; does not affect other riders.
        music.setBaseVolume(volume)
        scope.launch { env.settingsRepository.setMusicVolume(volume) }
    }

    override fun setRiderMuted(riderId: String, muted: Boolean) {
        keyFor(riderId)?.let { voiceEngine.setLocalMute(it, muted) }
    }

    override fun setRiderVolume(riderId: String, volume: Float) {
        keyFor(riderId)?.let { voiceEngine.setLocalVolume(it, volume) }
    }

    override fun sendQuickAlert(kind: QuickAlertKind) {
        // The whole path is guarded so a tone/speech/haptic hiccup on any device
        // can never force-close the app.
        runCatching {
            connection.sendControl(QuickAlertMsg(kind.name, riderName))
            if (env.settings.spokenAlerts) env.tones.alert()
            env.announcer.announceOwnAlert(kind)
            env.haptics.alert()
            _events.tryEmit(SessionEvent.QuickAlertReceived(kind, riderName))
        }.onFailure { RLog.e(RLog.Cat.SESSION, "sendQuickAlert failed", it) }
    }

    override fun triggerEmergency() {
        runCatching {
            connection.sendControl(Emergency(riderName, System.currentTimeMillis()))
            env.haptics.emergency(); env.tones.emergency()
            env.announcer.announceEmergency(riderName)
            _events.tryEmit(SessionEvent.EmergencyReceived(riderName, System.currentTimeMillis()))
        }.onFailure { RLog.e(RLog.Cat.SESSION, "triggerEmergency failed", it) }
    }

    override fun setRideMode(active: Boolean) {
        _state.value = _state.value.copy(rideModeActive = active)
    }

    override fun startRide() {
        // Only the host starts the ride; clients follow RideStarted.
    }

    override fun endRide() = leave()

    override fun leave() {
        shutdown(notifyHost = true)
    }

    // ------------------------------------------------------ connection cbs

    private inner class ConnectionListener : HostConnection.Listener {
        override fun onJoined(accepted: JoinAccepted) {
            succession = accepted.succession
            val riders = accepted.roster.map { it.toDomain() }
            _state.value = _state.value.copy(
                phase = if (accepted.rideStarted) RidePhase.RIDING else RidePhase.LOBBY,
                riders = riders,
                ride = accepted.ride.toDomain(currentEndpoint.host, currentEndpoint.pin),
                connection = ConnectionState(ConnectionQuality.GOOD),
            )
            electionJob?.cancel() // we're connected again
            stopMigrationScanner()
        }

        override fun onRejected(reason: String) {
            _events.tryEmit(SessionEvent.Info(rejectMessage(reason)))
            _state.value = _state.value.copy(connection = ConnectionState(ConnectionQuality.DISCONNECTED))
        }

        override fun onControlMessage(message: ControlMessage) = handleControl(message)

        override fun onVoice(voice: VoicePackets.Datagram.Voice) {
            if (voice.isEnd || voice.payload.isEmpty()) return
            voiceEngine.onRemoteVoice(voice.senderKey, voice.seq, voice.isPcm, voice.payload)
        }

        override fun onConnectionStateChanged(quality: ConnectionQuality) {
            _state.value = _state.value.copy(
                connection = ConnectionState(quality, env.clock.lastRttMs.toInt().takeIf { it >= 0 }),
            )
            if (quality == ConnectionQuality.RECONNECTING) {
                _events.tryEmit(SessionEvent.Info("Reconnecting…"))
            }
        }

        override fun onPermanentlyDisconnected() {
            RLog.w(RLog.Cat.SESSION, "permanently disconnected from host")
            maybeStartElection()
        }
    }

    private fun handleControl(message: ControlMessage) {
        when (message) {
            is RosterUpdate -> {
                succession = message.succession
                val riders = message.roster.map { it.toDomain() }
                _state.value = _state.value.copy(
                    riders = riders,
                    ride = message.ride.toDomain(currentEndpoint.host, currentEndpoint.pin),
                    talkingRiderNames = riders.filter { it.isTalking }.map { it.name },
                )
                syncForgottenRemotes(riders)
            }

            is PlaybackSync -> {
                music.onHostState(
                    track = message.track?.toDomain(),
                    isPlaying = message.isPlaying,
                    positionMs = message.positionMs,
                    hostSampleTimeMs = message.hostTimeMs,
                    hostNowMs = env.clock.hostNow(),
                    syncSeq = message.syncSeq,
                )
            }

            is VoiceStart -> {
                if (env.settings.spokenAlerts) env.tones.talkStart()
                markTalking(message.riderKey, true, message.riderName)
            }

            is VoiceStop -> markTalking(message.riderKey, false, null)

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
                    SessionEvent.EmergencyReceived(message.riderName, message.atMs, message.latitude, message.longitude),
                )
            }.onFailure { RLog.w(RLog.Cat.SESSION, "handle emergency failed", it) }.let {}

            is RideStarted -> {
                _state.value = _state.value.copy(phase = RidePhase.RIDING)
                stats.onRideStarted(System.currentTimeMillis())
                _events.tryEmit(SessionEvent.Info("Ride started"))
            }

            is RideEnded -> {
                _events.tryEmit(SessionEvent.RideEnded(message.summary.toDomain()))
                _state.value = _state.value.copy(phase = RidePhase.ENDED)
                shutdown(notifyHost = false)
            }

            is HostTransfer -> {
                RLog.i(RLog.Cat.SESSION, "host transfer -> ${message.newHostName} @ ${message.newHostAddress}")
                electionJob?.cancel()
                stopMigrationScanner()
                currentEndpoint = currentEndpoint.copy(
                    host = message.newHostAddress,
                    controlPort = message.controlPort,
                )
                _events.tryEmit(SessionEvent.HostChanged(message.newHostName))
                connection.redirect(currentEndpoint)
            }

            is Bye -> {
                RLog.i(RLog.Cat.SESSION, "host closed link")
            }

            else -> Unit
        }
    }

    // -------------------------------------------------------- host election

    private fun maybeStartElection() {
        if (!env.settings.hostMigrationEnabled) {
            _events.tryEmit(SessionEvent.HostLost)
            _state.value = _state.value.copy(connection = ConnectionState(ConnectionQuality.DISCONNECTED))
            return
        }
        if (electionJob?.isActive == true) return

        val ride = _state.value.ride ?: run {
            _events.tryEmit(SessionEvent.HostLost)
            return
        }
        val deadHostId = _state.value.riders.firstOrNull { it.isHost }?.id ?: return
        val aliveIds = _state.value.riders
            .filter { it.state != RiderState.DISCONNECTED }
            .map { it.id }
            .toSet()

        val rank = HostElection.myRank(succession, deadHostId, riderId, aliveIds)
        if (rank == null) {
            _events.tryEmit(SessionEvent.HostLost)
            return
        }

        _events.tryEmit(SessionEvent.Info("Host lost — selecting a new host…"))

        // Staggered election. Rank 0 promotes almost immediately; higher ranks
        // wait proportionally and, crucially, spend that wait RE-DISCOVERING the
        // ride: if a lower-ranked peer already became host (it re-advertises the
        // SAME rideId from a new address), we simply rejoin it instead of
        // creating a second host. Only if no new host appears by our deadline do
        // we promote ourselves — which covers the case where rank 0 died too.
        //
        // Note the hard physical limit: if the dead host was ALSO the Wi-Fi
        // hotspot, the whole local network is gone and neither rediscovery nor a
        // new host can be reached — the UI then falls back to "reconnect to a
        // new host". Migration only truly succeeds when the hotspot is a
        // separate device (router / dedicated hotspot).
        val scanner = com.ridesync.app.networking.discovery.RideScanner(env.appContext, scope)
        migrationScanner = scanner
        scanner.start()

        electionJob = scope.launch {
            val deadline = System.currentTimeMillis() + HostElection.promotionDelayMs(rank) +
                HostElection.PROMOTION_STAGGER_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                val newHost = scanner.rides.value.firstOrNull {
                    it.rideId == ride.rideId && it.address != currentEndpoint.host && it.hostId != deadHostId
                }
                if (newHost != null) {
                    RLog.i(RLog.Cat.SESSION, "found migrated host at ${newHost.address}; rejoining")
                    stopMigrationScanner()
                    currentEndpoint = currentEndpoint.copy(host = newHost.address, controlPort = newHost.controlPort)
                    _events.tryEmit(SessionEvent.HostChanged(newHost.hostName.ifBlank { "New host" }))
                    connection.start(currentEndpoint)
                    return@launch
                }
                delay(300)
            }
            if (!isActive) return@launch
            // No one else took over in time — promote ourselves.
            stopMigrationScanner()
            RLog.i(RLog.Cat.SESSION, "promoting self to host (rank $rank)")
            _events.tryEmit(SessionEvent.HostChanged(riderName))
            // Fully release THIS client — including its audio engines — before
            // the new HostSession starts its own. Two live AudioRecord captures
            // on VOICE_COMMUNICATION would collide, so tear down first.
            shutdown(notifyHost = false, stopEngines = true)
            onPromoteToHost(ride.rideId, ride.name, ride.pin)
        }
    }

    private fun stopMigrationScanner() {
        migrationScanner?.stop()
        migrationScanner = null
    }

    // -------------------------------------------------------------- helpers

    private fun markTalking(key: Int, talking: Boolean, name: String?) {
        val riders = _state.value.riders.map {
            if (it.key == key) it.copy(isTalking = talking) else it
        }
        _state.value = _state.value.copy(
            riders = riders,
            talkingRiderNames = riders.filter { it.isTalking }.map { it.name },
        )
    }

    private fun syncForgottenRemotes(riders: List<Rider>) {
        val activeKeys = riders.filter { it.id != riderId }.map { it.key }.toSet()
        // Nothing to eagerly forget here; VoiceEngine ages out idle remotes.
    }

    private fun startDuckWatcher() {
        duckJob = scope.launch {
            voiceEngine.anySpeaking.collect { speaking ->
                music.setSpeaking(speaking)
                _state.value = _state.value.copy(musicDucked = speaking)
            }
        }
    }

    private fun startTicker() {
        scope.launch {
            music.state.collect { musicState ->
                _state.value = _state.value.copy(music = musicState)
            }
        }
        scope.launch {
            while (isActive) {
                if (_state.value.phase == RidePhase.RIDING && stats.startedAtMs > 0) {
                    _state.value = _state.value.copy(elapsedMs = System.currentTimeMillis() - stats.startedAtMs)
                }
                delay(1000)
            }
        }
    }

    private fun keyFor(riderId: String): Int? =
        _state.value.riders.firstOrNull { it.id == riderId }?.key

    private fun rejectMessage(reason: String): String = when (reason) {
        com.ridesync.app.networking.protocol.JoinRejected.REASON_WRONG_PIN -> "Wrong ride PIN"
        com.ridesync.app.networking.protocol.JoinRejected.REASON_FULL -> "Ride is full"
        com.ridesync.app.networking.protocol.JoinRejected.REASON_VERSION -> "App version mismatch — update RideSync"
        else -> "Couldn't join that ride"
    }

    private fun shutdown(notifyHost: Boolean, stopEngines: Boolean = true) {
        duckJob?.cancel()
        electionJob?.cancel()
        stopMigrationScanner()
        connection.stop(notify = notifyHost)
        if (stopEngines) {
            voiceEngine.stop()
            music.release()
            env.bluetooth.stopCommunicationMode()
        }
        RLog.i(RLog.Cat.SESSION, "client session shut down")
    }
}
