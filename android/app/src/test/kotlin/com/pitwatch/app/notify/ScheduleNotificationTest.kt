package com.pitwatch.app.notify

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.widget.ScheduleWidgetModels
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ScheduleNotificationTest {
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private val ready = ScheduleWidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        config, SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `collapsed - next match, the one after, and the last result`() {
        val c = ScheduleNotification.content(ready, Locale.US)
        assertTrue(c.title.startsWith("Next: Q36 · "), c.title)
        assertTrue(c.text!!.startsWith("then Q"), c.text)
        assertTrue(c.text!!.endsWith("Last Q22 W 403–299"), c.text)
        assertEquals("Last Q22 W 403–299", c.summary)
    }

    @Test
    fun `expanded - at most six lines, in-motion phase, breaks, day prefixes`() {
        val c = ScheduleNotification.content(ready, Locale.US)
        assertTrue(c.lines.size <= 6)
        assertTrue(c.lines.first().startsWith("Q36 · ") && c.lines.first().endsWith(" · ON FIELD"), c.lines.first())
        assertTrue(c.lines.any { it.startsWith("End of day") }, c.lines.toString())
        assertTrue(c.lines.any { it.startsWith("Sat · Q") }, c.lines.toString()) // first row of a later day
    }

    @Test
    fun `not configured shows the setup message and nothing else`() {
        val c = ScheduleNotification.content(ScheduleWidgetModels.build(EventCache(), UserConfig(), SNAP_NOW), Locale.US)
        assertEquals("Set up PitWatch", c.title)
        assertEquals(emptyList(), c.lines)
    }
}
