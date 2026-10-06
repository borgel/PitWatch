package com.pitwatch.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitwatch.app.MainActivity
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.Phase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle


/** The widget at whatever size it's placed; [countdown] renders the live chronometer (swappable in tests). */
@Composable
fun WidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit) {
    val size = LocalSize.current
    val wide = size.width >= PitWatchWidget.MEDIUM.width
    val tall = size.height >= PitWatchWidget.LARGE.height
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(model.header, style = muted, maxLines = 1)
        if (tall) model.eventTitle?.let { Text(it, style = muted, maxLines = 1) }
        val next = model.next
        if (model.state != WidgetModel.State.READY || next == null) {
            Spacer(GlanceModifier.height(8.dp))
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp))
            model.last?.let { if (wide) LastResult(it) }
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth()) {
            Column(GlanceModifier.defaultWeight()) {
                NextMatch(next, times, model.countdownDeadline, countdown)
                if (wide) {
                    AllianceText(next.red, "red")
                    AllianceText(next.blue, "blue")
                }
            }
            if (wide && !tall) model.last?.let { LastResult(it) }
        }
        if (tall) {
            // Last result goes above the list: a long upcoming list may only truncate itself (found on-device).
            model.last?.let {
                Spacer(GlanceModifier.height(6.dp))
                Text(
                    "LAST  ${it.shortLabel}  ${it.outcome} ${it.ourScore}–${it.theirScore}",
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
            }
            val lines = WidgetLines.fit(model.laterDays, WidgetLines.budget(size.height))
            if (lines.isNotEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                Text("UPCOMING", style = muted)
            }
            // Own Column: Glance drops children past 10 per Column, and the parent is already busy.
            Column {
            for (line in lines) {
                when (line) {
                    is WidgetLine.Header -> Text(
                        line.label,
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                        modifier = GlanceModifier.padding(top = 4.dp),
                    )
                    is WidgetLine.Entry -> when (val item = line.item) {
                        is MatchListModel.Item.Upcoming -> Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(item.row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), modifier = GlanceModifier.defaultWeight())
                            Text(times.match(item.row.time, item.row.estimated), style = muted)
                        }
                        is MatchListModel.Item.Break -> Text(item.title, style = muted, modifier = GlanceModifier.padding(vertical = 2.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun NextMatch(row: MatchListModel.MatchRow, times: TimeFormat, deadline: Instant?, countdown: @Composable (Instant) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
        row.phase?.let {
            Spacer(GlanceModifier.width(6.dp))
            PhaseBadge(it)
        }
    }
    Text(times.match(row.time, row.estimated), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    if (deadline != null) {
        // The platform Chronometer gets its own line: sharing a row with small text clipped it on-device.
        countdown(deadline)
        row.countdown?.let { Text(it.target, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1) }
    }
}

@Composable
private fun PhaseBadge(phase: Phase) {
    Box(GlanceModifier.background(ColorProvider(StatusColors.phase(phase))).cornerRadius(8.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(phase.stateLabel, style = TextStyle(color = ColorProvider(StatusColors.onPhase(phase)), fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun AllianceText(line: MatchListModel.AllianceLine, color: String) {
    val dot = if (color == "red") "🔴" else "🔵"
    val teams = line.teams.joinToString(" ") { it.number }
    val opr = line.summedOpr?.let { "  Σ%.0f".format(it) } ?: ""
    Text("$dot $teams$opr", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun LastResult(result: MatchListModel.Result) {
    Column(GlanceModifier.padding(start = 8.dp, top = 4.dp)) {
        Text("LAST", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        Text(result.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
        Text("${result.outcome} ${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
