package com.pitwatch.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, config: UserConfig, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshState by container.stores.refreshState.data.collectAsStateWithLifecycle(initialValue = RefreshState())
    var eventOverride by rememberSaveable { mutableStateOf(config.eventKeyOverride.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }

    /** Saves a config change (and refreshes) on the app scope via [SettingsActions]. */
    fun update(transform: (UserConfig) -> UserConfig) = SettingsActions.save(context, container, transform)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Time source")
            Choice("FRC Nexus queue times", config.effectiveTimeSource == TimeSource.NEXUS, enabled = config.isNexusConfigured) {
                update { it.copy(timeSource = TimeSource.NEXUS) }
            }
            Choice("TBA match times", config.effectiveTimeSource == TimeSource.TBA) { update { it.copy(timeSource = TimeSource.TBA) } }
            ListItem(
                headlineContent = { Text("Use scheduled TBA times") },
                supportingContent = { Text("Instead of TBA's predicted times") },
                trailingContent = { Switch(config.useScheduledTime, { checked -> update { it.copy(useScheduledTime = checked) } }) },
            )
            ListItem(
                headlineContent = { Text("Queue offset") },
                supportingContent = { Text("${config.queueOffsetMinutes} min before the match (TBA times)") },
                trailingContent = {
                    Row {
                        TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes - 5).coerceAtLeast(0)) } }) { Text("−") }
                        TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes + 5).coerceAtMost(60)) } }) { Text("+") }
                    }
                },
            )
            HorizontalDivider()

            Section("Live tracking")
            Choice("Near match (starts 2 h before)", config.liveActivityMode == LiveActivityMode.NEAR_MATCH) {
                update { it.copy(liveActivityMode = LiveActivityMode.NEAR_MATCH) }
            }
            Choice("All day", config.liveActivityMode == LiveActivityMode.ALL_DAY) { update { it.copy(liveActivityMode = LiveActivityMode.ALL_DAY) } }
            if (PromotionHint.shouldShow(Build.VERSION.SDK_INT_FULL, NotificationManagerCompat.from(context).canPostPromotedNotifications())) {
                ListItem(
                    headlineContent = { Text("Live Updates are off for PitWatch") },
                    supportingContent = { Text("Tracking shows as a normal notification instead of in the status bar.") },
                    trailingContent = {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { Text("Settings") }
                    },
                )
            }
            HorizontalDivider()

            Section("Event")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    eventOverride, { eventOverride = it.trim().lowercase() },
                    label = { Text("Event key override (blank = auto)") }, singleLine = true,
                    isError = eventOverride.isNotEmpty() && !EventKeys.isValid(eventOverride),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = eventOverride.isEmpty() || EventKeys.isValid(eventOverride),
                    onClick = { update { it.copy(eventKeyOverride = eventOverride.ifEmpty { null }) } },
                ) { Text("Save event") }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))

            Section("API keys")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { update { it.copy(apiKey = apiKey.trim(), nexusApiKey = nexusKey.trim().ifEmpty { null }) } }) {
                    Text("Save keys")
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))

            Section("Status")
            Text(RefreshStatusText.format(refreshState, container.clock()), Modifier.padding(horizontal = 16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        container.repository.refresh(container.clock(), force = true)
                        rearmAutoStart(context, container)
                        RefreshWorker.ensureScheduled(context)
                    }
                },
                modifier = Modifier.padding(16.dp),
            ) { Text("Force refresh") }
            HorizontalDivider()

            Section("About")
            Text(
                "Queue data from frc.nexus · Match data from The Blue Alliance",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = onSelect).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 12.dp))
    }
}
