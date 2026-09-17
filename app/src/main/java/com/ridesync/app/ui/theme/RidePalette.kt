package com.ridesync.app.ui.theme

import androidx.compose.ui.graphics.Color
import com.ridesync.app.domain.model.QuickAlertKind

/**
 * A bright, friendly colour palette layered on top of the dark theme. Used to
 * give riders their own colour and each quick alert a distinct look, so the
 * app feels lively instead of monochrome — while the core charcoal + accent
 * theme keeps everything calm and readable.
 */
object RidePalette {

    /** Distinct rider colours, chosen to stay legible on the dark surfaces. */
    val riderColors: List<Color> = listOf(
        Color(0xFFFF6B6B), // coral
        Color(0xFF4ECDC4), // teal
        Color(0xFF5B9BFF), // sky blue
        Color(0xFFA78BFA), // violet
        Color(0xFFFFB020), // amber
        Color(0xFF34D399), // mint
        Color(0xFFF472B6), // pink
        Color(0xFFFB923C), // orange
        Color(0xFF38BDF8), // cyan
        Color(0xFFC084FC), // lilac
    )

    /** A stable colour for a rider, derived from a stable key (their id). */
    fun forRider(key: String): Color {
        if (key.isEmpty()) return riderColors[0]
        val h = key.fold(0) { acc, c -> acc * 31 + c.code } and 0x7FFFFFFF
        return riderColors[h % riderColors.size]
    }

    /** A vivid colour per quick-alert kind. */
    fun forAlert(kind: QuickAlertKind): Color = when (kind) {
        QuickAlertKind.STOPPING -> Color(0xFFFF5A5F) // red — stop
        QuickAlertKind.FUEL -> Color(0xFF34D399) // green — go refuel
        QuickAlertKind.BREAK -> Color(0xFF4ECDC4) // teal — relax
        QuickAlertKind.SLOW_DOWN -> Color(0xFFFFB020) // amber — caution
        QuickAlertKind.TURNING -> Color(0xFFA78BFA) // violet — direction
        QuickAlertKind.HAZARD -> Color(0xFFFB7185) // rose — danger
    }
}
