package com.ridesync.app.ui.ride

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.RecordVoiceOver
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

/** Push-to-Talk / Open Intercom segmented toggle. */
@Composable
fun CommModeToggle(mode: CommMode, onToggle: () -> Unit) {
    val colors = RideSyncTheme.colors
    RideCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.RecordVoiceOver, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("Communication mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    if (mode == CommMode.PUSH_TO_TALK) "Push-to-Talk" else "Open Intercom (always live)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
            SegPill(
                leftLabel = "PTT",
                rightLabel = "Open",
                rightSelected = mode == CommMode.OPEN_INTERCOM,
                onClick = onToggle,
            )
        }
    }
}

@Composable
private fun SegPill(leftLabel: String, rightLabel: String, rightSelected: Boolean, onClick: () -> Unit) {
    val colors = RideSyncTheme.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(3.dp),
    ) {
        listOf(leftLabel to !rightSelected, rightLabel to rightSelected).forEach { (label, selected) ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) colors.accent else Color.Transparent)
                    .padding(horizontal = Space.m, vertical = 6.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color(0xFF1A0E00) else colors.mutedText,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
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
