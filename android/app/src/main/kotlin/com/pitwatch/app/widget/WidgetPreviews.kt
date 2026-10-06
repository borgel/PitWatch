package com.pitwatch.app.widget

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.pitwatch.app.data.NotificationPrefs
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.first

fun interface PreviewPublisher {
    suspend fun publish(receiver: KClass<out GlanceAppWidgetReceiver>): Int
}

/** Registers both widgets' generated picker previews once per app version (the platform rate-limits this call). */
object WidgetPreviews {
    private val RECEIVERS = listOf(PitWatchWidgetReceiver::class, ScheduleWidgetReceiver::class)

    suspend fun registerOnce(prefs: DataStore<NotificationPrefs>, versionCode: Int, publisher: PreviewPublisher) {
        if (prefs.data.first().previewsVersion == versionCode) return
        val results = try {
            RECEIVERS.map { publisher.publish(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WidgetPreviews", "preview registration failed", e)
            return
        }
        // Rate-limited or failed: leave the version unrecorded so a later launch tries again.
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
            prefs.updateData { it.copy(previewsVersion = versionCode) }
        }
    }

    fun publisher(manager: GlanceAppWidgetManager) = PreviewPublisher { manager.setWidgetPreviews(it) }
}
