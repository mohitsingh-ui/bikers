package com.ridesync.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ridesync.app.AppContainer
import com.ridesync.app.data.preferences.Settings
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.session.RideSession
import com.ridesync.app.domain.session.RideState
import com.ridesync.app.networking.client.HostEndpoint
import com.ridesync.app.networking.discovery.DiscoveredRide
import com.ridesync.app.networking.discovery.RideScanner
import com.ridesync.app.networking.protocol.Ports
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Bridges the [com.ridesync.app.domain.session.SessionManager] and repositories
 * to Compose. One VM for the whole app keeps the active session observable
 * across navigation and survives host migration (the session swaps underneath).
 */
class RideSyncViewModel(private val container: AppContainer) : ViewModel() {

    private val sessionManager = container.sessionManager
    private val env = container.environment

    // Any UI-triggered coroutine that throws must log, never crash the app.
    private val vmErrorHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
        com.ridesync.app.core.RLog.e(com.ridesync.app.core.RLog.Cat.UI, "ui coroutine failed", e)
    }

    private fun launchSafe(block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) =
        viewModelScope.launch(vmErrorHandler, block = block)

    val activeSession: StateFlow<RideSession?> = sessionManager.active

    val settings: StateFlow<Settings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    val riderName: StateFlow<String> = container.profileRepository.riderName
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val onboardingDone: StateFlow<Boolean> = container.profileRepository.onboardingDone
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val recentRides = container.recentRidesRepository.recentRides
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val rideState: StateFlow<RideState?> = activeSession
        .flatMapLatest { it?.state ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // Discovery ---------------------------------------------------------------

    private var scanner: RideScanner? = null

    fun startScanning(): StateFlow<List<DiscoveredRide>> {
        val existing = scanner
        if (existing != null) return existing.rides
        val fresh = RideScanner(env.appContext, viewModelScope)
        fresh.start()
        scanner = fresh
        return fresh.rides
    }

    fun refreshScan() = scanner?.refresh()

    fun stopScanning() {
        scanner?.stop()
        scanner = null
    }

    fun gatewayHint(): String? = env.networkMonitor.gatewayAddress()

    fun localIp(): String? = env.networkMonitor.localIpAddress()

    // Session lifecycle -------------------------------------------------------

    fun createRide(rideName: String, hostName: String, onResult: (Boolean) -> Unit) {
        launchSafe {
            if (hostName.isNotBlank()) container.profileRepository.setRiderName(hostName)
            val host = sessionManager.createHost(rideName, hostName)
            onResult(host != null)
        }
    }

    fun joinDiscovered(ride: DiscoveredRide, riderName: String, pin: String) {
        launchSafe {
            if (riderName.isNotBlank()) container.profileRepository.setRiderName(riderName)
            sessionManager.joinRide(
                HostEndpoint(
                    rideId = ride.rideId,
                    host = ride.address,
                    controlPort = ride.controlPort,
                    voicePort = Ports.VOICE_UDP,
                    pin = pin,
                ),
                riderName,
            )
        }
    }

    fun joinByPin(host: String, port: Int, pin: String, rideId: String, riderName: String) {
        launchSafe {
            if (riderName.isNotBlank()) container.profileRepository.setRiderName(riderName)
            sessionManager.joinByPin(host, port, pin, rideId, riderName)
        }
    }

    fun startSimulation(riderNames: List<String>) {
        launchSafe {
            val me = riderName.value.ifBlank { "You" }
            sessionManager.startSimulation(me, riderNames)
        }
    }

    fun leaveSession() {
        sessionManager.stopActive()
    }

    // Settings mutators (delegated) ------------------------------------------

    fun setCommMode(mode: CommMode) = launchSafe { container.settingsRepository.setCommMode(mode) }
    fun setRiderName(name: String) = launchSafe { container.profileRepository.setRiderName(name) }
    fun completeOnboarding() = launchSafe { container.profileRepository.setOnboardingDone() }

    val settingsRepository get() = container.settingsRepository

    // Licensing / subscription -----------------------------------------------

    val licenseState: StateFlow<com.ridesync.app.license.LicenseState> = container.licenseManager.state

    /** Activate a pasted license key; [onResult] gets true on success. */
    fun activateLicense(key: String, onResult: (Boolean) -> Unit = {}) = launchSafe {
        onResult(container.licenseManager.activate(key))
    }

    fun refreshLicense() = launchSafe { container.licenseManager.refresh(force = true) }

    fun signOutLicense() = launchSafe { container.licenseManager.signOut() }

    fun refreshAudioRoute() {
        env.bluetooth.refresh()
    }

    val audioRoute get() = env.bluetooth.route

    fun quickAlert(kind: QuickAlertKind) {
        activeSession.value?.sendQuickAlert(kind)
    }

    // Phone audio sharing: the ride screen asks, MainActivity runs the system
    // media-projection consent dialog, then the service starts capture.
    private val _audioShareRequests = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val audioShareRequests: kotlinx.coroutines.flow.SharedFlow<Unit> = _audioShareRequests

    fun requestPhoneAudioShare() {
        _audioShareRequests.tryEmit(Unit)
    }

    fun stopPhoneAudioShare() {
        com.ridesync.app.service.RideSessionService.stopAudioCapture(env.appContext)
        sessionManager.stopPhoneAudioShare()
    }

    override fun onCleared() {
        stopScanning()
        super.onCleared()
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RideSyncViewModel(container) as T
        }
    }
}
