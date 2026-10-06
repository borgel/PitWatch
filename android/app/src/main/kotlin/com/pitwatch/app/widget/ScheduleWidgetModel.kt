package com.pitwatch.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The schedule widget (and schedule notification): upcoming matches from the next one on, and the last result. */
data class ScheduleWidgetModel(
    val state: WidgetModel.State,
    val header: String,
    val days: List<MatchListModel.Day>,
    val last: MatchListModel.Result?,
    val message: String?,
    val timeZone: ZoneId = ZoneId.systemDefault(),
    val zoneLabel: String? = null,
)

object ScheduleWidgetModels {
    fun build(
        cache: EventCache,
        config: UserConfig,
        now: Instant,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScheduleWidgetModel {
        // Same states and messages as the main widget; only READY needs the full schedule.
        val widget = WidgetModels.build(cache, config, now, locale, zone)
        if (widget.state != WidgetModel.State.READY) {
            return ScheduleWidgetModel(widget.state, widget.header, emptyList(), widget.last, widget.message, widget.timeZone, widget.zoneLabel)
        }
        val list = MatchListModels.build(cache, config, RefreshState(), now, locale, zone)
        return ScheduleWidgetModel(WidgetModel.State.READY, widget.header, list.days, widget.last, null, list.timeZone, list.zoneLabel)
    }
}

data class SchedulePlan(val header: Boolean, val lastLine: Boolean, val listLines: Int)

object SchedulePlans {
    /** A day header plus one row always fit first; then the team header, the last result, and more rows. */
    fun plan(height: Dp, hasLast: Boolean): SchedulePlan {
        val minimum = WidgetPlans.ROW * 2
        var left = height - WidgetPlans.PADDING - minimum
        fun take(wanted: Boolean, cost: Dp): Boolean = (wanted && left >= cost).also { if (it) left -= cost }
        val header = take(true, WidgetPlans.HEADER)
        val last = take(hasLast, WidgetPlans.LAST_LINE - 12.dp) // no divider above it here
        val lines = ((left + minimum) / WidgetPlans.ROW).toInt().coerceAtLeast(2)
        return SchedulePlan(header, last, lines)
    }
}
