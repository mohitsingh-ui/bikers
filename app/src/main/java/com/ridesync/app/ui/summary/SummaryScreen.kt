package com.ridesync.app.ui.summary

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
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ridesync.app.domain.model.RideSummary
import com.ridesync.app.ui.components.PrimaryButton
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

@Composable
fun SummaryScreen(summary: RideSummary, onDone: () -> Unit) {
    val colors = RideSyncTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = Space.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.xxl))
        Spacer(Modifier.height(Space.xl))
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(colors.accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(Space.l))
        Text("Ride complete", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
        Spacer(Modifier.height(Space.xs))
        Text(summary.rideName, style = MaterialTheme.typography.titleMedium, color = colors.mutedText)

        Spacer(Modifier.height(Space.xl))
        RideCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                StatRow("Duration", formatDuration(summary.durationMs))
                Divider()
                StatRow("Riders", summary.riderCount.toString())
                Divider()
                StatRow("Connection interruptions", summary.interruptions.toString())
                Divider()
                StatRow("Music synchronized", formatDuration(summary.musicSyncedMs))
                Divider()
                StatRow("Time on voice", "${summary.talkSeconds}s")
            }
        }

        Spacer(Modifier.weight(1f))
        Text(
            "Everything stayed on your devices — no ride data was uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.faintText,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.m))
        PrimaryButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(Space.xxl))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = Space.m),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = RideSyncTheme.colors.mutedText)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(RideSyncTheme.colors.cardStroke))
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "%02dh %02dm".format(h, m) else "%02dm".format(m)
}
