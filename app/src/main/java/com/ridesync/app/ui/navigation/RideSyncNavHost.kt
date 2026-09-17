package com.ridesync.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.domain.model.RideSummary
import com.ridesync.app.music.ExternalMusicLauncher
import com.ridesync.app.networking.protocol.Ports
import com.ridesync.app.networking.protocol.RideCodes
import com.ridesync.app.ui.RideSyncViewModel
import com.ridesync.app.ui.SessionEventEffects
import com.ridesync.app.ui.audiocheck.AudioCheckScreen
import com.ridesync.app.ui.createride.CreateRideScreen
import com.ridesync.app.ui.createride.HostLobbyScreen
import com.ridesync.app.ui.devmode.DevModeScreen
import com.ridesync.app.ui.home.HomeScreen
import com.ridesync.app.ui.joinride.JoinRideScreen
import com.ridesync.app.ui.joinride.RiderSetupScreen
import com.ridesync.app.ui.onboarding.OnboardingScreen
import com.ridesync.app.ui.ride.EmergencyOverlay
import com.ridesync.app.ui.ride.RideModeScreen
import com.ridesync.app.ui.ride.RideScreen
import com.ridesync.app.ui.ride.VoiceMixScreen
import com.ridesync.app.ui.settings.SettingsScreen
import com.ridesync.app.ui.summary.SummaryScreen

/**
 * Central navigation graph. One [RideSyncViewModel] is shared across the graph
 * so the active session survives navigation and host migration.
 */
