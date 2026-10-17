package com.pitwatch.app.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** [Widgets] (redrawn by [WidgetRefresh], previewed by [WidgetPreviews]) matches the widgets in the manifest. */
@RunWith(RobolectricTestRunner::class)
class WidgetRegistrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun manifestWidgets(): Set<String> = context.packageManager
        .queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName), PackageManager.GET_META_DATA)
        .map { it.activityInfo.name.substringAfterLast('.') }.toSet()

    @Test
    fun `the event schedule widget is declared`() {
        assertTrue("EventScheduleWidgetReceiver" in manifestWidgets(), manifestWidgets().toString())
    }

    @Test
    fun `every declared widget is refreshed and previewed`() {
        assertEquals(manifestWidgets(), Widgets.RECEIVERS.map { it.simpleName!! }.toSet())
    }
}
