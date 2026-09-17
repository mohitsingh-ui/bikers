package com.ridesync.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.ridesync.app.ui.theme.RidePalette
import java.util.Random

private data class Confetto(
    val startX: Float,
    val drift: Float,
    val fall: Float,
    val color: Color,
    val w: Float,
)

/**
 * A short, cheerful confetti burst. Increment [trigger] to fire a new one
 * (e.g. when a ride starts). It draws nothing when idle and never intercepts
 * touches, so it's safe to lay over any screen.
 */
@Composable
fun ConfettiOverlay(trigger: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(1f) } // 1f = finished / idle
    val pieces = remember { mutableStateListOf<Confetto>() }

    LaunchedEffect(trigger) {
        if (trigger <= 0) return@LaunchedEffect
        val rnd = Random()
        pieces.clear()
        repeat(70) {
            pieces.add(
                Confetto(
                    startX = rnd.nextFloat(),
                    drift = rnd.nextFloat() - 0.5f,
                    fall = 0.7f + rnd.nextFloat() * 0.6f,
                    color = RidePalette.riderColors[rnd.nextInt(RidePalette.riderColors.size)],
                    w = 7f + rnd.nextFloat() * 7f,
                ),
            )
        }
        progress.snapTo(0f)
        progress.animateTo(1f, animationSpec = tween(2400, easing = LinearEasing))
    }

    if (progress.value < 1f && pieces.isNotEmpty()) {
        val t = progress.value
        Canvas(modifier.fillMaxSize()) {
            pieces.forEach { p ->
                val px = (p.startX + p.drift * t * 0.4f) * size.width
                val py = (t * p.fall) * (size.height + 80f) - 60f
                drawRect(
                    color = p.color.copy(alpha = (1f - t).coerceIn(0f, 1f)),
                    topLeft = Offset(px, py),
                    size = Size(p.w, p.w * 0.6f),
                )
            }
        }
    }
}
