package com.ridesync.app.ui.joinride

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.networking.discovery.DiscoveredRide
import com.ridesync.app.networking.protocol.RideCodes
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

@Composable
fun JoinRideScreen(
    discoveredRides: List<DiscoveredRide>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onScanQr: () -> Unit,
    onJoin: (DiscoveredRide) -> Unit,
    onJoinByPin: (pin: String) -> Unit,
) {
    val colors = RideSyncTheme.colors
    var pin by remember { mutableStateOf("") }

    RideScaffold(
        title = "Join Ride",
        onBack = onBack,
        actionIcon = Icons.Filled.QrCodeScanner,
        onAction = onScanQr,
    ) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("Available rides")
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onRefresh).padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = colors.accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Refresh", color = colors.accent, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(Space.m))

            if (discoveredRides.isEmpty()) {
                NoRidesFound()
            } else {
                discoveredRides.forEach { ride ->
                    DiscoveredRideRow(ride = ride, onJoin = { onJoin(ride) })
                    Spacer(Modifier.height(Space.s))
                }
            }

            Spacer(Modifier.height(Space.xl))
            SectionLabel("Enter ride PIN")
            Spacer(Modifier.height(Space.s))
            Text(
                "On the same hotspot but the ride isn’t showing? Type the 4-digit PIN from the host’s screen.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
            )
            Spacer(Modifier.height(Space.m))
            OutlinedTextField(
                value = pin,
                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pin = it },
                placeholder = { Text("4821") },
                singleLine = true,
                textStyle = MaterialTheme.typography.headlineMedium.copy(letterSpacing = 8.sp, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            )
            Spacer(Modifier.height(Space.m))
            PrimaryButton(
                text = "Join with PIN",
                onClick = { onJoinByPin(pin) },
                enabled = RideCodes.isValidPin(pin),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun DiscoveredRideRow(ride: DiscoveredRide, onJoin: () -> Unit) {
    val colors = RideSyncTheme.colors
    RideCard(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(onClick = onJoin)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.DirectionsBike, contentDescription = null, tint = colors.accent, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(ride.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Host: ${ride.hostName.ifBlank { "—" }} • ${ride.riderCount}/${ride.maxRiders} riders",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
            Box(
                Modifier.clip(RoundedCornerShape(12.dp)).background(colors.accent).padding(horizontal = Space.l, vertical = Space.s),
            ) {
                Text("Join", color = androidx.compose.ui.graphics.Color(0xFF1A0E00), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun NoRidesFound() {
    val colors = RideSyncTheme.colors
    RideCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(Space.m), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp), color = colors.accent)
                Spacer(Modifier.width(Space.s))
                Text("Looking for RideSync network…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(Space.s))
            Text(
                "No rides found yet. Make sure you’re connected to the host’s Wi-Fi hotspot.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
                textAlign = TextAlign.Center,
            )
        }
    }
}
