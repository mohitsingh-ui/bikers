package com.ridesync.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ridesync.app.data.preferences.AccentChoice
import com.ridesync.app.data.preferences.AppTheme
import com.ridesync.app.data.preferences.Settings
import com.ridesync.app.data.preferences.SettingsRepository
import com.ridesync.app.data.preferences.VoiceCodecChoice
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.license.LicenseState
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: Settings,
    repo: SettingsRepository,
    scope: CoroutineScope,
    license: LicenseState,
    onOpenSubscription: () -> Unit,
    onSendDiagnostics: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    fun launch(block: suspend () -> Unit) = scope.launch { block() }

    RideScaffold(title = "Settings", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))

            // SUBSCRIPTION — the rider limit lives here now
            SettingsGroup("Subscription") {
                NavRow(
                    title = "Your plan",
                    subtitle = "${license.effectiveTier.displayName} · up to ${license.maxRiders} riders" +
                        if (license.effectiveStatus == LicenseState.Status.EXPIRED) " · renew to restore" else "",
                    value = if (license.effectiveTier.isPaid) null else "Upgrade",
                    onClick = onOpenSubscription,
                )
            }

            // COMMUNICATION
            SettingsGroup("Communication") {
                ChoiceRow(
                    title = "Voice mode",
                    options = listOf("Push-to-Talk", "Open Intercom"),
                    selectedIndex = if (settings.commMode == CommMode.PUSH_TO_TALK) 0 else 1,
                    onSelect = { launch { repo.setCommMode(if (it == 0) CommMode.PUSH_TO_TALK else CommMode.OPEN_INTERCOM) } },
                )
                if (settings.commMode == CommMode.OPEN_INTERCOM) {
                    Text(
                        "Open Intercom may increase background noise and battery usage.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.statusYellow,
                        modifier = Modifier.padding(horizontal = Space.m, vertical = Space.xs),
                    )
                }
                SliderRow("Voice volume", "${(settings.voiceVolume * 100).toInt()}%", settings.voiceVolume) {
                    launch { repo.setVoiceVolume(it) }
                }
                SliderRow("Microphone sensitivity", "${(settings.micSensitivity * 100).toInt()}%", settings.micSensitivity, range = 0.5f..2f) {
                    launch { repo.setMicSensitivity(it) }
                }
                ToggleRow("Noise suppression", "Reduce wind and road noise", settings.noiseSuppression) {
                    launch { repo.setNoiseSuppression(it) }
                }
                ToggleRow("Echo cancellation", checked = settings.echoCancellation) {
                    launch { repo.setEchoCancellation(it) }
                }
                ChoiceRow(
                    title = "Voice codec",
                    options = listOf("Opus (efficient)", "PCM (fallback)"),
                    selectedIndex = if (settings.voiceCodec == VoiceCodecChoice.OPUS) 0 else 1,
                    onSelect = { launch { repo.setVoiceCodec(if (it == 0) VoiceCodecChoice.OPUS else VoiceCodecChoice.PCM) } },
                )
            }

            // MUSIC
            SettingsGroup("Music") {
                ToggleRow("Synchronized playback", "Keep everyone’s music in sync", settings.syncEnabled) {
                    launch { repo.setSyncEnabled(it) }
                }
                ToggleRow("Auto music ducking", "Lower music when someone talks", settings.duckingEnabled) {
                    launch { repo.setDuckingEnabled(it) }
                }
                SliderRow("Ducking level", "${(settings.duckLevel * 100).toInt()}%", settings.duckLevel, range = 0f..0.8f) {
                    launch { repo.setDuckLevel(it) }
                }
                SliderRow("Fade back", "${"%.1f".format(settings.duckFadeMs / 1000f)}s", settings.duckFadeMs / 5000f, range = 0.04f..1f) {
                    launch { repo.setDuckFadeMs((it * 5000).toInt()) }
                }
                ToggleRow("Host-only music control", "Only the host changes the shared track", settings.hostOnlyMusic) {
                    launch { repo.setHostOnlyMusic(it) }
                }
            }

            // RIDE
            SettingsGroup("Ride") {
                NavRow(
                    title = "Maximum riders",
                    subtitle = "Set by your plan — tap to change",
                    value = "${license.maxRiders}",
                    onClick = onOpenSubscription,
                )
                ToggleRow("Host migration", "Elect a new host if the host drops", settings.hostMigrationEnabled) {
                    launch { repo.setHostMigrationEnabled(it) }
                }
                ToggleRow("Auto reconnect", "Rejoin automatically after a drop", settings.autoReconnect) {
                    launch { repo.setAutoReconnect(it) }
                }
            }

            // SAFETY
            SettingsGroup("Safety") {
                ToggleRow("Emergency button", "Long-press to alert the group", settings.emergencyEnabled) {
                    launch { repo.setEmergencyEnabled(it) }
                }
                ToggleRow("Location sharing", "Include GPS in emergency alerts (opt-in)", settings.locationSharing) {
                    launch { repo.setLocationSharing(it) }
                }
                ToggleRow("Quick alerts", "One-tap group signals", settings.quickAlertsEnabled) {
                    launch { repo.setQuickAlertsEnabled(it) }
                }
                ToggleRow("Spoken voice alerts", "Say each alert out loud, e.g. “Slow down, from Ravi”", settings.spokenAlerts) {
                    launch { repo.setSpokenAlerts(it) }
                }
                ToggleRow("Haptic feedback", checked = settings.hapticsEnabled) {
                    launch { repo.setHapticsEnabled(it) }
                }
            }

            // APPEARANCE
            SettingsGroup("Appearance") {
                ChoiceRow(
                    title = "Theme",
                    options = listOf("Dark", "Light", "System"),
                    selectedIndex = when (settings.theme) { AppTheme.DARK -> 0; AppTheme.LIGHT -> 1; AppTheme.SYSTEM -> 2 },
                    onSelect = { launch { repo.setTheme(when (it) { 1 -> AppTheme.LIGHT; 2 -> AppTheme.SYSTEM; else -> AppTheme.DARK }) } },
                )
                ChoiceRow(
                    title = "Accent color",
                    options = listOf("Ember", "Amber", "Cobalt"),
                    selectedIndex = when (settings.accent) { AccentChoice.EMBER -> 0; AccentChoice.AMBER -> 1; AccentChoice.COBALT -> 2 },
                    onSelect = { launch { repo.setAccent(when (it) { 1 -> AccentChoice.AMBER; 2 -> AccentChoice.COBALT; else -> AccentChoice.EMBER }) } },
                )
            }

            // DIAGNOSTICS
            SettingsGroup("Diagnostics") {
                NavRow(
                    title = "Send diagnostics report",
                    subtitle = "Share recent logs and any crash report to help fix issues",
                    onClick = onSendDiagnostics,
                )
            }

            // PRIVACY note
            Text(
                "RideSync works fully offline over your local Wi-Fi. Your voice is sent directly between the phones in your ride — no phone call is placed, nothing is uploaded, and conversations are never recorded.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.faintText,
                modifier = Modifier.padding(vertical = Space.m),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
