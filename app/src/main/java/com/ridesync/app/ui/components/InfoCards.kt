package com.ridesync.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * Consistent info/warning/error card: icon + title + body + optional action.
 * Used for permission prompts, Bluetooth warnings, network errors, etc.
 */
@Composable
fun BorderedInfo(
    icon: ImageVector,
    tint: Color,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    RideCard(modifier = modifier.fillMaxWidth()) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(4.dp))
                    Text(body, style = MaterialTheme.typography.bodyMedium, color = RideSyncTheme.colors.mutedText)
                }
            }
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(Space.m))
                SecondaryButton(text = actionText, onClick = onAction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
