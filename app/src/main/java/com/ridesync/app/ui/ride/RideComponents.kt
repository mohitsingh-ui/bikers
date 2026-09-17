package com.ridesync.app.ui.ride

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.domain.model.ConnectionQuality
import com.ridesync.app.domain.model.QuickAlertKind
import com.ridesync.app.ui.components.MeterBar
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RidePalette
import com.ridesync.app.ui.theme.RideSyncTheme

/** "● N RIDERS CONNECTED" banner. */
@Composable
fun ConnectedBanner(count: Int, quality: ConnectionQuality) {
    val colors = RideSyncTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(colors.statusGreen))
        Spacer(Modifier.width(Space.s))
        Text(
            "$count ${if (count == 1) "RIDER" else "RIDERS"} CONNECTED",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
}

/** Shows who is talking + live ducking meter. */
@Composable
fun SpeakingIndicator(
    talkingNames: List<String>,
    ducked: Boolean,
    duckLevelPercent: Int,
    modifier: Modifier = Modifier,
) {
    val colors = RideSyncTheme.colors
    AnimatedVisibility(
        visible = talkingNames.isNotEmpty(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        RideCard(modifier = Modifier.fillMaxWidth(), stroke = true) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(Space.s))
                    Text(
                        talkingText(talkingNames),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (ducked) {
                    Spacer(Modifier.height(Space.s))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Music volume", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                        Spacer(Modifier.width(Space.s))
                        MeterBar(
                            fraction = duckLevelPercent / 100f,
                            color = colors.accent,
                            modifier = Modifier.weight(1f),
                            height = 6.dp,
                        )
                        Spacer(Modifier.width(Space.s))
                        Text("$duckLevelPercent%", style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
                    }
                }
            }
        }
    }
}

private fun talkingText(names: List<String>): String = when (names.size) {
    0 -> ""
    1 -> "${names[0]} is talking"
    2 -> "${names[0]} & ${names[1]} are talking"
    else -> "${names[0]} +${names.size - 1} talking"
}

/** Grid of one-tap quick alerts. */
@Composable
fun QuickAlertsRow(onAlert: (QuickAlertKind) -> Unit, modifier: Modifier = Modifier) {
    val items = QuickAlertKind.entries
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
        items.take(3).forEach { kind ->
            QuickAlertChip(kind, onClick = { onAlert(kind) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun QuickAlertChip(kind: QuickAlertKind, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    val color = RidePalette.forAlert(kind)
    Column(
        modifier
            .clip(shape)
            .background(color.copy(alpha = 0.12f))
            .border(androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.55f)), shape)
            .clickable(onClick = onClick)
            .padding(vertical = Space.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape).background(color.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(kind.emoji, fontSize = 17.sp)
        }
        Spacer(Modifier.height(5.dp))
        Text(
            alertLabel(kind),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun alertLabel(kind: QuickAlertKind): String = when (kind) {
    QuickAlertKind.STOPPING -> "Stopping"
    QuickAlertKind.FUEL -> "Fuel"
    QuickAlertKind.BREAK -> "Break"
    QuickAlertKind.SLOW_DOWN -> "Slow down"
    QuickAlertKind.TURNING -> "Turning"
    QuickAlertKind.HAZARD -> "Hazard"
}

/** Small pill for the local connection quality. */
@Composable
fun ConnectionQualityPill(quality: ConnectionQuality, rttMs: Int?) {
    val colors = RideSyncTheme.colors
    val (color, label, fraction) = when (quality) {
        ConnectionQuality.EXCELLENT -> Triple(colors.statusGreen, "Excellent", 1f)
        ConnectionQuality.GOOD -> Triple(colors.statusGreen, "Good", 0.75f)
        ConnectionQuality.WEAK -> Triple(colors.statusYellow, "Weak", 0.4f)
        ConnectionQuality.RECONNECTING -> Triple(colors.statusYellow, "Reconnecting…", 0.25f)
        ConnectionQuality.DISCONNECTED -> Triple(colors.statusRed, "Disconnected", 0.1f)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(72.dp)) {
            MeterBar(fraction = fraction, color = color, height = 6.dp)
        }
        Spacer(Modifier.width(Space.s))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
        if (rttMs != null && rttMs > 0) {
            Spacer(Modifier.width(6.dp))
            Text("${rttMs}ms", style = MaterialTheme.typography.labelMedium, color = colors.faintText)
        }
    }
}
