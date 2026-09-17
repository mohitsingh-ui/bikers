package com.ridesync.app.ui.devmode

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ridesync.app.ui.components.BorderedInfo
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * Developer mode: choose simulated riders and launch a full ride experience on
 * one phone — no network or other devices needed. The simulator scripts joins,
 * talking (with ducking), quick alerts, drops and reconnects.
 */
@Composable
fun DevModeScreen(
    onBack: () -> Unit,
    onStartSimulation: (List<String>) -> Unit,
) {
    val colors = RideSyncTheme.colors
    val candidates = listOf("Mohit", "Rahul", "Aman", "Rohit", "Priya", "Vikram")
    val selected = remember { mutableStateListOf("Mohit", "Rahul", "Aman", "Rohit") }

    RideScaffold(title = "Developer Mode", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            BorderedInfo(
                icon = Icons.Filled.Science,
                tint = colors.accent,
                title = "Test without four phones",
                body = "Runs the full ride UI with simulated riders. You’ll see talking indicators, automatic music ducking, quick alerts, and drop/reconnect events play out.",
            )

            Spacer(Modifier.height(Space.l))
            SectionLabel("Simulated riders")
            Spacer(Modifier.height(Space.s))
            candidates.forEach { name ->
                val isSelected = selected.contains(name)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(
                            androidx.compose.foundation.BorderStroke(1.5.dp, if (isSelected) colors.accent else colors.cardStroke),
                            RoundedCornerShape(14.dp),
                        )
                        .clickable {
                            if (isSelected) selected.remove(name) else if (selected.size < 7) selected.add(name)
                        }
                        .padding(Space.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    if (isSelected) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = colors.accent, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.height(Space.s))
            }

            Spacer(Modifier.height(Space.l))
            PrimaryButton(
                text = "Start Simulation (${selected.size} riders)",
                onClick = { onStartSimulation(selected.toList()) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xxl))
        }
    }
}
