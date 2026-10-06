package com.pitwatch.app.widget

import androidx.compose.ui.unit.dp
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ScheduleWidgetModelTest {
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun ready() = ScheduleWidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        config, SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `ready - the schedule starts with the next match and keeps the last result`() {
        val m = ready()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("5507 · #34 · 1-2-0", m.header)
        val first = m.days.first().items.filterIsInstance<MatchListModel.Item.Upcoming>().first().row
        assertEquals("Q36", first.shortLabel)
        assertEquals("Q22", m.last?.shortLabel)
    }

    @Test
    fun `not configured and no event show the main widget's messages`() {
        assertEquals("Set up PitWatch", ScheduleWidgetModels.build(EventCache(), UserConfig(), SNAP_NOW).message)
        val noEvent = ScheduleWidgetModels.build(EventCache(), config, SNAP_NOW)
        assertEquals(WidgetModel.State.NO_EVENT, noEvent.state)
        assertEquals("No event yet", noEvent.message)
    }

    @Test
    fun `size plan keeps at least a day and a row, then adds header and last result`() {
        val tiny = SchedulePlans.plan(110.dp, hasLast = true)
        assertTrue(tiny.listLines >= 2)
        val twoByTwo = SchedulePlans.plan(223.dp, hasLast = true)
        assertTrue(twoByTwo.header && twoByTwo.lastLine)
        assertTrue(twoByTwo.listLines >= 5)
        assertTrue(SchedulePlans.plan(450.dp, hasLast = true).listLines > twoByTwo.listLines)
    }
}
