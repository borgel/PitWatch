package com.pitwatch.core.logic

import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Schedule intelligence for one tracked team. */
class MatchSchedule(matches: List<Match>, teamKey: String) {
    val allMatches: List<Match> = matches.sortedBy { it.sortOrder }
    val teamMatches: List<Match> = allMatches.filter { m -> m.alliances.values.any { teamKey in it.teamKeys } }
    val upcomingMatches: List<Match> = teamMatches.filter { !it.isPlayed }
    /** Most recent first. */
    val pastMatches: List<Match> = teamMatches.filter { it.isPlayed }.reversed()

    val nextMatch: Match? get() = upcomingMatches.firstOrNull()
    val lastPlayedMatch: Match? get() = pastMatches.firstOrNull()

    /** Background refresh cadence; tighter as the next match (or its next Nexus phase) approaches. */
    fun refreshInterval(now: Instant, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Duration {
        val next = nextMatch ?: return ONE_DAY

        if (nexusEvent != null) {
            val nextPhase = NexusMatchMerge.nexusInfo(next, nexusEvent)?.times?.nextPhaseDate(after = now)
            if (nextPhase != null) {
                val until = secondsBetween(now, nextPhase.date)
                return Duration.ofSeconds(
                    when {
                        until < 0 && until > -900 -> 300L // phase just passed
                        until <= 600 -> 300L
                        until <= 1800 -> 600L
                        else -> 900L
                    },
                )
            }
        }

        val matchDate = referenceDate(next, useScheduledTime) ?: return ONE_DAY
        val until = secondsBetween(now, matchDate)
        return Duration.ofSeconds(
            when {
                until < 0 && until > -900 -> 600L // match just completed
                until <= 1800 -> 900L
                until <= 7200 -> 1800L
                else -> 3600L
            },
        )
    }

    fun nextReloadTime(now: Instant, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Instant =
        now.plus(refreshInterval(now, useScheduledTime, nexusEvent))

    /**
     * Whether live tracking should auto-start now. With correlated Nexus data the earliest Nexus phase
     * time is the reference (15-min grace after it); otherwise the TBA match time.
     */
    fun shouldStartLiveActivity(
        now: Instant,
        mode: LiveActivityMode,
        useScheduledTime: Boolean,
        hasActiveLiveActivity: Boolean,
        nexusEvent: NexusEvent? = null,
    ): Boolean {
        if (hasActiveLiveActivity) return false
        val next = nextMatch ?: return false

        val nexusDate = NexusMatchMerge.nexusInfo(next, nexusEvent)?.times
            ?.let { it.queueDate ?: it.onDeckDate ?: it.onFieldDate ?: it.startDate }
        if (nexusDate != null) {
            val until = secondsBetween(now, nexusDate)
            return when (mode) {
                LiveActivityMode.NEAR_MATCH -> until > -900 && until <= 7200
                LiveActivityMode.ALL_DAY -> until > -900
            }
        }

        val matchDate = referenceDate(next, useScheduledTime) ?: return false
        val until = secondsBetween(now, matchDate)
        return when (mode) {
            LiveActivityMode.NEAR_MATCH -> until > 0 && until <= 7200
            LiveActivityMode.ALL_DAY -> until <= 7200
        }
    }

    /**
     * Upcoming matches with inferred schedule breaks inserted between consecutive matches. Brackets use
     * Nexus-correlated start times, falling back to TBA times; pairs with an unknown time get no breaks.
     */
    fun upcomingTimeline(nexusEvent: NexusEvent?, zone: ZoneId): List<UpcomingScheduleItem> {
        if (nexusEvent == null) return upcomingMatches.map { UpcomingScheduleItem.MatchItem(it) }

        val allBreaks = ScheduleBreakDetector.detectBreaks(nexusEvent.matches, zone)
        fun effectiveTime(match: Match): Instant? =
            NexusMatchMerge.nexusInfo(match, nexusEvent)?.times?.startDate ?: match.matchDate(useScheduled = true)

        return buildList {
            upcomingMatches.forEachIndexed { index, match ->
                add(UpcomingScheduleItem.MatchItem(match))
                val following = upcomingMatches.getOrNull(index + 1) ?: return@forEachIndexed
                val prevTime = effectiveTime(match) ?: return@forEachIndexed
                val nextTime = effectiveTime(following) ?: return@forEachIndexed
                // Half-open: a break starting exactly at nextTime belongs to the next pair.
                allBreaks.filter { it.start >= prevTime && it.start < nextTime }
                    .forEach { add(UpcomingScheduleItem.BreakItem(it)) }
            }
        }
    }

    private fun referenceDate(match: Match, useScheduledTime: Boolean): Instant? {
        if (useScheduledTime) match.time?.let { return Instant.ofEpochSecond(it) }
        return match.matchDate(useScheduled = false)
    }

    private companion object {
        val ONE_DAY: Duration = Duration.ofDays(1)

        /** Fractional seconds, matching Swift's TimeInterval comparisons at the thresholds. */
        fun secondsBetween(from: Instant, to: Instant): Double = Duration.between(from, to).toMillis() / 1000.0
    }
}

sealed interface UpcomingScheduleItem {
    val id: String

    data class MatchItem(val match: Match) : UpcomingScheduleItem {
        override val id: String get() = "match:${match.key}"
    }

    data class BreakItem(val scheduleBreak: ScheduleBreak) : UpcomingScheduleItem {
        override val id: String get() = "break:${scheduleBreak.startsAfter}->${scheduleBreak.endsBefore}"
    }
}
