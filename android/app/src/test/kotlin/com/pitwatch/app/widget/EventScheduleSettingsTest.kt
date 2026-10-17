package com.pitwatch.app.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitwatch.app.installTestContainer
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class EventScheduleSettingsTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val widgetId = 42

    @Before
    fun setUp() {
        installTestContainer(tmp.root)
        // Redrawing a Glance widget runs through WorkManager.
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        val info = AppWidgetProviderInfo().apply { provider = ComponentName(context, EventScheduleWidgetReceiver::class.java) }
        shadowOf(AppWidgetManager.getInstance(context)).addBoundWidget(widgetId, info)
        // Glance caches each widget's state store for the whole process, so one test's choice would leak into the next.
        runBlocking { updateAppWidgetState(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) { it.clear() } }
    }

    private fun intent(id: Int = widgetId) = Intent(context, EventScheduleSettingsActivity::class.java)
        .setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)

    private fun stored(): Boolean? = runBlocking {
        val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        getAppWidgetState(context, PreferencesGlanceStateDefinition, glanceId)[EventScheduleWidget.SHOW_PLAYED]
    }

    @Test
    fun `played matches start on, and switching them off is saved for this widget`() {
        ActivityScenario.launchActivityForResult<EventScheduleSettingsActivity>(intent()).use { scenario ->
            compose.onNodeWithText("Show played matches").assertIsOn()
            compose.onNodeWithText("Show played matches").performClick()
            compose.onNodeWithText("Show played matches").assertIsOff()
            compose.waitUntil(5_000) { stored() == false }

            compose.onNodeWithText("Done").performClick()
            assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
            assertEquals(widgetId, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        }
    }

    @Test
    fun `reopening shows the widget's saved choice`() {
        runBlocking {
            updateAppWidgetState(context, GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)) { it[EventScheduleWidget.SHOW_PLAYED] = false }
        }
        ActivityScenario.launch<EventScheduleSettingsActivity>(intent()).use {
            compose.onNodeWithText("Show played matches").assertIsOff()
        }
    }

    @Test
    fun `an unknown widget closes the screen instead of crashing`() {
        ActivityScenario.launchActivityForResult<EventScheduleSettingsActivity>(intent(id = 99)).use { scenario ->
            assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        }
    }
}
