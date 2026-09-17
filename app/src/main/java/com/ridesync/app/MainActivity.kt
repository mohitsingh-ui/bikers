package com.ridesync.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.networking.protocol.RideCodes
import com.ridesync.app.service.RideSessionService
import com.ridesync.app.ui.RideSyncViewModel
import com.ridesync.app.ui.navigation.RideSyncNavHost
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * Single-activity host. Requests runtime permissions contextually, keeps the
 * foreground service in step with whether a ride is active, and routes the
 * ridesync://join deep link into the nav graph.
 */
class MainActivity : ComponentActivity() {

    private var pendingJoin by mutableStateOf<RideCodes.JoinTarget?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { /* result handled reactively */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pendingJoin = parseJoinIntent(intent)
        requestCorePermissions()

        val app = application as RideSyncApp

        setContent {
            val vm: RideSyncViewModel = viewModel(factory = RideSyncViewModel.Factory(app.container))
            val settings by vm.settings.collectAsState()

            RideSyncTheme(appTheme = settings.theme, accentChoice = settings.accent) {
                RideSyncNavHost(
                    viewModel = vm,
                    pendingJoin = pendingJoin,
                    onConsumePendingJoin = { pendingJoin = null },
                )
            }

            // Keep the foreground service tied to session lifecycle.
            val rideState by vm.rideState.collectAsState()
            val active = rideState != null && rideState?.phase != RidePhase.ENDED
            androidx.compose.runtime.LaunchedEffect(active, rideState?.ride?.name) {
                if (active) {
                    RideSessionService.start(
                        this@MainActivity,
                        rideState?.ride?.name ?: "RideSync",
                        "Ride in progress • ${rideState?.connectedCount ?: 1} connected",
                    )
                } else {
                    RideSessionService.stop(this@MainActivity)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseJoinIntent(intent)?.let { pendingJoin = it }
    }

    private fun parseJoinIntent(intent: Intent?): RideCodes.JoinTarget? {
        val data = intent?.data?.toString() ?: return null
        return RideCodes.parseJoinUri(data)
    }

    private fun requestCorePermissions() {
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add(Manifest.permission.BLUETOOTH_CONNECT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}
