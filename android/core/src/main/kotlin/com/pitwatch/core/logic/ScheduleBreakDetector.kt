package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusMatch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A non-match interval: from Nexus's own markers, or inferred from gaps in estimated start times. */
data class ScheduleBreak(
    val kind: Kind,
    val startsAfter: String,
    /** Null for a marker after the last scheduled match (e.g. alliance selection). */
    val endsBefore: String?,
    val start: Instant,
    val end: Instant?,
    /** Nexus's name for the break ("Lunch", "Alliance selection"); null when inferred. */
    val label: String? = null,
) {
    enum class Kind {
        /** Same local day, overlapping 11:30–13:00. */
        LUNCH,
        /** Same local day, not overlapping midday. */
        SESSION_BREAK,
        /** Crosses a local-day boundary. */
        OVERNIGHT,
    }

    val duration: Duration? get() = end?.let { Duration.between(start, it) }

    /** What to call it in the UI. */
    val title: String
        get() = label ?: when (kind) {
            Kind.LUNCH -> "Lunch"
            Kind.OVERNIGHT -> "End of day"
            Kind.SESSION_BREAK -> "Break"
        }
}

object ScheduleBreakDetector {
    /** Roughly 2.5× a typical FRC cycle. */
    val DEFAULT_MINIMUM_GAP: Duration = Duration.ofMinutes(20)

    /**
     * Gaps of at least [minimumGap] between consecutive estimated start times. [zone] must be the
     * event's local zone: classification asks "did we cross midnight?" and "do we straddle noon?".
     */
    fun detectBreaks(
        matches: List<NexusMatch>,
        zone: ZoneId,
        minimumGap: Duration = DEFAULT_MINIMUM_GAP,
    ): List<ScheduleBreak> {
        val stamped = matches
            .mapNotNull { m -> m.times.startDate?.let { m.label to it } }
            .sortedBy { it.second }
        if (stamped.size < 2) return emptyList()

        return stamped.zipWithNext().mapNotNull { (prev, curr) ->
            if (Duration.between(prev.second, curr.second) < minimumGap) return@mapNotNull null
            val kind = when {
                prev.second.atZone(zone).toLocalDate() != curr.second.atZone(zone).toLocalDate() -> ScheduleBreak.Kind.OVERNIGHT
                straddlesLocalLunch(prev.second, curr.second, zone) -> ScheduleBreak.Kind.LUNCH
                else -> ScheduleBreak.Kind.SESSION_BREAK
            }
            ScheduleBreak(kind, prev.first, curr.first, prev.second, curr.second)
        }
    }

    /** True if [start, end] overlaps 11:30–13:00 on start's local day. */
    private fun straddlesLocalLunch(start: Instant, end: Instant, zone: ZoneId): Boolean {
        val day = start.atZone(zone).toLocalDate()
        val lunchStart = day.atTime(11, 30).atZone(zone).toInstant()
        val lunchEnd = day.atTime(13, 0).atZone(zone).toInstant()
        return start <= lunchEnd && end >= lunchStart
    }
}
