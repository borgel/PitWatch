package com.pitwatch.app

import android.app.Application
import com.pitwatch.app.live.LiveNotification

class PitWatchApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        LiveNotification.ensureChannel(this)
    }
}
