package com.pitwatch.app.ui.matches

import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.logic.PhaseDerivation
import com.pitwatch.core.logic.UpcomingScheduleItem
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Matches screen renders, derived from persisted state. */
data class MatchListModel(
    val title: String,
    val status: Status?,
    val nowQueuing: String?,
    val nexusUnavailable: Boolean,
    val error: String?,
    val days: List<Day>,
    val results: List<Result>,
    val empty: Empty?,
) {
    data class Status(val teamNumber: Int, val rank: Int?, val record: String?) {
        val text: String get() = listOfNotNull("Team $teamNumber", rank?.let { "Rank #$it" }, record).joinToString(" · ")
    }

    data class Day(val date: LocalDate?, val label: String, val items: List<Item>)

    sealed interface Item {
        val id: String

        data class Upcoming(val row: MatchRow) : Item {
            override val id: String get() = row.key
        }

        data class Break(val title: String, val start: Instant, val end: Instant?) : Item {
            override val id: String get() = "break:$title:$start"
        }
    }

    data class MatchRow(
        val key: String,
        val label: String,
        val shortLabel: String,
        val time: Instant?,
        /** True when [time] is an estimate (Nexus or TBA predicted), shown with "~". */
        val estimated: Boolean,
        val alliance: MatchAlliance?,
        val phase: Phase?,
        val red: AllianceLine,
        val blue: AllianceLine,
        val isNext: Boolean,
        val countdown: Countdown?,
    ) {
        val url: String get() = "https://www.thebluealliance.com/match/$key"
    }

    data class AllianceLine(val teams: List<TeamChip>, val summedOpr: Double?)
    data class TeamChip(val number: String, val isUs: Boolean)

    /** "12m to on deck": counts down to [deadline]. */
    data class Countdown(val deadline: Instant, val target: String)

    data class Result(val key: String, val label: String, val shortLabel: String, val ourScore: Int, val theirScore: Int, val outcome: String) {
        val url: String get() = "https://www.thebluealliance.com/match/$key"
    }

    enum class Empty { NOT_CONFIGURED, NO_EVENT, NO_MATCHES }
}

object MatchListModels {
    fun build(cache: EventCache, config: UserConfig, refreshState: RefreshState, now: Instant, locale: Locale = Locale.getDefault()): MatchListModel {
        val event = cache.event
        val title = event?.shortName ?: event?.name ?: "PitWatch"
        val error = refreshState.lastError
        val teamKey = config.teamKey
        val teamNumber = config.teamNumber
        if (!config.isConfigured || teamKey == null || teamNumber == null) {
            return MatchListModel(title, null, null, false, error, emptyList(), emptyList(), MatchListModel.Empty.NOT_CONFIGURED)
        }
        val ranking = cache.rankings?.rankings?.firstOrNull { it.teamKey == teamKey }
        val status = MatchListModel.Status(teamNumber, ranking?.rank, ranking?.record?.display)
        val nexusUnavailable = config.isNexusConfigured && event != null && cache.nexusEvent == null
        if (event == null) {
            return MatchListModel(title, status, null, nexusUnavailable, error, emptyList(), emptyList(), MatchListModel.Empty.NO_EVENT)
        }

        val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        val schedule = MatchSchedule(cache.matches, teamKey)
        val nextKey = schedule.nextMatch?.key

        fun line(match: Match, color: String): MatchListModel.AllianceLine {
            val keys = match.alliances[color]?.teamKeys.orEmpty()
            return MatchListModel.AllianceLine(keys.map { MatchListModel.TeamChip(it.removePrefix("frc"), it == teamKey) }, cache.oprs?.summedOpr(keys))
        }

        fun row(match: Match): MatchListModel.MatchRow {
            val isNext = match.key == nextKey
            val nexusMatch = NexusMatchMerge.nexusInfo(match, nexus)
            val nexusStart = nexusMatch?.times?.startDate
            val time = nexusStart ?: if (config.useScheduledTime) {
                match.time?.let(Instant::ofEpochSecond) ?: match.matchDate()
            } else {
                match.matchDate()
            }
            val estimated = nexusStart != null || (!config.useScheduledTime && match.predictedTime != null && match.actualTime == null)
            val derived = nexusMatch?.let { PhaseDerivation.derivePhase(it, now) }
            val countdown = when {
                !isNext -> null
                derived != null -> derived.deadline?.let { MatchListModel.Countdown(it, "to " + (derived.phase.nextPhaseProse ?: "match end")) }
                else -> time?.let {
                    MatchListModel.Countdown(it.minus(config.queueOffset), if (config.queueOffsetMinutes > 0) "to queue" else "to match")
                }
            }
            val alliance = when (match.allianceColor(teamKey)) {
                "red" -> MatchAlliance.RED
                "blue" -> MatchAlliance.BLUE
                else -> null
            }
            return MatchListModel.MatchRow(
                match.key, match.label, match.shortLabel, time, estimated, alliance, derived?.phase,
                line(match, "red"), line(match, "blue"), isNext, countdown,
            )
        }

        val zone = event.zone
        val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
        val entries: List<Pair<Instant?, MatchListModel.Item>> = schedule.upcomingTimeline(nexus, zone).map { item ->
            when (item) {
                is UpcomingScheduleItem.MatchItem -> row(item.match).let { it.time to MatchListModel.Item.Upcoming(it) }
                is UpcomingScheduleItem.BreakItem -> item.scheduleBreak.let { it.start to MatchListModel.Item.Break(it.title, it.start, it.end) }
            }
        }
        val days = mutableListOf<MatchListModel.Day>()
        for ((time, item) in entries) {
            // Untimed matches stay with the day before them (or "Time TBD" at the top).
            val date = time?.atZone(zone)?.toLocalDate() ?: days.lastOrNull()?.date
            if (days.isNotEmpty() && days.last().date == date) {
                days[days.lastIndex] = days.last().let { it.copy(items = it.items + item) }
            } else {
                days += MatchListModel.Day(date, date?.format(dayFormat) ?: "Time TBD", listOf(item))
            }
        }

        val results = schedule.pastMatches.mapNotNull { match ->
            val ours = match.allianceColor(teamKey) ?: return@mapNotNull null
            val theirs = if (ours == "red") "blue" else "red"
            val our = match.alliances[ours]?.score ?: return@mapNotNull null
            val their = match.alliances[theirs]?.score ?: return@mapNotNull null
            val outcome = when {
                our > their -> "W"
                our < their -> "L"
                else -> "T"
            }
            MatchListModel.Result(match.key, match.label, match.shortLabel, our, their, outcome)
        }

        val empty = if (days.isEmpty() && results.isEmpty()) MatchListModel.Empty.NO_MATCHES else null
        return MatchListModel(title, status, nexus?.nowQueuing, nexusUnavailable, error, days, results, empty)
    }
}
