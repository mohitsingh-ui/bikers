package com.ridesync.app.ui.ride

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ridesync.app.domain.model.CommMode
import com.ridesync.app.domain.model.RidePhase
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.SecondaryButton
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme
import kotlinx.coroutines.delay

/**
 * Two clear voice modes: Push-to-Talk (hold the big button to speak) and
 * Always On (hands-free — your mic is always live for the group). Tap a card to
 * switch.
 */
@Composable
fun CommModeToggle(mode: CommMode, onToggle: () -> Unit) {
    val colors = RideSyncTheme.colors
    Column(Modifier.fillMaxWidth()) {
        Text(
            "VOICE MODE",
            style = MaterialTheme.typography.labelMedium,
            color = colors.mutedText,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(Space.s))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            VoiceModeCard(
                emoji = "🎙️",
                title = "Push to talk",
                subtitle = "Hold to speak",
                selected = mode == CommMode.PUSH_TO_TALK,
                color = colors.accent,
                onClick = { if (mode != CommMode.PUSH_TO_TALK) onToggle() },
                modifier = Modifier.weight(1f),
            )
            VoiceModeCard(
                emoji = "📢",
                title = "Always on",
                subtitle = "Hands-free",
                selected = mode == CommMode.OPEN_INTERCOM,
                color = Color(0xFF34D399),
                onClick = { if (mode != CommMode.OPEN_INTERCOM) onToggle() },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun VoiceModeCard(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .border(
                androidx.compose.foundation.BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else colors.cardStroke),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
        )
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
    }
}

/**
 * Emergency button: long-press 2 s to fire. A radial progress ring fills while
 * held so accidental taps don't trigger it.
 */
@Composable
fun EmergencyButton(onTriggered: () -> Unit) {
    val colors = RideSyncTheme.colors
    var holding by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    val animated by animateFloatAsState(if (holding) 1f else 0f, tween(2000), label = "emergencyHold")

    LaunchedEffect(holding) {
        if (holding) {
            val start = System.currentTimeMillis()
            while (holding) {
                progress = ((System.currentTimeMillis() - start) / 2000f).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    onTriggered()
                    holding = false
                    progress = 0f
                    break
                }
                delay(16)
            }
            progress = 0f
        }
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.statusRed.copy(alpha = 0.12f))
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, colors.statusRed.copy(alpha = 0.6f)), RoundedCornerShape(16.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        holding = true
                        try {
                            awaitRelease()
                        } finally {
                            holding = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // progress underlay
        Box(
            Modifier
                .fillMaxWidth(progress)
                .height(58.dp)
                .background(colors.statusRed.copy(alpha = 0.25f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.statusRed, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.s))
            Text(
                if (holding) "Hold to alert…" else "EMERGENCY — hold 2s",
                style = MaterialTheme.typography.labelLarge,
                color = colors.statusRed,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
fun ShareAudioCard(sharing: Boolean, onToggle: () -> Unit) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (sharing) colors.accent.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, if (sharing) colors.accent else colors.cardStroke), shape)
            .clickable(onClick = onToggle)
            .padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = if (sharing) colors.accent else colors.mutedText,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(
                    if (sharing) "Sharing phone audio" else "Share phone audio",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Play this phone's audio on all riders' devices",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
            Text(
                if (sharing) "ON" else "OFF",
                style = MaterialTheme.typography.labelLarge,
                color = if (sharing) colors.accent else colors.faintText,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(Space.s))
        Text(
            "Note: YouTube Music and Spotify block audio capture, so they won't stream. Local music players and most other apps work.",
            style = MaterialTheme.typography.labelMedium,
            color = colors.faintText,
        )
    }
}

/**
 * Host-only card: pick an MP3/audio file from the phone and play it. It plays
 * through the shared ExoPlayer so it syncs to riders like the built-in tracks,
 * and — unlike DRM-protected streaming apps — a local file also streams through
 * "Share phone audio" when that's on.
 */
@Composable
fun PlayFileCard(onPick: () -> Unit) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.cardStroke), shape)
            .clickable(onClick = onPick)
            .padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text("Play a file from your phone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Pick an MP3 — it plays in sync on every rider", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
        }
    }
}

@Composable
fun RideModeCta(onOpenRideMode: () -> Unit) {
    val colors = RideSyncTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(androidx.compose.foundation.BorderStroke(1.dp, colors.cardStroke), RoundedCornerShape(16.dp))
            .clickable(onClick = onOpenRideMode)
            .padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text("Ride Mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text("Big controls only — safe for riding", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
        }
        Icon(Icons.Filled.Campaign, contentDescription = null, tint = colors.faintText, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun HostControls(isHost: Boolean, phase: RidePhase, onStartRide: () -> Unit, onEndRide: () -> Unit) {
    if (phase == RidePhase.RIDING) {
        SecondaryButton(text = "End Ride", onClick = onEndRide, modifier = Modifier.fillMaxWidth())
    } else if (isHost) {
        PrimaryButton(text = "Start Ride", onClick = onStartRide, modifier = Modifier.fillMaxWidth())
    }
}
