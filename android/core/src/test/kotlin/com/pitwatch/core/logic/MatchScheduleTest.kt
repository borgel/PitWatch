package com.pitwatch.core.logic

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.fixture
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchScheduleTest {
    private val fixtureMatches = PitWatchJson.decodeFromString<List<Match>>(fixture("matches.json"))
    private fun secs(n: Long) = Duration.ofSeconds(n)

    @Test
    fun `next and last match`() {
        val schedule = MatchSchedule(fixtureMatches, "frc1234")
        assertEquals("2026miket_qm32", schedule.nextMatch?.key)
        assertEquals("2026miket_qm31", schedule.lastPlayedMatch?.key)
    }

    @Test
    fun `team matches`() {
        assertEquals(2, MatchSchedule(fixtureMatches, "frc1234").teamMatches.size)
    }

    @Test
    fun `upcoming and past split`() {
        val schedule = MatchSchedule(fixtureMatches, "frc1234")
        assertEquals(listOf("2026miket_qm32"), schedule.upcomingMatches.map { it.key })
        assertEquals(listOf("2026miket_qm31"), schedule.pastMatches.map { it.key })
    }

    @Test
    fun `past matches are most recent first`() {
        val played = (1..3).map { testMatch(it, actualTime = it.toLong(), redScore = 1, blueScore = 0) }
        assertEquals(listOf(3, 2, 1), MatchSchedule(played, "frc1234").pastMatches.map { it.matchNumber })
    }

    @Test
    fun `no matches`() {
        val schedule = MatchSchedule(emptyList(), "frc1234")
        assertNull(schedule.nextMatch)
        assertNull(schedule.lastPlayedMatch)
        assertTrue(schedule.teamMatches.isEmpty())
    }

    @Test
    fun `adaptive refresh interval from TBA time`() {
        val schedule = MatchSchedule(fixtureMatches, "frc1234")
        val matchDate = Instant.ofEpochSecond(1712000000)
        assertEquals(secs(3600), schedule.refreshInterval(matchDate.minusSeconds(10800), useScheduledTime = true))
        assertEquals(secs(1800), schedule.refreshInterval(matchDate.minusSeconds(5400), useScheduledTime = true))
        assertEquals(secs(900), schedule.refreshInterval(matchDate.minusSeconds(1200), useScheduledTime = true))
        assertEquals(secs(600), schedule.refreshInterval(matchDate.plusSeconds(300), useScheduledTime = true))
    }

    @Test
    fun `refresh interval is a day with no upcoming match`() {
        assertEquals(Duration.ofDays(1), MatchSchedule(emptyList(), "frc1234").refreshInterval(Instant.EPOCH, true))
    }

    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)

    private fun nexusFor(queue: Instant?, start: Instant?) = NexusEvent(
        dataAsOfTime = 0,
        matches = listOf(
            NexusMatch(
                "Qualification 32", null, listOf("1234", "5678", "9012"), listOf("3456", "7890", "1111"),
                NexusMatchTimes(estimatedQueueTime = queue?.toEpochMilli(), estimatedStartTime = start?.toEpochMilli()),
            ),
        ),
    )

    @Test
    fun `refresh interval tightens with nexus times`() {
        val matchTime = now.plusSeconds(3600)
        val schedule = MatchSchedule(listOf(testMatch(32, time = matchTime.epochSecond)), "frc1234")
        val interval = schedule.refreshInterval(now, useScheduledTime = false, nexusEvent = nexusFor(now.plusSeconds(1200), matchTime))
        assertTrue(interval <= secs(600), "was $interval")
    }

    @Test
    fun `refresh interval falls back without nexus`() {
        val schedule = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(3600).epochSecond)), "frc1234")
        assertEquals(secs(1800), schedule.refreshInterval(now, useScheduledTime = true))
    }

    @Test
    fun `next reload time adds the interval`() {
        val schedule = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(3600).epochSecond)), "frc1234")
        assertEquals(now.plusSeconds(1800), schedule.nextReloadTime(now, useScheduledTime = true))
    }

    private fun startCheck(
        matchIn: Long, mode: LiveActivityMode, active: Boolean = false, nexus: NexusEvent? = null,
    ) = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(matchIn).epochSecond)), "frc1234")
        .shouldStartLiveActivity(now, mode, useScheduledTime = true, hasActiveLiveActivity = active, nexusEvent = nexus)

    @Test
    fun `near match mode starts within two hours of the TBA time`() {
        assertTrue(startCheck(3600, LiveActivityMode.NEAR_MATCH))
        assertFalse(startCheck(3 * 3600, LiveActivityMode.NEAR_MATCH))
        assertFalse(startCheck(-60, LiveActivityMode.NEAR_MATCH))
    }

    @Test
    fun `all day mode also starts for a match already past its TBA time`() {
        assertTrue(startCheck(-60, LiveActivityMode.ALL_DAY))
        assertFalse(startCheck(3 * 3600, LiveActivityMode.ALL_DAY))
    }

    @Test
    fun `never starts while one is active or with nothing upcoming`() {
        assertFalse(startCheck(3600, LiveActivityMode.NEAR_MATCH, active = true))
        assertFalse(
            MatchSchedule(emptyList(), "frc1234")
                .shouldStartLiveActivity(now, LiveActivityMode.ALL_DAY, true, false),
        )
    }

    @Test
    fun `nexus queue time drives the start window when available`() {
        // TBA says 5 h away, Nexus queue is in 1 h → start.
        assertTrue(startCheck(5 * 3600, LiveActivityMode.NEAR_MATCH, nexus = nexusFor(now.plusSeconds(3600), null)))
        // Queue 10 min ago is still within the 15-min grace.
        assertTrue(startCheck(5 * 3600, LiveActivityMode.NEAR_MATCH, nexus = nexusFor(now.minusSeconds(600), null)))
        // Queue 20 min ago is past the grace.
        assertFalse(startCheck(5 * 3600, LiveActivityMode.NEAR_MATCH, nexus = nexusFor(now.minusSeconds(1200), null)))
    }

    private fun windowStart(matchIn: Long, mode: LiveActivityMode, nexus: NexusEvent? = null) =
        MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(matchIn).epochSecond)), "frc1234")
            .liveActivityWindowStart(now, mode, useScheduledTime = true, nexusEvent = nexus)

    @Test
    fun `window opens two hours before the TBA time`() {
        assertEquals(now.plusSeconds(3600), windowStart(3 * 3600, LiveActivityMode.NEAR_MATCH))
        assertEquals(now.plusSeconds(3600), windowStart(3 * 3600, LiveActivityMode.ALL_DAY))
    }

    @Test
    fun `window start is now when already inside the window`() {
        assertEquals(now, windowStart(3600, LiveActivityMode.NEAR_MATCH))
    }

    @Test
    fun `near-match window closes at the TBA time, all-day stays open`() {
        assertNull(windowStart(-60, LiveActivityMode.NEAR_MATCH))
        assertEquals(now, windowStart(-60, LiveActivityMode.ALL_DAY))
    }

    @Test
    fun `nexus window closes 15 minutes after the queue time`() {
        assertEquals(now, windowStart(5 * 3600, LiveActivityMode.NEAR_MATCH, nexusFor(now.minusSeconds(600), null)))
        assertNull(windowStart(5 * 3600, LiveActivityMode.NEAR_MATCH, nexusFor(now.minusSeconds(1200), null)))
        assertEquals(now.plusSeconds(3600), windowStart(5 * 3600, LiveActivityMode.NEAR_MATCH, nexusFor(now.plusSeconds(3 * 3600), null)))
    }

    @Test
    fun `window start agrees with shouldStartLiveActivity`() {
        for (matchIn in listOf(-1200L, -60, 0, 60, 3600, 7200, 7201, 4 * 3600)) {
            for (mode in LiveActivityMode.entries) {
                val schedule = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(matchIn).epochSecond)), "frc1234")
                val start = schedule.liveActivityWindowStart(now, mode, useScheduledTime = true) ?: continue
                assertTrue(schedule.shouldStartLiveActivity(start, mode, true, false), "matchIn=$matchIn mode=$mode")
                if (start > now) assertFalse(schedule.shouldStartLiveActivity(start.minusSeconds(1), mode, true, false))
            }
        }
    }
}
