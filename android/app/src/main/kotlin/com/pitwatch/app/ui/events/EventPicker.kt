package com.pitwatch.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.ui.scoreboard.ScoreboardCard
import com.pitwatch.app.ui.scoreboard.ScreenHeader
import com.pitwatch.app.ui.scoreboard.condensed
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.Event
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class EventOption(val key: String, val name: String, val dates: String, val location: String?) {
    companion object {
        fun from(event: Event, locale: Locale = Locale.getDefault()) = EventOption(
            key = event.key,
            name = event.name,
            dates = dates(event, locale),
            location = listOfNotNull(event.city, event.stateProv).joinToString(", ").ifEmpty { null },
        )

        /** "Apr 9 – 12, 2026", "Mar 30 – Apr 2, 2026", "Dec 30, 2025 – Jan 2, 2026"; raw strings if unparseable. */
        fun dates(event: Event, locale: Locale): String {
            val start = runCatching { LocalDate.parse(event.startDate) }.getOrNull()
            val end = runCatching { LocalDate.parse(event.endDate) }.getOrNull()
            if (start == null || end == null) return "${event.startDate} – ${event.endDate}"
            fun f(pattern: String, date: LocalDate) = DateTimeFormatter.ofPattern(pattern, locale).format(date)
            return when {
                start == end -> f("MMM d, yyyy", start)
                start.year != end.year -> "${f("MMM d, yyyy", start)} – ${f("MMM d, yyyy", end)}"
                start.month != end.month -> "${f("MMM d", start)} – ${f("MMM d, yyyy", end)}"
                else -> "${f("MMM d", start)} – ${f("d, yyyy", end)}"
            }
        }
    }
}

sealed interface EventPickerState {
    data object Loading : EventPickerState
    data class Error(val message: String) : EventPickerState
    data class Loaded(val options: List<EventOption>) : EventPickerState
}

@Composable
fun EventPickerScreen(container: AppContainer, config: UserConfig, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<EventPickerState>(EventPickerState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        state = EventPickerState.Loading
        state = try {
            EventPickerState.Loaded(container.repository.seasonEvents(container.clock()).map { EventOption.from(it) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EventPickerState.Error(e.message ?: "Unknown error")
        }
    }
    EventPickerContent(
        state = state,
        selectedKey = config.eventKeyOverride,
        onSelect = { key ->
            container.scope.launch {
                container.stores.config.updateData { it.copy(eventKeyOverride = key) }
                RefreshWorker.refreshNow(context.applicationContext)
            }
            onDone()
        },
        onBack = onDone,
        onRetry = { attempt++ },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerContent(
    state: EventPickerState,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Scaffold(
        topBar = {
            ScreenHeader(
                "Choose event", null,
                navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when (state) {
            EventPickerState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Loading events…", Modifier.padding(top = 72.dp))
            }
            is EventPickerState.Error -> Column(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Couldn't load events: ${state.message}", color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
            }
            is EventPickerState.Loaded -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { EventRow("Auto (current or next event)", null, selectedKey == null) { onSelect(null) } }
                items(state.options, key = { it.key }) { option ->
                    EventRow(option.name, listOfNotNull(option.dates, option.location).joinToString(" · "), option.key == selectedKey) { onSelect(option.key) }
                }
            }
        }
    }
}

/** A picker row: the current event gets an accent bar on its left edge and a check. */
@Composable
private fun EventRow(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 16.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(name, style = condensed(20.sp))
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", Modifier.padding(end = 14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
