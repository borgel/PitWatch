package com.pitwatch.app.ui

import android.content.Context
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.config.UserConfig
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

object SettingsActions {
    /**
     * Saves a config change on the app scope (it must survive leaving the screen). [refetch] (keys, event) queues
     * the refresh before re-rendering widgets, so a slow widget update can never hold it up.
     */
    fun save(context: Context, container: AppContainer, refetch: Boolean = false, transform: (UserConfig) -> UserConfig): Job {
        val appContext = context.applicationContext
        return container.scope.launch {
            container.stores.config.updateData { transform(it) }
            rearmAutoStart(appContext, container)
            if (refetch) RefreshWorker.refreshNow(appContext)
            container.updateWidgets()
        }
    }
}
