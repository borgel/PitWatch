package com.pitwatch.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant

/** Glance can't load bundled fonts; the system condensed face stands in for Barlow Condensed. */
internal val CONDENSED = FontFamily("sans-serif-condensed")

/** The widget at whatever size it's placed, in the live card's scoreboard design; [countdown] renders the live chronometer (swappable in tests). */
@Composable
fun WidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit) {
    val size = LocalSize.current
    val wide = size.width >= WidgetPlans.WIDE
    val tall = size.height >= PitWatchWidget.LARGE.height
    val colors = scoreboardColors()
    val muted = labelStyle(colors.muted, 12.sp)
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    val next = model.next
    val plan = WidgetPlans.plan(
        size.height, size.width, tall,
        hasCountdown = model.countdownDeadline != null, hasPhase = next?.phase != null, hasTarget = next?.countdown != null,
        hasLast = model.last != null, hasFooter = model.footer != null,
    )
    // Glance renders at most 10 children per Column: this one holds at most 5.
    Column(
        GlanceModifier.fillMaxSize().background(colors.background).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>())
            .semantics { testTag = "widget-root" },
    ) {
        if (model.state != WidgetModel.State.READY || next == null) {
            Text(model.header, style = muted, maxLines = 1)
            Text(model.message.orEmpty(), style = TextStyle(color = colors.text, fontSize = 16.sp), modifier = GlanceModifier.padding(top = 8.dp))
            model.last?.let { if (wide) LastResult(it) }
            return@Column
        }
        // Wide widgets keep the record in the top-right corner, over the countdown (asked for); narrow ones on top.
        if (plan.header && !wide) Text(model.header, style = muted, maxLines = 1)
        CompositionLocalProvider(LocalCountdownSp provides if (plan.largeCountdown) 56f else 40f) {
            Scoreboard(model, next, plan, wide, times, colors, countdown)
        }
        val lines = WidgetLines.fit(model.laterDays, plan.listLines)
        if (tall && (plan.lastLine || lines.isNotEmpty())) {
            Box(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline)) {}
            }
            // Last result goes above the list: a long upcoming list may only truncate itself (found on-device).
            if (plan.lastLine) model.last?.let { LastLine(it, compact = !wide) }
            UpcomingList(lines, times)
        }
    }
}

/**
 * The live card's scoreboard: the match and its phase, the countdown and what it counts to, the stage bar with each
 * stage's time, the alliances with our number on its chip, and the footer. At most 7 children.
 */
