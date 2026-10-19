package com.pitwatch.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
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
import com.pitwatch.app.R
import com.pitwatch.app.live.LiveCard
import com.pitwatch.app.live.StageChevrons
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
    /** The card's colors as plain values, for what's computed from them (the stage band's tints). */
    val day: LiveCard.Colors,
    val night: LiveCard.Colors,
)

@Composable
internal fun scoreboardColors(): ScoreboardColors {
    val context = LocalContext.current
    val day = LiveCard.colors(context, dark = false)
    val night = LiveCard.colors(context, dark = true)
    fun pair(pick: (LiveCard.Colors) -> Int) = ColorProvider(day = Color(pick(day)), night = Color(pick(night)))
    val stripe = ColorProvider(day = Color(day.text).copy(alpha = 0.05f), night = Color(night.text).copy(alpha = 0.07f))
    return ScoreboardColors(pair { it.background }, pair { it.text }, pair { it.muted }, pair { it.empty }, stripe, day, night)
}

internal fun labelStyle(color: ColorProvider, size: TextUnit = 11.sp) =
    TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)

/**
 * The stage band: the iOS app's chevrons, tinted (asked for; see [StageChevrons]). Four stages, and between each pair a
 * joint holding the earlier stage's arrow tip and the later one's notch. [labels] puts each stage's name, and its clock
 * time, inside it; without them the band is just its colors (the phase pill names the stage).
 */
@Composable
internal fun StageBand(phase: Phase?, started: Boolean, times: List<Instant?>, labels: StageLabels, zone: ZoneId, colors: ScoreboardColors) {
    val current = PhaseSteps.current(phase, started)
    val looks = StageChevrons.NAMES.indices.map {
        StageChevrons.look(it, current, colors.day, dark = false) to StageChevrons.look(it, current, colors.night, dark = true)
    }
    fun color(i: Int, pick: (StageChevrons.Look) -> Int) = ColorProvider(day = Color(pick(looks[i].first)), night = Color(pick(looks[i].second)))
    // The joint narrows with the band so its tips keep their angle: the bare band's are short (as in the mock-up).
    val (height, joint) = when (labels) {
        StageLabels.NONE -> 14.dp to 8.dp
        StageLabels.NAMES -> 24.dp to 12.dp
        StageLabels.TIMES -> 34.dp to 14.dp
    }
    Row(GlanceModifier.fillMaxWidth().padding(top = 6.dp).height(height + 6.dp)) {
        StageChevrons.NAMES.forEachIndexed { i, name ->
            if (i > 0) {
                Box(GlanceModifier.width(joint).fillMaxHeight().semantics { testTag = "stage-joint" }) {
                    ChevronPart(R.drawable.stage_tip, color(i - 1) { it.fill })
                    ChevronPart(R.drawable.stage_notch, color(i) { it.fill })
                }
            }
            val fill = color(i) { it.fill }
            val ground = when (i) {
                0 -> GlanceModifier.background(ImageProvider(R.drawable.stage_body_start), colorFilter = ColorFilter.tint(fill))
                StageChevrons.NAMES.lastIndex -> GlanceModifier.background(ImageProvider(R.drawable.stage_body_end), colorFilter = ColorFilter.tint(fill))
                else -> GlanceModifier.background(fill)
            }
            Box(
                GlanceModifier.defaultWeight().fillMaxHeight().then(ground).padding(start = if (i == 0) 8.dp else 4.dp, end = 2.dp)
                    .semantics { testTag = "phase-step" },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (labels != StageLabels.NONE) {
                    Column {
                        val weight = if (looks[i].first.bold) FontWeight.Bold else FontWeight.Medium
                        Text(
                            name,
                            style = TextStyle(color = color(i) { it.name }, fontSize = 11.sp, fontWeight = weight, fontFamily = CONDENSED),
                            maxLines = 1,
                            modifier = GlanceModifier.semantics { testTag = "stage-label" },
                        )
                        val time = times.getOrNull(i)?.takeIf { labels == StageLabels.TIMES }?.atZone(zone)?.format(CLOCK)
                        time?.let {
                            Text(
                                it,
                                style = TextStyle(color = color(i) { look -> look.time }, fontSize = 10.sp, fontWeight = FontWeight.Medium, fontFamily = CONDENSED),
                                maxLines = 1,
                                modifier = GlanceModifier.semantics { testTag = "stage-time" },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Half of a chevron joint, stretched to it and tinted. */
@Composable
private fun ChevronPart(drawable: Int, color: ColorProvider) {
    Image(
        ImageProvider(drawable),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        colorFilter = ColorFilter.tint(color),
        modifier = GlanceModifier.fillMaxSize(),
    )
}

private val CLOCK = DateTimeFormatter.ofPattern("h:mm")

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
