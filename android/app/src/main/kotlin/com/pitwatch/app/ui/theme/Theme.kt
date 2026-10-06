package com.pitwatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase

/** Material You: wallpaper-derived colors, light/dark following the system (minSdk 36 always has dynamic color). */
@Composable
fun PitWatchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Fixed status colors — the only non-theme colors in the app. */
object StatusColors {
    fun phase(phase: Phase): Color = when (phase) {
        Phase.PRE_QUEUE -> Color(0xFF636366)
        Phase.QUEUEING -> Color(0xFFFF9500)
        Phase.ON_DECK -> Color(0xFFFF6B00)
        Phase.ON_FIELD -> Color(0xFF30D158)
    }

    /** Badge label color, chosen for contrast on [phase]. */
    fun onPhase(phase: Phase): Color = if (phase == Phase.PRE_QUEUE) Color.White else Color(0xFF1C1C1E)

    fun alliance(alliance: MatchAlliance): Color = when (alliance) {
        MatchAlliance.RED -> Color(0xFFFF3B30)
        MatchAlliance.BLUE -> Color(0xFF1E6FFF)
    }
}
