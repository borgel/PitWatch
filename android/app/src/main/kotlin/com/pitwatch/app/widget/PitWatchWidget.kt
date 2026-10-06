package com.pitwatch.app.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.R
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** One responsive widget; renders only from the persisted cache (never network). */
class PitWatchWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val stores = container.stores
        // Read once so the first frame isn't empty; after that the content follows the stores, because a running
        // Glance session only recomposes on update() — it doesn't call provideGlance again.
        val initialCache = stores.cache.data.first()
        val initialConfig = stores.config.data.first()
        provideContent {
            GlanceTheme {
                LiveWidget(stores.cache.data, stores.config.data, initialCache, initialConfig, container.clock) { deadline, now ->
                    ChronometerCountdown(deadline, now)
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class PitWatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PitWatchWidget()
}

/**
 * The widget's content, recomputed from the stores on every composition — and [clock] is read during
 * composition, so each re-render (data change or deadline alarm) shows the current phase and countdown.
 */
@Composable
fun LiveWidget(
    cacheFlow: Flow<EventCache>,
    configFlow: Flow<UserConfig>,
    initialCache: EventCache,
    initialConfig: UserConfig,
    clock: () -> Instant,
    countdown: @Composable (deadline: Instant, now: Instant) -> Unit,
) {
    val cache by cacheFlow.collectAsState(initialCache)
    val config by configFlow.collectAsState(initialConfig)
    val now = clock()
    WidgetContent(WidgetModels.build(cache, config, now)) { deadline -> countdown(deadline, now) }
}

/** A platform Chronometer: ticks on the home screen with no app updates. */
@Composable
private fun ChronometerCountdown(deadline: Instant, now: Instant) {
    val context = LocalContext.current
    val remaining = Duration.between(now, deadline).toMillis()
    val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(R.id.countdown, true)
    }
    AndroidRemoteViews(views)
}
