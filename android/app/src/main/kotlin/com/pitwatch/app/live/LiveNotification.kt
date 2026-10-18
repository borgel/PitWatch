package com.pitwatch.app.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.pitwatch.app.R
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.logic.ScheduleDelay
import com.pitwatch.core.model.MatchesAwayDisplay
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant

/** Builds the promoted Live Update for the tracked match. Pure apart from the Context it builds with. */
object LiveNotification {
    /**
     * v2: default importance (still silent), so it isn't filed with silent notifications, which lock screens
     * collapse or hide. A channel's importance can't change once created, hence the new id.
     */
    const val CHANNEL_ID = "live_match_v2"
    /** Rich mode's Live Update: quiet, since the scoreboard card beside it is the one that alerts. */
    const val COMPACT_CHANNEL_ID = "live_match_status"
    /** The first, silent channel, and the dual-notification spike's test channels. */
    private val OLD_CHANNEL_IDS = listOf("live_match", "live_match_quiet_spike", "live_card_spike")
    const val NOTIFICATION_ID = 1001
    /** Its own group, so new posts show separately (Android 15+ may still bundle single-notification groups; it's system UX). */
    const val GROUP = "pitwatch.live"
    val STALE_AFTER: Duration = Duration.ofMinutes(5)

    /** Queue, on deck, on field (iOS phase colors), then match. */
    private val SEGMENT_COLORS = StatusColors.notificationSegments.map { it.toArgb() }

    /** [dismissed] fires on swipe; the service decides whether that stops tracking or re-posts (pinned). */
    data class Actions(
        val content: PendingIntent?,
        val refresh: PendingIntent?,
        val stop: PendingIntent?,
        val dismissed: PendingIntent? = stop,
        /** The scoreboard card's own swipe ([LiveCard]): hides it, unless pinned; tracking goes on. */
        val cardDismissed: PendingIntent? = dismissed,
    )

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live match", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Queue status for the match you're tracking"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC // applies on creation; later it's the user's
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(COMPACT_CHANNEL_ID, "Live match status", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The short countdown in the status bar and Now Bar, beside the scoreboard card"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        OLD_CHANNEL_IDS.forEach(manager::deleteNotificationChannel)
    }

    /**
     * [rich]: once there's a match, the short Live Update that sits beside [LiveCard] — two lines, no progress bar,
     * buttons or ticking header time, so it stays small (Samsung also glued that time onto the title).
     */
    fun build(context: Context, snapshot: LiveSnapshot?, lastSuccess: Instant?, now: Instant, actions: Actions, rich: Boolean = false): Notification {
        val compact = rich && snapshot != null
        val builder = NotificationCompat.Builder(context, if (compact) COMPACT_CHANNEL_ID else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitwatch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setGroup(GROUP)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(context.getColor(android.R.color.system_accent1_600)) // Material You accent for the icon
            // Never setColorized(true): Live Updates must not be colorized, and a colorized one stays out of Samsung's
            // Now Bar. (The base API 36 source has the pre-release rule that required it; the shipped rule is the reverse.)
            // Match times aren't sensitive: show everything on the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(actions.content)
            .setDeleteIntent(actions.dismissed) // the service decides: stop, or re-post when pinned
        staleText(lastSuccess, now)?.let(builder::setSubText)
        if (compact) return compact(builder, snapshot!!, now)
        actions.refresh?.let { builder.addAction(0, "Refresh", it) }
        actions.stop?.let { builder.addAction(0, "Stop", it) }

        if (snapshot == null) {
            return builder.setContentTitle("PitWatch").setContentText("Waiting for match data").setShowWhen(false).build()
        }
        builder.setShortCriticalText(shortText(snapshot, now)).setStyle(progress(snapshot, now))
        val deadline = snapshot.deadline
        if (deadline != null && deadline > now) {
            // The time leads, as the title (the platform's ticking chronometer only fits small, beside it); the match
            // line moves under it. Re-rendered at least every minute by LiveMatchService.
            builder.setContentTitle(timeTitle(snapshot, now))
                .setContentText(listOfNotNull(title(snapshot), text(snapshot)).joinToString(" · "))
                .setWhen(deadline.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setContentTitle(title(snapshot)).setContentText(text(snapshot)).setShowWhen(false)
        }
        return builder.build()
    }

    /** "12m to on deck" over "Q32 · Red · 3 away"; without a countdown, the match line and field position. */
    private fun compact(builder: NotificationCompat.Builder, s: LiveSnapshot, now: Instant): Notification {
        builder.setShortCriticalText(shortText(s, now)).setShowWhen(false)
        if (s.deadline?.let { it > now } == true) {
            builder.setContentTitle(timeTitle(s, now)).setContentText(compactText(s))
        } else {
            builder.setContentTitle(title(s)).setContentText(text(s))
        }
        return builder.build()
    }

    /** "Q32 · Red · 3 away". */
    fun compactText(s: LiveSnapshot): String = listOfNotNull(
        s.matchLabel,
        s.alliance?.displayName?.let(::sentenceCase),
        s.matchesAway?.let { sentenceCase(MatchesAwayDisplay.text(it)) },
    ).joinToString(" · ")

    private fun sentenceCase(text: String) = text.lowercase().replaceFirstChar(Char::uppercase)

    /** "12m to on deck", "1h 38m to queue": minutes rounded up, like the status-bar chip. */
    fun timeTitle(s: LiveSnapshot, now: Instant): String {
        val seconds = s.deadline?.let { Duration.between(now, it).seconds }?.coerceAtLeast(0) ?: 0
        val minutes = (seconds + 59) / 60
        val time = when {
            minutes < 60 -> "${minutes}m"
            minutes % 60 == 0L -> "${minutes / 60}h"
            else -> "${minutes / 60}h ${minutes % 60}m"
        }
        return "$time ${s.target}"
    }

    /** "Q32 · RED · IN QUEUE". */
    fun title(s: LiveSnapshot): String =
        listOfNotNull(s.matchLabel, s.alliance?.displayName, s.phase.stateLabel)
            .joinToString(" · ")

    /** "3 AWAY · ON FIELD #29 · 12m behind". */
    fun text(s: LiveSnapshot): String? {
        return listOfNotNull(s.matchesAway?.let(MatchesAwayDisplay::text), s.onFieldNumber?.let { "ON FIELD #$it" }, s.scheduleDelay?.let(ScheduleDelay::text))
            .joinToString(" · ")
            .ifEmpty { null }
    }

    /**
     * How long until [timeTitle] (and the chip) show one minute less: they round whole seconds up to minutes, so the
     * count drops once fewer than (minutes − 1) × 60 + 1 whole seconds remain. Null once [deadline] has passed.
     */
    fun untilMinuteChanges(deadline: Instant?, now: Instant): Duration? {
        val remaining = deadline?.let { Duration.between(now, it) }?.takeIf { it > Duration.ZERO } ?: return null
        val minutes = (remaining.seconds + 59) / 60
        val boundary = if (minutes == 0L) Duration.ZERO else Duration.ofSeconds((minutes - 1) * 60 + 1)
        return remaining.minus(boundary).plusMillis(1)
    }

    /** Status-bar chip: "Q32 12m" counting down, "Q32 2h" when far out. */
    fun shortText(s: LiveSnapshot, now: Instant): String {
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
            .setProgress(position)
    }
}
