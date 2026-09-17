package com.ridesync.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.domain.model.MusicUiState
import com.ridesync.app.domain.model.SyncStatus
import com.ridesync.app.domain.model.TrackSource
import com.ridesync.app.ui.theme.RideSyncTheme
import kotlin.math.absoluteValue

/**
 * Compact now-playing card used on the ride screen. Shows album art
 * (procedurally drawn for demo tracks), title/artist, progress, transport, and
 * an honest sync-status line. Transport is disabled when the caller can't
 * control playback (host-only music on a client).
 */
@Composable
fun NowPlayingCard(
    music: MusicUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onResync: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
) {
    val colors = RideSyncTheme.colors
    RideCard(modifier = modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                SectionLabel("Now Playing")
                Spacer(Modifier.weight(1f))
                SyncStatusPill(music.syncStatus)
            }
            Spacer(Modifier.height(Space.m))

            if (music.track == null) {
                EmptyMusic()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(seed = music.track.id, size = if (compact) 56.dp else 96.dp)
                    Spacer(Modifier.width(Space.m))
                    Column(Modifier.weight(1f)) {
                        Text(
                            music.track.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            music.track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.height(Space.m))

                if (music.track.source == TrackSource.EXTERNAL && !music.trackAvailableLocally) {
                    // Honest fallback: we can't scrub this app's player.
                    ExternalFallback(onOpenExternal)
                } else {
                    ProgressRow(music.positionMs, music.durationMs, colors.accent)
                    Spacer(Modifier.height(Space.s))
                    TransportRow(
                        isPlaying = music.isPlaying,
                        enabled = music.canControl,
                        onPlayPause = onPlayPause,
                        onNext = onNext,
                        onPrevious = onPrevious,
                        onResync = onResync,
                    )
                    if (!music.canControl) {
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "Host controls synchronized music.",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.faintText,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncStatusPill(status: SyncStatus) {
    val colors = RideSyncTheme.colors
    val (color, label) = when (status) {
        SyncStatus.SYNCED -> colors.statusGreen to "Synced"
        SyncStatus.RESYNCING -> colors.statusYellow to "Resyncing…"
        SyncStatus.LOCAL_ONLY -> colors.faintText to "Local"
        SyncStatus.IDLE -> colors.faintText to "Idle"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.mutedText)
    }
}

@Composable
private fun ProgressRow(positionMs: Long, durationMs: Long, accent: Color) {
    val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    Column {
        MeterBar(fraction = fraction, color = accent, height = 6.dp)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.labelMedium, color = RideSyncTheme.colors.mutedText)
            Text(formatTime(durationMs), style = MaterialTheme.typography.labelMedium, color = RideSyncTheme.colors.mutedText)
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    enabled: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onResync: () -> Unit,
) {
    val colors = RideSyncTheme.colors
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrevious, enabled = enabled) {
            Icon(Icons.Filled.SkipPrevious, "Previous", tint = transportTint(enabled), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(Space.m))
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(if (enabled) colors.accent else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onPlayPause, enabled = enabled) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = if (enabled) Color(0xFF1A0E00) else colors.faintText,
                    modifier = Modifier.size(30.dp),
                )
            }
        }
        Spacer(Modifier.width(Space.m))
        IconButton(onClick = onNext, enabled = enabled) {
            Icon(Icons.Filled.SkipNext, "Next", tint = transportTint(enabled), modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(Space.s))
        IconButton(onClick = onResync, enabled = enabled) {
            Icon(Icons.Filled.Sync, "Resync everyone", tint = transportTint(enabled), modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun transportTint(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSurface else RideSyncTheme.colors.faintText

@Composable
private fun ExternalFallback(onOpen: () -> Unit) {
    Column {
        Text(
            "This track plays in an external music app, which can’t be scrubbed remotely. Open it to listen along.",
            style = MaterialTheme.typography.bodyMedium,
            color = RideSyncTheme.colors.mutedText,
        )
        Spacer(Modifier.height(Space.s))
        SecondaryButton(text = "Open in music app", onClick = onOpen, icon = Icons.Outlined.OpenInNew)
    }
}

@Composable
private fun EmptyMusic() {
    Text(
        "Nothing playing. The host can start synchronized music.",
        style = MaterialTheme.typography.bodyMedium,
        color = RideSyncTheme.colors.mutedText,
    )
}

/** Procedural gradient "album art" for demo tracks, seeded by track id. */
@Composable
fun AlbumArt(seed: String, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val h = seed.hashCode().absoluteValue
    val hue1 = (h % 360)
    val base = hsvColor(hue1.toFloat(), 0.55f, 0.85f)
    val base2 = hsvColor(((hue1 + 40) % 360).toFloat(), 0.65f, 0.55f)
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(base, base2))),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val step = this.size.width / 5
            for (i in 0..5) {
                drawLine(
                    color = Color.White.copy(alpha = 0.10f),
                    start = androidx.compose.ui.geometry.Offset(i * step, 0f),
                    end = androidx.compose.ui.geometry.Offset(0f, i * step),
                    strokeWidth = 2f,
                )
            }
        }
        Icon(Icons.Filled.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(size * 0.3f))
    }
}

private fun hsvColor(h: Float, s: Float, v: Float): Color {
    val c = v * s
    val x = c * (1 - kotlin.math.abs((h / 60f) % 2 - 1))
    val m = v - c
    val (r, g, b) = when {
        h < 60 -> Triple(c, x, 0f)
        h < 120 -> Triple(x, c, 0f)
        h < 180 -> Triple(0f, c, x)
        h < 240 -> Triple(0f, x, c)
        h < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m)
}

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return "%02d:%02d".format(m, s)
}
