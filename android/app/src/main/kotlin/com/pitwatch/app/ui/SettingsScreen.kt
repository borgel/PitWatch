package com.pitwatch.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.config.EventKeys
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.RefreshState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, config: UserConfig, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshState by container.stores.refreshState.data.collectAsStateWithLifecycle(initialValue = RefreshState())
    var eventOverride by rememberSaveable { mutableStateOf(config.eventKeyOverride.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }

    /** Saves a config change; [refetch] for changes that alter what to fetch (keys, event). */
    fun update(refetch: Boolean = false, transform: (UserConfig) -> UserConfig) = scope.launch {
        container.stores.config.updateData { transform(it) }
        rearmAutoStart(context, container)
        if (refetch) RefreshWorker.refreshNow(context)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Time source", style = MaterialTheme.typography.titleMedium)
        Choice("FRC Nexus (queue times)", config.effectiveTimeSource == TimeSource.NEXUS, enabled = config.isNexusConfigured) {
            update { it.copy(timeSource = TimeSource.NEXUS) }
        }
        Choice("TBA (match times)", config.effectiveTimeSource == TimeSource.TBA) { update { it.copy(timeSource = TimeSource.TBA) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use scheduled (not predicted) TBA times", Modifier.weight(1f))
            Switch(config.useScheduledTime, { checked -> update { it.copy(useScheduledTime = checked) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Queue offset: ${config.queueOffsetMinutes} min", Modifier.weight(1f))
            TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes - 5).coerceAtLeast(0)) } }) { Text("−") }
            TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes + 5).coerceAtMost(60)) } }) { Text("+") }
        }

        Text("Live tracking", style = MaterialTheme.typography.titleMedium)
        Choice("Near match (2 h before)", config.liveActivityMode == LiveActivityMode.NEAR_MATCH) {
            update { it.copy(liveActivityMode = LiveActivityMode.NEAR_MATCH) }
        }
        Choice("All day", config.liveActivityMode == LiveActivityMode.ALL_DAY) { update { it.copy(liveActivityMode = LiveActivityMode.ALL_DAY) } }
        if (!NotificationManagerCompat.from(context).canPostPromotedNotifications()) {
            Text("Live Updates are turned off for PitWatch, so tracking shows as a normal notification.")
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Notification settings") }
        }

        Text("Event", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            eventOverride, { eventOverride = it.trim().lowercase() },
            label = { Text("Event key override (blank = auto)") }, singleLine = true,
            isError = eventOverride.isNotEmpty() && !EventKeys.isValid(eventOverride),
        )
        Button(
            enabled = eventOverride.isEmpty() || EventKeys.isValid(eventOverride),
            onClick = { update(refetch = true) { it.copy(eventKeyOverride = eventOverride.ifEmpty { null }) } },
        ) { Text("Save event") }

        Text("API keys", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true)
        OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true)
        Button(onClick = { update(refetch = true) { it.copy(apiKey = apiKey.trim(), nexusApiKey = nexusKey.trim().ifEmpty { null }) } }) {
            Text("Save keys")
        }

        Text("Status", style = MaterialTheme.typography.titleMedium)
        Text(RefreshStatusText.format(refreshState, container.clock()))
        OutlinedButton(onClick = {
            scope.launch {
                container.repository.refresh(container.clock(), force = true)
                rearmAutoStart(context, container)
                RefreshWorker.ensureScheduled(context)
            }
        }) { Text("Force refresh") }

        Text("Queue data from frc.nexus · Match data from The Blue Alliance", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label)
    }
}
