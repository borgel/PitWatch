package com.pitwatch.app

import android.content.Context
import com.pitwatch.app.data.Repository
import com.pitwatch.app.data.Stores
import com.pitwatch.app.widget.WidgetRefresh
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled DI: everything long-lived, created once per process. Tests swap it on [PitWatchApp]. */
class AppContainer(
    val stores: Stores,
    val repository: Repository,
    val scope: CoroutineScope,
    val clock: () -> Instant = Instant::now,
    /** Re-renders home-screen widgets from the persisted cache. */
    val updateWidgets: suspend () -> Unit = {},
) {
    companion object {
        fun create(context: Context): AppContainer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Bounded requests: a hung poll degrades (IOException → null/error) instead of stalling the live loop.
            val http = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 10_000
                }
            }
            val stores = Stores(File(context.filesDir, "pitwatch"), scope)
            val appContext = context.applicationContext
            lateinit var container: AppContainer
            val updateWidgets: suspend () -> Unit = { WidgetRefresh.run(appContext, container) }
            val repository = Repository(
                stores,
                { TbaClient(it, http, BuildConfig.TBA_BASE_URL) },
                { NexusClient(it, http, BuildConfig.NEXUS_BASE_URL) },
                onChanged = updateWidgets,
            )
            container = AppContainer(stores, repository, scope, updateWidgets = updateWidgets)
            return container
        }
    }
}
