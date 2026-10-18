package com.pitwatch.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Every match at the event in a scrolling list. Each placed widget keeps its own "show played matches" choice. */
class EventScheduleWidget : GlanceAppWidget() {
    // Two fixed layouts, not one per launcher-reported size: each copy carries the whole list (see MatchLine).
    override val sizeMode = SizeMode.Responsive(SIZES)
    override val previewSizeMode = SizeMode.Responsive(PitWatchWidget.PREVIEW_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val stores = container.stores
        val initialCache = stores.cache.data.first()
        val initialConfig = stores.config.data.first()
        provideContent {
            val showPlayed = currentState<Preferences>()[SHOW_PLAYED] ?: true
            GlanceTheme { LiveEventScheduleWidget(stores.cache.data, stores.config.data, initialCache, initialConfig, showPlayed, container.clock) }
        }
    }

    /** Generated widget-picker preview (Android 15+): the real layout with sample content. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { GlanceTheme { EventScheduleContent(SampleWidgetData.eventSchedule(Instant.now())) } }
    }

    companion object {
        /** Per-widget (Glance state) setting; on by default. */
        val SHOW_PLAYED = booleanPreferencesKey("show_played")

        /** Wide enough for both alliances' team numbers beside the label and time. */
        val TEAMS_WIDTH = 300.dp

        val SIZES = setOf(DpSize(110.dp, 110.dp), DpSize(TEAMS_WIDTH, 110.dp))

        /** Lines grouped into each list item: under a Column's 10-child limit. */
        const val LINES_PER_ITEM = WidgetLines.MAX_LINES
    }
}

class EventScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EventScheduleWidget()
}

@Composable
fun LiveEventScheduleWidget(
    cacheFlow: Flow<EventCache>,
    configFlow: Flow<UserConfig>,
    initialCache: EventCache,
    initialConfig: UserConfig,
    showPlayed: Boolean,
    clock: () -> Instant,
) {
    val cache by cacheFlow.collectAsState(initialCache)
    val config by configFlow.collectAsState(initialConfig)
    EventScheduleContent(EventScheduleModels.build(cache, config, showPlayed, clock()))
}

@Composable
fun EventScheduleContent(model: EventScheduleModel) {
    val teams = LocalSize.current.width >= EventScheduleWidget.TEAMS_WIDTH
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    Column(
        // The scoreboard's background, like the other widgets and the live card, so they all match.
        GlanceModifier.fillMaxSize().background(scoreboardColors().background).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>())
            .semantics { testTag = "event-schedule-root" },
    ) {
        Text(model.title, style = muted, maxLines = 1)
        if (model.state != WidgetModel.State.READY) {
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp), modifier = GlanceModifier.padding(top = 8.dp))
            return@Column
        }
        LazyColumn(GlanceModifier.fillMaxSize().padding(top = 2.dp).semantics { testTag = "event-schedule-list" }) {
            // Several lines per list item: each item is its own RemoteViews with a fixed cost, and every item travels
            // in the one widget update (see EventScheduleWidgetSizeTest). A Column holds at most 10 children.
            items(model.lines.chunked(EventScheduleWidget.LINES_PER_ITEM)) { chunk ->
                Column(GlanceModifier.fillMaxWidth()) {
                    for (line in chunk) {
                        when (line) {
                            is EventScheduleLine.Header -> Text(
                                line.label,
                                style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
                                modifier = GlanceModifier.padding(top = 6.dp, bottom = 1.dp),
                            )
                            is EventScheduleLine.Upcoming -> MatchLine(line.row, teams, times.match(line.row.time, line.row.estimated), played = false)
                            is EventScheduleLine.Played -> MatchLine(line.row, teams, "${line.row.redScore}–${line.row.blueScore}", played = true)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Label, (wide) both alliances, then the time or the red–blue score. Our matches sit on a tinted band with a dot in
 * our alliance's color. As few Texts as possible: every row travels in the one widget update, under the ~1 MB binder
 * limit, and each Text costs ~260 bytes before styling (see EventScheduleWidgetSizeTest).
 */
@Composable
private fun MatchLine(row: EventMatchRow, teams: Boolean, trailing: String, played: Boolean) {
    val band = if (row.ours != null) GlanceModifier.background(GlanceTheme.colors.secondaryContainer).cornerRadius(8.dp) else GlanceModifier
    val text = if (row.ours != null) GlanceTheme.colors.onSecondaryContainer else GlanceTheme.colors.onSurface
    Row(GlanceModifier.fillMaxWidth().then(band).padding(6.dp, 3.dp, 6.dp, 3.dp), verticalAlignment = Alignment.CenterVertically) {
        // Whichever of label or teams comes last takes the slack, pushing the dot and trailing text to the edge (and
        // keeping the label and team columns aligned on our rows too).
        Text(
            row.shortLabel,
            style = TextStyle(color = text, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            modifier = if (teams) GlanceModifier.width(46.dp) else GlanceModifier.defaultWeight(),
        )
        if (teams) {
            val style = TextStyle(color = text, fontSize = 13.sp, fontFamily = CONDENSED)
            val all = row.red.teams.joinToString(" ") { it.number } + "  ·  " + row.blue.teams.joinToString(" ") { it.number }
            if ((row.red.teams + row.blue.teams).none { it.dq }) {
                // One Text per row: every row of the list goes into one RemoteViews update (binder budget).
                Text(all, style = style, maxLines = 1, modifier = GlanceModifier.defaultWeight())
            } else {
                // Rare: split the teams around each red card, so the mark follows the disqualified team's number.
                Row(GlanceModifier.defaultWeight(), verticalAlignment = Alignment.CenterVertically) {
                    TeamSegments.of(row.red, row.blue).forEach { segment ->
                        if (segment == null) RedCard() else Text(segment, style = style, maxLines = 1)
                    }
                }
            }
        }
        row.ours?.let { Text("● ", style = TextStyle(color = ColorProvider(StatusColors.alliance(it)), fontSize = 12.sp)) }
        Text(trailing, style = TextStyle(color = text, fontSize = 14.sp, fontWeight = if (played) FontWeight.Bold else null))
    }
}

/** The event schedule's teams text split around red cards: strings, with null where a red card goes. */
internal object TeamSegments {
    fun of(red: MatchListModel.AllianceLine, blue: MatchListModel.AllianceLine): List<String?> {
        val out = mutableListOf<String?>()
        val text = StringBuilder()
        fun alliance(line: MatchListModel.AllianceLine) = line.teams.forEachIndexed { i, team ->
            if (i > 0) text.append(" ")
            text.append(team.number)
            if (team.dq) {
                out += text.toString()
                text.clear()
                out += null
            }
        }
        alliance(red)
        text.append("  ·  ")
        alliance(blue)
        if (text.isNotEmpty()) out += text.toString()
        return out
    }
}
