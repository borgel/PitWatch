package com.pitwatch.app.widget

import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import com.pitwatch.app.ui.matches.MatchTime
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The event schedule widget: every match at the event, upcoming first, then (optionally) results newest first. */
data class EventScheduleModel(
    val state: WidgetModel.State,
    val title: String,
    val lines: List<EventScheduleLine>,
    val message: String?,
    val timeZone: ZoneId = ZoneId.systemDefault(),
    val zoneLabel: String? = null,
)

sealed interface EventScheduleLine {
    data class Header(val label: String) : EventScheduleLine
    data class Upcoming(val row: EventMatchRow) : EventScheduleLine
    data class Played(val row: EventMatchRow) : EventScheduleLine
}

data class EventMatchRow(
    val key: String,
    val shortLabel: String,
    val time: Instant?,
    val estimated: Boolean,
    val red: MatchListModel.AllianceLine,
    val blue: MatchListModel.AllianceLine,
    /** Our alliance when we're in this match. */
    val ours: MatchAlliance?,
    val redScore: Int?,
    val blueScore: Int?,
)

object EventScheduleModels {
    const val RESULTS = "Results"

    /**
     * Every line travels in the one widget update (~1 MB binder limit; see EventScheduleWidgetSizeTest). Past this, the
     * oldest results go first, then the far end of the upcoming schedule. A 120-match event uses ~125 lines.
     */
    const val MAX_LINES = 180

    fun build(
        cache: EventCache,
        config: UserConfig,
        showPlayed: Boolean,
        now: Instant,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): EventScheduleModel {
        val list = MatchListModels.build(cache, config, RefreshState(), now, locale, zone)
        val teamKey = config.teamKey
        // Same messages as the other widgets when there's nothing to list yet.
        if (list.empty == MatchListModel.Empty.NOT_CONFIGURED || list.empty == MatchListModel.Empty.NO_EVENT || teamKey == null) {
            val widget = WidgetModels.build(cache, config, now, locale, zone)
            return EventScheduleModel(widget.state, widget.header, emptyList(), widget.message, zone)
        }
        val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }

        fun line(match: Match, color: String) = MatchListModel.AllianceLine(MatchListModels.teams(match, color, teamKey), null)

        fun row(match: Match, played: Boolean): EventMatchRow {
            val (time, estimated) = MatchTime.of(match, NexusMatchMerge.nexusInfo(match, nexus), config)
            val ours = when (match.allianceColor(teamKey)) {
                "red" -> MatchAlliance.RED
                "blue" -> MatchAlliance.BLUE
                else -> null
            }
            return EventMatchRow(
                match.key, match.shortLabel, time, estimated, line(match, "red"), line(match, "blue"), ours,
                match.alliances["red"]?.score?.takeIf { played }, match.alliances["blue"]?.score?.takeIf { played },
            )
        }

        val (played, upcoming) = cache.matches.sortedBy { it.sortOrder }.partition { it.isPlayed }
        val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
        val upcomingLines = buildList {
            var day: LocalDate? = null
            for ((i, match) in upcoming.withIndex()) {
                val r = row(match, played = false)
                // Untimed matches stay under the day before them (or "Time TBD" at the top).
                val date = r.time?.atZone(zone)?.toLocalDate() ?: day
                if (i == 0 || date != day) {
                    add(EventScheduleLine.Header(date?.format(dayFormat) ?: "Time TBD"))
                    day = date
                }
                add(EventScheduleLine.Upcoming(r))
            }
        }.take(MAX_LINES).dropLastWhile { it is EventScheduleLine.Header }
        // Results get what's left after the header: newest first, so the oldest are the ones cut.
        val resultRoom = MAX_LINES - upcomingLines.size - 1
        val resultLines = if (showPlayed && played.isNotEmpty() && resultRoom > 0) {
            listOf(EventScheduleLine.Header(RESULTS)) + played.asReversed().take(resultRoom).map { EventScheduleLine.Played(row(it, played = true)) }
        } else {
            emptyList()
        }
        val lines = upcomingLines + resultLines
        if (lines.isEmpty()) {
            val message = if (cache.matches.isEmpty()) "No matches yet" else "No upcoming matches"
            return EventScheduleModel(WidgetModel.State.NO_UPCOMING, list.title, emptyList(), message, zone, list.zoneLabel)
        }
        return EventScheduleModel(WidgetModel.State.READY, list.title, lines, null, zone, list.zoneLabel)
    }
}
