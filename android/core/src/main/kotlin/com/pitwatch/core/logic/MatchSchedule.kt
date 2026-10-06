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
        val ref = liveReference(next, useScheduledTime, nexusEvent) ?: return false
        val until = secondsBetween(now, ref.date)
        // With Nexus, both modes open 2 h before the queue time. Deliberate divergence from iOS, whose
        // all-day rule had no lead limit and so re-armed for tomorrow's first match right after the last one.
        return if (ref.fromNexus) {
            until > -900 && until <= 7200
        } else {
            when (mode) {
                LiveActivityMode.NEAR_MATCH -> until > 0 && until <= 7200
                LiveActivityMode.ALL_DAY -> until <= 7200
            }
        }
    }

    /**
     * Earliest instant at or after [now] when [shouldStartLiveActivity] (nothing active) is true, or null
     * once that window has closed. Arms the auto-start alarm.
     */
    fun liveActivityWindowStart(
        now: Instant,
        mode: LiveActivityMode,
        useScheduledTime: Boolean,
        nexusEvent: NexusEvent? = null,
    ): Instant? {
        val next = nextMatch ?: return null
        val ref = liveReference(next, useScheduledTime, nexusEvent) ?: return null
        val opens = ref.date.minus(LIVE_LEAD)
        if (ref.fromNexus) {
            if (!now.isBefore(ref.date.plus(NEXUS_GRACE))) return null
            return maxOf(now, opens)
        }
        if (mode == LiveActivityMode.NEAR_MATCH && !now.isBefore(ref.date)) return null
        return maxOf(now, opens)
    }

    private data class LiveReference(val date: Instant, val fromNexus: Boolean)

    /** Liveness is measured against the earliest correlated Nexus phase time, else the TBA match time. */
    private fun liveReference(next: Match, useScheduledTime: Boolean, nexusEvent: NexusEvent?): LiveReference? {
        NexusMatchMerge.nexusInfo(next, nexusEvent)?.times
            ?.let { it.queueDate ?: it.onDeckDate ?: it.onFieldDate ?: it.startDate }
            ?.let { return LiveReference(it, fromNexus = true) }
        return referenceDate(next, useScheduledTime)?.let { LiveReference(it, fromNexus = false) }
    }

    /**
     * Upcoming matches with schedule breaks (Nexus markers, else inferred) inserted between consecutive matches. Brackets use
     * Nexus-correlated start times, falling back to TBA times; pairs with an unknown time get no breaks.
     */
    fun upcomingTimeline(nexusEvent: NexusEvent?, zone: ZoneId): List<UpcomingScheduleItem> {
        if (nexusEvent == null) return upcomingMatches.map { UpcomingScheduleItem.MatchItem(it) }

        val allBreaks = ScheduleBreaks.forEvent(nexusEvent, zone)
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
        val LIVE_LEAD: Duration = Duration.ofHours(2)
        val NEXUS_GRACE: Duration = Duration.ofMinutes(15)

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
