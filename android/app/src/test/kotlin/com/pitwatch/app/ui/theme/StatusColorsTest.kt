package com.pitwatch.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class StatusColorsTest {
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `phase colors match the spec`() {
        assertEquals(listOf(0xFF636366, 0xFFFF9500, 0xFFFF6B00, 0xFF30D158).map { it.toInt() },
            Phase.entries.map { StatusColors.phase(it).toArgb() })
    }

    @Test
    fun `badge text is readable on every phase color`() {
        for (phase in Phase.entries) assertTrue(contrast(StatusColors.phase(phase), StatusColors.onPhase(phase)) >= 4.5, "$phase")
    }

    @Test
    fun `alliance bands are the deep scoreboard colors, readable with white text`() {
        assertEquals(0xFFC62828.toInt(), StatusColors.alliance(MatchAlliance.RED).toArgb())
        assertEquals(0xFF1E5BD6.toInt(), StatusColors.alliance(MatchAlliance.BLUE).toArgb())
        for (a in MatchAlliance.entries) assertTrue(contrast(StatusColors.alliance(a), Color.White) >= 4.5, "$a")
    }

    @Test
    fun `outcome pills are readable and a tie has none`() {
        for (code in listOf("W", "L")) {
            val pill = assertNotNull(StatusColors.outcome(code))
            assertTrue(contrast(pill.container, pill.content) >= 4.5, code)
        }
        assertNull(StatusColors.outcome("T"))
    }

    @Test
    fun `notification segments are queue, on deck, on field, then the match`() {
        assertEquals(
            listOf(Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD).map { StatusColors.phase(it) } + Color(0xFF0A84FF),
            StatusColors.notificationSegments,
        )
    }

    @Test
    fun `phase text is readable on light surfaces and unchanged on dark ones`() {
        for (phase in listOf(Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD)) {
            assertTrue(contrast(StatusColors.phaseText(phase, onLight = true), Color(0xFFF2F0F7)) >= 4.5, "$phase")
            assertEquals(StatusColors.phase(phase), StatusColors.phaseText(phase, onLight = false))
        }
    }
}
