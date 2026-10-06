package com.pitwatch.app.ui.matches

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.app.ui.LocalTimeFormat
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.config.UserConfig
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
            TopAppBar(
                title = { Text(model.title) },
                actions = { IconButton(onClick = onPickEvent) { Icon(Icons.Filled.DateRange, contentDescription = "Choose event") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("matches"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "status") { StatusCard(model, tracking, onToggleTracking) }
                model.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                when (model.empty) {
                    MatchListModel.Empty.NO_EVENT -> item(key = "empty") {
                        EmptyState("No event yet", "Pick an event", onPickEvent)
                    }
                    MatchListModel.Empty.NO_MATCHES -> item(key = "empty") { EmptyState("No matches scheduled yet", null, null) }
                    else -> Unit
                }
                model.days.forEachIndexed { index, day ->
                    item(key = "day:$index") { SectionHeader(day.label) }
                    items(day.items, key = { it.id }) { item ->
                        when (item) {
                            is MatchListModel.Item.Upcoming ->
                                if (item.row.isNext) NextMatchCard(item.row, now, onOpenMatch) else MatchRowItem(item.row, onOpenMatch)
                            is MatchListModel.Item.Break -> BreakRow(item)
                        }
                    }
                }
                if (model.results.isNotEmpty()) {
                    item(key = "results") { SectionHeader("Results") }
                    items(model.results, key = { "result:${it.key}" }) { ResultRow(it, onOpenMatch) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(model: MatchListModel, tracking: Boolean, onToggleTracking: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.status?.let { Text(it.text, style = MaterialTheme.typography.titleMedium) }
            if (tracking) {
                OutlinedButton(onClick = onToggleTracking) { Text("Stop live tracking") }
            } else {
                Button(onClick = onToggleTracking) { Text("Start live tracking") }
            }
            model.nowQueuing?.let { Text("Now queuing: $it", style = MaterialTheme.typography.bodyMedium) }
            if (model.nexusUnavailable) {
                Text("Nexus unavailable — showing TBA times", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyState(title: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun PhaseBadge(phase: Phase) {
    Surface(color = StatusColors.phase(phase), shape = RoundedCornerShape(50)) {
        Text(
            phase.stateLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = StatusColors.onPhase(phase),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AllianceLineText(line: MatchListModel.AllianceLine, alliance: com.pitwatch.core.model.MatchAlliance) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(StatusColors.alliance(alliance), CircleShape))
        Spacer(Modifier.width(6.dp))
        line.teams.forEach { team ->
            Text(
                team.number,
                fontWeight = if (team.isUs) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        line.summedOpr?.let { Text("Σ %.1f".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NextMatchCard(row: MatchListModel.MatchRow, now: Instant, onOpenMatch: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(row.url) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                row.phase?.let { PhaseBadge(it) }
            }
            Row {
                Text(LocalTimeFormat.current.match(row.time, row.estimated), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                row.countdown?.let {
                    Text("${Countdowns.text(it.deadline, now)} ${it.target}", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                }
            }
            AllianceLineText(row.red, com.pitwatch.core.model.MatchAlliance.RED)
            AllianceLineText(row.blue, com.pitwatch.core.model.MatchAlliance.BLUE)
        }
    }
}

@Composable
internal fun MatchRowItem(row: MatchListModel.MatchRow, onOpenMatch: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenMatch(row.url) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.alliance?.let { Box(Modifier.size(8.dp).background(StatusColors.alliance(it), CircleShape)) }
        Spacer(Modifier.width(8.dp))
        Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Only matches already in motion get a badge; color is for urgency, not for "later".
        row.phase?.takeIf { it != Phase.PRE_QUEUE }?.let {
            PhaseBadge(it)
            Spacer(Modifier.width(8.dp))
        }
        Text(LocalTimeFormat.current.match(row.time, row.estimated), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
    HorizontalDivider()
}

@Composable
private fun BreakRow(item: MatchListModel.Item.Break) {
    val range = item.end?.let { " · " + LocalTimeFormat.current.range(item.start, it) } ?: ""
    Text(
        item.title + range,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
    )
}

@Composable
private fun ResultRow(result: MatchListModel.Result, onOpenMatch: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenMatch(result.url) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(result.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${result.outcome} ${result.ourScore}–${result.theirScore}", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
    HorizontalDivider()
}
