package com.pitwatch.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pitwatch.app.MainActivity
import com.pitwatch.app.R
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.widget.ScheduleWidgetModel
import com.pitwatch.app.widget.WidgetModel
import com.pitwatch.core.model.Phase
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The opt-in schedule notification: the schedule widget's content, as text. */
object ScheduleNotification {
    const val CHANNEL_ID = "schedule"
    const val NOTIFICATION_ID = 1002
    /** Its own group, so new posts show separately (Android 15+ may still bundle single-notification groups; it's system UX). */
    const val GROUP = "pitwatch.schedule"
    private const val MAX_LINES = 6

    data class Content(val title: String, val text: String?, val lines: List<String>, val summary: String?)

    /** [today] defaults to the schedule's first day; the notifier passes the real date so a later day is named. */
    fun content(model: ScheduleWidgetModel, locale: Locale = Locale.getDefault(), today: LocalDate? = null): Content {
        val summary = model.last?.let { "Last ${it.shortLabel} ${it.outcome} ${it.ourScore}–${it.theirScore}" }
        // Out of matches (e.g. alliance selection): still say how the last one went.
        if (model.state != WidgetModel.State.READY) return Content(model.message ?: "PitWatch", null, emptyList(), summary)
        val times = TimeFormat(model.timeZone, model.zoneLabel)
        val rows = model.days.flatMap { day -> day.items.filterIsInstance<MatchListModel.Item.Upcoming>().map { it.row } }
        val next = rows.first()
        val dayFormat = DateTimeFormatter.ofPattern("EEE", locale)
        val reference = today ?: model.days.firstOrNull()?.date
        val nextDay = model.days.firstOrNull()?.date?.takeIf { it != reference }?.format(dayFormat)?.let { "$it " } ?: ""
        val title = "Next: ${next.shortLabel} · $nextDay${times.match(next.time, next.estimated)}"
        // The last result is the summary, which the shade already shows beside the title.
        val text = rows.getOrNull(1)?.let { "then ${it.shortLabel} ${times.match(it.time, it.estimated)}" }
        // Today's rows stand alone; the first row of any other day carries its weekday.
        val lines = model.days.flatMap { day ->
            val prefix = day.date?.takeIf { it != reference }?.format(dayFormat)?.let { "$it · " } ?: ""
            day.items.mapIndexed { i, item ->
                val head = if (i == 0) prefix else ""
                when (item) {
                    is MatchListModel.Item.Upcoming -> head + item.row.shortLabel + " · " + times.match(item.row.time, item.row.estimated) +
                        (item.row.phase?.takeIf { it != Phase.PRE_QUEUE }?.let { " · ${it.stateLabel}" } ?: "")
                    is MatchListModel.Item.Break -> head + item.title + (item.end?.let { " " + times.range(item.start, it) } ?: "")
                }
            }
        }.take(MAX_LINES)
        return Content(title, text, lines, summary)
    }

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Match schedule", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Your upcoming matches and the last result"
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, content: Content): Notification {
        fun broadcast(code: Int, action: String) = PendingIntent.getBroadcast(
            context, code, Intent(context, ScheduleNotificationReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle().also { s ->
            content.lines.forEach(s::addLine)
            content.summary?.let(s::setSummaryText)
        }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitwatch)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(style)
            .setGroup(GROUP)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setDeleteIntent(broadcast(11, ScheduleNotifier.ACTION_DISMISSED))
            .addAction(0, "Turn off", broadcast(12, ScheduleNotifier.ACTION_TURN_OFF))
            .build()
    }
}
