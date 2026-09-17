package com.ridesync.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DeveloperMode
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.data.preferences.RecentRide
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme
import java.util.Calendar

@Composable
fun HomeScreen(
    riderName: String,
    recentRides: List<RecentRide>,
    planLabel: String,
    canUpgrade: Boolean,
    onCreateRide: () -> Unit,
    onJoinRide: () -> Unit,
    onSettings: () -> Unit,
    onSubscription: () -> Unit,
    onDevMode: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.l),
    ) {
        Spacer(Modifier.height(Space.xxl))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting() + if (riderName.isNotBlank()) ", $riderName" else "", style = MaterialTheme.typography.bodyLarge, color = colors.mutedText)
                Spacer(Modifier.height(2.dp))
                Text(
                    "Ready to ride?",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        brush = Brush.linearGradient(
                            listOf(colors.accent, Color(0xFF34D399), Color(0xFF5B9BFF)),
                        ),
                    ),
                )
            }
            IconRoundButton(Icons.Filled.Settings, "Settings", onSettings)
        }

        Spacer(Modifier.height(Space.xl))

        BigActionCard(
            title = "Create Ride",
            subtitle = "Start a new group ride",
            icon = Icons.Filled.Add,
            brush = Brush.linearGradient(listOf(colors.accent, Color(0xFFFF5A3C))),
            contentColor = Color(0xFF1A0E00),
            subColor = Color(0xCC2A1500),
            onClick = onCreateRide,
        )
        Spacer(Modifier.height(Space.m))
        BigActionCard(
            title = "Join Ride",
            subtitle = "Join your friend’s ride",
            icon = Icons.Filled.Login,
            brush = Brush.linearGradient(listOf(Color(0xFF5B9BFF), Color(0xFF9B7BFF))),
            contentColor = Color.White,
            subColor = Color(0xE6FFFFFF),
            onClick = onJoinRide,
        )

        Spacer(Modifier.height(Space.m))
        PlanChip(label = planLabel, canUpgrade = canUpgrade, onClick = onSubscription)

        Spacer(Modifier.height(Space.xl))

        if (recentRides.isNotEmpty()) {
            SectionLabel("Recent rides")
            Spacer(Modifier.height(Space.m))
            recentRides.take(4).forEach { ride ->
                RecentRideRow(ride)
                Spacer(Modifier.height(Space.s))
            }
            Spacer(Modifier.height(Space.m))
        }

        RideCard(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .clickable(onClick = onDevMode),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DeveloperMode, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("Developer Mode", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Text("Test the full ride with simulated riders", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                }
            }
        }
        Spacer(Modifier.height(Space.xxl))
    }
}

@Composable
private fun PlanChip(label: String, canUpgrade: Boolean, onClick: () -> Unit) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(BorderStroke(1.dp, colors.cardStroke), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text("Your plan", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        }
        if (canUpgrade) {
            Text("Upgrade", style = MaterialTheme.typography.labelLarge, color = colors.accent, fontWeight = FontWeight.Bold)
        } else {
            Text("Manage", style = MaterialTheme.typography.labelLarge, color = colors.mutedText)
        }
    }
}

@Composable
private fun BigActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    brush: Brush,
    contentColor: Color,
    subColor: Color,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(shape)
            .background(brush)
            .clickable(onClick = onClick)
            .padding(Space.xl),
    ) {
        Column(Modifier.align(Alignment.CenterStart)) {
            Icon(
                icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(Space.m))
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                color = contentColor,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = subColor,
            )
        }
        Icon(
            Icons.Filled.DirectionsBike,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.18f),
            modifier = Modifier.align(Alignment.BottomEnd).size(56.dp),
        )
    }
}

@Composable
private fun RecentRideRow(ride: RecentRide) {
    val colors = RideSyncTheme.colors
    RideCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Groups, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(ride.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "${ride.riderCount} riders • ${formatDuration(ride.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
            Icon(Icons.Filled.History, contentDescription = null, tint = colors.faintText, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun IconRoundButton(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = desc, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
    }
}

private fun greeting(): String {
    return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Ready to ride"
    }
}

private fun formatDuration(ms: Long): String {
    val minutes = ms / 60000
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
