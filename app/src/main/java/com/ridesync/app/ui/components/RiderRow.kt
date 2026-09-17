package com.ridesync.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.domain.model.Rider
import com.ridesync.app.domain.model.RiderState
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * One rider row: avatar initial, name, HOST badge, connection status (always
 * icon+text, never color alone), talking indicator, and battery when known.
 */
@Composable
fun RiderRow(
    rider: Rider,
    modifier: Modifier = Modifier,
    isSelf: Boolean = false,
) {
    val colors = RideSyncTheme.colors
    val talkingBorder by animateColorAsState(
        if (rider.isTalking) colors.accent else Color.Transparent,
        label = "talkBorder",
    )
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.5.dp, if (rider.isTalking) talkingBorder else colors.cardStroke), RoundedCornerShape(16.dp))
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RiderAvatar(rider = rider, talking = rider.isTalking)
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    rider.name + if (isSelf) " (You)" else "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (rider.isHost) {
                    Spacer(Modifier.width(Space.s))
                    HostBadge()
                }
            }
            Spacer(Modifier.height(3.dp))
            RiderStatusLine(rider)
        }
        if (rider.batteryPercent != null) {
            Spacer(Modifier.width(Space.s))
            BatteryPill(rider.batteryPercent)
        }
    }
}

@Composable
private fun RiderAvatar(rider: Rider, talking: Boolean) {
    val colors = RideSyncTheme.colors
    val scale by animateFloatAsState(if (talking) 1f else 0.98f, label = "avatarScale")
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (rider.isHost) colors.accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (talking) {
                Icon(Icons.Filled.Mic, contentDescription = "Talking", tint = colors.accent, modifier = Modifier.size(20.dp))
            } else {
                Text(
                    rider.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = if (rider.isHost) colors.accent else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
fun HostBadge() {
    val colors = RideSyncTheme.colors
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accent)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text("HOST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A0E00), letterSpacing = 0.8.sp)
    }
}

@Composable
private fun RiderStatusLine(rider: Rider) {
    val colors = RideSyncTheme.colors
    val (color, label) = when (rider.state) {
        RiderState.CONNECTED -> colors.statusGreen to "Connected"
        RiderState.RECONNECTING -> colors.statusYellow to "Reconnecting…"
        RiderState.DISCONNECTED -> colors.statusRed to "Disconnected"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
        if (rider.isTalking) {
            Spacer(Modifier.width(Space.s))
            Icon(Icons.Filled.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text("Talking", style = MaterialTheme.typography.bodyMedium, color = colors.accent, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun BatteryPill(percent: Int) {
    val colors = RideSyncTheme.colors
    val color = when {
        percent <= 15 -> colors.statusRed
        percent <= 35 -> colors.statusYellow
        else -> colors.statusGreen
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.BatteryStd, contentDescription = "Battery", tint = color, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(3.dp))
        Text("$percent%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}
