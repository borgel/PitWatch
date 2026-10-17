package com.pitwatch.app.live

import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Instant
import java.time.ZoneOffset

sealed interface LiveDecision {
    data class Track(val matchKey: String) : LiveDecision
    data object Stop : LiveDecision
}

object LiveLifecycle {
    /** Which match the live notification follows next. A scored match is finished at once: no score is shown. */
    fun decide(cache: EventCache, config: UserConfig, trackedMatchKey: String?, now: Instant): LiveDecision {
        val teamKey = config.teamKey ?: return LiveDecision.Stop
        // Matches TBA never scored don't hold up the schedule (see StaleMatches).
        val schedule = MatchSchedule(StaleMatches.drop(cache.matches, cache.nexusEvent, now), teamKey)
        val tracked = cache.matches.firstOrNull { it.key == trackedMatchKey }
            ?: return schedule.nextMatch?.let { LiveDecision.Track(it.key) } ?: LiveDecision.Stop
        val nearMatch = config.liveActivityMode == LiveActivityMode.NEAR_MATCH
        // Unplayed and not given up on (never scored, long past its time; see StaleMatches): keep following it.
        if (!tracked.isPlayed && !StaleMatches.isStale(tracked, cache.nexusEvent, now)) return LiveDecision.Track(tracked.key)
        if (nearMatch) return LiveDecision.Stop

        val next = schedule.nextMatch ?: return LiveDecision.Stop
        val zone = cache.event?.zone ?: ZoneOffset.UTC
        val nextDay = next.matchDate()?.atZone(zone)?.toLocalDate()
        return if (nextDay == null || nextDay == now.atZone(zone).toLocalDate()) LiveDecision.Track(next.key) else LiveDecision.Stop
    }
}
