package com.ridesync.app.ui.ride

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.session.RideState
import com.ridesync.app.ui.components.NowPlayingCard
import com.ridesync.app.ui.components.PushToTalkButton
import com.ridesync.app.ui.components.RiderRow
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * The full ride dashboard (host or client, per [state.isHost]). Ride Mode is a
 * separate, stripped-down screen ([RideModeScreen]); this is the configured,
 * full-control view used before/around riding.
 */
@Composable
fun RideScreen(
    state: RideState,
    duckLevelPercent: Int,
    onBack: () -> Unit,
    onPushToTalk: (Boolean) -> Unit,
    onToggleCommMode: () -> Unit,
    onMusicPlayPause: () -> Unit,
    onMusicNext: () -> Unit,
    onMusicPrevious: () -> Unit,
    onMusicResync: () -> Unit,
    onOpenExternal: () -> Unit,
    onQuickAlert: (QuickAlertKind) -> Unit,
    onEmergency: () -> Unit,
    onOpenVoiceMix: () -> Unit,
    onOpenRideMode: () -> Unit,
    onToggleShareAudio: () -> Unit,
    onPlayLocalFile: (uri: String, title: String) -> Unit,
    onStartRide: () -> Unit,
    onEndRide: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    val micEnabled = state.commMode == CommMode.PUSH_TO_TALK
    val context = androidx.compose.ui.platform.LocalContext.current
    val filePicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            // OpenDocument (SAF) returns a URI that supports a durable read grant,
            // so the track keeps playing across config changes / process restore.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            onPlayLocalFile(uri.toString(), queryDisplayName(context, uri))
        }
    }

    RideScaffold(
        title = state.ride?.name ?: "Ride",
        onBack = onBack,
        actionIcon = Icons.Filled.Tune,
        onAction = onOpenVoiceMix,
    ) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            ConnectedBanner(state.connectedCount, state.connection.quality)
            Spacer(Modifier.height(Space.m))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                ConnectionQualityPill(state.connection.quality, state.connection.rttMs)
            }

            Spacer(Modifier.height(Space.l))
            // Roster
            state.riders.forEach { rider ->
                RiderRow(rider = rider, isSelf = rider.id == state.selfId)
                Spacer(Modifier.height(Space.s))
            }

            Spacer(Modifier.height(Space.m))
            SpeakingIndicator(
                talkingNames = state.talkingRiderNames,
                ducked = state.musicDucked,
                duckLevelPercent = duckLevelPercent,
            )

            Spacer(Modifier.height(Space.l))

            // PTT — the centerpiece
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PushToTalkButton(
                    isTransmitting = state.isTransmitting,
                    enabled = micEnabled,
                    onPressChange = onPushToTalk,
                    size = 190.dp,
                )
            }
            if (!micEnabled) {
                Spacer(Modifier.height(Space.s))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Text(
                        "Open Intercom is on — mic is always live",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                    )
                }
            }

            Spacer(Modifier.height(Space.l))
            CommModeToggle(mode = state.commMode, onToggle = onToggleCommMode)

            Spacer(Modifier.height(Space.l))
            SectionLabel("Quick alerts")
            Spacer(Modifier.height(Space.s))
            QuickAlertsRow(onAlert = onQuickAlert)
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                QuickAlertKind.entries.drop(3).forEach { kind ->
                    QuickAlertChip(kind, onClick = { onQuickAlert(kind) }, modifier = Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(Space.l))
            NowPlayingCard(
                music = state.music,
                onPlayPause = onMusicPlayPause,
                onNext = onMusicNext,
                onPrevious = onMusicPrevious,
                onResync = onMusicResync,
                onOpenExternal = onOpenExternal,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isHost) {
                Spacer(Modifier.height(Space.m))
                PlayFileCard(onPick = { filePicker.launch(arrayOf("audio/*")) })

                Spacer(Modifier.height(Space.l))
                ShareAudioCard(
                    sharing = state.phoneAudioSharing,
                    onToggle = onToggleShareAudio,
                )
            }

            Spacer(Modifier.height(Space.l))
            EmergencyButton(onTriggered = onEmergency)

            Spacer(Modifier.height(Space.l))
            RideModeCta(onOpenRideMode)

            Spacer(Modifier.height(Space.l))
            HostControls(
                isHost = state.isHost,
                phase = state.phase,
                onStartRide = onStartRide,
                onEndRide = onEndRide,
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

/**
 * Resolve a human-readable name for a picked content:// audio file so the Now
 * Playing card and the broadcast title show something friendlier than a URI.
 * Falls back gracefully if the provider doesn't expose a display name.
 */
private fun queryDisplayName(context: android.content.Context, uri: android.net.Uri): String {
    var name = ""
    runCatching {
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx) ?: ""
            }
        }
    }
    if (name.isBlank()) name = uri.lastPathSegment?.substringAfterLast('/') ?: "My music"
    return name.substringBeforeLast('.').ifBlank { name }
}
