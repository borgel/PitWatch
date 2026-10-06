package com.pitwatch.app.widget

import android.content.Context
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
    // Exact: the layout sees the widget's real size (Responsive reports the bucket, so a tall widget looked
    // only 250 dp high and the upcoming list was budgeted away — found on-device).
    override val sizeMode = SizeMode.Exact

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

    /** Generated widget-picker preview (Android 15+): the real layout with sample content and a static countdown. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                WidgetContent(SampleWidgetData.main(Instant.now())) {
                    Text(
                        "3:27",
                        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed")),
                    )
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
    // 56 sp only where the upcoming list still fits beneath it (see WidgetPlans); 40 sp otherwise.
    val textSp = if (WidgetPlans.largeCountdown(LocalSize.current.height)) 56f else 40f
    val remaining = Duration.between(now, deadline).toMillis()
    val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(R.id.countdown, true)
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, textSp)
    }
    AndroidRemoteViews(views)
}
