package com.pitwatch.app.schedule

import com.pitwatch.app.data.LiveControl
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Instant

object AutoStartPlanner {
    /** When to arm the auto-start alarm; null leaves it disarmed. */
    fun nextStart(cache: EventCache, config: UserConfig, control: LiveControl, now: Instant): Instant? {
        val teamKey = config.teamKey
        if (!config.isConfigured || teamKey == null) return null
        val schedule = MatchSchedule(cache.matches, teamKey)
        val next = schedule.nextMatch ?: return null
        if (next.key == control.suppressedMatchKey) return null
        val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        return schedule.liveActivityWindowStart(now, config.liveActivityMode, config.useScheduledTime, nexus)
    }
}
