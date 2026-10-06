package com.pitwatch.app

import android.app.Application
import androidx.glance.appwidget.GlanceAppWidgetManager
import com.pitwatch.app.live.LiveNotification
import com.pitwatch.app.widget.WidgetPreviews
import kotlinx.coroutines.launch

class PitWatchApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        LiveNotification.ensureChannel(this)
        container.scope.launch {
            WidgetPreviews.registerOnce(container.stores.notificationPrefs, BuildConfig.VERSION_CODE, WidgetPreviews.publisher(GlanceAppWidgetManager(this@PitWatchApp)))
        }
    }
}
