package com.ridesync.app.ui.createride

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
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

            Spacer(Modifier.height(Space.xl))
            RideCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = colors.accent, modifier = Modifier.width(20.dp))
                    Spacer(Modifier.width(Space.m))
                    Text(
                        "Everyone must be on the same Wi-Fi hotspot. RideSync works fully offline — no internet needed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                    )
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
