package com.pitwatch.app.live

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
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
        result: LiveSnapshot.Result? = null,
    ) = LiveSnapshot("2026test_qm32", "Q32", MatchAlliance.RED, phase, deadline, milestones, 3, 29, result)

    private fun build(s: LiveSnapshot?, lastSuccess: Instant? = now) =
        LiveNotification.build(context, s, lastSuccess, now, noActions)

    private fun style(n: Notification) = Notification.Builder.recoverBuilder(context, n).style as Notification.ProgressStyle

    @Test
    fun `requests promotion with title, text and status-bar chip`() {
        val n = build(snapshot())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals("Q32 · RED · IN QUEUE", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3 AWAY · on field #29", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Q32 10m", n.shortCriticalText)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
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
    fun `result shows the outcome and fills the bar`() {
        val n = build(snapshot(result = LiveSnapshot.Result(95, 80)))
        assertEquals("Q32 · RED · FINAL", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("W 95–80", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Q32 W", n.shortCriticalText)
        assertEquals(400, style(n).progress)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
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
}
