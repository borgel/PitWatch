package com.pitwatch.app.live

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.res.ColorStateList
import android.os.SystemClock
import android.graphics.Typeface
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.RemoteViews
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import com.pitwatch.app.R
import com.pitwatch.app.ui.scoreboard.PhaseSteps
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.app.ui.theme.scoreboardDark
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.ScheduleDelay
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.MatchesAwayDisplay
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * The rich live notification's scoreboard card, beside the short Live Update ([LiveNotification] with `rich`).
 * Custom views can't be promoted, so this is a plain notification: the system's own layout collapsed, the scoreboard
 * expanded. It's the foreground-service notification, the only kind an app may colorize, so the whole card takes the
 * app's scoreboard color for the current light or dark theme.
 */
object LiveCard {
    const val CHANNEL_ID = "live_scoreboard"
    const val NOTIFICATION_ID = 1003
    private const val GROUP = "pitwatch.scoreboard"
    private val JOINTS = listOf(R.id.card_tip1 to R.id.card_notch1, R.id.card_tip2 to R.id.card_notch2, R.id.card_tip3 to R.id.card_notch3)
    private val CLOCK = DateTimeFormatter.ofPattern("h:mm")
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** The card's colors in one theme: the app's scoreboard ground (dark) or surface (light), and text on it. */
    data class Colors(val background: Int, val text: Int, val muted: Int, val empty: Int)

