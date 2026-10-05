package com.pitwatch.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer

@Composable
fun PitWatchRoot(container: AppContainer) {
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = null)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val current = config ?: return
    when {
        !current.isConfigured -> SetupScreen(container, current)
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(container, current, onBack = { showSettings = false })
        }
        else -> HomeScreen(container, current, onOpenSettings = { showSettings = true })
    }
}
