package com.pitwatch.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.ui.TimeFormat
import java.time.Instant
import kotlinx.coroutines.flow.first

/**
 * "Next match (simple)": only the next match — its number on our alliance's color, the countdown and what it counts
 * to, the phase, and the stage bar (asked for). Same data and look as [PitWatchWidget], nothing else.
 */
class SimpleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val previewSizeMode = SizeMode.Responsive(PREVIEW_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val stores = container.stores
        val initialCache = stores.cache.data.first()
        val initialConfig = stores.config.data.first()
        provideContent {
            GlanceTheme {
                LiveWidget(
                    stores.cache.data, stores.config.data, initialCache, initialConfig, container.clock,
                    content = { model, countdown -> SimpleWidgetContent(model, countdown) },
                ) { deadline, now ->
                    ChronometerCountdown(deadline, now)
                }
            }
        }
    }

    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme { SimpleWidgetContent(SampleWidgetData.main(Instant.now())) { StaticCountdown() } }
        }
    }

    companion object {
        /** 2×1, 2×2 and 4×2 as measured on a Pixel launcher (emulator-5580). */
        val PREVIEW_SIZES = setOf(androidx.compose.ui.unit.DpSize(172.dp, 100.dp), androidx.compose.ui.unit.DpSize(172.dp, 223.dp), androidx.compose.ui.unit.DpSize(360.dp, 223.dp))
    }
}

class SimpleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = SimpleWidget()
}

/** The simple widget at whatever size it's placed: its top filled by the match, countdown and phase; the stage bar under. */
@Composable
fun SimpleWidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit) {
    val size = LocalSize.current
    val colors = scoreboardColors()
    val next = model.next
    val deadline = model.countdownDeadline
    val plan = SimplePlans.plan(size.height, size.width, hasCountdown = deadline != null, hasPhase = next?.phase != null)
    val wide = size.width >= WidgetPlans.WIDE
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    Column(
        GlanceModifier.fillMaxSize().background(colors.background).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>())
            .semantics { testTag = "widget-root" },
        verticalAlignment = if (plan.singleRow) Alignment.CenterVertically else Alignment.Top,
    ) {
        if (model.state != WidgetModel.State.READY || next == null) {
            Text(model.message.orEmpty(), style = TextStyle(color = colors.text, fontSize = 16.sp))
            return@Column
        }
        val chipSp = when {
            plan.singleRow -> 26.sp
            plan.big -> 44.sp
            else -> 30.sp
        }
        val countdownSp = when {
            plan.singleRow -> 32f
            plan.big -> 56f
            else -> 40f
        }
        val start = times.match(next.time, next.estimated)
        val timeStyle = TextStyle(color = colors.text, fontSize = (countdownSp * 0.6f).sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
        val target = next.countdown?.target?.uppercase()?.takeIf { plan.target }
        val mainTime: @Composable () -> Unit = {
            CompositionLocalProvider(LocalCountdownSp provides countdownSp) {
                if (deadline != null) countdown(deadline) else Text(start, style = timeStyle, maxLines = 1)
            }
        }
        when {
            plan.singleRow -> Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MatchChip(next.shortLabel, next.alliance, chipSp, colors)
                Spacer(GlanceModifier.defaultWeight())
                mainTime()
            }
            wide -> Row(GlanceModifier.fillMaxWidth()) {
                // The chip side takes only what it needs; the clock's side takes the rest (see LocalCountdownAlignEnd).
                Column {
                    MatchChip(next.shortLabel, next.alliance, chipSp, colors)
                    if (plan.phase) next.phase?.let { PhasePill(it, if (plan.big) 15.sp else 12.sp) }
                }
                Column(horizontalAlignment = Alignment.End, modifier = GlanceModifier.defaultWeight()) {
                    CompositionLocalProvider(LocalCountdownAlignEnd provides true) { mainTime() }
                    target?.let { Text(it, style = labelStyle(colors.muted, if (plan.big) 14.sp else 12.sp), maxLines = 1) }
                }
            }
            else -> {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    MatchChip(next.shortLabel, next.alliance, chipSp, colors)
                    if (plan.phase) next.phase?.let {
                        Spacer(GlanceModifier.width(8.dp))
                        PhasePill(it)
                    }
                }
                mainTime()
                target?.let { Text(it, style = labelStyle(colors.muted, 12.sp), maxLines = 1) }
            }
        }
        if (plan.phaseBar) StageBand(next.phase, next.started, model.stageTimes, plan.stageLabels, model.timeZone, colors)
    }
}
