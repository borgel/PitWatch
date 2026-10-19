package com.pitwatch.app.live

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.R
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.theme.scoreboardDark
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveCardTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cache = snapshotCache()
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private val snapshot = assertNotNull(LiveSnapshots.build(cache, config, "2026cancmp_qm36", SNAP_NOW))
    private val pending = PendingIntent.getActivity(context, 0, Intent(), PendingIntent.FLAG_IMMUTABLE)
    private val hide = PendingIntent.getActivity(context, 1, Intent(), PendingIntent.FLAG_IMMUTABLE)
    private val actions = LiveNotification.Actions(pending, pending, pending, cardDismissed = hide)

    private fun card(dark: Boolean) = LiveCard.build(context, cache, config, snapshot, SNAP_NOW, actions, dark)

    private fun scoreboard(dark: Boolean) =
        LiveCard.scoreboard(context, cache, config, snapshot, SNAP_NOW, LiveCard.colors(context, dark), dark).apply(context, FrameLayout(context))

    private fun text(root: android.view.View, id: Int) = root.findViewById<TextView>(id).text

    @Test
    fun `the whole card takes the app's scoreboard color, dark and light`() {
        // Asked for: no mismatched system colors around the scoreboard, and it follows light and dark mode.
        // Dark is the app's own dark scoreboard ground (the user's choice over plain #0F1216).
        val dark = card(dark = true)
        assertTrue(dark.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(scoreboardDark(dynamicDarkColorScheme(context)).surface.toArgb(), dark.color)
        val light = card(dark = false)
        assertTrue(light.extras.getBoolean(Notification.EXTRA_COLORIZED))
        assertEquals(dynamicLightColorScheme(context).surface.toArgb(), light.color)
    }

    @Test
    fun `collapsed, it's the system's own layout with a ticking header time`() {
        val n = card(dark = true)
        assertEquals(LiveCard.CHANNEL_ID, n.channelId)
        assertEquals("Q36 · RED · ON FIELD", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("To match start · Field #36 · Now · 24m behind", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(snapshot.deadline!!.toEpochMilli(), n.`when`)
        assertEquals(null, n.contentView, "no custom collapsed view")
    }

    @Test
    fun `it has the buttons, alerts once, shows on the lock screen and isn't a Live Update itself`() {
        val n = card(dark = true)
        assertEquals(listOf("Refresh", "Stop"), n.actions.map { it.title.toString() })
        assertTrue(n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(Notification.VISIBILITY_PUBLIC, n.visibility)
        assertEquals(hide, n.deleteIntent) // its own swipe: hides the card, tracking goes on (asked for)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `expanded, the scoreboard - match, countdown target, stages with times, alliances, context`() {
        val root = scoreboard(dark = true)
        assertEquals("Q36", text(root, R.id.card_label).toString())
        assertEquals("ON FIELD", text(root, R.id.card_pill).toString())
        assertEquals("TO MATCH START", text(root, R.id.card_target).toString())
        assertEquals("QUEUE", text(root, R.id.card_seg1_label).toString())
        assertEquals("START", text(root, R.id.card_seg4_label).toString())
        assertTrue(text(root, R.id.card_seg1_time).isNotEmpty() && text(root, R.id.card_seg4_time).isNotEmpty())
        val red = text(root, R.id.card_red)
        assertTrue(red.toString().contains("4698") && red.toString().contains("5507"))
        val ours = (red as Spanned).getSpans(0, red.length, BackgroundColorSpan::class.java).single()
        assertEquals("5507", red.subSequence(red.getSpanStart(ours), red.getSpanEnd(ours)).toString().trim())
        assertEquals("NOW · FIELD #36 · 24M BEHIND", text(root, R.id.card_footer).toString())
    }

    @Test
    fun `its channel exists from app start, alerts, and shows on the lock screen`() {
        val channel = assertNotNull(context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel(LiveCard.CHANNEL_ID))
        assertEquals(android.app.NotificationManager.IMPORTANCE_DEFAULT, channel.importance)
        assertEquals(Notification.VISIBILITY_PUBLIC, channel.lockscreenVisibility)
    }

    @Test
    fun `scoreboard text follows the theme`() {
        val dark = LiveCard.colors(context, dark = true)
        val light = LiveCard.colors(context, dark = false)
        kotlin.test.assertNotEquals(dark.text, light.text)
        assertEquals(dark.text, scoreboard(dark = true).findViewById<TextView>(R.id.card_label).currentTextColor)
        assertEquals(light.text, scoreboard(dark = false).findViewById<TextView>(R.id.card_label).currentTextColor)
        assertEquals(light.muted, scoreboard(dark = false).findViewById<TextView>(R.id.card_footer).currentTextColor)
    }

    @Test
    fun `the stages are a tinted chevron band - each tip in its stage's fill, notched into the next`() {
        // Asked for: the iOS chevrons on the card. The snapshot is on field, stage 3 of 4.
        val colors = LiveCard.colors(context, dark = true)
        val root = scoreboard(dark = true)
        fun tint(id: Int) = root.findViewById<android.view.View>(id).backgroundTintList!!.defaultColor
        val fills = (0..3).map { StageChevrons.look(it, current = 3, colors = colors, dark = true).fill }
        assertEquals(fills, listOf(R.id.card_seg1, R.id.card_seg2, R.id.card_seg3, R.id.card_seg4).map(::tint))
        assertEquals(colors.empty, fills[3])
        assertEquals(listOf(fills[0], fills[1], fills[2]), listOf(R.id.card_tip1, R.id.card_tip2, R.id.card_tip3).map(::tint))
        assertEquals(listOf(fills[1], fills[2], fills[3]), listOf(R.id.card_notch1, R.id.card_notch2, R.id.card_notch3).map(::tint))
    }

    @Test
    fun `past the start estimate, START is the solid current stage`() {
        val now = java.time.Instant.ofEpochMilli(1775868749195)
        val started = assertNotNull(LiveSnapshots.build(cache, config, "2026cancmp_qm36", now))
        val colors = LiveCard.colors(context, dark = true)
        val root = LiveCard.scoreboard(context, cache, config, started, now, colors, true).apply(context, FrameLayout(context))
        fun tint(id: Int) = root.findViewById<android.view.View>(id).backgroundTintList!!.defaultColor
        val fills = (0..3).map { StageChevrons.look(it, current = 4, colors = colors, dark = true).fill }
        assertEquals(fills, listOf(R.id.card_seg1, R.id.card_seg2, R.id.card_seg3, R.id.card_seg4).map(::tint))
        assertEquals(StageChevrons.State.CURRENT, StageChevrons.state(4, 4))
        assertEquals(StageChevrons.State.PAST, StageChevrons.state(3, 4))
    }
}