@Composable
private fun Scoreboard(
    model: WidgetModel,
    next: MatchListModel.MatchRow,
    plan: WidgetPlan,
    wide: Boolean,
    times: TimeFormat,
    colors: ScoreboardColors,
    countdown: @Composable (Instant) -> Unit,
) {
    val label = TextStyle(color = colors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val deadline = model.countdownDeadline
    val start = times.match(next.time, next.estimated)
    val target = next.countdown?.target?.uppercase()?.takeIf { plan.target }
    Column(GlanceModifier.fillMaxWidth().semantics { testTag = "scoreboard" }) {
        if (wide) {
            Row(GlanceModifier.fillMaxWidth()) {
                Column(GlanceModifier.defaultWeight()) {
                    Text(next.shortLabel, style = label, maxLines = 1)
                    next.phase?.let { PhasePill(it) }
                }
                Column(horizontalAlignment = Alignment.End, modifier = GlanceModifier.semantics { testTag = "scoreboard-right" }) {
                    if (plan.header) Text(model.header, style = labelStyle(colors.muted, 12.sp), maxLines = 1)
                    if (deadline != null) countdown(deadline) else Text(start, style = label.copy(fontSize = 24.sp), maxLines = 1)
                    target?.let { Text(it, style = labelStyle(colors.muted, 12.sp), maxLines = 1) }
                }
            }
        } else {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(next.shortLabel, style = label, maxLines = 1)
                next.phase?.let {
                    Spacer(GlanceModifier.width(8.dp))
                    PhasePill(it)
                }
            }
            // The platform Chronometer gets its own line: sharing a row with small text clipped it on-device.
            if (deadline != null) countdown(deadline) else if (plan.time) Text(start, style = label.copy(fontSize = 20.sp), maxLines = 1)
            target?.let { Text(it, style = labelStyle(colors.muted, 12.sp), maxLines = 1) }
        }
        if (plan.phaseBar) StageBar(next.phase, colors)
        if (plan.stageLabels != StageLabels.NONE) {
            StageLabelsRow(next.phase, model.stageTimes, plan.stageLabels == StageLabels.TIMES, model.timeZone, colors)
        }
        if (plan.alliances) {
            AllianceLine(MatchAlliance.RED, next.red, colors)
            AllianceLine(MatchAlliance.BLUE, next.blue, colors)
        }
        if (plan.footer) model.footer?.let {
            Text(it, style = labelStyle(colors.muted, 12.sp), maxLines = plan.footerLines, modifier = GlanceModifier.padding(top = 4.dp).semantics { testTag = "footer" })
        }
        // The start time on its own line, when the countdown has it and the stage labels don't show it.
        if (plan.time && deadline != null) Text(start, style = TextStyle(color = colors.muted, fontSize = 13.sp), maxLines = 1)
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

/** A red card: the team was disqualified from the match (TBA marks red cards; it publishes no yellow cards). */
@Composable
internal fun RedCard() {
    Box(GlanceModifier.width(6.dp).height(9.dp).background(ColorProvider(StatusColors.redCard)).cornerRadius(2.dp).semantics { testTag = "red-card" }) {}
}

/** Tall widgets: one line above the list; [compact] (narrow widgets) drops "LAST ·" — the outcome pill says it. */
@Composable
internal fun LastLine(result: MatchListModel.Result, compact: Boolean) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (compact) result.shortLabel else "LAST · ${result.shortLabel}",
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        Text("${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Spacer(GlanceModifier.width(6.dp))
        OutcomePill(result)
        // Asked for: who got a red card in that match, after the score ("▮5419").
        result.disqualified.forEach { team ->
            Spacer(GlanceModifier.width(6.dp))
            RedCard()
            Spacer(GlanceModifier.width(2.dp))
            Text(team, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED), maxLines = 1)
        }
    }
}

/** Wide widgets with no upcoming match: the last result, below the message. */
@Composable
private fun LastResult(result: MatchListModel.Result) {
    Column(GlanceModifier.padding(start = 8.dp, top = 4.dp)) {
        Text("LAST", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Text(result.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Text("${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        OutcomePill(result)
    }
}

/**
 * The upcoming schedule, one line per entry. Glance drops children past 10 per Column, so the lines go in
 * sub-columns of [WidgetLines.MAX_LINES] inside an outer Column (at most 3, for up to 27 lines).
 */
@Composable
internal fun UpcomingList(lines: List<WidgetLine>, times: TimeFormat) {
    Column {
        lines.chunked(WidgetLines.MAX_LINES).forEach { chunk -> UpcomingChunk(chunk, times) }
    }
}

@Composable
private fun UpcomingChunk(lines: List<WidgetLine>, times: TimeFormat) {
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
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
                        // The label never wraps; when a narrow widget runs out of room, the time is what gets trimmed.
                        Text(
                            item.row.shortLabel,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
                            maxLines = 1,
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        // A match already in motion shows its phase where the time goes: its start is "now" anyway.
                        val moving = item.row.phase?.takeIf { it != Phase.PRE_QUEUE }
                        if (moving != null) {
                            Pill(moving.stateLabel, ColorProvider(StatusColors.phase(moving)), ColorProvider(StatusColors.onPhase(moving)))
                        } else {
                            Text(
                                times.match(item.row.time, item.row.estimated),
                                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontFamily = CONDENSED),
                                maxLines = 1,
                            )
                        }
                    }
                    is MatchListModel.Item.Break -> Text(item.title, style = muted, modifier = GlanceModifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}
