package com.pitwatch.app.widget

import android.content.Context
import android.os.Bundle
import android.os.Parcel
import androidx.compose.ui.unit.DpSize
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.LA
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.Match
import java.util.Locale
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Found on-device: the whole 120-match schedule made a 1.2 MB widget update, past the ~1 MB binder limit, and the
 * launcher showed "Can't show content". A scrolling widget's rows all travel in that one update, once for each of the
 * widget's sizes, so together they must stay well under the limit.
 */
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
class EventScheduleWidgetSizeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")

    private fun bytes(model: EventScheduleModel, size: DpSize): Int = runBlocking {
        val views = GlanceRemoteViews().compose(context, size, null, Bundle.EMPTY) { GlanceTheme { EventScheduleContent(model) } }.remoteViews
        val parcel = Parcel.obtain()
        try {
            views.writeToParcel(parcel, 0)
            parcel.dataSize()
        } finally {
            parcel.recycle()
        }
    }

    /** One update carries a render at each of the widget's sizes. */
    private fun update(model: EventScheduleModel) = EventScheduleWidget.SIZES.sumOf { bytes(model, it) }

    @Test
    fun `the full event schedule fits in one widget update`() {
        val model = EventScheduleModels.build(snapshotCache(), config, showPlayed = true, SNAP_NOW, Locale.US, LA)
        val total = update(model)
        println("event schedule widget update: $total bytes for ${model.lines.size} lines")
        assertTrue(total < LIMIT, "widget update is $total bytes")
    }

    @Test
    fun `the largest list the model allows still fits`() {
        // 160 quals plus a full double-elimination bracket and finals, near the model's line cap.
        val cache = snapshotCache()
        val template = cache.matches.first()
        val extra = (121..160).map { n -> template.copy(key = "2026cancmp_qm$n", matchNumber = n, actualTime = null) } +
            (1..13).map { s -> template.copy(key = "2026cancmp_sf${s}m1", compLevel = "sf", setNumber = s, matchNumber = 1, actualTime = null) } +
            (1..3).map { n -> template.copy(key = "2026cancmp_f1m$n", compLevel = "f", setNumber = 1, matchNumber = n, actualTime = null) }
        val big: List<Match> = cache.matches + extra
        val model = EventScheduleModels.build(cache.copy(matches = big), config, showPlayed = true, SNAP_NOW, Locale.US, LA)
        val total = update(model)
        println("event schedule widget update: $total bytes for ${model.lines.size} lines")
        assertTrue(model.lines.size >= EventScheduleModels.MAX_LINES * 9 / 10, "test a list near the cap")
        assertTrue(total < LIMIT, "widget update is $total bytes for ${model.lines.size} lines")
    }

    private companion object {
        /** Half the ~1 MB binder limit, which the process's other in-flight transactions share. */
        const val LIMIT = 512 * 1024
    }
}
