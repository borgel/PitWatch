package com.pitwatch.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.Event
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class EventOption(val key: String, val name: String, val dates: String, val location: String?) {
    companion object {
        fun from(event: Event) = EventOption(
            key = event.key,
            name = event.name,
            dates = "${event.startDate} – ${event.endDate}",
            location = listOfNotNull(event.city, event.stateProv).joinToString(", ").ifEmpty { null },
        )
    }
}

sealed interface EventPickerState {
    data object Loading : EventPickerState
    data class Error(val message: String) : EventPickerState
    data class Loaded(val options: List<EventOption>) : EventPickerState
}

@Composable
fun EventPickerScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = UserConfig())
    var state by remember { mutableStateOf<EventPickerState>(EventPickerState.Loading) }
    LaunchedEffect(Unit) {
        state = try {
            EventPickerState.Loaded(container.repository.seasonEvents(container.clock()).map(EventOption::from))
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
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerContent(state: EventPickerState, selectedKey: String?, onSelect: (String?) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when (state) {
            EventPickerState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Loading events…", Modifier.padding(top = 72.dp))
            }
            is EventPickerState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Couldn't load events: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            is EventPickerState.Loaded -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        headlineContent = { Text("Auto (current or next event)") },
                        trailingContent = { if (selectedKey == null) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                        modifier = Modifier.clickable { onSelect(null) },
                    )
                    HorizontalDivider()
                }
                items(state.options, key = { it.key }) { option ->
                    ListItem(
                        headlineContent = { Text(option.name) },
                        supportingContent = { Text(listOfNotNull(option.dates, option.location).joinToString(" · ")) },
                        trailingContent = { if (option.key == selectedKey) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                        modifier = Modifier.clickable { onSelect(option.key) },
                    )
                }
            }
        }
    }
}
