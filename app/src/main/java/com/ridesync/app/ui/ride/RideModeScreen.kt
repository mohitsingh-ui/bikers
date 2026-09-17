package com.ridesync.app.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.domain.session.RideState
import com.ridesync.app.ui.components.PushToTalkButton
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * RIDE MODE — the safety screen. Only the essentials, all enormous:
 * a giant Push-to-Talk, one music play/pause, one mute, and the biggest
 * alerts. No keyboard, no navigation, no clutter. Everything is configured
 * before entering; this is what a rider sees at speed.
 */
@Composable
fun RideModeScreen(
    state: RideState,
    duckLevelPercent: Int,
    onPushToTalk: (Boolean) -> Unit,
    onMusicPlayPause: () -> Unit,
    onToggleMuteMusic: () -> Unit,
    musicMuted: Boolean,
    onQuickAlert: (QuickAlertKind) -> Unit,
    onEmergency: () -> Unit,
    onExit: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    val micEnabled = state.commMode == CommMode.PUSH_TO_TALK

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(Space.l),
    ) {
        // Top bar: talking status + exit
        Row(Modifier.fillMaxWidth().padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(colors.statusGreen))
            Spacer(Modifier.width(Space.s))
            Text(
                "${state.connectedCount} connected",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface).clickable(onClick = onExit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Exit ride mode", tint = MaterialTheme.colorScheme.onSurface)
            }
        }

        Spacer(Modifier.height(Space.m))
        // Who's talking — large
        val talking = state.talkingRiderNames
        Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
            if (talking.isNotEmpty()) {
                Text(
                    if (talking.size == 1) "${talking[0]} is talking" else "${talking.size} talking",
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        // Giant PTT centered
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            PushToTalkButton(
                isTransmitting = state.isTransmitting,
                enabled = micEnabled,
                onPressChange = onPushToTalk,
                size = 260.dp,
            )
        }

        // Music + mute row (two huge buttons)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            BigControl(
                icon = if (state.music.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (state.music.isPlaying) "Pause" else "Play",
                modifier = Modifier.weight(1f),
                onClick = onMusicPlayPause,
                enabled = state.music.canControl,
            )
            BigControl(
                icon = if (musicMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                label = if (musicMuted) "Unmute" else "Mute",
                modifier = Modifier.weight(1f),
                onClick = onToggleMuteMusic,
                accentWhenActive = musicMuted,
            )
        }

        Spacer(Modifier.height(Space.m))
        // Two biggest alerts
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            BigAlert(QuickAlertKind.STOPPING, Modifier.weight(1f), onQuickAlert)
            BigAlert(QuickAlertKind.SLOW_DOWN, Modifier.weight(1f), onQuickAlert)
        }
        Spacer(Modifier.height(Space.m))
        EmergencyButton(onTriggered = onEmergency)
        Spacer(Modifier.height(Space.s))
    }
}

@Composable
private fun BigControl(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true,
    accentWhenActive: Boolean = false,
) {
    val colors = RideSyncTheme.colors
    Column(
        modifier
            .height(96.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (accentWhenActive) colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Space.m),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface else colors.faintText,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.faintText)
    }
}

@Composable
private fun BigAlert(kind: QuickAlertKind, modifier: Modifier = Modifier, onAlert: (QuickAlertKind) -> Unit) {
    val colors = RideSyncTheme.colors
    Column(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable { onAlert(kind) }
            .padding(Space.m),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(kind.emoji, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(alertLabel(kind), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