    fun colors(context: Context, dark: Boolean): Colors {
        val scheme = if (dark) scoreboardDark(dynamicDarkColorScheme(context)) else dynamicLightColorScheme(context)
        return Colors(scheme.surface.toArgb(), scheme.onSurface.toArgb(), scheme.onSurfaceVariant.toArgb(), scheme.surfaceVariant.toArgb())
    }

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Live match scoreboard", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The scoreboard for the match you're tracking; it alerts once when tracking starts"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun build(
        context: Context,
        cache: EventCache,
        config: UserConfig,
        s: LiveSnapshot,
        now: Instant,
        actions: LiveNotification.Actions,
        dark: Boolean,
        /** Re-posts after the first: no sound (onlyAlertOnce only covers updates while it's still showing). */
        silent: Boolean = false,
    ): Notification {
        val colors = colors(context, dark)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitwatch)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            // Collapsed: no custom view, so the system's own layout (and type) shows the title and text.
            .setCustomBigContentView(scoreboard(context, cache, config, s, now, colors, dark))
            .setContentTitle(LiveNotification.title(s))
            .setContentText(collapsedText(s, now))
            .setOngoing(true)
            .setOnlyAlertOnce(true) // alerts when tracking starts, then updates quietly
            .setSilent(silent)
            .setGroup(GROUP)
            .setColorized(true)
            .setColor(colors.background)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(actions.content)
            .setDeleteIntent(actions.cardDismissed) // the service decides: hide it, or re-post when pinned
        actions.refresh?.let { builder.addAction(0, "Refresh", it) }
        actions.stop?.let { builder.addAction(0, "Stop", it) }
        val deadline = s.deadline?.takeIf { it > now }
        if (deadline != null) {
            // The system ticks this header time itself, so it never lags.
            builder.setWhen(deadline.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    /** "To match start · Field #36 · Now · 24m behind": what the header time counts to, then the context. */
    private fun collapsedText(s: LiveSnapshot, now: Instant): String = listOfNotNull(
        s.target.takeIf { s.deadline?.let { it > now } == true }?.let(::sentenceCase),
        s.onFieldNumber?.let { "Field #$it" },
        s.matchesAway?.let { sentenceCase(MatchesAwayDisplay.text(it)) },
        s.scheduleDelay?.let(ScheduleDelay::text),
    ).joinToString(" · ")

    private fun sentenceCase(text: String) = text.lowercase().replaceFirstChar(Char::uppercase)

    /** "NOW · FIELD #36 · 24M BEHIND": the scoreboard's last line (the widgets show it too). */
    fun footer(s: LiveSnapshot): String = listOfNotNull(
        s.matchesAway?.let(MatchesAwayDisplay::text),
        s.onFieldNumber?.let { "FIELD #$it" },
        s.scheduleDelay?.let(ScheduleDelay::text),
    ).joinToString(" · ").uppercase()

    /** The expanded scoreboard, in [colors]. */
    internal fun scoreboard(context: Context, cache: EventCache, config: UserConfig, s: LiveSnapshot, now: Instant, colors: Colors, dark: Boolean): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.live_card_expanded)
        views.setTextViewText(R.id.card_label, s.matchLabel)
        views.setTextColor(R.id.card_label, colors.text)
        views.setTextViewText(R.id.card_pill, s.phase.stateLabel)
        views.setTextColor(R.id.card_pill, StatusColors.onPhase(s.phase).toArgb())
        views.setColorStateList(R.id.card_pill, "setBackgroundTintList", ColorStateList.valueOf(StatusColors.phase(s.phase).toArgb()))
        views.setTextColor(R.id.card_countdown, colors.text)
        views.setTextColor(R.id.card_target, colors.muted)
        val deadline = s.deadline?.takeIf { it > now }
        if (deadline != null) {
            views.setChronometer(R.id.card_countdown, SystemClock.elapsedRealtime() + Duration.between(now, deadline).toMillis(), null, true)
            views.setChronometerCountDown(R.id.card_countdown, true)
            views.setViewVisibility(R.id.card_countdown, View.VISIBLE)
            views.setTextViewText(R.id.card_target, s.target.uppercase())
        } else {
            views.setViewVisibility(R.id.card_countdown, View.GONE)
            views.setTextViewText(R.id.card_target, "")
        }

        // Stage band: the chevrons, filled up to the current stage; each stage's clock time inside it.
        val current = PhaseSteps.current(s.phase)
        val m = s.milestones
        val ids = listOf(
            Triple(R.id.card_seg1, R.id.card_seg1_label, R.id.card_seg1_time),
            Triple(R.id.card_seg2, R.id.card_seg2_label, R.id.card_seg2_time),
            Triple(R.id.card_seg3, R.id.card_seg3_label, R.id.card_seg3_time),
            Triple(R.id.card_seg4, R.id.card_seg4_label, R.id.card_seg4_time),
        )
        val looks = ids.indices.map { StageChevrons.look(it, current, colors, dark) }
        val times = listOf(m.queue, m.onDeck, m.onField, m.start)
        ids.forEachIndexed { i, (segment, label, time) ->
            val look = looks[i]
            fun tint(id: Int, color: Int) = views.setColorStateList(id, "setBackgroundTintList", ColorStateList.valueOf(color))
            tint(segment, look.fill)
            val name = SpannableString(StageChevrons.NAMES[i])
            if (look.bold) name.setSpan(StyleSpan(Typeface.BOLD), 0, name.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            views.setTextViewText(label, name)
            views.setTextColor(label, look.name)
            views.setTextViewText(time, times[i]?.atZone(ZoneId.systemDefault())?.format(CLOCK).orEmpty())
            views.setTextColor(time, look.time)
            if (i > 0) {
                // The joint before this stage: the earlier stage's tip, this one's notch.
                tint(JOINTS[i - 1].first, looks[i - 1].fill)
                tint(JOINTS[i - 1].second, look.fill)
            }
        }

        val match = cache.matches.firstOrNull { it.key == s.matchKey }
        fun line(color: String, alliance: MatchAlliance): CharSequence {
            val allianceColor = StatusColors.alliance(alliance).toArgb()
            val sb = SpannableStringBuilder()
            sb.append("● ", ForegroundColorSpan(allianceColor), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            match?.alliances?.get(color)?.teamKeys.orEmpty().forEachIndexed { i, key ->
                if (i > 0) sb.append(" · ")
                val number = key.removePrefix("frc")
                if (key == config.teamKey) {
                    // Our number on an alliance-colored chip.
                    val start = sb.length
                    sb.append(" $number ")
                    sb.setSpan(BackgroundColorSpan(allianceColor), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(ForegroundColorSpan(WHITE), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else {
                    sb.append(number)
                }
            }
            return sb
        }
        views.setTextViewText(R.id.card_red, line("red", MatchAlliance.RED))
        views.setTextViewText(R.id.card_blue, line("blue", MatchAlliance.BLUE))
        views.setTextColor(R.id.card_red, colors.text)
        views.setTextColor(R.id.card_blue, colors.text)

        val footer = footer(s)
        views.setTextViewText(R.id.card_footer, footer)
        views.setTextColor(R.id.card_footer, colors.muted)
        views.setViewVisibility(R.id.card_footer, if (footer.isEmpty()) View.GONE else View.VISIBLE)
        return views
    }
}
