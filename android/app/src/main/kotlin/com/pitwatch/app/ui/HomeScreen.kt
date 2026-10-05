package com.pitwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Placeholder home until Plan 3's match list: event, next match, status, actions. */
@Composable
fun HomeScreen(container: AppContainer, config: UserConfig, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache by container.stores.cache.data.collectAsStateWithLifecycle(initialValue = EventCache())
    val next = MatchSchedule(cache.matches, config.teamKey.orEmpty()).nextMatch
    val time = DateTimeFormatter.ofPattern("EEE h:mm a").withZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Team ${config.teamNumber}", style = MaterialTheme.typography.headlineSmall)
        Text(cache.event?.name ?: "No event yet")
        Text(
            next?.let { "Next: ${it.label}" + (it.matchDate()?.let { at -> " · ~${time.format(at)}" } ?: "") }
                ?: "No upcoming matches",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = {
            scope.launch { container.stores.liveControl.updateData { LiveControl() } }
            LiveMatchService.start(context)
        }) { Text("Start live tracking") }
        OutlinedButton(onClick = {
            scope.launch {
                container.repository.refresh(container.clock(), force = true)
                rearmAutoStart(context, container)
            }
        }) { Text("Refresh now") }
        OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
    }
}
