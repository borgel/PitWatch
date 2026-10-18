package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import java.time.Duration
import java.time.Instant

/** How far ahead (negative) or behind (positive) the event is running. */
object ScheduleDelay {
    /** Drift this small reads as "On time". */
    val ON_TIME: Duration = Duration.ofMinutes(2)

    /**
     * The event's next unplayed match (any teams): its estimated start — Nexus, else TBA predicted — minus its
     * scheduled time. Null when that match has no schedule or no estimate, or everything is played.
     */
    fun of(matches: List<Match>, nexusEvent: NexusEvent?): Duration? {
        val next = matches.filterNot { it.isPlayed }.minByOrNull { it.sortOrder } ?: return null
        val scheduled = next.time?.let(Instant::ofEpochSecond) ?: return null
        val estimated = NexusMatchMerge.nexusInfo(next, nexusEvent)?.times?.startDate
            ?: next.predictedTime?.let(Instant::ofEpochSecond)
            ?: return null
        return Duration.between(scheduled, estimated)
    }

    /** "On time", "12m behind", "5m ahead", "1h 5m behind". */
    fun text(delay: Duration): String {
        if (delay.abs() <= ON_TIME) return "On time"
        val minutes = (delay.abs().seconds + 30) / 60
        val amount = if (minutes >= 60) "${minutes / 60}h" + (minutes % 60).takeIf { it > 0 }?.let { " ${it}m" }.orEmpty() else "${minutes}m"
        return amount + if (delay.isNegative) " ahead" else " behind"
    }
}
