package com.pitwatch.app.widget

import androidx.glance.appwidget.GlanceAppWidgetManager
import com.pitwatch.app.data.NotificationPrefs
import com.pitwatch.app.data.Stores
import androidx.compose.ui.unit.dp
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetPreviewsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val stores by lazy { Stores(tmp.root, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }

    @Test
    fun `sample data is a ready widget with a schedule`() {
        val now = Instant.parse("2026-04-11T00:00:00Z")
        assertEquals(WidgetModel.State.READY, SampleWidgetData.main(now).state)
        assertEquals("Q36", SampleWidgetData.main(now).next?.shortLabel)
        assertEquals(WidgetModel.State.READY, SampleWidgetData.schedule(now).state)
        kotlin.test.assertTrue(SampleWidgetData.schedule(now).days.isNotEmpty())
    }

    @Test
    fun `previews are published once per install`() = runBlocking {
        val calls = mutableListOf<String>()
        val ok = PreviewPublisher { calls += it.simpleName!!; GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }
        WidgetPreviews.registerOnce(stores.notificationPrefs, 7L, ok)
        WidgetPreviews.registerOnce(stores.notificationPrefs, 7L, ok)
        assertEquals(listOf("PitWatchWidgetReceiver", "ScheduleWidgetReceiver"), calls)
        assertEquals(7L, stores.notificationPrefs.data.first().previewsVersion)
    }

    @Test
    fun `a rate-limited publish is retried on a later launch`() = runBlocking {
        WidgetPreviews.registerOnce(stores.notificationPrefs, 7L) { GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED }
        assertEquals(NotificationPrefs().previewsVersion, stores.notificationPrefs.data.first().previewsVersion)
    }

    @Test
    fun `previews render at real widget sizes, not the 110 dp minimum`() {
        for (widget in listOf(PitWatchWidget(), ScheduleWidget())) {
            val mode = widget.previewSizeMode as androidx.glance.appwidget.SizeMode.Responsive
            kotlin.test.assertTrue(mode.sizes.all { it.width >= 170.dp && it.height >= 170.dp }, mode.sizes.toString())
        }
    }
}
