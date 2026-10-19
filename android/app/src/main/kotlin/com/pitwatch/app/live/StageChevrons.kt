package com.pitwatch.app.live

import androidx.compose.ui.graphics.toArgb
import com.pitwatch.app.ui.theme.StatusColors

/**
 * The stage band's look, shared by the live card and the widgets: the iOS app's chevrons, "tinted" (asked for, from
 * the mock-ups). Which stages fill is unchanged — up to the current one ([com.pitwatch.app.ui.scoreboard.PhaseSteps]);
 * the current stage is solid in its color, earlier ones a wash of theirs, later ones empty.
 */
object StageChevrons {
    val NAMES = listOf("QUEUE", "DECK", "FIELD", "START")

    /** Text on a solid stage color (the phase pill's). */
    const val ON_STAGE = 0xFF1C1C1E.toInt()
    private const val ON_STAGE_TIME = 0xB81C1C1E.toInt()

    private val STAGE = StatusColors.notificationSegments.map { it.toArgb() }

    enum class State { EMPTY, PAST, CURRENT }

    fun state(step: Int, current: Int?): State = when {
        current == null || step > current -> State.EMPTY
        step == current -> State.CURRENT
        else -> State.PAST
    }

    data class Look(val fill: Int, val name: Int, val time: Int, val bold: Boolean)

    /** Stage [index] (0-based) of the band, on a card or widget in [colors]. */
    fun look(index: Int, current: Int?, colors: LiveCard.Colors, dark: Boolean): Look {
        val color = STAGE[index]
        return when (state(index + 1, current)) {
            State.CURRENT -> Look(color, ON_STAGE, ON_STAGE_TIME, bold = true)
            State.PAST -> {
                // On light, the name is the color darkened (as StatusColors.phaseText) so it reads on the pale wash.
                val name = if (dark) color else blend(color, 0xFF000000.toInt(), 0.45f)
                Look(blend(colors.background, color, if (dark) 0.28f else 0.22f), name, name, bold = false)
            }
            State.EMPTY -> Look(colors.empty, colors.muted, colors.muted, bold = false)
        }
    }

    private fun blend(from: Int, to: Int, t: Float): Int {
        fun ch(shift: Int) = ((from shr shift and 0xFF) * (1 - t) + (to shr shift and 0xFF) * t).toInt() shl shift
        return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
    }
}
