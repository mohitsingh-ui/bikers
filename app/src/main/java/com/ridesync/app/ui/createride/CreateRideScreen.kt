package com.ridesync.app.ui.createride

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import com.ridesync.app.domain.model.ConnectionMode
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

@Composable
fun CreateRideScreen(
    defaultRideName: String,
    defaultRiderName: String,
    onBack: () -> Unit,
    onCreate: (rideName: String, hostName: String) -> Unit,
) {
    val colors = RideSyncTheme.colors
    var rideName by remember { mutableStateOf(defaultRideName) }
    var hostName by remember { mutableStateOf(defaultRiderName) }
    var connMode by remember { mutableStateOf(ConnectionMode.WIFI) }

    RideScaffold(title = "Create Ride", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(
                "You’ll be the host. Turn on your phone’s hotspot and have your crew connect to it, then join with the PIN or QR code.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
            )
            Spacer(Modifier.height(Space.xl))

            SectionLabel("Ride name")
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = rideName,
                onValueChange = { if (it.length <= 40) rideName = it },
                placeholder = { Text("Delhi Weekend Ride") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            )

            Spacer(Modifier.height(Space.l))
            SectionLabel("Your rider name")
            Spacer(Modifier.height(Space.s))
            OutlinedTextField(
                value = hostName,
                onValueChange = { if (it.length <= 24) hostName = it },
                placeholder = { Text("Mohit") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )

            Spacer(Modifier.height(Space.l))
            SectionLabel("How riders connect")
            Spacer(Modifier.height(Space.s))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                ConnectionMode.entries.forEach { mode ->
                    ConnectionModeChip(
                        mode = mode,
                        selected = connMode == mode,
                        onClick = { connMode = mode },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(Space.s))
            RideCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = connModeColor(connMode), modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(Space.m))
                    Column {
                        Text(connMode.note, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
                        if (!connMode.available) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Wi-Fi will be used for this ride.",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.statusYellow,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.xl))
            PrimaryButton(
                text = "Create Ride",
                onClick = { onCreate(rideName.trim(), hostName.trim()) },
                enabled = rideName.trim().isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

private fun connModeColor(mode: ConnectionMode): Color = when (mode) {
    ConnectionMode.WIFI -> Color(0xFF34D399)
    ConnectionMode.BLUETOOTH -> Color(0xFF5B9BFF)
    ConnectionMode.RADIO -> Color(0xFFA78BFA)
}

@Composable
private fun ConnectionModeChip(
    mode: ConnectionMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RideSyncTheme.colors
    val color = connModeColor(mode)
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier
            .clip(shape)
            .background(if (selected) color.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface)
            .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) color else colors.cardStroke), shape)
            .clickable(onClick = onClick)
            .padding(vertical = Space.m, horizontal = Space.s),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(mode.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            mode.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) color else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            if (mode.available) mode.tagline else "Needs hardware",
            style = MaterialTheme.typography.labelSmall,
            color = colors.faintText,
        )
    }
}
