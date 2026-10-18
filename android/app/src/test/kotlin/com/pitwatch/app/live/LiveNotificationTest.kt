package com.pitwatch.app.live

import android.app.Notification
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)
    private val noActions = LiveNotification.Actions(null, null, null)
    private val noMilestones = LiveSnapshot.Milestones(null, null, null, null, null)

    private fun snapshot(
        phase: Phase = Phase.QUEUEING,
        deadline: Instant? = now.plusSeconds(600),
        milestones: LiveSnapshot.Milestones = noMilestones,
        target: String = "to on deck",
        scheduleDelay: java.time.Duration? = null,
    ) = LiveSnapshot("2026test_qm32", "Q32", MatchAlliance.RED, phase, deadline, target, milestones, 3, 29, scheduleDelay)

    private fun build(s: LiveSnapshot?, lastSuccess: Instant? = now) =
        LiveNotification.build(context, s, lastSuccess, now, noActions)

    private fun style(n: Notification) = Notification.Builder.recoverBuilder(context, n).style as Notification.ProgressStyle

    @Test
    fun `requests promotion with title, text and status-bar chip`() {
        val n = build(snapshot())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        // Asked for: the time first and prominent, so it's the title; the match line moves under it.
        assertEquals("10m to on deck", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Q32 · RED · IN QUEUE · 3 AWAY · ON FIELD #29", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Q32 10m", n.shortCriticalText)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(LiveNotification.GROUP, n.group) // never auto-bundled with the schedule notification
    }

    @Test
    fun `ends the match line with how far ahead or behind the event is`() {
        val behind = build(snapshot(scheduleDelay = java.time.Duration.ofMinutes(12)))
        assertEquals("Q32 · RED · IN QUEUE · 3 AWAY · ON FIELD #29 · 12m behind", behind.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val onTime = build(snapshot(deadline = null, scheduleDelay = java.time.Duration.ofMinutes(1)))
        assertEquals("3 AWAY · ON FIELD #29 · On time", onTime.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    fun `shows in full on the lock screen and stays eligible for Live Update promotion`() {
        // Asked for: visible on the lock screen. Match times aren't sensitive. Live Updates "must NOT setColorized
        // to TRUE" (developer.android.com, Live Update requirements): colorizing kept it out of Samsung's Now Bar.
        // (The base API 36 source, and so Robolectric, still has the pre-release rule that required it.)
        val n = build(snapshot())
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(LiveNotification.CHANNEL_ID, n.channelId)
    }

    @Test
    fun `its channel isn't silent, but makes no sound, and replaces the old silent one`() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(android.app.NotificationChannel("live_match", "Live match", android.app.NotificationManager.IMPORTANCE_LOW))
        LiveNotification.ensureChannel(context)
        val channel = kotlin.test.assertNotNull(manager.getNotificationChannel(LiveNotification.CHANNEL_ID))
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
        assertNull(manager.getNotificationChannel("live_match"), "the old silent channel is removed")
    }

    @Test
    fun `rich mode - a short, quiet Live Update beside the scoreboard card`() {
        // Asked for: small and not expandable in the shade (the card has the detail); Samsung glued the ticking
        // header time onto the title, so there is none.
        val n = LiveNotification.build(context, snapshot(), now, now, noActions, rich = true)
        assertEquals(LiveNotification.COMPACT_CHANNEL_ID, n.channelId)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals("10m to on deck", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("Q32 · Red · 3 away", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Q32 10m", n.shortCriticalText)
        assertNull(Notification.Builder.recoverBuilder(context, n).style, "no progress bar")
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
        assertFalse(n.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertNull(n.actions)
    }

    @Test
    fun `rich mode with no match yet - the usual waiting notification, with its buttons`() {
        val pending = android.app.PendingIntent.getActivity(context, 0, android.content.Intent(), android.app.PendingIntent.FLAG_IMMUTABLE)
        val n = LiveNotification.build(context, null, now, now, LiveNotification.Actions(null, pending, pending), rich = true)
        assertEquals(LiveNotification.CHANNEL_ID, n.channelId)
        assertEquals("Waiting for match data", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(2, n.actions.size)
    }

    @Test
    fun `the quiet channel for rich mode is silent and on the lock screen, and test channels are gone`() {
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        for (id in listOf("live_match_quiet_spike", "live_card_spike")) {
            manager.createNotificationChannel(android.app.NotificationChannel(id, id, android.app.NotificationManager.IMPORTANCE_LOW))
        }
        LiveNotification.ensureChannel(context)
        val quiet = kotlin.test.assertNotNull(manager.getNotificationChannel(LiveNotification.COMPACT_CHANNEL_ID))
        assertEquals(android.app.NotificationManager.IMPORTANCE_LOW, quiet.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, quiet.lockscreenVisibility)
        assertNull(manager.getNotificationChannel("live_match_quiet_spike"))
        assertNull(manager.getNotificationChannel("live_card_spike"))
    }

    @Test
    fun `the next redraw lands just as the title's minute count changes`() {
        // On-device: "7m to on deck" beside a 06:00 countdown — redrawn once a minute, at no particular second.
        fun next(remainingMs: Long) = LiveNotification.untilMinuteChanges(now.plusMillis(remainingMs), now)?.toMillis()
        assertEquals(39_001, next(400_000)) // 6:40 shows "7m"; at 6:00.999 left it shows "6m"
        assertEquals(1, next(361_000)) // 6:01 shows "7m": it changes a millisecond later
        assertEquals(59_501, next(360_500)) // 6:00.5 already shows "6m"; next change below 5:01
        assertEquals(1, next(1_000))
        assertEquals(501, next(500)) // under a second: the next change is the deadline itself
        assertNull(LiveNotification.untilMinuteChanges(now.minusSeconds(1), now))
        assertNull(LiveNotification.untilMinuteChanges(null, now))
        // Each wait lands where the title shows one minute less.
        val title = { ms: Long -> LiveNotification.timeTitle(snapshot(deadline = now.plusMillis(ms)), now) }
        assertEquals("7m to on deck", title(400_000))
        assertEquals("6m to on deck", title(400_000 - 39_001))
    }

    @Test
    fun `counts down to the phase deadline`() {
        val n = build(snapshot())
        assertEquals(now.plusSeconds(600).toEpochMilli(), n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Test
    fun `segments follow real durations when every milestone is known`() {
        val m = LiveSnapshot.Milestones(
            queue = now.minusSeconds(60), onDeck = now.plusSeconds(240), onField = now.plusSeconds(540),
            start = now.plusSeconds(840), end = now.plusSeconds(990),
        )
        val s = style(build(snapshot(milestones = m)))
        assertEquals(listOf(300, 300, 300, 150), s.progressSegments.map { it.length })
        assertEquals(listOf(300, 600, 900), s.progressPoints.map { it.position })
        assertEquals(60, s.progress)
    }

    @Test
    fun `falls back to equal segments without milestones`() {
        val s = style(build(snapshot(phase = Phase.QUEUEING)))
        assertEquals(listOf(100, 100, 100, 100), s.progressSegments.map { it.length })
        assertEquals(50, s.progress)
    }

    @Test
    fun `the time title rounds up to the minute and switches to hours`() {
        val far = build(snapshot(phase = Phase.PRE_QUEUE, deadline = now.plusSeconds(98 * 60 + 1), target = "to queue"))
        assertEquals("1h 39m to queue", far.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("2h to queue", LiveNotification.timeTitle(snapshot(deadline = now.plusSeconds(120 * 60), target = "to queue"), now))
        assertEquals("1m to on deck", LiveNotification.timeTitle(snapshot(deadline = now.plusSeconds(20)), now))
    }

    @Test
    fun `without a countdown the match line stays the title`() {
        val n = build(snapshot(deadline = null))
        assertEquals("Q32 · RED · IN QUEUE", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3 AWAY · ON FIELD #29", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        val passed = build(snapshot(deadline = now.minusSeconds(5)))
        assertEquals("Q32 · RED · IN QUEUE", passed.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `a scored match never shows its score`() {
        // Asked for: no last-match score in the live notification (the tracker also moves on once it's scored).
        val played = com.pitwatch.app.testMatch(32, actualTime = 100, red = listOf("frc1234", "frc2", "frc3"), redScore = 95, blueScore = 80)
        val s = kotlin.test.assertNotNull(
            LiveSnapshots.build(com.pitwatch.core.store.EventCache(matches = listOf(played)), com.pitwatch.core.config.UserConfig(teamNumber = 1234, apiKey = "k"), played.key, now),
        )
        val n = build(s)
        val shown = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT).map { n.extras.getCharSequence(it)?.toString().orEmpty() }
        assertTrue(shown.none { "95" in it || "80" in it || "FINAL" in it }, shown.toString())
    }

    @Test
    fun `stale data says when it was last updated`() {
        assertEquals("Updated 6m ago", build(snapshot(), lastSuccess = now.minusSeconds(6 * 60)).extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        assertNull(build(snapshot(), lastSuccess = now.minusSeconds(60)).extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `chip switches to hours when far out`() {
        assertEquals("Q32 2h", LiveNotification.shortText(snapshot(deadline = now.plusSeconds(125 * 60)), now))
        assertEquals("Q32 1m", LiveNotification.shortText(snapshot(deadline = now.plusSeconds(1)), now))
        assertEquals("Q32", LiveNotification.shortText(snapshot(deadline = null), now))
    }

    @Test
    fun `no snapshot shows a waiting notification`() {
        val n = build(null, lastSuccess = null)
        assertEquals("Waiting for match data", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Waiting for data", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
    }

    @Test
    fun `accent follows the Material You system palette`() {
        assertEquals(context.getColor(android.R.color.system_accent1_600), build(snapshot()).color)
    }

    @Test
    fun `segment colors come from the app's status colors`() {
        val s = style(build(snapshot()))
        assertEquals(StatusColors.notificationSegments.map { it.toArgb() }, s.progressSegments.map { it.color })
    }
}
