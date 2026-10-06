package com.pitwatch.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `dark mode pulls ground and surface toward scoreboard black`() {
        val base = darkColorScheme()
        val out = scoreboardDark(base)
        val expected = lerp(base.surface, Color(0xFF0F1216), 0.6f)
        assertEquals(expected, out.surface)
        assertEquals(expected, out.background)
        assertEquals(base.primary, out.primary) // the accent stays the wallpaper's
    }

    @Test
    fun `text stays readable on the darker surface`() {
        val out = scoreboardDark(darkColorScheme())
        val (hi, lo) = listOf(out.onSurface.luminance(), out.surface.luminance()).sortedDescending()
        assertTrue((hi + 0.05) / (lo + 0.05) >= 4.5)
    }

    @Test
    fun `display and titles are condensed, body is Barlow`() {
        assertEquals(BarlowCondensed, PitWatchTypography.titleLarge.fontFamily)
        assertEquals(BarlowCondensed, PitWatchTypography.labelLarge.fontFamily)
        assertEquals(Barlow, PitWatchTypography.bodyLarge.fontFamily)
        assertEquals(BarlowCondensed, PitWatchType.countdownHero.fontFamily)
    }
}
