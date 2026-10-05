package com.pitwatch.core.logic

import com.pitwatch.core.model.Event
import java.time.Instant

/** Port of iOS BackgroundRefresh.autoDetectEvent. */
object EventSelection {
    /** Active event, else soonest upcoming, else most recently ended; null only for an empty list. */
    fun autoDetect(events: List<Event>, now: Instant): Event? {
        events.firstOrNull { it.isActive(now) }?.let { return it }
        events.filter { (it.startInstant ?: Instant.MIN) > now }
            .minByOrNull { it.startInstant ?: Instant.MAX }
            ?.let { return it }
        return events.maxByOrNull { it.endInstant ?: Instant.MIN }
    }
}
