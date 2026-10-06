package com.pitwatch.app.live

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pitwatch.app.R
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchesAwayDisplay
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant

/** Builds the promoted Live Update for the tracked match. Pure apart from the Context it builds with. */
object LiveNotification {
    const val CHANNEL_ID = "live_match"
    const val NOTIFICATION_ID = 1001
    val STALE_AFTER: Duration = Duration.ofMinutes(5)

    /** Queue, on deck, on field (iOS phase colors), then match. */
    private val SEGMENT_COLORS = StatusColors.notificationSegments.map { it.toArgb() }

    /** [dismissed] fires on swipe; the service decides whether that stops tracking or re-posts (pinned). */
    data class Actions(val content: PendingIntent?, val refresh: PendingIntent?, val stop: PendingIntent?, val dismissed: PendingIntent? = stop)

    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Live match")
                .setDescription("Queue status for the match you're tracking")
                .setShowBadge(false)
                .build(),
        )
    }

    fun build(context: Context, snapshot: LiveSnapshot?, lastSuccess: Instant?, now: Instant, actions: Actions): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitwatch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(context.getColor(android.R.color.system_accent1_600)) // Material You accent for the icon
            .setContentIntent(actions.content)
            .setDeleteIntent(actions.dismissed) // the service decides: stop, or re-post when pinned
        actions.refresh?.let { builder.addAction(0, "Refresh", it) }
        actions.stop?.let { builder.addAction(0, "Stop", it) }
        staleText(lastSuccess, now)?.let(builder::setSubText)

        if (snapshot == null) {
            return builder.setContentTitle("PitWatch").setContentText("Waiting for match data").setShowWhen(false).build()
        }
        builder.setContentTitle(title(snapshot))
            .setContentText(text(snapshot))
            .setShortCriticalText(shortText(snapshot, now))
            .setStyle(progress(snapshot, now))
        val deadline = snapshot.deadline
        if (snapshot.result == null && deadline != null && deadline > now) {
            builder.setWhen(deadline.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    /** "Q32 · RED · IN QUEUE", or "… · FINAL" once scored. */
    fun title(s: LiveSnapshot): String =
        listOfNotNull(s.matchLabel, s.alliance?.displayName, if (s.result != null) "FINAL" else s.phase.stateLabel)
            .joinToString(" · ")

    /** "3 AWAY · ON FIELD #29", or "W 95–80" once scored. */
    fun text(s: LiveSnapshot): String? {
        s.result?.let { return "${it.outcome} ${it.ourScore}–${it.theirScore}" }
        return listOfNotNull(s.matchesAway?.let(MatchesAwayDisplay::text), s.onFieldNumber?.let { "ON FIELD #$it" })
            .joinToString(" · ")
            .ifEmpty { null }
    }

    /** Status-bar chip: "Q32 12m" counting down, "Q32 2h" when far out, "Q32 W" after the result. */
    fun shortText(s: LiveSnapshot, now: Instant): String {
        s.result?.let { return "${s.matchLabel} ${it.outcome}" }
        val deadline = s.deadline ?: return s.matchLabel
        val remaining = Duration.between(now, deadline)
        val minutes = if (remaining.isNegative || remaining.isZero) 0 else (remaining.seconds + 59) / 60
        return if (minutes >= 60) "${s.matchLabel} ${minutes / 60}h" else "${s.matchLabel} ${minutes}m"
    }

    fun staleText(lastSuccess: Instant?, now: Instant): String? {
        lastSuccess ?: return "Waiting for data"
        val age = Duration.between(lastSuccess, now)
        return if (age > STALE_AFTER) "Updated ${age.toMinutes()}m ago" else null
    }

    /** Four segments — queue, on deck, on field, match — sized by real durations when Nexus provides them all. */
    private fun progress(s: LiveSnapshot, now: Instant): NotificationCompat.ProgressStyle {
        val m = s.milestones
        val bounds = listOf(m.queue, m.onDeck, m.onField, m.start, m.end)
        val known = bounds.filterNotNull()
        val lengths: List<Int>
        val position: Int
        if (known.size == bounds.size && known.zipWithNext().all { (a, b) -> a <= b }) {
            lengths = known.zipWithNext { a, b -> maxOf(1, Duration.between(a, b).seconds.toInt()) }
            position = Duration.between(known.first(), now).seconds.toInt().coerceIn(0, lengths.sum())
        } else {
            lengths = List(4) { 100 }
            position = when (s.phase) {
                Phase.PRE_QUEUE -> 0
                Phase.QUEUEING -> 50
                Phase.ON_DECK -> 150
                Phase.ON_FIELD -> 250
            }
        }
        val segments = lengths.zip(SEGMENT_COLORS) { length, color -> NotificationCompat.ProgressStyle.Segment(length).setColor(color) }
        val points = lengths.runningReduce(Int::plus).dropLast(1).map { NotificationCompat.ProgressStyle.Point(it) }
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(segments)
            .setProgressPoints(points)
            .setProgress(if (s.result != null) lengths.sum() else position)
    }
}
