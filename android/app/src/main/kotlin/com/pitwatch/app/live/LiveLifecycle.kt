package com.pitwatch.app.live

import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

sealed interface LiveDecision {
    data class Track(val matchKey: String) : LiveDecision
    data object Stop : LiveDecision
}

object LiveLifecycle {
    val NEAR_MATCH_LINGER: Duration = Duration.ofMinutes(15)
    val ALL_DAY_LINGER: Duration = Duration.ofMinutes(5)

    /**
     * Which match the live notification follows next. [resultSeenAt] is when the tracked match's score
     * first appeared (null = not yet seen).
     */
    fun decide(cache: EventCache, config: UserConfig, trackedMatchKey: String?, resultSeenAt: Instant?, now: Instant): LiveDecision {
        val teamKey = config.teamKey ?: return LiveDecision.Stop
        // Matches TBA never scored don't hold up the schedule (see StaleMatches).
        val schedule = MatchSchedule(StaleMatches.drop(cache.matches, cache.nexusEvent, now), teamKey)
        val tracked = cache.matches.firstOrNull { it.key == trackedMatchKey }
            ?: return schedule.nextMatch?.let { LiveDecision.Track(it.key) } ?: LiveDecision.Stop
        val nearMatch = config.liveActivityMode == LiveActivityMode.NEAR_MATCH
        if (!tracked.isPlayed) {
            if (!StaleMatches.isStale(tracked, cache.nexusEvent, now)) return LiveDecision.Track(tracked.key)
            // Never scored: treat as finished, with no result to linger on.
        } else {
            val linger = if (nearMatch) NEAR_MATCH_LINGER else ALL_DAY_LINGER
            if (resultSeenAt == null || now < resultSeenAt.plus(linger)) return LiveDecision.Track(tracked.key)
        }
        if (nearMatch) return LiveDecision.Stop

        val next = schedule.nextMatch ?: return LiveDecision.Stop
        val zone = cache.event?.zone ?: ZoneOffset.UTC
        val nextDay = next.matchDate()?.atZone(zone)?.toLocalDate()
        return if (nextDay == null || nextDay == now.atZone(zone).toLocalDate()) LiveDecision.Track(next.key) else LiveDecision.Stop
    }
}
