package com.pitwatch.app.ui.matches

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.app.ui.LocalTimeFormat
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.scoreboard.AccentButton
import com.pitwatch.app.ui.scoreboard.AllianceBands
import com.pitwatch.app.ui.scoreboard.AllianceLine
import com.pitwatch.app.ui.scoreboard.HeaderIconButton
import com.pitwatch.app.ui.scoreboard.HeroCountdownText
import com.pitwatch.app.ui.scoreboard.OutcomePill
import com.pitwatch.app.ui.scoreboard.PhasePill
import com.pitwatch.app.ui.scoreboard.PhaseTimeline
import com.pitwatch.app.ui.scoreboard.ScoreboardCard
import com.pitwatch.app.ui.scoreboard.ScreenHeader
import com.pitwatch.app.ui.scoreboard.SectionLabel
import com.pitwatch.app.ui.scoreboard.condensed
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Countdowns {
    /** "now", "12m", "1h 5m" — minutes rounded up. */
    fun text(deadline: Instant, now: Instant): String {
        val seconds = Duration.between(now, deadline).seconds
        if (seconds <= 0) return "now"
        val minutes = (seconds + 59) / 60
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }
}


/** Stateful wrapper: collects persisted state and wires actions. */
@Composable
fun MatchesScreen(container: AppContainer, config: UserConfig, onPickEvent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache by container.stores.cache.data.collectAsStateWithLifecycle(initialValue = EventCache())
    val refreshState by container.stores.refreshState.data.collectAsStateWithLifecycle(initialValue = RefreshState())
    val tracking by LiveMatchService.tracking.collectAsStateWithLifecycle()
    val now by produceState(container.clock()) {
        while (true) {
            delay(30_000)
            value = container.clock()
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    val model = remember(cache, config, refreshState, now) { MatchListModels.build(cache, config, refreshState, now) }

    MatchesContent(
        model = model,
        now = now,
        tracking = tracking,
        refreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    container.repository.refresh(container.clock(), force = true)
                    rearmAutoStart(context, container)
                } finally {
                    refreshing = false
                }
            }
        },
        onToggleTracking = {
            if (tracking) {
                context.startService(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP))
            } else {
                scope.launch { container.stores.liveControl.updateData { LiveControl() } }
                LiveMatchService.start(context)
            }
        },
        onOpenMatch = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onPickEvent = onPickEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesContent(
    model: MatchListModel,
    now: Instant,
    tracking: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleTracking: () -> Unit,
    onOpenMatch: (url: String) -> Unit,
    onPickEvent: () -> Unit,
) {
    val timeFormat = remember(model.timeZone, model.zoneLabel) { TimeFormat(model.timeZone, model.zoneLabel) }
    CompositionLocalProvider(LocalTimeFormat provides timeFormat) { MatchesBody(model, now, tracking, refreshing, onRefresh, onToggleTracking, onOpenMatch, onPickEvent) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchesBody(
    model: MatchListModel,
    now: Instant,
    tracking: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleTracking: () -> Unit,
    onOpenMatch: (url: String) -> Unit,
    onPickEvent: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenHeader(model.title, model.status?.text, action = { HeaderIconButton(Icons.Filled.DateRange, "Choose event", onPickEvent) })
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("matches"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero") { Hero(model, now, tracking, onToggleTracking, onOpenMatch) }
                model.nowQueuing?.let { queuing ->
                    item(key = "queuing") {
                        Text(
                            "NOW QUEUING · ${queuing.uppercase()}",
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            style = condensed(20.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (model.nexusUnavailable) {
                    item(key = "nexus") {
                        Text(
                            "Nexus unavailable — showing TBA times",
                            Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                model.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                when (model.empty) {
                    MatchListModel.Empty.NO_EVENT -> item(key = "empty") { EmptyState("No event yet", "Pick an event", onPickEvent) }
                    MatchListModel.Empty.NO_MATCHES -> item(key = "empty") { EmptyState("No matches scheduled yet", null, null) }
                    else -> Unit
                }
                model.days.forEachIndexed { index, day ->
                    // The hero shows the next match; a day left empty without it gets no header.
                    val items = day.items.filterNot { it is MatchListModel.Item.Upcoming && it.row.isNext }
                    if (items.isEmpty()) return@forEachIndexed
                    item(key = "day:$index") { SectionLabel(day.label, Modifier.padding(start = 4.dp, top = 10.dp)) }
                    items(items, key = { it.id }) { item ->
                        when (item) {
                            is MatchListModel.Item.Upcoming -> MatchRowItem(item.row, onOpenMatch)
                            is MatchListModel.Item.Break -> BreakRow(item)
                        }
                    }
                }
                if (model.results.isNotEmpty()) {
                    item(key = "results") { SectionLabel("Last", Modifier.padding(start = 4.dp, top = 10.dp)) }
                    items(model.results, key = { "result:${it.key}" }) { ResultRow(it, onOpenMatch) }
                }
            }
        }
    }
}

/** Next match, big countdown, phase timeline, alliance bands, and the tracking button; just the button when nothing is next. */
@Composable
private fun Hero(model: MatchListModel, now: Instant, tracking: Boolean, onToggleTracking: () -> Unit, onOpenMatch: (String) -> Unit) {
    val next = model.next
    ScoreboardCard(Modifier.fillMaxWidth()) {
        if (next != null) {
            Column(Modifier.clickable { onOpenMatch(next.url) }) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(next.label.uppercase(), Modifier.weight(1f), style = condensed(44.sp, FontWeight.ExtraBold), maxLines = 1)
                    next.phase?.let { PhasePill(it, 18.sp) }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    next.countdown?.let { HeroCountdownText(it.deadline, now) }
                    val start = LocalTimeFormat.current.match(next.time, next.estimated) + " start"
                    Text(
                        listOfNotNull(next.countdown?.target, start).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PhaseTimeline(next.phase, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
                AllianceBands(next.red, next.blue, Modifier.padding(top = 18.dp))
            }
        }
        AccentButton(
            if (tracking) "Stop live tracking" else "Start live tracking",
            onToggleTracking,
            Modifier.padding(20.dp),
            outlined = tracking,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyState(title: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title.uppercase(), style = condensed(22.sp))
        if (action != null && onAction != null) OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}

@Composable
internal fun MatchRowItem(row: MatchListModel.MatchRow, onOpenMatch: (String) -> Unit) {
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 16.dp, onClick = { onOpenMatch(row.url) }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.alliance?.let {
                    Text("●", style = condensed(22.sp), color = StatusColors.alliance(it))
                    Spacer(Modifier.width(8.dp))
                }
                Text(row.shortLabel, Modifier.weight(1f), style = condensed(28.sp, FontWeight.ExtraBold))
                // Only matches already in motion get a pill; color is for urgency, not for "later".
                row.phase?.takeIf { it != Phase.PRE_QUEUE }?.let {
                    PhasePill(it, 14.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    LocalTimeFormat.current.match(row.time, row.estimated),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AllianceLine(MatchAlliance.RED, row.red)
            AllianceLine(MatchAlliance.BLUE, row.blue)
        }
    }
}

@Composable
private fun BreakRow(item: MatchListModel.Item.Break) {
    val range = item.end?.let { " " + LocalTimeFormat.current.range(item.start, it) } ?: ""
    Text(
        "— ${(item.title + range).uppercase()} —",
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        style = condensed(17.sp, letterSpacing = 2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultRow(result: MatchListModel.Result, onOpenMatch: (String) -> Unit) {
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 16.dp, onClick = { onOpenMatch(result.url) }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.shortLabel, Modifier.weight(1f), style = condensed(28.sp, FontWeight.ExtraBold))
                OutcomePill(result)
            }
            AllianceLine(MatchAlliance.RED, result.red) { Score(result.redScore, winner = result.redScore >= result.blueScore) }
            AllianceLine(MatchAlliance.BLUE, result.blue) { Score(result.blueScore, winner = result.blueScore >= result.redScore) }
        }
    }
}

/** The winning score at full strength, the losing one muted; a tie keeps both full. */
@Composable
private fun Score(score: Int, winner: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface
    Text("$score", style = condensed(22.sp, FontWeight.ExtraBold), color = if (winner) color else color.copy(alpha = 0.6f))
}
