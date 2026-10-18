package com.pitwatch.app.widget

import com.pitwatch.app.LA
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.store.EventCache
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class EventScheduleModelTest {
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(showPlayed: Boolean = true, cache: EventCache = snapshotCache(), config: UserConfig = this.config) =
        EventScheduleModels.build(cache, config, showPlayed, SNAP_NOW, Locale.US, LA)

    private fun EventScheduleModel.upcoming() = lines.filterIsInstance<EventScheduleLine.Upcoming>().map { it.row }
    private fun EventScheduleModel.played() = lines.filterIsInstance<EventScheduleLine.Played>().map { it.row }

    @Test
    fun `every unplayed match at the event, in order, from the next one`() {
        val m = build()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("California Northern", m.title)
        val upcoming = m.upcoming()
        assertEquals(85, upcoming.size) // 120 matches, 35 played
        assertEquals("Q36", upcoming.first().shortLabel)
        assertEquals("Q120", upcoming.last().shortLabel)
        assertTrue(m.lines.first() is EventScheduleLine.Header, "upcoming starts under a day header")
    }

    @Test
    fun `played matches follow under Results, most recent first, with both scores`() {
        val m = build()
        val resultsAt = m.lines.indexOfFirst { it is EventScheduleLine.Header && it.label == "Results" }
        assertTrue(resultsAt > m.lines.indexOfLast { it is EventScheduleLine.Upcoming }, "results come after every upcoming match")
        val played = m.played()
        assertEquals(35, played.size)
        assertEquals("Q35", played.first().shortLabel)
        assertEquals(216, played.first().redScore)
        assertEquals(401, played.first().blueScore)
        assertEquals("Q1", played.last().shortLabel)
    }

    @Test
    fun `with played matches off, only the upcoming schedule`() {
        val m = build(showPlayed = false)
        assertTrue(m.played().isEmpty())
        assertFalse(m.lines.any { it is EventScheduleLine.Header && it.label == "Results" })
        assertEquals(85, m.upcoming().size)
    }

    @Test
    fun `our matches carry our alliance and mark our team`() {
        val q36 = build().upcoming().first()
        assertEquals(MatchAlliance.RED, q36.ours)
        assertTrue(q36.red.teams.single { it.isUs }.number == "5507")
        val q35 = build().played().first() // 2135, 253, 7419 vs ... — not us
        assertNull(q35.ours)
    }

    @Test
    fun `upcoming times are the same as the Matches screen's (Nexus estimates)`() {
        val q36 = build().upcoming().first()
        assertTrue(q36.estimated)
        val times = TimeFormat(LA, null)
        assertTrue(times.match(q36.time, q36.estimated).startsWith("~"))
    }

    @Test
    fun `not configured and no event show the other widgets' messages`() {
        assertEquals("Set up PitWatch", build(cache = EventCache(), config = UserConfig()).message)
        val noEvent = build(cache = EventCache())
        assertEquals(WidgetModel.State.NO_EVENT, noEvent.state)
        assertEquals("No event yet", noEvent.message)
    }

    @Test
    fun `an event without matches, or with every match played and results hidden, says so`() {
        val noMatches = build(cache = snapshotCache().copy(matches = emptyList()))
        assertEquals(WidgetModel.State.NO_UPCOMING, noMatches.state)
        assertEquals("No matches yet", noMatches.message)

        val done = snapshotCache().let { c -> c.copy(matches = c.matches.filter { it.isPlayed }) }
        assertEquals("No upcoming matches", build(showPlayed = false, cache = done).message)
        val doneWithResults = build(showPlayed = true, cache = done)
        assertEquals(WidgetModel.State.READY, doneWithResults.state)
        assertEquals(35, doneWithResults.played().size)
    }

    @Test
    fun `an outsized event drops the oldest results first, keeping every upcoming match`() {
        val cache = snapshotCache()
        val played = cache.matches.first { it.isPlayed }
        val upcoming = cache.matches.first { !it.isPlayed }
        // 150 played and 100 upcoming quals: past the line cap.
        val matches = (1..150).map { n -> played.copy(key = "2026cancmp_qm$n", matchNumber = n) } +
            (151..250).map { n -> upcoming.copy(key = "2026cancmp_qm$n", matchNumber = n) }
        val m = build(cache = cache.copy(matches = matches))
        assertTrue(m.lines.size <= EventScheduleModels.MAX_LINES, "${m.lines.size} lines")
        assertEquals(100, m.upcoming().size)
        assertEquals("Q150", m.played().first().shortLabel)
        assertTrue(m.played().last().shortLabel != "Q1", "the oldest results are the ones dropped")
    }

    @Test
    fun `a disqualified team is marked in the results`() {
        // The captured event: 5419 was disqualified in Q16.
        val q16 = build().played().first { it.shortLabel == "Q16" }
        val chips = q16.red.teams + q16.blue.teams
        assertEquals(listOf("5419"), chips.filter { it.dq }.map { it.number })
    }
}
