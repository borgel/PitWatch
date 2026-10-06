package com.pitwatch.app.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
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
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/** One responsive widget; renders only from the persisted cache (never network). */
class PitWatchWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val now = container.clock()
        val model = WidgetModels.build(container.stores.cache.data.first(), container.stores.config.data.first(), now)
        provideContent {
            GlanceTheme { WidgetContent(model) { deadline -> ChronometerCountdown(deadline, now) } }
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
