package com.ridesync.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.ridesync.app.ui.theme.RideSyncTheme
import kotlin.math.sin

/**
 * The single most important control in the app. A large circular press-and-hold
 * button. Idle: outlined ring. Pressed: filled accent with a live pulse. Fully
 * accessible with a semantic label and a 200dp+ hit target.
 */
@Composable
fun PushToTalkButton(
    isTransmitting: Boolean,
    enabled: Boolean,
    onPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 200.dp,
) {
    val colors = RideSyncTheme.colors
    var pressed by remember { mutableStateOf(false) }
    val active = pressed || isTransmitting

    val ringScale by animateFloatAsState(if (active) 1.06f else 1f, label = "ringScale")
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.05f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulseAlpha",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(
            Modifier
                .size(size)
                .semantics {
                    contentDescription = if (active) "Transmitting. Release to stop talking." else "Push and hold to talk"
                }
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onPress = {
                            pressed = true
                            onPressChange(true)
                            try {
                                awaitRelease()
                            } finally {
                                pressed = false
                                onPressChange(false)
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(size)) {
                val radius = this.size.minDimension / 2f
                if (active) {
                    // Outer pulse halo
                    drawCircle(color = colors.accent.copy(alpha = pulseAlpha), radius = radius * ringScale)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(colors.accentBright, colors.accent),
                            center = center,
                            radius = radius * 0.92f,
                        ),
                        radius = radius * 0.86f,
                    )
                } else {
                    drawCircle(
                        color = if (enabled) colors.accent.copy(alpha = 0.10f) else Color.Gray.copy(alpha = 0.08f),
                        radius = radius * 0.86f,
                    )
                    drawCircle(
                        color = if (enabled) colors.accent else Color.Gray,
                        radius = radius * 0.86f,
                        style = Stroke(width = 3.dp.toPx()),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = null,
                    tint = if (active) Color(0xFF1A0E00) else if (enabled) colors.accent else Color.Gray,
                    modifier = Modifier.size(size * 0.24f),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (active) "TALKING" else "HOLD TO TALK",
                    color = if (active) Color(0xFF1A0E00) else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (size > 180.dp) 15.sp else 13.sp,
                    letterSpacing = 1.sp,
                )
            }
        }
    }
}

/** Animated transmission waveform shown when someone is talking. */
@Composable
fun TalkingWaveform(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1100, easing = androidx.compose.animation.core.LinearEasing)),
        label = "phase",
    )
    Canvas(modifier.height(40.dp)) {
        val midY = size.height / 2f
        val bars = 28
        val gap = size.width / bars
        for (i in 0 until bars) {
            val x = i * gap + gap / 2
            val env = sin(phase + i * 0.55f)
            val amp = (0.25f + 0.75f * ((env + 1f) / 2f)) * (midY * 0.9f)
            drawLine(
                color = color.copy(alpha = 0.85f),
                start = Offset(x, midY - amp),
                end = Offset(x, midY + amp),
                strokeWidth = gap * 0.4f,
            )
        }
    }
}
