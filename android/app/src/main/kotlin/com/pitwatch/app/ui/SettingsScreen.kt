package com.pitwatch.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.app.ui.scoreboard.AccentButton
import com.pitwatch.app.ui.scoreboard.ScoreboardCard
import com.pitwatch.app.ui.scoreboard.ScreenHeader
import com.pitwatch.app.ui.scoreboard.SectionLabel
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
    // Re-checked on every resume: the user may have just flipped it in system notification settings.
    var canPromote by remember { mutableStateOf(NotificationManagerCompat.from(context).canPostPromotedNotifications()) }
    LifecycleResumeEffect(Unit) {
        canPromote = NotificationManagerCompat.from(context).canPostPromotedNotifications()
        onPauseOrDispose { }
    }

    /** Saves a config change (and refreshes) on the app scope via [SettingsActions]. */
    fun update(transform: (UserConfig) -> UserConfig) = SettingsActions.save(context, container, transform)

    Scaffold(
        topBar = {
            ScreenHeader(
                "Settings", RefreshStatusText.format(refreshState, container.clock()).lineSequence().first(),
                navigation = onBack?.let { back -> { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("Time source") {
                Choice("FRC Nexus queue times", config.effectiveTimeSource == TimeSource.NEXUS, enabled = config.isNexusConfigured) {
                    update { it.copy(timeSource = TimeSource.NEXUS) }
                }
                Choice("TBA match times", config.effectiveTimeSource == TimeSource.TBA) { update { it.copy(timeSource = TimeSource.TBA) } }
                ListItem(
                    headlineContent = { Text("Use scheduled TBA times") },
                    supportingContent = { Text("Instead of TBA's predicted times") },
                    trailingContent = { Switch(config.useScheduledTime, { checked -> update { it.copy(useScheduledTime = checked) } }) },
                    colors = clearRow(),
                )
                ListItem(
                    headlineContent = { Text("Queue offset") },
                    supportingContent = { Text("${config.queueOffsetMinutes} min before the match (TBA times)") },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes - 5).coerceAtLeast(0)) } }) { Text("−", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp)) }
                            TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes + 5).coerceAtMost(60)) } }) { Text("+", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp)) }
                        }
                    },
                    colors = clearRow(),
                )
            }

            Section("Live tracking") {
                Choice("Near match (starts 2 h before)", config.liveActivityMode == LiveActivityMode.NEAR_MATCH) {
                    update { it.copy(liveActivityMode = LiveActivityMode.NEAR_MATCH) }
                }
                Choice("All day", config.liveActivityMode == LiveActivityMode.ALL_DAY) { update { it.copy(liveActivityMode = LiveActivityMode.ALL_DAY) } }
                if (PromotionHint.shouldShow(Build.VERSION.SDK_INT_FULL, canPromote)) {
                    ListItem(
                        headlineContent = { Text("Live Updates are off for PitWatch") },
                        supportingContent = { Text("Tracking shows as a normal notification instead of in the status bar.") },
                        trailingContent = {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }) { Text("Settings") }
                        },
                        colors = clearRow(),
                    )
                }
            }

            Section("Event") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        eventOverride, { eventOverride = it.trim().lowercase() },
                        label = { Text("Event key override (blank = auto)") }, singleLine = true,
                        isError = eventOverride.isNotEmpty() && !EventKeys.isValid(eventOverride),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AccentButton(
                        "Save event",
                        enabled = eventOverride.isEmpty() || EventKeys.isValid(eventOverride),
                        onClick = { update { it.copy(eventKeyOverride = eventOverride.ifEmpty { null }) } },
                    )
                }
            }

            Section("API keys") {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(ApiKeyHelp.TBA) })
                    OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(ApiKeyHelp.NEXUS) })
                    AccentButton("Save keys", onClick = { update { it.copy(apiKey = apiKey.trim(), nexusApiKey = nexusKey.trim().ifEmpty { null }) } })
                }
            }

            Section("Status") {
                // The header fits one line; errors (TBA and Nexus) are spelled out here.
                Text(RefreshStatusText.format(refreshState, container.clock()), Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            container.repository.refresh(container.clock(), force = true)
                            rearmAutoStart(context, container)
                            RefreshWorker.ensureScheduled(context)
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) { Text("Force refresh") }
            }

            Section("About") {
                Text(
                    "Queue data from frc.nexus · Match data from The Blue Alliance",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

/** A titled group of settings on a rounded card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title, Modifier.padding(start = 8.dp, top = 14.dp))
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 20.dp) { Column(Modifier.padding(vertical = 6.dp), content = content) }
}

/** List rows sit directly on the card. */
@Composable
private fun clearRow() = ListItemDefaults.colors(containerColor = Color.Transparent)

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
