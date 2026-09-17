package com.ridesync.app.ui.audiocheck

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ridesync.app.ui.components.MeterBar
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.SectionLabel
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

data class CheckItem(val label: String, val detail: String, val ok: Boolean, val icon: ImageVector)

/**
 * Pre-ride audio + network check. The mic test shows a live input meter while
 * the rider speaks (fed from [micLevel] 0..1). This is the "everything works"
 * confidence screen before starting.
 */
@Composable
fun AudioCheckScreen(
    headsetOk: Boolean,
    headsetName: String,
    micOk: Boolean,
    speakerOk: Boolean,
    networkOk: Boolean,
    micLevel: Float,
    testing: Boolean,
    onStartTest: () -> Unit,
    onBack: () -> Unit,
    onProceed: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    val checks = listOf(
        CheckItem("Headset", if (headsetOk) headsetName else "Not connected", headsetOk, Icons.Filled.Headphones),
        CheckItem("Microphone", if (micOk) "Working" else "No access", micOk, Icons.Filled.Mic),
        CheckItem("Speaker", if (speakerOk) "Working" else "Unavailable", speakerOk, Icons.Filled.VolumeUp),
        CheckItem("Network", if (networkOk) "Ready" else "Not connected", networkOk, Icons.Filled.Wifi),
    )

    RideScaffold(title = "Audio Check", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            checks.forEach { item ->
                CheckRow(item)
                Spacer(Modifier.height(Space.s))
            }

            Spacer(Modifier.height(Space.l))
            SectionLabel("Microphone test")
            Spacer(Modifier.height(Space.s))
            RideCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        if (testing) "Speak now…" else "Tap Test Audio, then speak. You should see the meter move.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.mutedText,
                    )
                    Spacer(Modifier.height(Space.m))
                    MeterBar(fraction = if (testing) micLevel else 0f, color = colors.accent, height = 14.dp)
                }
            }

            Spacer(Modifier.height(Space.m))
            PrimaryButton(
                text = if (testing) "Testing…" else "Test Audio",
                onClick = onStartTest,
                modifier = Modifier.fillMaxWidth(),
                enabled = !testing,
            )
            Spacer(Modifier.height(Space.m))
            com.ridesync.app.ui.components.SecondaryButton(text = "Looks good — continue", onClick = onProceed, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun CheckRow(item: CheckItem) {
    val colors = RideSyncTheme.colors
    RideCard(modifier = Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Space.m)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(item.icon, contentDescription = null, tint = colors.mutedText, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text(item.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(item.detail, style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            }
            Icon(
                if (item.ok) Icons.Filled.CheckCircle else Icons.Filled.Error,
                contentDescription = if (item.ok) "OK" else "Attention needed",
                tint = if (item.ok) colors.statusGreen else colors.statusYellow,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
