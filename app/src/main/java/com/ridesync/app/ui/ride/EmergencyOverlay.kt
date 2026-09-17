package com.ridesync.app.ui.ride

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme
import kotlinx.coroutines.delay

/**
 * Full-width emergency banner that slides in over any screen when a rider
 * triggers an alert. High-contrast red; auto-dismisses after a while but can be
 * dismissed immediately.
 */
@Composable
fun EmergencyOverlay(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RideSyncTheme.colors
    LaunchedEffect(message) {
        delay(12000)
        onDismiss()
    }
    Row(
        modifier
            .fillMaxWidth()
            .padding(Space.m)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.statusRed)
            .padding(Space.l),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(Space.m))
        Column(Modifier.weight(1f)) {
            Text("EMERGENCY ALERT", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            Text(message, color = Color.White, style = MaterialTheme.typography.titleMedium)
            Text("All riders notified", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
        }
        Icon(
            Icons.Filled.Close,
            contentDescription = "Dismiss",
            tint = Color.White,
            modifier = Modifier.size(24.dp).clickable(onClick = onDismiss),
        )
    }
}
