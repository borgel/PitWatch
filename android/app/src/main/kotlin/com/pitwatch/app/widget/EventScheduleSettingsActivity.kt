package com.pitwatch.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import com.pitwatch.app.ui.theme.PitWatchTheme
import kotlinx.coroutines.launch

/**
 * The event schedule widget's settings, opened from the widget's long-press menu (reconfigurable, and optional
 * when the widget is added). Each change is saved for that one widget and redraws it straight away.
 */
class EventScheduleSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        val glanceId = try {
            GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        } catch (e: IllegalArgumentException) {
            null // the widget was removed (or never existed): nothing to configure
        }
        if (glanceId == null) {
            finish()
            return
        }
        enableEdgeToEdge()
        lifecycleScope.launch {
            val initial = getAppWidgetState(this@EventScheduleSettingsActivity, PreferencesGlanceStateDefinition, glanceId)[EventScheduleWidget.SHOW_PLAYED] ?: true
            setContent {
                PitWatchTheme {
                    Surface {
                        EventScheduleSettingsScreen(
                            initial,
                            onShowPlayedChange = { lifecycleScope.launch { save(glanceId, it) } },
                            onDone = {
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
                                finish()
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun save(glanceId: GlanceId, showPlayed: Boolean) {
        updateAppWidgetState(this, glanceId) { it[EventScheduleWidget.SHOW_PLAYED] = showPlayed }
        EventScheduleWidget().update(this, glanceId)
    }
}

@Composable
fun EventScheduleSettingsScreen(initialShowPlayed: Boolean, onShowPlayedChange: (Boolean) -> Unit, onDone: () -> Unit) {
    var showPlayed by remember { mutableStateOf(initialShowPlayed) }
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Event schedule widget", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        ListItem(
            headlineContent = { Text("Show played matches") },
            supportingContent = { Text("Results with both scores, below the upcoming matches.") },
            trailingContent = { Switch(showPlayed, onCheckedChange = null) },
            modifier = Modifier.toggleable(showPlayed, role = Role.Switch) {
                showPlayed = it
                onShowPlayedChange(it)
            },
        )
        Button(onDone, Modifier.align(Alignment.End).padding(horizontal = 16.dp)) { Text("Done") }
    }
}
