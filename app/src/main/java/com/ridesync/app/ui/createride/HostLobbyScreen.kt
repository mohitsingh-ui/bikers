package com.ridesync.app.ui.createride

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Wifi
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.domain.session.RideState
import com.ridesync.app.networking.protocol.RideCodes
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.QrCode
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RiderRow
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * Host waiting room: shows the PIN + QR, the live roster as riders join, and
 * the Start Ride button (enabled as soon as the host is ready — riding solo is
 * allowed; more can join mid-ride).
 */
@Composable
fun HostLobbyScreen(
    state: RideState,
    localIp: String?,
    onStartRide: () -> Unit,
    onLeave: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    val ride = state.ride
    var showQr by remember { mutableStateOf(false) }

    RideScaffold(title = "Ride Ready", onBack = onLeave) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(
                ride?.name ?: "Group Ride",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOST", style = MaterialTheme.typography.labelMedium, color = colors.accent, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(Space.s))
                Text(ride?.hostName ?: "", style = MaterialTheme.typography.bodyLarge, color = colors.mutedText)
            }

            Spacer(Modifier.height(Space.l))

            // Connection count
            RideCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${state.connectedCount} / ${ride?.maxRiders ?: 4} riders connected",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (state.connectedCount < 2) {
                        Spacer(Modifier.height(Space.xs))
                        Text("Waiting for riders…", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                    }
                    Spacer(Modifier.height(Space.l))

                    if (showQr && ride != null) {
                        val payload = RideCodes.buildJoinUri(
                            rideId = ride.rideId,
                            rideName = ride.name,
                            hostName = ride.hostName,
                            hostAddress = localIp ?: ride.hostAddress ?: "",
                            controlPort = ride.controlPort,
                            pin = ride.pin,
                        )
                        QrCode(content = payload, size = 220.dp)
                        Spacer(Modifier.height(Space.m))
                        Text("Scan to join", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                    } else {
                        SectionLabel("Ride PIN")
                        Spacer(Modifier.height(Space.s))
                        Text(
                            ride?.pin ?: "----",
                            fontSize = 46.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 12.sp,
                            color = colors.accent,
                        )
                    }
                    Spacer(Modifier.height(Space.l))
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable { showQr = !showQr }.padding(Space.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (showQr) "Show PIN instead" else "Show QR code", color = colors.mutedText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(Modifier.height(Space.l))
            HotspotHint(localIp)

            Spacer(Modifier.height(Space.l))
            SectionLabel("Riders")
            Spacer(Modifier.height(Space.s))
            state.riders.forEach { rider ->
                RiderRow(rider = rider, isSelf = rider.id == state.selfId)
                Spacer(Modifier.height(Space.s))
            }

            Spacer(Modifier.height(Space.l))
            PrimaryButton(text = "Start Ride", onClick = onStartRide, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun HotspotHint(localIp: String?) {
    val colors = RideSyncTheme.colors
    RideCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Wifi, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.m))
            Column {
                Text("Hotspot is your ride network", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "Turn on your phone’s Wi-Fi hotspot in Settings. Riders connect to it, then open RideSync." +
                        (localIp?.let { "\nYour address: $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.mutedText,
                )
            }
        }
    }
}
