package com.pitwatch.app.widget

import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the home-screen widget shows, at any size. Built from the same model as the Matches screen. */
data class WidgetModel(
    val state: State,
    val header: String,
    val eventTitle: String?,
    val next: MatchListModel.MatchRow?,
    /** Everything after [next], in order: matches and breaks. */
    val later: List<MatchListModel.Item>,
    val last: MatchListModel.Result?,
    /** Shown instead of match content outside [State.READY]. */
    val message: String?,
    /** Live chronometer target; null once passed so the widget never counts negative. */
    val countdownDeadline: Instant?,
) {
    enum class State { NOT_CONFIGURED, NO_EVENT, NO_UPCOMING, READY }
}

object WidgetModels {
    fun build(cache: EventCache, config: UserConfig, now: Instant, locale: Locale = Locale.getDefault()): WidgetModel {
        val list = MatchListModels.build(cache, config, RefreshState(), now, locale)
        if (list.empty == MatchListModel.Empty.NOT_CONFIGURED) {
            return WidgetModel(WidgetModel.State.NOT_CONFIGURED, "PitWatch", null, null, emptyList(), null, "Set up PitWatch", null)
        }
        val header = listOfNotNull(config.teamNumber?.toString(), list.status?.rank?.let { "#$it" }, list.status?.record).joinToString(" · ")
        val event = cache.event
            ?: return WidgetModel(WidgetModel.State.NO_EVENT, header, null, null, emptyList(), null, "No event yet", null)

        val items = list.days.flatMap { it.items }
        val next = items.filterIsInstance<MatchListModel.Item.Upcoming>().firstOrNull()?.row
        val last = list.results.firstOrNull()
        if (next == null) {
            val start = event.startInstant?.takeIf { it > now }
            val message = start?.let {
                "Next event: ${list.title} · " + DateTimeFormatter.ofPattern("MMM d", locale).withZone(event.zone).format(it)
            } ?: "No upcoming matches"
            return WidgetModel(WidgetModel.State.NO_UPCOMING, header, list.title, null, emptyList(), last, message, null)
        }
        val later = items.dropWhile { !(it is MatchListModel.Item.Upcoming && it.row.key == next.key) }.drop(1)
        val deadline = next.countdown?.deadline?.takeIf { it > now }
        return WidgetModel(WidgetModel.State.READY, header, list.title, next, later, last, null, deadline)
    }
}
