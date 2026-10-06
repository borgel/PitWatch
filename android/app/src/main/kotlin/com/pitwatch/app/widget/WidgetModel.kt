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
    /** Glance renders at most 10 children per Column; the list gets its own Column, kept under the limit. */
    const val MAX_LINES = 9

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

/** Which parts of the widget fit at a size; the upcoming list gets the height that's left. */
data class WidgetPlan(
    val header: Boolean,
    val phaseBar: Boolean,
    val target: Boolean,
    val alliances: Boolean,
    val time: Boolean,
    val lastLine: Boolean,
    val eventTitle: Boolean,
    val listLines: Int,
    val largeCountdown: Boolean,
)

object WidgetPlans {
    // Part heights measured on-device (emulator-5580): the full 4×3 layout (360×344 dp) sums to ~294 dp.
    internal val PADDING = 28.dp
    private val LABEL = 40.dp
    private val COUNTDOWN = 52.dp
    private val COUNTDOWN_LARGE = 70.dp
    private val TARGET = 17.dp
    private val PHASE_BAR = 19.dp
    internal val HEADER = 18.dp
    private val ALLIANCES = 46.dp
    private val TIME = 18.dp
    internal val LAST_LINE = 38.dp
    private val TITLE = 18.dp
    internal val ROW = 24.dp

    /** The 56 sp countdown only where the list still fits beneath it; 40 sp below. */
    fun largeCountdown(height: Dp): Boolean = height >= 400.dp

    /**
     * The match label and countdown always show; then, in priority order, each part is drawn only if it fits:
     * what the countdown counts to, the phase bar, the team header, the alliances (wide only), the start time,
     * and on tall widgets the last result and event title. Nothing is drawn to be clipped.
     */
    fun plan(
        height: Dp,
        wide: Boolean,
        tall: Boolean,
        hasCountdown: Boolean,
        hasPhase: Boolean,
        hasTarget: Boolean,
        hasLast: Boolean,
        hasTitle: Boolean,
    ): WidgetPlan {
        val large = largeCountdown(height)
        var left = height - PADDING - LABEL - if (hasCountdown) (if (large) COUNTDOWN_LARGE else COUNTDOWN) else 0.dp
        fun take(wanted: Boolean, cost: Dp): Boolean = (wanted && left >= cost).also { if (it) left -= cost }
        // Without a countdown, the start time is the next match's most useful line.
        val earlyTime = !hasCountdown && take(true, TIME)
        val target = take(hasCountdown && hasTarget, TARGET)
        val phaseBar = take(hasPhase, PHASE_BAR)
        val header = take(true, HEADER)
        val alliances = take(wide, ALLIANCES)
        val time = earlyTime || take(true, TIME)
        val lastLine = take(tall && hasLast, LAST_LINE)
        val eventTitle = take(tall && hasTitle, TITLE)
        val lines = if (tall) (left / ROW).toInt().coerceAtLeast(0) else 0
        return WidgetPlan(header, phaseBar, target, alliances, time, lastLine, eventTitle, lines, large)
    }
}
