package com.pitwatch.app

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.pitwatch.app.live.LiveNotification
import com.pitwatch.app.notify.ScheduleNotification
import com.pitwatch.app.widget.WidgetPreviews
import kotlinx.coroutines.launch

class PitWatchApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        LiveNotification.ensureChannel(this)
        ScheduleNotification.ensureChannel(this)
        container.scope.launch {
            // Keyed on the install, not the version code: a sideloaded rebuild keeps its version code but may change the preview.
            val installKey = packageManager.getPackageInfo(packageName, 0).lastUpdateTime
            WidgetPreviews.registerOnce(container.stores.notificationPrefs, installKey, WidgetPreviews.publisher(GlanceAppWidgetManager(this@PitWatchApp)))
        }
    }
}
