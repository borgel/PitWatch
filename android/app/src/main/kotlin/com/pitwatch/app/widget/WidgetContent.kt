package com.pitwatch.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
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
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitwatch.app.MainActivity
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.scoreboard.PhaseSteps
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant

/** Glance can't load bundled fonts; the system condensed face stands in for Barlow Condensed. */
private val CONDENSED = FontFamily("sans-serif-condensed")

/** The widget at whatever size it's placed; [countdown] renders the live chronometer (swappable in tests). */
@Composable
fun WidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit) {
    val size = LocalSize.current
    val wide = size.width >= PitWatchWidget.MEDIUM.width
    val tall = size.height >= PitWatchWidget.LARGE.height
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    // Glance renders at most 10 children per Column: this one holds at most 7.
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(model.header, style = muted, maxLines = 1)
        if (tall) model.eventTitle?.let { Text(it.uppercase(), style = muted, maxLines = 1) }
        val next = model.next
        if (model.state != WidgetModel.State.READY || next == null) {
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp), modifier = GlanceModifier.padding(top = 8.dp))
            model.last?.let { if (wide) LastResult(it) }
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(GlanceModifier.defaultWeight()) {
                NextMatch(next, times, model.countdownDeadline, tall, countdown)
                next.phase?.let { PhaseBar(it) }
                if (wide) {
                    AllianceRow(MatchAlliance.RED, next.red)
                    AllianceRow(MatchAlliance.BLUE, next.blue)
                }
            }
            if (wide && !tall) model.last?.let { LastResult(it) }
        }
        if (tall) {
            Box(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline)) {}
            }
            // Last result goes above the list: a long upcoming list may only truncate itself (found on-device).
            model.last?.let { LastLine(it) }
            val lines = WidgetLines.fit(model.laterDays, WidgetLines.budget(size.height))
            if (lines.isNotEmpty()) Text("UPCOMING", style = muted, modifier = GlanceModifier.padding(top = 6.dp))
            // Own Column: Glance drops children past 10 per Column, and the parent is already busy.
            Column {
                for (line in lines) {
                    when (line) {
                        is WidgetLine.Header -> Text(
                            line.label,
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
                            modifier = GlanceModifier.padding(top = 4.dp),
                        )
                        is WidgetLine.Entry -> when (val item = line.item) {
                            is MatchListModel.Item.Upcoming -> Row(
                                GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                item.row.alliance?.let { Text("● ", style = TextStyle(color = ColorProvider(StatusColors.alliance(it)), fontSize = 13.sp)) }
                                Text(
                                    item.row.shortLabel,
                                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Text(
                                    times.match(item.row.time, item.row.estimated),
                                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontFamily = CONDENSED),
                                )
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
private fun NextMatch(row: MatchListModel.MatchRow, times: TimeFormat, deadline: Instant?, tall: Boolean, countdown: @Composable (Instant) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        row.phase?.let {
            Spacer(GlanceModifier.width(8.dp))
            Pill(it.stateLabel, ColorProvider(StatusColors.phase(it)), ColorProvider(StatusColors.onPhase(it)))
        }
    }
    Text(times.match(row.time, row.estimated), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    if (deadline != null) {
        // The platform Chronometer gets its own line: sharing a row with small text clipped it on-device.
        countdown(deadline)
        if (tall) row.countdown?.let { Text(it.target, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1) }
    }
}

/** Four rounded segments filled up to the current phase; the current one taller. */
@Composable
private fun PhaseBar(phase: Phase) {
    val current = PhaseSteps.current(phase)
    Row(GlanceModifier.fillMaxWidth().height(11.dp), verticalAlignment = Alignment.Bottom) {
        PhaseSteps.steps.forEachIndexed { i, (_, stepPhase) ->
            val step = i + 1
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            val fill = stepPhase?.takeIf { current != null && step <= current }?.let { ColorProvider(StatusColors.phase(it)) }
            Box(
                GlanceModifier.defaultWeight().height(if (step == current) 11.dp else 7.dp)
                    .background(fill ?: GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(4.dp)
                    .semantics { testTag = "phase-step" },
            ) {}
        }
    }
}

@Composable
private fun AllianceRow(alliance: MatchAlliance, line: MatchListModel.AllianceLine) {
    val style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val (before, us, after) = TeamSplit.of(line)
    Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(5.dp).height(16.dp).background(ColorProvider(StatusColors.alliance(alliance))).cornerRadius(3.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        if (before.isNotEmpty()) Text(before, style = style, maxLines = 1)
        us?.let {
            Box(GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(5.dp).padding(horizontal = 5.dp)) {
                Text(it, style = style.copy(color = GlanceTheme.colors.onPrimary), maxLines = 1)
            }
        }
        if (after.isNotEmpty()) Text(after, style = style, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, container: ColorProvider, content: ColorProvider) {
    Box(GlanceModifier.background(container).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = TextStyle(color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
    }
}

@Composable
private fun OutcomePill(result: MatchListModel.Result) {
    val pill = StatusColors.outcome(result.outcome)
    Pill(
        result.outcomeLabel,
        pill?.let { ColorProvider(it.container) } ?: GlanceTheme.colors.surfaceVariant,
        pill?.let { ColorProvider(it.content) } ?: GlanceTheme.colors.onSurfaceVariant,
    )
}

/** Tall widgets: one line above the list. */
@Composable
private fun LastLine(result: MatchListModel.Result) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "LAST · ${result.shortLabel}",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
            modifier = GlanceModifier.defaultWeight(),
        )
        Text("${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Spacer(GlanceModifier.width(6.dp))
        OutcomePill(result)
    }
}

/** Wide, short widgets: a column beside the next match. */
@Composable
private fun LastResult(result: MatchListModel.Result) {
    Column(GlanceModifier.padding(start = 8.dp, top = 4.dp)) {
        Text("LAST", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Text(result.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Text("${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        OutcomePill(result)
    }
}
