package com.ridesync.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ridesync.app.ui.theme.RideSyncTheme

/** 8dp spacing scale used throughout. */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Rounded, subtly-stroked surface card — the core container of the UI. */
@Composable
fun RideCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    stroke: Boolean = true,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = if (stroke) BorderStroke(1.dp, RideSyncTheme.colors.cardStroke) else null,
    ) {
        Box(Modifier.padding(contentPadding)) { content() }
    }
}

/** Primary CTA in accent color. Large touch target. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = RideSyncTheme.colors.accent,
            contentColor = Color(0xFF1A0E00),
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = RideSyncTheme.colors.faintText,
        ),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(Space.s))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, RideSyncTheme.colors.cardStroke),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(Space.s))
        }
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
    }
}

/** Section label in the muted, tracked style used across screens. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = RideSyncTheme.colors.mutedText,
        letterSpacing = 1.2.sp,
        modifier = modifier,
    )
}

/** A coloured status dot ALWAYS paired with a label (accessibility: never color-only). */
@Composable
fun StatusChip(
    color: Color,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        } else {
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.width(Space.xs + 2.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Simple labelled meter (e.g. connection quality, volume). */
@Composable
fun LabeledMeter(
    label: String,
    fraction: Float,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = RideSyncTheme.colors.mutedText)
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(Space.s))
        MeterBar(fraction = fraction, color = color)
    }
}

@Composable
fun MeterBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 8.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(color),
        )
    }
}
