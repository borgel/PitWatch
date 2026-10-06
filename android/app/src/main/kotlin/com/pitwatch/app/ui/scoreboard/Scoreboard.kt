package com.pitwatch.app.ui.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.Barlow
import com.pitwatch.app.ui.theme.BarlowCondensed
import com.pitwatch.app.ui.theme.PitWatchType
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** Condensed scoreboard type at [size] and [weight]. */
fun condensed(size: TextUnit, weight: FontWeight = FontWeight.Bold, letterSpacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = BarlowCondensed, fontWeight = weight, fontSize = size, letterSpacing = letterSpacing)

/** Rounded surface-container card; in light mode a hairline ring stands in for elevation. */
@Composable
fun ScoreboardCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    val light = colors.surface.luminance() > 0.5f
    Column(
        modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .then(if (light) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = PitWatchType.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StatusPill(text: String, container: Color, content: Color, fontSize: TextUnit = 16.sp) {
    Text(
        text.uppercase(),
        Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
        color = content,
        style = condensed(fontSize, FontWeight.ExtraBold, 1.sp),
        maxLines = 1,
    )
}

@Composable
fun PhasePill(phase: Phase, fontSize: TextUnit = 16.sp) =
    StatusPill(phase.stateLabel, StatusColors.phase(phase), StatusColors.onPhase(phase), fontSize)

@Composable
fun OutcomePill(result: MatchListModel.Result, fontSize: TextUnit = 16.sp) {
    val pill = StatusColors.outcome(result.outcome)
    val colors = MaterialTheme.colorScheme
    StatusPill(result.outcomeLabel, pill?.container ?: colors.surfaceVariant, pill?.content ?: colors.onSurfaceVariant, fontSize)
}

@Composable
fun TeamChip(number: String, container: Color, content: Color, style: TextStyle) {
    Text(
        number,
        Modifier
            .background(container, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = "Your team, $number" },
        color = content,
        style = style,
        maxLines = 1,
    )
}

/** "4698 · [5507] · 1678": our team in a chip. */
@Composable
private fun Teams(line: MatchListModel.AllianceLine, style: TextStyle, color: Color, chipContainer: Color, chipContent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        line.teams.forEachIndexed { i, team ->
            if (i > 0) Text(" · ", style = style, color = color)
            if (team.isUs) TeamChip(team.number, chipContainer, chipContent, style) else Text(team.number, style = style, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun Opr(value: Double, color: Color) =
    Text("Σ %.0f".format(value), style = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = color)

/** Compact alliance row for lists: a colored edge stripe, the teams, and a trailing value (Σ OPR by default). */
@Composable
fun AllianceLine(
    alliance: MatchAlliance,
    line: MatchListModel.AllianceLine,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { line.summedOpr?.let { Opr(it, MaterialTheme.colorScheme.onSurfaceVariant) } },
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(StatusColors.alliance(alliance), RoundedCornerShape(3.dp)))
        Box(Modifier.weight(1f)) { Teams(line, condensed(20.sp, letterSpacing = 0.5.sp), colors.onSurface, colors.primary, colors.onPrimary) }
        trailing()
    }
}

/** Full-width red and blue bands for the hero. */
@Composable
fun AllianceBands(red: MatchListModel.AllianceLine, blue: MatchListModel.AllianceLine, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        for ((alliance, line) in listOf(MatchAlliance.RED to red, MatchAlliance.BLUE to blue)) {
            val band = StatusColors.alliance(alliance)
            Row(Modifier.fillMaxWidth().background(band).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Teams(line, condensed(26.sp, letterSpacing = 1.sp), Color.White, Color.White, band) }
                line.summedOpr?.let { Opr(it, Color.White) }
            }
        }
    }
}

object PhaseSteps {
    /** Label and the phase that fills it; the last step is the match itself, which no phase reaches. */
    val steps: List<Pair<String, Phase?>> =
        listOf("QUEUE" to Phase.QUEUEING, "ON DECK" to Phase.ON_DECK, "ON FIELD" to Phase.ON_FIELD, "MATCH" to null)

    /** 1-based current step; null before queueing and for TBA-only rows with no phase. */
    fun current(phase: Phase?): Int? = when (phase) {
        Phase.QUEUEING -> 1
        Phase.ON_DECK -> 2
        Phase.ON_FIELD -> 3
        Phase.PRE_QUEUE, null -> null
    }

    fun description(phase: Phase?): String {
        val step = current(phase) ?: return "Not queued yet"
        return phase!!.stateLabel.lowercase().replaceFirstChar { it.uppercase() } + ", step $step of 4"
    }
}

/** Queue → on deck → on field → match: filled up to the current phase, the current step taller. */
@Composable
fun PhaseTimeline(phase: Phase?, modifier: Modifier = Modifier) {
    val current = PhaseSteps.current(phase)
    val colors = MaterialTheme.colorScheme
    val description = PhaseSteps.description(phase)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            PhaseSteps.steps.forEachIndexed { i, (_, stepPhase) ->
                val step = i + 1
                val fill = stepPhase?.takeIf { current != null && step <= current }?.let(StatusColors::phase)
                Box(
                    Modifier.weight(1f).height(if (step == current) 16.dp else 10.dp)
                        .background(fill ?: colors.surfaceContainerHighest, RoundedCornerShape(6.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PhaseSteps.steps.forEachIndexed { i, (label, stepPhase) ->
                val isCurrent = i + 1 == current
                Text(
                    if (isCurrent) "$label ◂" else label,
                    Modifier.weight(1f),
                    style = condensed(16.sp, letterSpacing = 1.sp),
                    color = if (isCurrent && stepPhase != null) StatusColors.phase(stepPhase) else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

object HeroCountdown {
    /** "3:27" under an hour, "1:05:09" beyond, "0:00" once passed. */
    fun text(deadline: Instant, now: Instant): String {
        val s = Duration.between(now, deadline).seconds.coerceAtLeast(0)
        return if (s < 3600) "%d:%02d".format(s / 60, s % 60) else "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }
}

/** The hero's big ticking countdown; [now] arrives every 30 s, so it ticks locally in between. */
@Composable
fun HeroCountdownText(deadline: Instant, now: Instant, modifier: Modifier = Modifier) {
    val ticking by produceState(now, deadline, now) {
        while (true) {
            delay(1_000)
            value = value.plusSeconds(1)
        }
    }
    BasicText(
        HeroCountdown.text(deadline, ticking),
        modifier.fillMaxWidth(),
        style = PitWatchType.countdownHero.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = 128.sp),
    )
}

/** Replaces the top app bar: condensed caps title, a muted subtitle, optional leading navigation and trailing action. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        navigation?.let {
            it()
            Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = condensed(26.sp, letterSpacing = 0.4.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        action?.invoke()
    }
}

@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(44.dp)) { Icon(icon, contentDescription = description) }
}

/** Full-width 56 dp accent button in condensed caps; [outlined] for the "stop" form. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(28.dp)
    val label: @Composable () -> Unit = { Text(text.uppercase(), style = condensed(22.sp, FontWeight.ExtraBold, 1.sp), textAlign = TextAlign.Center) }
    if (outlined) {
        OutlinedButton(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled, shape = shape) { label() }
    } else {
        Button(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled, shape = shape) { label() }
    }
}
