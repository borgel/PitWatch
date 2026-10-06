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
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WidgetModelTest {
    private val cache = snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json")))
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(c: EventCache = cache, cfg: UserConfig = config, now: Instant = SNAP_NOW) = WidgetModels.build(c, cfg, now, Locale.US)

    @Test
    fun `ready - header, next match, what follows, last result`() {
        val m = build()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("5507 · #34 · 1-2-0", m.header)
        assertEquals("California Northern", m.eventTitle)
        assertEquals("Q36", m.next?.shortLabel)
        val later = m.later.take(2).map {
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
}
