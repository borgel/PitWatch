package com.pitwatch.app.ui.matches

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.testEvent
import com.pitwatch.app.testMatch
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventOPRs
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class MatchListModelTest {
    private val cache = snapshotCache().copy(
        rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json")),
        oprs = PitWatchJson.decodeFromString<EventOPRs>(fixture("$SNAP/tba_oprs.json")),
    )
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(c: EventCache = cache, cfg: UserConfig = config, state: RefreshState = RefreshState()) =
        MatchListModels.build(c, cfg, state, SNAP_NOW, Locale.US, com.pitwatch.app.LA)

    private fun MatchListModel.Day.names() = items.map {
        when (it) {
            is MatchListModel.Item.Upcoming -> it.row.shortLabel
            is MatchListModel.Item.Break -> it.title
        }
    }

    @Test
    fun `header, status and queue line`() {
        val m = build()
        assertEquals("California Northern", m.title)
        assertEquals("Team 5507 · Rank #34 · 1-2-0", m.status?.text)
        assertEquals("Qualification 38", m.nowQueuing)
        assertFalse(m.nexusUnavailable)
        assertNull(m.empty)
    }

    @Test
    fun `upcoming matches are grouped by event-local day with named breaks`() {
        val days = build().days
        assertEquals(listOf("Friday, Apr 10", "Saturday, Apr 11", "Sunday, Apr 12"), days.map { it.label })
        assertEquals(listOf("Q36", "End of day"), days[0].names())
        assertEquals(listOf("Q43", "Q52", "Q61", "Lunch", "Q72", "Q81", "End of day"), days[1].names())
        assertEquals(listOf("Q99", "Q106", "Q116"), days[2].names())
    }

    @Test
    fun `next match carries phase, countdown and alliances`() {
        val next = (build().days[0].items[0] as MatchListModel.Item.Upcoming).row
        assertTrue(next.isNext)
        assertEquals(Phase.ON_FIELD, next.phase)
        assertEquals(MatchListModel.Countdown(Instant.ofEpochMilli(1775868749195).plusSeconds(150), "to match end"), next.countdown)
        assertTrue(next.red.teams.single { it.isUs }.number == "5507")
        assertEquals(cache.oprs!!.summedOpr(listOf("frc4698", "frc5507", "frc1678")), next.red.summedOpr)
        assertTrue(next.estimated)
        val later = (build().days[1].items[0] as MatchListModel.Item.Upcoming).row
        assertFalse(later.isNext)
        assertNull(later.countdown)
    }

    @Test
    fun `results are most recent first from our side`() {
        val results = build().results
        assertEquals(listOf("Qual 22", "Qual 14", "Qual 1"), results.map { it.label })
        assertEquals(listOf("W", "L", "L"), results.map { it.outcome })
        assertEquals(403 to 299, results[0].ourScore to results[0].theirScore)
        assertEquals("https://www.thebluealliance.com/match/2026cancmp_qm22", results[0].url)
    }

    @Test
    fun `empty states`() {
        assertEquals(MatchListModel.Empty.NOT_CONFIGURED, build(cfg = UserConfig()).empty)
        assertEquals(MatchListModel.Empty.NO_EVENT, build(c = EventCache()).empty)
        assertEquals(MatchListModel.Empty.NO_MATCHES, build(c = EventCache(event = cache.event)).empty)
    }

    @Test
    fun `nexus unavailable and errors are surfaced`() {
        val m = build(c = cache.copy(nexusEvent = null), state = RefreshState(lastError = "API error 500"))
        assertTrue(m.nexusUnavailable)
        assertEquals("API error 500", m.error)
    }

    @Test
    fun `TBA-only event - TBA times, no phases, no breaks`() {
        // Review focus #1
        val m = build(cfg = config.copy(timeSource = TimeSource.TBA, queueOffsetMinutes = 10))
        val rows = m.days.flatMap { it.items }
        assertTrue(rows.none { it is MatchListModel.Item.Break })
        val upcoming = rows.filterIsInstance<MatchListModel.Item.Upcoming>().map { it.row }
        assertTrue(upcoming.all { it.phase == null })
        val next = upcoming.first()
        assertEquals(Instant.ofEpochSecond(1775868749), next.time) // TBA predicted time
        assertEquals(MatchListModel.Countdown(Instant.ofEpochSecond(1775868749).minusSeconds(600), "to queue"), next.countdown)
    }

    @Test
    fun `at the event, times are unlabeled`() {
        val m = build()
        assertEquals(com.pitwatch.app.LA, m.timeZone)
        assertNull(m.zoneLabel)
    }

    @Test
    fun `away from the event, days and times follow the phone's zone, labeled`() {
        // User's choice: a parent watching from elsewhere sees their own clock, marked with its zone.
        val tokyo = java.time.ZoneId.of("Asia/Tokyo")
        val m = MatchListModels.build(cache, config, RefreshState(), SNAP_NOW, Locale.US, tokyo)
        assertEquals(tokyo, m.timeZone)
        assertEquals("JST", m.zoneLabel)
        assertEquals("Saturday, Apr 11", m.days.first().label) // qm36 is 09:52 Saturday in Tokyo
        // Break names still come from the event's local schedule.
        assertTrue(m.days.flatMap { it.items }.any { it is MatchListModel.Item.Break && it.title == "Lunch" })
    }

    @Test
    fun `matches without a time are still listed`() {
        // Review focus #3
        val c = EventCache(event = testEvent(), matches = listOf(testMatch(1, time = null), testMatch(2, time = 1_775_900_000)))
        val m = MatchListModels.build(c, UserConfig(teamNumber = 1234, apiKey = "k"), RefreshState(), SNAP_NOW, Locale.US, com.pitwatch.app.LA)
        assertEquals(listOf("Q1", "Q2"), m.days.flatMap { it.items }.map { (it as MatchListModel.Item.Upcoming).row.shortLabel })
        assertEquals("Time TBD", m.days.first().label)
    }

    @Test
    fun `item ids stay unique when match times go backwards`() {
        // Review finding m4: overlapping bracket windows used to repeat a break, and duplicate keys crash LazyColumn.
        val la = com.pitwatch.app.LA
        fun at(t: String) = com.pitwatch.app.localInstant("2026-04-11T$t", la)
        val nexus = com.pitwatch.core.model.NexusEvent(0, matches = listOf(
            com.pitwatch.core.model.NexusMatch("Qualification 1", times = com.pitwatch.core.model.NexusMatchTimes(estimatedStartTime = at("10:00:00").toEpochMilli())),
            com.pitwatch.core.model.NexusMatch("Qualification 3", times = com.pitwatch.core.model.NexusMatchTimes(estimatedStartTime = at("11:00:00").toEpochMilli())),
            com.pitwatch.core.model.NexusMatch("Qualification 50", times = com.pitwatch.core.model.NexusMatchTimes(estimatedStartTime = at("12:00:00").toEpochMilli()), breakAfter = "Lunch"),
            com.pitwatch.core.model.NexusMatch("Qualification 4", times = com.pitwatch.core.model.NexusMatchTimes(estimatedStartTime = at("14:00:00").toEpochMilli())),
        ))
        val matches = listOf(
            testMatch(1), testMatch(2, time = at("13:00:00").epochSecond), testMatch(3), testMatch(4),
        )
        val c = EventCache(event = testEvent(), matches = matches, nexusEvent = nexus)
        val m = MatchListModels.build(c, UserConfig(teamNumber = 1234, apiKey = "k", nexusApiKey = "n"), RefreshState(), SNAP_NOW, Locale.US, com.pitwatch.app.LA)
        val ids = m.days.flatMap { it.items }.map { it.id }
        assertEquals(ids.distinct(), ids)
        assertEquals(1, m.days.flatMap { it.items }.count { it is MatchListModel.Item.Break })
    }
}
