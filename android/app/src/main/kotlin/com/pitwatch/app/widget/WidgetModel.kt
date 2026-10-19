package com.pitwatch.app.widget

import com.pitwatch.app.live.LiveCard
import com.pitwatch.app.live.LiveSnapshots
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.ScheduleDelay
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
    /** "12m behind", "On time": how the event is running (part of [footer]). */
    val scheduleDelay: String? = null,
    /** The next match's stage times — queue, on deck, on field, start — as the live card shows them. */
    val stageTimes: List<Instant?> = emptyList(),
    /** "NOW · FIELD #36 · 24M BEHIND", as the live card's last line. */
    val footer: String? = null,
    /** Once there's nothing upcoming: every result, newest first (shown instead of a near-empty widget). */
    val results: List<MatchListModel.Result> = emptyList(),
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
            return WidgetModel(WidgetModel.State.NOT_CONFIGURED, "PitWatch", null, emptyList(), null, "Set up PitWatch", null, zone)
        }
        val header = listOfNotNull(config.teamNumber?.toString(), list.status?.rank?.let { "#$it" }, list.status?.record).joinToString(" · ")
        val event = cache.event
            ?: return WidgetModel(WidgetModel.State.NO_EVENT, header, null, emptyList(), null, "No event yet", null, zone)

        val next = list.days.flatMap { it.items }.filterIsInstance<MatchListModel.Item.Upcoming>().firstOrNull()?.row
        val last = list.results.firstOrNull()
        if (next == null) {
            val start = event.startInstant?.takeIf { it > now }
            val message = start?.let {
                "Next event: ${list.title} · " + DateTimeFormatter.ofPattern("MMM d", locale).withZone(event.zone).format(it)
            } ?: "No upcoming matches"
            return WidgetModel(WidgetModel.State.NO_UPCOMING, header, null, emptyList(), last, message, null, zone, list.zoneLabel, results = list.results)
        }
        val nextDay = list.days.indexOfFirst { day -> day.items.any { it is MatchListModel.Item.Upcoming && it.row.key == next.key } }
        val sameDayRest = list.days[nextDay].items.dropWhile { !(it is MatchListModel.Item.Upcoming && it.row.key == next.key) }.drop(1)
        val laterDays = listOfNotNull(list.days[nextDay].copy(items = sameDayRest).takeIf { sameDayRest.isNotEmpty() }) + list.days.drop(nextDay + 1)
        val deadline = next.countdown?.deadline?.takeIf { it > now }
        val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        val delay = ScheduleDelay.of(cache.matches, nexus)?.let(ScheduleDelay::text)
        // The live card's own snapshot, so the widgets and the card show the same stage times and footer.
        val live = LiveSnapshots.build(cache, config, next.key, now)
        val stageTimes = live?.milestones?.let { listOf(it.queue, it.onDeck, it.onField, it.start) }.orEmpty()
        val footer = live?.let(LiveCard::footer)?.ifEmpty { null }
        return WidgetModel(WidgetModel.State.READY, header, next, laterDays, last, null, deadline, zone, list.zoneLabel, delay, stageTimes, footer)
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
    fun fit(days: List<MatchListModel.Day>, budget: Int, cap: Int = MAX_LINES): List<WidgetLine> {
        val lines = mutableListOf<WidgetLine>()
        var left = minOf(budget, cap)
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

/** What fits inside the stage band's chevrons: nothing, the stage names, or names over their clock times. */
enum class StageLabels { NONE, NAMES, TIMES }

/** Which parts of the widget fit at a size; the upcoming list gets the height that's left. */
data class WidgetPlan(
    /** The team record: top-right corner on wide widgets (no cost), its own top line on narrow ones. */
    val header: Boolean,
    val phaseBar: Boolean,
    val stageLabels: StageLabels,
    /** What the countdown counts to ("TO MATCH START"). */
    val target: Boolean,
    val alliances: Boolean,
    /** "NOW · FIELD #36 · 24M BEHIND", as the live card's last line. */
    val footer: Boolean,
    /** Narrow widgets give the footer two lines: one cut it to "24M BEH…" on-device. */
    val footerLines: Int,
    /** The start time on its own line (when the stage labels don't already show it). */
    val time: Boolean,
    val lastLine: Boolean,
    val listLines: Int,
    val largeCountdown: Boolean,
)

object WidgetPlans {
    // Part heights in dp, measured on-device (emulator-5580) for the previous layout and the live card's.
    internal val PADDING = 28.dp
    private val LABEL = 40.dp
    private val PILL = 22.dp
    private val COUNTDOWN = 52.dp
    private val COUNTDOWN_LARGE = 70.dp
    private val TARGET = 17.dp
    internal val HEADER = 18.dp
    /** The chevron band bare (14 dp) and its top gap; with names inside it grows to 24 dp, with times to 34. */
    private val PHASE_BAR = 20.dp
    private val STAGE_LABELS = 20.dp
    private val ALLIANCES = 46.dp
    private val FOOTER = 18.dp
    private val TIME = 18.dp
    internal val LAST_LINE = 38.dp
    internal val ROW = 24.dp
    /** From here the scoreboard's top is two columns: match and phase left; record, countdown and target right. */
    val WIDE = 250.dp
    /** From here each chevron fits its stage's name... */
    val STAGE_NAMES = 230.dp
    /** ...and from here its clock time under the name. */
    val STAGE_TIMES = 300.dp

    /** The 56 sp countdown only where the list still fits beneath it; 40 sp below. */
    fun largeCountdown(height: Dp): Boolean = height >= 400.dp

    /**
     * The match label and countdown always show; then, in priority order, each part is drawn only if it fits: what the
     * countdown counts to, the stage bar and its labels, the record (narrow only — wide widgets put it in the top-right
     * corner, at no cost), the alliances (wide only), the footer, the start time, and on tall widgets the last result.
     * Nothing is drawn to be clipped.
     */
    fun plan(
        height: Dp,
        width: Dp,
        tall: Boolean,
        hasCountdown: Boolean,
        hasTarget: Boolean,
        hasLast: Boolean,
        hasFooter: Boolean,
    ): WidgetPlan {
        val wide = width >= WIDE
        val large = largeCountdown(height)
        val countdown = if (large) COUNTDOWN_LARGE else COUNTDOWN
        var left = height - PADDING
        fun take(wanted: Boolean, cost: Dp): Boolean = (wanted && left >= cost).also { if (it) left -= cost }
        val header: Boolean
        var time: Boolean
        if (wide) {
            // Left: match and phase pill. Right: record over the countdown (or, without one, the start time).
            left -= maxOf(LABEL + PILL, HEADER + if (hasCountdown) countdown else TIME)
            header = true
            time = !hasCountdown
        } else {
            left -= LABEL + if (hasCountdown) countdown else 0.dp
            header = false
            time = !hasCountdown && take(true, TIME) // without a countdown, the start time is what matters most
        }
        val target = take(hasCountdown && hasTarget, TARGET)
        // Even before Nexus reports a phase: then the bar is empty, and its labels still carry the stage times.
        val phaseBar = take(true, PHASE_BAR)
        val stageLabels = when {
            !take(phaseBar && width >= STAGE_NAMES, STAGE_LABELS) -> StageLabels.NONE
            width >= STAGE_TIMES -> StageLabels.TIMES
            else -> StageLabels.NAMES
        }
        val narrowHeader = !wide && take(true, HEADER)
        val alliances = take(wide, ALLIANCES)
        val footerLines = if (wide) 1 else 2
        val footer = take(hasFooter, FOOTER * footerLines)
        // The start time shows in the START label when there are times; otherwise on its own line.
        if (!time && stageLabels != StageLabels.TIMES) time = take(true, TIME)
        val lastLine = take(tall && hasLast, LAST_LINE)
        val lines = if (tall) (left / ROW).toInt().coerceAtLeast(0) else 0
        return WidgetPlan(header || narrowHeader, phaseBar, stageLabels, target, alliances, footer, footerLines, time, lastLine, lines, large)
    }
}

/** What the simple widget fits: its top (match chip, phase, countdown, target), then the stage bar. */
data class SimplePlan(
    /** Too short for more: the match chip and countdown side by side. */
    val singleRow: Boolean,
    /** Room to fill the top with big type (56 sp countdown). */
    val big: Boolean,
    val phase: Boolean,
    val target: Boolean,
    val phaseBar: Boolean,
    val stageLabels: StageLabels,
)

object SimplePlans {
    private val PADDING = 28.dp
    private val SINGLE_ROW_BELOW = 60.dp
    private val TOP_BIG = 96.dp
    private val TOP_WIDE = 70.dp
    private val TOP_NARROW = 96.dp
    private val TARGET = 17.dp
    private val PHASE_BAR = 20.dp
    private val STAGE_LABELS = 20.dp

    /** Asked for: the top filled with the next match number, main time and phase name; the stage bar below. */
    fun plan(height: Dp, width: Dp, hasCountdown: Boolean, hasPhase: Boolean): SimplePlan {
        var left = height - PADDING
        if (left < SINGLE_ROW_BELOW) return SimplePlan(true, false, false, false, false, StageLabels.NONE)
        fun take(wanted: Boolean, cost: Dp): Boolean = (wanted && left >= cost).also { if (it) left -= cost }
        val big: Boolean
        val target: Boolean
        if (width >= WidgetPlans.WIDE) {
            // Two columns: chip over the phase on the left, countdown over its target on the right.
            big = left >= TOP_BIG + PHASE_BAR
            left -= if (big) TOP_BIG else TOP_WIDE
            target = hasCountdown
        } else {
            big = false
            left -= TOP_NARROW
            target = take(hasCountdown, TARGET)
        }
        val phaseBar = take(true, PHASE_BAR) // empty until Nexus reports a phase (see WidgetPlans)
        val stageLabels = when {
            !take(phaseBar && width >= WidgetPlans.STAGE_NAMES, STAGE_LABELS) -> StageLabels.NONE
            width >= WidgetPlans.STAGE_TIMES -> StageLabels.TIMES
            else -> StageLabels.NAMES
        }
        return SimplePlan(false, big, hasPhase, target, phaseBar, stageLabels)
    }
}
