package com.pitwatch.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Upcoming schedule and the last result only — no countdown. Renders from the persisted cache. */
class ScheduleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override val previewSizeMode = SizeMode.Responsive(PitWatchWidget.PREVIEW_SIZES)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val stores = container.stores
        val initialCache = stores.cache.data.first()
        val initialConfig = stores.config.data.first()
        provideContent { GlanceTheme { LiveScheduleWidget(stores.cache.data, stores.config.data, initialCache, initialConfig, container.clock) } }
    }

    /** Generated widget-picker preview (Android 15+): the real layout with sample content. */
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { GlanceTheme { ScheduleWidgetContent(SampleWidgetData.schedule(Instant.now())) } }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

@Composable
fun LiveScheduleWidget(cacheFlow: Flow<EventCache>, configFlow: Flow<UserConfig>, initialCache: EventCache, initialConfig: UserConfig, clock: () -> Instant) {
    val cache by cacheFlow.collectAsState(initialCache)
    val config by configFlow.collectAsState(initialConfig)
    ScheduleWidgetContent(ScheduleWidgetModels.build(cache, config, clock()))
}

@Composable
fun ScheduleWidgetContent(model: ScheduleWidgetModel) {
    val size = LocalSize.current
    val wide = size.width >= PitWatchWidget.MEDIUM.width
    val plan = SchedulePlans.plan(size.height, hasLast = model.last != null)
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed"))
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    // At most 3 children: header, last result, list (its own Column).
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>())
            .semantics { testTag = "schedule-root" },
    ) {
        if (plan.header || model.state != WidgetModel.State.READY) Text(model.header, style = muted, maxLines = 1)
        if (model.state != WidgetModel.State.READY) {
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp), modifier = GlanceModifier.padding(top = 8.dp))
            // Out of matches (e.g. alliance selection): still show how the last one went.
            if (plan.lastLine) model.last?.let { LastLine(it, compact = !wide) }
            return@Column
        }
        if (plan.lastLine) model.last?.let { LastLine(it, compact = !wide) }
        UpcomingList(WidgetLines.fit(model.days, plan.listLines, cap = WidgetLines.MAX_LINES * 3), times)
    }
}
