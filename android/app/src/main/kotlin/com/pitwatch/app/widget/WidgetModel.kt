package com.pitwatch.app.widget

import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the home-screen widget shows, at any size. Built from the same model as the Matches screen. */
data class WidgetModel(
    val state: State,
    val header: String,
    val eventTitle: String?,
    val next: MatchListModel.MatchRow?,
    /** What follows [next], grouped by day; the next match's own day keeps only what comes after it. */
    val laterDays: List<MatchListModel.Day>,
    val last: MatchListModel.Result?,
    /** Shown instead of match content outside [State.READY]. */
    val message: String?,
    /** Live chronometer target; null once passed so the widget never counts negative. */
    val countdownDeadline: Instant?,
    val timeZone: ZoneId = ZoneId.systemDefault(),
    val zoneLabel: String? = null,
) {
    enum class State { NOT_CONFIGURED, NO_EVENT, NO_UPCOMING, READY }
}

object WidgetModels {
    fun build(
        cache: EventCache,
        config: UserConfig,
        now: Instant,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetModel {
        val list = MatchListModels.build(cache, config, RefreshState(), now, locale, zone)
        if (list.empty == MatchListModel.Empty.NOT_CONFIGURED) {
            return WidgetModel(WidgetModel.State.NOT_CONFIGURED, "PitWatch", null, null, emptyList(), null, "Set up PitWatch", null, zone)
        }
        val header = listOfNotNull(config.teamNumber?.toString(), list.status?.rank?.let { "#$it" }, list.status?.record).joinToString(" · ")
        val event = cache.event
            ?: return WidgetModel(WidgetModel.State.NO_EVENT, header, null, null, emptyList(), null, "No event yet", null, zone)

        val next = list.days.flatMap { it.items }.filterIsInstance<MatchListModel.Item.Upcoming>().firstOrNull()?.row
        val last = list.results.firstOrNull()
        if (next == null) {
            val start = event.startInstant?.takeIf { it > now }
            val message = start?.let {
                "Next event: ${list.title} · " + DateTimeFormatter.ofPattern("MMM d", locale).withZone(event.zone).format(it)
            } ?: "No upcoming matches"
            return WidgetModel(WidgetModel.State.NO_UPCOMING, header, list.title, null, emptyList(), last, message, null, zone, list.zoneLabel)
        }
        val nextDay = list.days.indexOfFirst { day -> day.items.any { it is MatchListModel.Item.Upcoming && it.row.key == next.key } }
        val sameDayRest = list.days[nextDay].items.dropWhile { !(it is MatchListModel.Item.Upcoming && it.row.key == next.key) }.drop(1)
        val laterDays = listOfNotNull(list.days[nextDay].copy(items = sameDayRest).takeIf { sameDayRest.isNotEmpty() }) + list.days.drop(nextDay + 1)
        val deadline = next.countdown?.deadline?.takeIf { it > now }
        return WidgetModel(WidgetModel.State.READY, header, list.title, next, laterDays, last, null, deadline, zone, list.zoneLabel)
    }
}

/** One line of the large widget's upcoming list. */
sealed interface WidgetLine {
    data class Header(val label: String) : WidgetLine
    data class Entry(val item: MatchListModel.Item) : WidgetLine
}

object WidgetLines {
    /** Height the large layout uses above the upcoming list (header, title, next match with the 56 sp countdown, phase bar, alliances, divider, last result, label). */
    private val FIXED: Dp = 308.dp
    private val ROW: Dp = 22.dp

    /** Glance renders at most 10 children per Column; the list gets its own Column, kept under the limit. */
    const val MAX_LINES = 9

    /** How many upcoming lines fit in a widget of [height]. */
    fun budget(height: Dp): Int = ((height - FIXED) / ROW).toInt().coerceAtLeast(0)

    /** Days and their items within [budget] lines; a day header is only shown with at least one item under it. */
    fun fit(days: List<MatchListModel.Day>, budget: Int): List<WidgetLine> {
        val lines = mutableListOf<WidgetLine>()
        var left = minOf(budget, MAX_LINES)
        for (day in days) {
            if (left < 2) break
            lines += WidgetLine.Header(day.label)
            left--
            for (item in day.items) {
                if (left == 0) break
                lines += WidgetLine.Entry(item)
                left--
            }
        }
        return lines
    }
}

/** "4698 · ", "5507", " · 1678": the text either side of our team, so it can sit in its own chip. */
object TeamSplit {
    fun of(line: MatchListModel.AllianceLine): Triple<String, String?, String> {
        val numbers = line.teams.map { it.number }
        val i = line.teams.indexOfFirst { it.isUs }
        if (i < 0) return Triple(numbers.joinToString(" · "), null, "")
        val before = numbers.take(i).joinToString(" · ").let { if (it.isEmpty()) it else "$it · " }
        val after = numbers.drop(i + 1).joinToString(" · ").let { if (it.isEmpty()) it else " · $it" }
        return Triple(before, numbers[i], after)
    }
}
