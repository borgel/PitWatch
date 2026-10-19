package com.pitwatch.app.live

import androidx.compose.ui.graphics.toArgb
import com.pitwatch.app.ui.theme.StatusColors
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Asked for: the iOS chevron band in place of the four bars, "tinted": earlier stages a wash of their color. */
class StageChevronsTest {
    private val dark = LiveCard.Colors(background = 0xFF101217.toInt(), text = 0xFFE3E2E9.toInt(), muted = 0xFF8F919B.toInt(), empty = 0xFF2A2D36.toInt())
    private val light = LiveCard.Colors(background = 0xFFFAF8FF.toInt(), text = 0xFF1A1B21.toInt(), muted = 0xFF6B6D78.toInt(), empty = 0xFFE1E0EA.toInt())
    private val stage = StatusColors.notificationSegments.map { it.toArgb() }

    @Test
    fun `stages fill by the same rule as before - up to the current one`() {
        assertEquals(
            listOf(StageChevrons.State.PAST, StageChevrons.State.CURRENT, StageChevrons.State.EMPTY, StageChevrons.State.EMPTY),
            (1..4).map { StageChevrons.state(it, current = 2) },
        )
        assertTrue((1..4).all { StageChevrons.state(it, current = null) == StageChevrons.State.EMPTY })
    }

    @Test
    fun `the current stage is solid in its color, bold, with dark text`() {
        val look = StageChevrons.look(1, current = 2, colors = dark, dark = true)
        assertEquals(stage[1], look.fill)
        assertTrue(look.bold)
        assertEquals(StageChevrons.ON_STAGE, look.name)
    }

    @Test
    fun `earlier stages are a wash of their color over the card, named in that color`() {
        val look = StageChevrons.look(0, current = 2, colors = dark, dark = true)
        assertNotEquals(stage[0], look.fill)
        assertNotEquals(dark.background, look.fill)
        assertEquals(stage[0], look.name)
        assertFalse(look.bold)
        // On light, the stage color is darkened so the name reads on the pale wash.
        val pale = StageChevrons.look(0, current = 2, colors = light, dark = false)
        assertTrue(luma(pale.name) < luma(stage[0]))
    }

    @Test
    fun `later stages are empty, in the muted text color`() {
        val look = StageChevrons.look(3, current = 2, colors = light, dark = false)
        assertEquals(light.empty, look.fill)
        assertEquals(light.muted, look.name)
        assertEquals(light.muted, look.time)
    }

    private fun luma(c: Int) = 0.299 * (c shr 16 and 0xFF) + 0.587 * (c shr 8 and 0xFF) + 0.114 * (c and 0xFF)
}
