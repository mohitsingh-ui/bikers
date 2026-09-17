package com.ridesync.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ridesync.app.data.preferences.AccentChoice
import com.ridesync.app.data.preferences.AppTheme

/** Extra semantic colors not covered by Material's scheme. */
data class RideSyncColors(
    val accent: Color,
    val accentBright: Color,
    val statusGreen: Color,
    val statusYellow: Color,
    val statusRed: Color,
    val statusInfo: Color,
    val cardStroke: Color,
    val elevatedSurface: Color,
    val mutedText: Color,
    val faintText: Color,
)

val LocalRideSyncColors = staticCompositionLocalOf {
    RideSyncColors(
        accent = Ember,
        accentBright = EmberBright,
        statusGreen = StatusGreen,
        statusYellow = StatusYellow,
        statusRed = StatusRed,
        statusInfo = StatusBlue,
        cardStroke = Charcoal700,
        elevatedSurface = Charcoal750,
        mutedText = MutedGrey,
        faintText = FaintGrey,
    )
}

private fun accentColor(choice: AccentChoice): Pair<Color, Color> = when (choice) {
    AccentChoice.EMBER -> Ember to EmberBright
    AccentChoice.AMBER -> Amber to Color(0xFFFFC44D)
    AccentChoice.COBALT -> Cobalt to Color(0xFF60A5FA)
}

private fun darkScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = Color(0xFF1A0E00),
    primaryContainer = EmberDim,
    onPrimaryContainer = SoftWhite,
    secondary = MutedGrey,
    onSecondary = Charcoal900,
    background = Charcoal900,
    onBackground = SoftWhite,
    surface = Charcoal800,
    onSurface = SoftWhite,
    surfaceVariant = Charcoal700,
    onSurfaceVariant = MutedGrey,
    outline = Charcoal600,
    error = StatusRed,
    onError = Color(0xFF2A0A0A),
)

private fun lightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnBackground,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = Color(0xFFCBD0DA),
    error = Color(0xFFD03A3A),
    onError = Color.White,
)

@Composable
fun RideSyncTheme(
    appTheme: AppTheme = AppTheme.DARK,
    accentChoice: AccentChoice = AccentChoice.EMBER,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val dark = when (appTheme) {
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
        AppTheme.SYSTEM -> systemDark
    }
    val (accent, accentBright) = accentColor(accentChoice)
    val scheme = if (dark) darkScheme(accent) else lightScheme(accent)

    val rideColors = RideSyncColors(
        accent = accent,
        accentBright = accentBright,
        statusGreen = StatusGreen,
        statusYellow = StatusYellow,
        statusRed = StatusRed,
        statusInfo = if (dark) StatusBlue else Cobalt,
        cardStroke = if (dark) Charcoal700 else Color(0xFFDDE1E8),
        elevatedSurface = if (dark) Charcoal750 else LightSurfaceVariant,
        mutedText = if (dark) MutedGrey else LightOnSurfaceVariant,
        faintText = if (dark) FaintGrey else Color(0xFF9AA0AD),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !dark
        }
    }

    CompositionLocalProvider(LocalRideSyncColors provides rideColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = RideSyncTypography,
            content = content,
        )
    }
}

/** Convenience accessor: RideSyncTheme.colors. */
object RideSyncTheme {
    val colors: RideSyncColors
        @Composable get() = LocalRideSyncColors.current
}

private val InterFallback = FontFamily.SansSerif

val RideSyncTypography = Typography(
    displaySmall = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineLarge = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Bold, fontSize = 28.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Bold, fontSize = 23.sp),
    titleLarge = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Medium, fontSize = 12.sp, letterSpacing = 0.6.sp),
    labelSmall = TextStyle(fontFamily = InterFallback, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.8.sp),
)
