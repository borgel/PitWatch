package com.pitwatch.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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

/** Bottom navigation on a phone; a side rail in a wide window ([isWideWindow]). */
@Composable
fun PitWatchScaffold(selected: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    if (isWideWindow()) {
        // No Scaffold insets: the rail pads for the system bars on its side; screens pad for the rest themselves.
        Scaffold(contentWindowInsets = WindowInsets(0)) { padding ->
            Row(Modifier.padding(padding).fillMaxSize()) {
                NavigationRail(Modifier.testTag("nav-rail"), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Spacer(Modifier.height(12.dp))
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == selected,
                            onClick = { onSelect(tab) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label, style = MaterialTheme.typography.bodySmall) },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.onSurface,
                                indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            ),
                        )
                    }
                }
                // Like the bottom-bar layout, screens get the system bars padded for them; the rail covers the start edge
                // (bars and any camera cutout there), so only the other sides are padded here.
                val sides = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End + WindowInsetsSides.Bottom)
                Box(Modifier.weight(1f).windowInsetsPadding(sides).consumeWindowInsets(WindowInsets.safeDrawing)) { content() }
            }
        }
        return
    }
    Scaffold(
        bottomBar = {
            NavigationBar(Modifier.testTag("nav-bar"), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
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
        ReadableWidth { SetupScreen(container, current) }
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.MATCHES) }
    var pickingEvent by rememberSaveable { mutableStateOf(false) }
    PitWatchScaffold(tab, onSelect = { tab = it; pickingEvent = false }) {
        when (tab) {
            Tab.MATCHES -> if (pickingEvent) {
                BackHandler { pickingEvent = false }
                ReadableWidth { EventPickerScreen(container, current, onDone = { pickingEvent = false }) }
            } else {
                MatchesScreen(container, current, onPickEvent = { pickingEvent = true })
            }
            Tab.PIT_MAP -> PitMapScreen(container, current, onOpenSettings = { tab = Tab.SETTINGS })
            Tab.SETTINGS -> ReadableWidth { SettingsScreen(container, current) }
        }
    }
}
