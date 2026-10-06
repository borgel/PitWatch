package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import java.time.ZoneId

/** Where breaks come from: Nexus's explicit `breakAfter` markers when an event has any, else inference. */
object ScheduleBreaks {
    fun forEvent(nexusEvent: NexusEvent, zone: ZoneId): List<ScheduleBreak> =
        if (nexusEvent.matches.any { it.breakAfter != null }) {
            fromMarkers(nexusEvent.matches)
        } else {
            ScheduleBreakDetector.detectBreaks(nexusEvent.matches, zone)
        }

    /** Each marker becomes a break from its match's start until the next match's start (none after the last). */
    fun fromMarkers(matches: List<NexusMatch>): List<ScheduleBreak> {
        val stamped = matches.mapNotNull { m -> m.times.startDate?.let { m to it } }.sortedBy { it.second }
        return stamped.mapIndexedNotNull { index, (match, start) ->
            val label = match.breakAfter ?: return@mapIndexedNotNull null
            val next = stamped.getOrNull(index + 1)
            ScheduleBreak(kindOf(label), match.label, next?.first?.label, start, next?.second, label)
        }
    }

    private fun kindOf(label: String): ScheduleBreak.Kind {
        val text = label.lowercase()
        return when {
            "lunch" in text -> ScheduleBreak.Kind.LUNCH
            "end of day" in text -> ScheduleBreak.Kind.OVERNIGHT
            else -> ScheduleBreak.Kind.SESSION_BREAK
        }
    }
}
