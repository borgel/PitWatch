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

    // The picker shows generated previews at real sizes (the default renders at the 110 dp minimum).
    override val previewSizeMode = SizeMode.Responsive(PREVIEW_SIZES)

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
            GlanceTheme { WidgetContent(SampleWidgetData.main(Instant.now())) { StaticCountdown() } }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)

        /** 2×2, 4×2 and 4×3 as measured on a Pixel launcher (emulator-5580). */
        val PREVIEW_SIZES = setOf(DpSize(172.dp, 223.dp), DpSize(360.dp, 223.dp), DpSize(360.dp, 344.dp))
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
    /** Which widget draws the model: the main one by default, or the simple one. */
    content: @Composable (WidgetModel, @Composable (Instant) -> Unit) -> Unit = { model, slot -> WidgetContent(model, slot) },
    countdown: @Composable (deadline: Instant, now: Instant) -> Unit,
) {
    val cache by cacheFlow.collectAsState(initialCache)
    val config by configFlow.collectAsState(initialConfig)
    val now = clock()
    content(WidgetModels.build(cache, config, now)) { deadline -> countdown(deadline, now) }
}

/** The picker preview's countdown: a fixed time at the size the widget asks for ([LocalCountdownSp]). */
@Composable
internal fun StaticCountdown() {
    Text(
        "3:27",
        style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = LocalCountdownSp.current.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed")),
    )
}

/** A platform Chronometer: ticks on the home screen with no app updates. Sized by the widget ([LocalCountdownSp]). */
@Composable
internal fun ChronometerCountdown(deadline: Instant, now: Instant) {
    val context = LocalContext.current
    val textSp = LocalCountdownSp.current
    val remaining = Duration.between(now, deadline).toMillis()
    val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(R.id.countdown, true)
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, textSp)
    }
    AndroidRemoteViews(views)
}
