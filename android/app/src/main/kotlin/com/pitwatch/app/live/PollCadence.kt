package com.pitwatch.app.live

import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant

/** How often the live service polls. Fast only around the tracked match's Nexus phase changes. */
object PollCadence {
    val FAST: Duration = Duration.ofSeconds(30)
    val SLOW: Duration = Duration.ofMinutes(2)
    val TBA_INTERVAL: Duration = Duration.ofMinutes(2)
    private val NEAR_AHEAD: Duration = Duration.ofMinutes(10)
    private val NEAR_BEHIND: Duration = Duration.ofMinutes(2)
    private val BACKOFF = listOf(30L, 60L, 120L).map(Duration::ofSeconds)

    fun nextDelay(
        cache: EventCache,
        config: UserConfig,
        trackedMatchKey: String?,
        now: Instant,
        consecutiveFailures: Int,
    ): Duration {
        if (consecutiveFailures > 0) return BACKOFF[minOf(consecutiveFailures, BACKOFF.size) - 1]
        if (config.effectiveTimeSource != TimeSource.NEXUS) return SLOW
        val match = cache.matches.firstOrNull { it.key == trackedMatchKey } ?: return SLOW
        val times = NexusMatchMerge.nexusInfo(match, cache.nexusEvent)?.times ?: return SLOW
        val near = listOfNotNull(times.queueDate, times.onDeckDate, times.onFieldDate, times.startDate).any { phase ->
            val until = Duration.between(now, phase)
            if (until.isNegative) until > NEAR_BEHIND.negated() else until <= NEAR_AHEAD
        }
        return if (near) FAST else SLOW
    }

    fun isTbaDue(lastTbaPoll: Instant?, now: Instant): Boolean =
        lastTbaPoll == null || Duration.between(lastTbaPoll, now) >= TBA_INTERVAL
}
