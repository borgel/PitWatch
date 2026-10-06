package com.pitwatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase

private val SCOREBOARD_BLACK = Color(0xFF0F1216)

/** Material You (wallpaper) colors in the Scoreboard type; light/dark follow the system (minSdk 36 always has dynamic color). */
@Composable
fun PitWatchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) scoreboardDark(dynamicDarkColorScheme(context)) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, typography = PitWatchTypography, content = content)
}

/** Dark mode reads as a scoreboard: ground and surface pulled 60 % toward near-black; the accent is untouched. */
fun scoreboardDark(scheme: ColorScheme): ColorScheme {
    val ground = lerp(scheme.surface, SCOREBOARD_BLACK, 0.6f)
    return scheme.copy(background = ground, surface = ground)
}

/** Fixed status colors — the only non-theme colors in the app. */
object StatusColors {
    fun phase(phase: Phase): Color = when (phase) {
        Phase.PRE_QUEUE -> Color(0xFF636366)
        Phase.QUEUEING -> Color(0xFFFF9500)
        Phase.ON_DECK -> Color(0xFFFF6B00)
        Phase.ON_FIELD -> Color(0xFF30D158)
    }

    /** [phase] as a text color: the bright phase colors are too light to read on light surfaces, so darken them there. */
    fun phaseText(phase: Phase, onLight: Boolean): Color = if (onLight) lerp(phase(phase), Color.Black, 0.45f) else phase(phase)

    /** Badge label color, chosen for contrast on [phase]. */
    fun onPhase(phase: Phase): Color = if (phase == Phase.PRE_QUEUE) Color.White else Color(0xFF1C1C1E)

    /** Band colors: deep enough for white text in light and dark mode. */
    fun alliance(alliance: MatchAlliance): Color = when (alliance) {
        MatchAlliance.RED -> Color(0xFFC62828)
        MatchAlliance.BLUE -> Color(0xFF1E5BD6)
    }

    data class Pill(val container: Color, val content: Color)

    /** WIN / LOSS pill colors for an outcome code ("W", "L", "T"); null for a tie, which uses the theme's neutral surface. */
    fun outcome(code: String): Pill? = when (code) {
        "W" -> Pill(Color(0xFF30D158), Color(0xFF0B1A10))
        "L" -> Pill(Color(0xFFD32F2F), Color.White)
        else -> null
    }

    /** Live Update progress segments: queue, on deck, on field, then the match itself. */
    val notificationSegments: List<Color> =
        listOf(Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD).map(::phase) + Color(0xFF0A84FF)
}
