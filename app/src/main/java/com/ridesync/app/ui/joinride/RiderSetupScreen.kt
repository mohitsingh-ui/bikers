package com.ridesync.app.ui.joinride

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PhoneAndroid
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ridesync.app.audio.AudioRouteState
import com.ridesync.app.ui.components.BorderedInfo
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * After choosing a ride, the rider names themselves and picks an audio device.
 * Bluetooth status is surfaced honestly — if only A2DP (music-only) is present,
 * we warn that voice will fall back to the phone mic.
 */
@Composable
fun RiderSetupScreen(
    defaultName: String,
    route: AudioRouteState,
    onRefreshRoute: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
    onBack: () -> Unit,
    onContinue: (riderName: String) -> Unit,
) {
    val colors = RideSyncTheme.colors
    var name by remember { mutableStateOf(defaultName) }
    var selected by remember { mutableStateOf(0) }

    RideScaffold(title = "Rider Setup", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            SectionLabel("What’s your rider name?")
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 24) name = it },
                placeholder = { Text("Rahul") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(Modifier.height(Space.xl))
            SectionLabel("Choose your audio device")
            Spacer(Modifier.height(Space.s))

            val deviceLabel = when (route.outputType) {
                AudioRouteState.OutputType.BLUETOOTH_A2DP,
                AudioRouteState.OutputType.BLUETOOTH_SCO,
                -> route.outputName
                AudioRouteState.OutputType.WIRED -> route.outputName
                else -> "Phone Speaker"
            }

            AudioDeviceOption(
                icon = if (route.isBluetooth) Icons.Filled.Headphones else Icons.Filled.PhoneAndroid,
                title = deviceLabel,
                subtitle = if (route.isBluetooth) "Bluetooth" else "Built-in",
                selected = selected == 0,
                onClick = { selected = 0; onRefreshRoute() },
            )
            Spacer(Modifier.height(Space.s))
            AudioDeviceOption(
                icon = Icons.Filled.PhoneAndroid,
                title = "Phone Speaker",
                subtitle = "Not recommended while riding",
                selected = selected == 1,
                onClick = { selected = 1 },
            )

            Spacer(Modifier.height(Space.l))
            if (!route.isBluetooth) {
                BorderedInfo(
                    icon = Icons.Filled.BluetoothDisabled,
                    tint = colors.statusYellow,
                    title = "Bluetooth headset not detected",
                    body = "Connect your helmet earbuds before starting the ride for hands-free voice.",
                    actionText = "Open Bluetooth Settings",
                    onAction = onOpenBluetoothSettings,
                )
            } else if (!route.hasBluetoothMic) {
                BorderedInfo(
                    icon = Icons.Filled.Bluetooth,
                    tint = colors.statusInfo,
                    title = "Music-only headset detected",
                    body = "This headset streams music but doesn’t expose a microphone. Your voice will use the phone mic. Many helmet intercoms support both — check your device.",
                    actionText = "Open Bluetooth Settings",
                    onAction = onOpenBluetoothSettings,
                )
            } else {
                RideCard(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = colors.statusGreen, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.m))
                        Text(
                            "${route.outputName} ready for voice + music.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.mutedText,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            PrimaryButton(
                text = "Continue",
                onClick = { onContinue(name.trim()) },
                enabled = name.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun AudioDeviceOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                androidx.compose.foundation.BorderStroke(1.5.dp, if (selected) colors.accent else colors.cardStroke),
                shape,
            )
            .clickable(onClick = onClick)
            .padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.accent else colors.mutedText, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
        }
        if (selected) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(colors.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = "Selected", tint = androidx.compose.ui.graphics.Color(0xFF1A0E00), modifier = Modifier.size(16.dp))
            }
        }
    }
}
