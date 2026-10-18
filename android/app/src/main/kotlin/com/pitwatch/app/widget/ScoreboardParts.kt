package com.pitwatch.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitwatch.app.live.LiveCard
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.scoreboard.PhaseSteps
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/*
 * The live notification's scoreboard card ([LiveCard]), in Glance: the widgets share its colors, stage bar, alliance
 * lines and footer, so they all look the same (asked for).
 */

/** Size of the platform Chronometer a widget's countdown slot draws (it can't take a Glance style). */
val LocalCountdownSp = compositionLocalOf { 40f }

/**
 * Right-align the Chronometer's text. Glance gives an AndroidRemoteViews its column's full width rather than its
 * text's, so in a right-hand column the clock fills the column and must hug its right edge (found on-device).
 */
val LocalCountdownAlignEnd = compositionLocalOf { false }

/** The card's colors, light and dark: the system switches between them with the theme. */
internal class ScoreboardColors(
    val background: ColorProvider,
    val text: ColorProvider,
    val muted: ColorProvider,
    val empty: ColorProvider,
    /** A faint band for alternating rows: the text color at low opacity, so it works on light and dark. */
    val stripe: ColorProvider,
)

@Composable
internal fun scoreboardColors(): ScoreboardColors {
    val context = LocalContext.current
    val day = LiveCard.colors(context, dark = false)
    val night = LiveCard.colors(context, dark = true)
    fun pair(pick: (LiveCard.Colors) -> Int) = ColorProvider(day = Color(pick(day)), night = Color(pick(night)))
    val stripe = ColorProvider(day = Color(day.text).copy(alpha = 0.05f), night = Color(night.text).copy(alpha = 0.07f))
    return ScoreboardColors(pair { it.background }, pair { it.text }, pair { it.muted }, pair { it.empty }, stripe)
}

internal fun labelStyle(color: ColorProvider, size: TextUnit = 11.sp) =
    TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)

/** Four rounded segments filled up to the current stage, the current one taller (the card's bar). */
@Composable
internal fun StageBar(phase: Phase?, colors: ScoreboardColors) {
    val current = PhaseSteps.current(phase)
    Row(GlanceModifier.fillMaxWidth().height(11.dp), verticalAlignment = Alignment.Bottom) {
        PhaseSteps.steps.indices.forEach { i ->
            val step = i + 1
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            val fill = if (current != null && step <= current) ColorProvider(STAGE_COLORS[i]) else colors.empty
            Box(
                GlanceModifier.defaultWeight().height(if (step == current) 11.dp else 7.dp).background(fill).cornerRadius(4.dp)
                    .semantics { testTag = "phase-step" },
            ) {}
        }
    }
}

private val STAGE_COLORS = StatusColors.notificationSegments
private val STAGE_NAMES = listOf("QUEUE", "DECK", "FIELD", "START")
private val CLOCK = DateTimeFormatter.ofPattern("h:mm")

/** Under the bar: each stage's name, with its clock time when [withTimes] ("QUEUE 9:41"); the current one colored. */
@Composable
internal fun StageLabelsRow(phase: Phase?, times: List<Instant?>, withTimes: Boolean, zone: ZoneId, colors: ScoreboardColors) {
    val current = PhaseSteps.current(phase)
    Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp)) {
        STAGE_NAMES.forEachIndexed { i, name ->
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            val time = times.getOrNull(i)?.takeIf { withTimes }?.atZone(zone)?.format(CLOCK)
            val color = if (i + 1 == current) ColorProvider(STAGE_COLORS[i]) else colors.muted
            Text(
                listOfNotNull(name, time).joinToString(" "),
                style = labelStyle(color),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight().semantics { testTag = "stage-label" },
            )
        }
    }
}

/** "● 4698 · [5507] · 1678": the alliance's dot, and our number on an alliance-colored chip (the card's line). */
@Composable
internal fun AllianceLine(alliance: MatchAlliance, line: MatchListModel.AllianceLine, colors: ScoreboardColors) {
    val style = TextStyle(color = colors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val allianceColor = ColorProvider(StatusColors.alliance(alliance))
    val (before, us, after) = TeamSplit.of(line)
    Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("● ", style = style.copy(color = allianceColor, fontSize = 12.sp))
        if (before.isNotEmpty()) Text(before, style = style, maxLines = 1)
        us?.let {
            Box(GlanceModifier.background(allianceColor).cornerRadius(4.dp).padding(horizontal = 4.dp)) {
                Text(it, style = style.copy(color = ColorProvider(Color.White)), maxLines = 1)
            }
        }
        if (after.isNotEmpty()) Text(after, style = style, maxLines = 1)
    }
}

/** The match number on our alliance's color (the simple widget, which has no alliance lines to show it). */
@Composable
internal fun MatchChip(label: String, alliance: MatchAlliance?, size: TextUnit, colors: ScoreboardColors) {
    val background = alliance?.let { ColorProvider(StatusColors.alliance(it)) } ?: colors.empty
    val text = if (alliance != null) ColorProvider(Color.White) else colors.text
    Box(GlanceModifier.background(background).cornerRadius(10.dp).padding(horizontal = 10.dp).semantics { testTag = "match-chip" }) {
        Text(label, style = TextStyle(color = text, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = CONDENSED), maxLines = 1)
    }
}

@Composable
internal fun PhasePill(phase: Phase, size: TextUnit = 12.sp) {
    Box(GlanceModifier.background(ColorProvider(StatusColors.phase(phase))).cornerRadius(6.dp).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(phase.stateLabel, style = labelStyle(ColorProvider(StatusColors.onPhase(phase)), size), maxLines = 1)
    }
}
