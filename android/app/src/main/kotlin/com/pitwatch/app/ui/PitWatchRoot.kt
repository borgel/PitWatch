package com.pitwatch.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.ui.events.EventPickerScreen
import com.pitwatch.app.ui.matches.MatchesScreen
import com.pitwatch.app.ui.pitmap.PitMapScreen

enum class Tab(val label: String, val icon: ImageVector) {
    MATCHES("Matches", Icons.AutoMirrored.Filled.List),
    PIT_MAP("Pit map", Icons.Filled.LocationOn),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun PitWatchScaffold(selected: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, style = MaterialTheme.typography.bodySmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

@Composable
fun PitWatchRoot(container: AppContainer) {
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = null)
    val current = config ?: return
    if (!current.isConfigured) {
        SetupScreen(container, current)
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.MATCHES) }
    var pickingEvent by rememberSaveable { mutableStateOf(false) }
    PitWatchScaffold(tab, onSelect = { tab = it; pickingEvent = false }) {
        when (tab) {
            Tab.MATCHES -> if (pickingEvent) {
                BackHandler { pickingEvent = false }
                EventPickerScreen(container, current, onDone = { pickingEvent = false })
            } else {
                MatchesScreen(container, current, onPickEvent = { pickingEvent = true })
            }
            Tab.PIT_MAP -> PitMapScreen(container, current, onOpenSettings = { tab = Tab.SETTINGS })
            Tab.SETTINGS -> SettingsScreen(container, current)
        }
    }
}
