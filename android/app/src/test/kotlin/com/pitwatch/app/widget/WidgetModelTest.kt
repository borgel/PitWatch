package com.pitwatch.app.widget

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WidgetModelTest {
    private val cache = snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json")))
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(c: EventCache = cache, cfg: UserConfig = config, now: Instant = SNAP_NOW) = WidgetModels.build(c, cfg, now, Locale.US, com.pitwatch.app.LA)

    @Test
    fun `ready - header, next match, what follows, last result`() {
        val m = build()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("5507 · #34 · 1-2-0", m.header)
        assertEquals("California Northern", m.eventTitle)
        assertEquals("Q36", m.next?.shortLabel)
        // What follows the next match, grouped by day (the next match's own day keeps only what's after it).
        assertEquals(listOf("Friday, Apr 10", "Saturday, Apr 11", "Sunday, Apr 12"), m.laterDays.map { it.label })
        val later = m.laterDays.flatMap { it.items }.take(2).map {
            when (it) {
                is MatchListModel.Item.Upcoming -> it.row.shortLabel
                is MatchListModel.Item.Break -> it.title
            }
        }
        assertEquals(listOf("End of day", "Q43"), later)
        assertEquals("Q22", m.last?.shortLabel)
        assertEquals(Instant.ofEpochMilli(1775868749195).plusSeconds(150), m.countdownDeadline)
    }

    @Test
    fun `no countdown once the deadline has passed`() {
        // Review focus #4: never show a negative chronometer.
        assertNull(build(now = SNAP_NOW.plusSeconds(3600)).countdownDeadline)
    }

    @Test
    fun `not configured`() {
        val m = build(cfg = UserConfig())
        assertEquals(WidgetModel.State.NOT_CONFIGURED, m.state)
        assertEquals("Set up PitWatch", m.message)
    }

    @Test
    fun `no event yet`() {
        assertEquals("No event yet", build(c = EventCache()).message)
    }

    @Test
    fun `upcoming event without matches shows when it starts`() {
        val m = build(c = EventCache(event = cache.event), now = Instant.parse("2026-04-01T12:00:00Z"))
        assertEquals(WidgetModel.State.NO_UPCOMING, m.state)
        assertEquals("Next event: California Northern · Apr 9", m.message)
    }

    @Test
    fun `finished schedule shows the last result`() {
        val played = cache.matches.filter { it.isPlayed }
        val m = build(c = cache.copy(matches = played))
        assertEquals("No upcoming matches", m.message)
        assertEquals("Q22", m.last?.shortLabel)
    }

    @Test
    fun `upcoming lines fit the budget and never orphan a day header`() {
        // Found on-device: day headers used up the rows and "Wednesday" showed with nothing under it.
        val days = build().laterDays
        fun names(lines: List<WidgetLine>) = lines.map {
            when (it) {
                is WidgetLine.Header -> "# ${it.label}"
                is WidgetLine.Entry -> when (val item = it.item) {
                    is MatchListModel.Item.Upcoming -> item.row.shortLabel
                    is MatchListModel.Item.Break -> item.title
                }
            }
        }
        assertEquals(listOf("# Friday, Apr 10", "End of day"), names(WidgetLines.fit(days, budget = 3)))
        assertEquals(listOf("# Friday, Apr 10", "End of day", "# Saturday, Apr 11", "Q43"), names(WidgetLines.fit(days, budget = 4)))
        assertEquals(emptyList(), WidgetLines.fit(days, budget = 1))
        // Glance renders at most 10 children per Column (found on-device: the tail was silently dropped).
        kotlin.test.assertTrue(WidgetLines.fit(days, budget = 50).size <= WidgetLines.MAX_LINES)
        assertEquals(9, WidgetLines.MAX_LINES)
    }

    @Test
    fun `row budget follows the widget's height`() {
        kotlin.test.assertTrue(WidgetLines.budget(300.dp) < WidgetLines.budget(450.dp))
        assertEquals(0, WidgetLines.budget(200.dp))
    }
}
