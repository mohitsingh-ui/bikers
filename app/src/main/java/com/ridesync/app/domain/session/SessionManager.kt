package com.ridesync.app.domain.session

import com.ridesync.app.core.RLog
import com.ridesync.app.data.preferences.Settings
import com.ridesync.app.networking.client.HostEndpoint
import com.ridesync.app.networking.host.RideConfig
import com.ridesync.app.networking.protocol.Ports
import com.ridesync.app.networking.protocol.RideCodes
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Owns the single active [RideSession] and the transitions between hosting,
 * joining, simulating, and (for migration) promoting a client to host.
 *
 * The UI observes [active] and always talks to whatever session is current, so
 * a host-migration swap is invisible to the ride screen.
 */
class SessionManager(
    private val env: SessionEnvironment,
) {
    // A failing session/network coroutine must log, not crash the app.
    private val errorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        RLog.e(RLog.Cat.SESSION, "session coroutine failed", e)
    }
    private val scope = CoroutineScope(
        SupervisorJob() + kotlinx.coroutines.Dispatchers.Default + errorHandler,
    )

    private val _active = MutableStateFlow<RideSession?>(null)
    val active: StateFlow<RideSession?> = _active

    val hasActiveSession: Boolean get() = _active.value != null

    init {
        scope.launch {
            env.settingsRepository.settings.collect { s ->
                env.settings = s
                env.announcer.enabled = s.spokenAlerts
                applyLiveSettings(s)
            }
        }
    }

    // ------------------------------------------------------------- hosting

    fun createHost(rideName: String, hostName: String): HostSession? {
        stopActive()
        val settings = env.settings
        val config = RideConfig(
            rideId = UUID.randomUUID().toString(),
            name = rideName.ifBlank { "Group Ride" },
            hostName = hostName.ifBlank { "Host" },
            pin = RideCodes.generatePin(),
            // Ride capacity comes from the host's subscription (Free = 4, up to
            // Fleet = 50), not a free setting. See LicenseManager.
            maxRiders = env.licenseManager.maxRiders,
            hostOnlyMusic = settings.hostOnlyMusic,
        )
        val host = HostSession(scope, env, hostRiderIdBlocking(), config)
        if (!host.startHosting()) return null
        _active.value = host
        return host
    }

    // -------------------------------------------------------------- joining

    fun joinRide(endpoint: HostEndpoint, riderName: String) {
        stopActive()
        val client = buildClient(endpoint, riderName)
        client.startClient()
        _active.value = client
    }

    fun joinByPin(host: String, port: Int, pin: String, rideId: String, riderName: String) {
        joinRide(
            HostEndpoint(
                rideId = rideId,
                host = host,
                controlPort = port,
                voicePort = Ports.VOICE_UDP,
                pin = pin,
            ),
            riderName,
        )
    }

    private fun buildClient(endpoint: HostEndpoint, riderName: String): ClientSession =
        ClientSession(
            scope = scope,
            env = env,
            riderId = hostRiderIdBlocking(),
            riderName = riderName,
            initialEndpoint = endpoint,
            onPromoteToHost = { rideId, rideName, pin -> promoteToHost(rideId, rideName, pin, riderName) },
        )

    /** Host migration: a former client becomes the new host and reopens the ride. */
    private fun promoteToHost(rideId: String, rideName: String, pin: String, hostName: String) {
        RLog.i(RLog.Cat.SESSION, "SessionManager promoting to host for ride $rideName")
        val config = RideConfig(
            rideId = rideId,
            name = rideName,
            hostName = hostName,
            pin = pin,
            maxRiders = env.licenseManager.maxRiders,
            hostOnlyMusic = env.settings.hostOnlyMusic,
        )
        val host = HostSession(scope, env, hostRiderIdBlocking(), config)
        if (host.startHosting()) {
            _active.value = host
        } else {
            RLog.e(RLog.Cat.SESSION, "promotion failed to bind host server")
        }
    }

    // ----------------------------------------------------------- simulation

    fun startSimulation(hostName: String, riderNames: List<String>): SimulatedSession {
        stopActive()
        val sim = SimulatedSession(scope, hostName.ifBlank { "You" }, riderNames)
        sim.startSimulation()
        _active.value = sim
        return sim
    }

    // --------------------------------------------------- phone audio sharing

    /** Start streaming the host phone's audio output to riders. Host only. */
    fun startPhoneAudioShare(projection: android.media.projection.MediaProjection): Boolean {
        val host = _active.value as? HostSession ?: return false
        return host.startMediaShare(projection)
    }

    fun stopPhoneAudioShare() {
        (_active.value as? HostSession)?.stopMediaShare()
    }

    val isHosting: Boolean get() = _active.value is HostSession

    // ------------------------------------------------------------- lifecycle

    fun stopActive() {
        _active.value?.let {
            runCatching { it.leave() }
        }
        _active.value = null
    }

    private fun applyLiveSettings(settings: Settings) {
        env.haptics.enabled = settings.hapticsEnabled
        env.ducking.enabled = settings.duckingEnabled
        env.ducking.duckLevel = settings.duckLevel
        env.ducking.fadeMs = settings.duckFadeMs
        env.ducking.holdMs = settings.duckHoldMs
    }

    private fun hostRiderIdBlocking(): String =
        kotlinx.coroutines.runBlocking { env.profileRepository.riderId() }
}