@Composable
fun RideSyncNavHost(
    viewModel: RideSyncViewModel,
    pendingJoin: RideCodes.JoinTarget?,
    onConsumePendingJoin: () -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val onboardingDone by viewModel.onboardingDone.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val riderName by viewModel.riderName.collectAsState()
    val recentRides by viewModel.recentRides.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()
    val rideState by viewModel.rideState.collectAsState()

    var lastSummary by remember { mutableStateOf<RideSummary?>(null) }
    var emergencyBanner by remember { mutableStateOf<String?>(null) }
    var musicMutedLocal by remember { mutableStateOf(false) }

    // Route session events to snackbar / summary / emergency banner.
    SessionEventEffects(
        session = activeSession,
        snackbar = snackbar,
        onRideEnded = { summary ->
            lastSummary = summary
            navController.navigate(Routes.SUMMARY) {
                popUpTo(Routes.HOME)
            }
        },
        onEmergency = { emergencyBanner = "${it.riderName} needs assistance" },
    )

    val duckPercent = (settings.duckLevel * 100).toInt()
    val start = if (onboardingDone) Routes.HOME else Routes.ONBOARDING

    // Handle a QR/deep-link join that arrived before the graph was ready.
    LaunchedEffectPendingJoin(pendingJoin, onConsumePendingJoin) { target ->
        val host = target.host ?: viewModel.gatewayHint()
        if (host != null) {
            navController.navigate(Routes.JOIN_RIDE)
            viewModel.joinByPin(
                host = host,
                port = target.port,
                pin = target.pin ?: "",
                rideId = target.rideId ?: "",
                riderName = riderName.ifBlank { "Rider" },
            )
            navController.navigate(Routes.RIDE)
        }
    }

    Box(Modifier.fillMaxSize()) {
        NavHost(navController = navController, startDestination = start) {

            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }

            composable(Routes.HOME) {
                HomeScreen(
                    riderName = riderName,
                    recentRides = recentRides,
                    onCreateRide = { navController.navigate(Routes.CREATE_RIDE) },
                    onJoinRide = { navController.navigate(Routes.JOIN_RIDE) },
                    onSettings = { navController.navigate(Routes.SETTINGS) },
                    onDevMode = { navController.navigate(Routes.DEV_MODE) },
                )
            }

            composable(Routes.CREATE_RIDE) {
                CreateRideScreen(
                    defaultRideName = settings.defaultRideName,
                    defaultRiderName = riderName,
                    onBack = { navController.popBackStack() },
                    onCreate = { rideName, hostName ->
                        viewModel.createRide(rideName, hostName) { ok ->
                            if (ok) navController.navigate(Routes.HOST_LOBBY) { popUpTo(Routes.HOME) }
                        }
                    },
                )
            }

            composable(Routes.HOST_LOBBY) {
                val state = rideState
                if (state != null) {
                    HostLobbyScreen(
                        state = state,
                        localIp = viewModel.localIp(),
                        onStartRide = {
                            activeSession?.startRide()
                            navController.navigate(Routes.RIDE) { popUpTo(Routes.HOME) }
                        },
                        onLeave = {
                            viewModel.leaveSession()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                    )
                }
            }

            composable(Routes.JOIN_RIDE) {
                val rides by viewModel.startScanning().collectAsState()
                JoinRideScreen(
                    discoveredRides = rides,
                    onBack = { viewModel.stopScanning(); navController.popBackStack() },
                    onRefresh = { viewModel.refreshScan() },
                    onScanQr = { navController.navigate(Routes.SCANNER) },
                    onJoin = { ride ->
                        viewModel.stopScanning()
                        navController.currentBackStackEntry?.savedStateHandle?.set("pendingRide", ride.rideId)
                        // Go to setup carrying the target via a lightweight holder.
                        PendingJoinHolder.discovered = ride
                        navController.navigate(Routes.RIDER_SETUP)
                    },
                    onJoinByPin = { pin ->
                        val host = viewModel.gatewayHint()
                        if (host != null) {
                            PendingJoinHolder.pinTarget = Triple(host, Ports.CONTROL_TCP, pin)
                            navController.navigate(Routes.RIDER_SETUP)
                        }
                    },
                )
            }

            composable(Routes.SCANNER) {
                com.ridesync.app.ui.scanner.QrScannerScreen(
                    onBack = { navController.popBackStack() },
                    onScanned = { target ->
                        PendingJoinHolder.scanTarget = target
                        navController.navigate(Routes.RIDER_SETUP) { popUpTo(Routes.JOIN_RIDE) }
                    },
                )
            }

            composable(Routes.RIDER_SETUP) {
                val route by viewModel.audioRoute.collectAsState()
                RiderSetupScreen(
                    defaultName = riderName,
                    route = route,
                    onRefreshRoute = { viewModel.refreshAudioRoute() },
                    onOpenBluetoothSettings = { openBluetoothSettings(context) },
                    onBack = { navController.popBackStack() },
                    onContinue = { name ->
                        viewModel.setRiderName(name)
                        connectPending(viewModel, name)
                        navController.navigate(Routes.RIDE) { popUpTo(Routes.HOME) }
                    },
                )
            }

            composable(Routes.RIDE) {
                val state = rideState
                if (state != null) {
                    RideScreen(
                        state = state,
                        duckLevelPercent = duckPercent,
                        onBack = { navController.popBackStack(Routes.HOME, inclusive = false) },
                        onPushToTalk = { activeSession?.setPushToTalk(it) },
                        onToggleCommMode = {
                            val next = if (state.commMode == CommMode.PUSH_TO_TALK) CommMode.OPEN_INTERCOM else CommMode.PUSH_TO_TALK
                            activeSession?.setCommMode(next)
                        },
                        onMusicPlayPause = { if (state.music.isPlaying) activeSession?.musicPause() else activeSession?.musicPlay() },
                        onMusicNext = { activeSession?.musicNext() },
                        onMusicPrevious = { activeSession?.musicPrevious() },
                        onMusicResync = { activeSession?.musicResync() },
                        onOpenExternal = { state.music.track?.let { ExternalMusicLauncher.openInMusicApp(context, it) } },
                        onQuickAlert = { activeSession?.sendQuickAlert(it) },
                        onEmergency = { activeSession?.triggerEmergency() },
                        onOpenVoiceMix = { navController.navigate(Routes.VOICE_MIX) },
                        onOpenRideMode = { activeSession?.setRideMode(true) },
                        onToggleShareAudio = {
                            if (state.phoneAudioSharing) viewModel.stopPhoneAudioShare()
                            else viewModel.requestPhoneAudioShare()
                        },
                        onStartRide = { activeSession?.startRide() },
                        onEndRide = { activeSession?.endRide() },
                    )
                    if (state.rideModeActive) {
                        RideModeScreen(
                            state = state,
                            duckLevelPercent = duckPercent,
                            onPushToTalk = { activeSession?.setPushToTalk(it) },
                            onMusicPlayPause = { if (state.music.isPlaying) activeSession?.musicPause() else activeSession?.musicPlay() },
                            onToggleMuteMusic = {
                                musicMutedLocal = !musicMutedLocal
                                activeSession?.setMusicVolume(if (musicMutedLocal) 0f else settings.musicVolume)
                            },
                            musicMuted = musicMutedLocal,
                            onQuickAlert = { activeSession?.sendQuickAlert(it) },
                            onEmergency = { activeSession?.triggerEmergency() },
                            onExit = { activeSession?.setRideMode(false) },
                        )
                    }
                } else {
                    // Session ended out from under us.
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            }

            composable(Routes.VOICE_MIX) {
                val state = rideState
                if (state != null) {
                    VoiceMixScreen(
                        state = state,
                        onSetVolume = { id, v -> activeSession?.setRiderVolume(id, v) },
                        onSetMuted = { id, m -> activeSession?.setRiderMuted(id, m) },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    settings = settings,
                    repo = viewModel.settingsRepository,
                    scope = scope,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.DEV_MODE) {
                DevModeScreen(
                    onBack = { navController.popBackStack() },
                    onStartSimulation = { riders ->
                        viewModel.startSimulation(riders)
                        activeSession?.startRide()
                        navController.navigate(Routes.RIDE) { popUpTo(Routes.HOME) }
                    },
                )
            }

            composable(Routes.SUMMARY) {
                val summary = lastSummary
                if (summary != null) {
                    SummaryScreen(summary = summary, onDone = {
                        viewModel.leaveSession()
                        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    })
                } else {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            }
        }

        // Global emergency banner overlay (over everything).
        emergencyBanner?.let { message ->
            EmergencyOverlay(message = message, onDismiss = { emergencyBanner = null }, modifier = Modifier.align(Alignment.TopCenter))
        }

        SnackbarHost(hostState = snackbar, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp))
    }
}

private fun connectPending(viewModel: RideSyncViewModel, riderName: String) {
    PendingJoinHolder.discovered?.let {
        viewModel.joinDiscovered(it, riderName, it.pin)
        PendingJoinHolder.clear()
        return
    }
    PendingJoinHolder.scanTarget?.let { t ->
        val host = t.host
        if (host != null) {
            viewModel.joinByPin(host, t.port, t.pin ?: "", t.rideId ?: "", riderName)
        }
        PendingJoinHolder.clear()
        return
    }
    PendingJoinHolder.pinTarget?.let { (host, port, pin) ->
        viewModel.joinByPin(host, port, pin, "", riderName)
        PendingJoinHolder.clear()
    }
}

/** Tiny process-lifetime holder to carry the chosen join target into setup. */
object PendingJoinHolder {
    var discovered: com.ridesync.app.networking.discovery.DiscoveredRide? = null
    var pinForDiscovered: String? = null
    var scanTarget: RideCodes.JoinTarget? = null
    var pinTarget: Triple<String, Int, String>? = null

    fun clear() {
        discovered = null
        pinForDiscovered = null
        scanTarget = null
        pinTarget = null
    }
}

@Composable
private fun LaunchedEffectPendingJoin(
    target: RideCodes.JoinTarget?,
    onConsume: () -> Unit,
    action: (RideCodes.JoinTarget) -> Unit,
) {
    androidx.compose.runtime.LaunchedEffect(target) {
        if (target != null) {
            action(target)
            onConsume()
        }
    }
}

private fun openBluetoothSettings(context: Context) {
    runCatching {
        context.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
