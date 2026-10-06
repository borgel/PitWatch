package com.pitwatch.app.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.pitwatch.core.model.Phase
import kotlin.test.assertEquals
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
}
