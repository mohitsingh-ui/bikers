package com.ridesync.app.ui.ride

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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import com.ridesync.app.domain.session.RideState
import com.ridesync.app.ui.components.RideCard
import com.ridesync.app.ui.components.RideScaffold
import com.ridesync.app.ui.components.Space
import com.ridesync.app.ui.theme.RideSyncTheme

/**
 * Per-rider local voice mix. Each rider gets a volume slider + mute, applied
 * ONLY on this phone — muting a rider here doesn't disconnect them for anyone.
 */
@Composable
fun VoiceMixScreen(
    state: RideState,
    onSetVolume: (riderId: String, volume: Float) -> Unit,
    onSetMuted: (riderId: String, muted: Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    val volumes = remember { mutableStateMapOf<String, Float>() }
    val muted = remember { mutableStateMapOf<String, Boolean>() }

    RideScaffold(title = "Voice Mix", onBack = onBack) { mod ->
        Column(
            mod
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.l),
        ) {
            Spacer(Modifier.height(Space.s))
            Text(
                "Adjust how loud each rider sounds to you. This is local to your phone — it won’t affect anyone else.",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.mutedText,
            )
            Spacer(Modifier.height(Space.l))

            state.riders.filter { it.id != state.selfId }.forEach { rider ->
                val vol = volumes[rider.id] ?: 1f
                val isMuted = muted[rider.id] ?: false
                RideCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(rider.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                            MuteToggle(isMuted) {
                                val newMuted = !isMuted
                                muted[rider.id] = newMuted
                                onSetMuted(rider.id, newMuted)
                            }
                        }
                        Spacer(Modifier.height(Space.s))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = if (isMuted) 0f else vol,
                                onValueChange = {
                                    volumes[rider.id] = it
                                    if (isMuted && it > 0f) {
                                        muted[rider.id] = false
                                        onSetMuted(rider.id, false)
                                    }
                                    onSetVolume(rider.id, it)
                                },
                                enabled = !isMuted,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent,
                                ),
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(Modifier.width(Space.m))
                            Text(
                                "${((if (isMuted) 0f else vol) * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.width(44.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.s))
            }
            if (state.riders.size <= 1) {
                Text("No other riders connected yet.", style = MaterialTheme.typography.bodyMedium, color = colors.mutedText)
            }
            Spacer(Modifier.height(Space.xxl))
        }
    }
}

@Composable
private fun MuteToggle(muted: Boolean, onClick: () -> Unit) {
    val colors = RideSyncTheme.colors
    androidx.compose.foundation.layout.Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (muted) colors.statusRed.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
            contentDescription = if (muted) "Unmute" else "Mute",
            tint = if (muted) colors.statusRed else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(20.dp),
        )
    }
}
