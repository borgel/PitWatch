package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.localInstant
import com.pitwatch.core.logic.ScheduleBreak.Kind
import com.pitwatch.core.logic.UpcomingScheduleItem.BreakItem
import com.pitwatch.core.logic.UpcomingScheduleItem.MatchItem
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import com.pitwatch.core.unixMs
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpcomingTimelineTest {
    private val utc = ZoneOffset.UTC
    private fun nm(label: String, start: Long?) = NexusMatch(label, times = NexusMatchTimes(estimatedStartTime = start))
    private fun nexus(vararg matches: NexusMatch) = NexusEvent(dataAsOfTime = 0, matches = matches.toList())
    private fun local(iso: String) = localInstant(iso, LA).unixMs
    private fun List<UpcomingScheduleItem>.matchKeys() = filterIsInstance<MatchItem>().map { it.match.key }
    private fun List<UpcomingScheduleItem>.breaks() = filterIsInstance<BreakItem>().map { it.scheduleBreak }

    @Test
    fun `no nexus returns matches only`() {
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(20)), "frc1234").upcomingTimeline(null, utc)
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `empty upcoming returns empty`() {
        assertTrue(MatchSchedule(emptyList(), "frc1234").upcomingTimeline(nexus(), utc).isEmpty())
    }

    @Test
    fun `single upcoming returns single match`() {
        val timeline = MatchSchedule(listOf(testMatch(10)), "frc1234")
            .upcomingTimeline(nexus(nm("Qualification 10", 1_700_000_000_000)), utc)
        assertEquals(listOf("2026test_qm10"), timeline.matchKeys())
        assertEquals(1, timeline.size)
    }

    @Test
    fun `inserts break between bracketing matches`() {
        val q10 = local("2026-04-11T11:00:00")
        val q20 = local("2026-04-11T13:00:00")
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(20)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 10", q10),
                nm("Qualification 15", q10 + 10 * 60_000),
                nm("Qualification 16", q20 - 10 * 60_000),
                nm("Qualification 20", q20),
            ),
            LA,
        )
        assertEquals(3, timeline.size)
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        val middle = assertIs<BreakItem>(timeline[1]).scheduleBreak
        assertEquals(Kind.LUNCH, middle.kind)
        assertEquals("Qualification 15", middle.startsAfter)
        assertEquals("Qualification 16", middle.endsBefore)
    }

    @Test
    fun `drops breaks before the first upcoming match`() {
        val timeline = MatchSchedule(listOf(testMatch(50), testMatch(60)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 45", local("2026-04-11T11:00:00")),
                nm("Qualification 46", local("2026-04-11T12:30:00")),
                nm("Qualification 50", local("2026-04-11T15:00:00")),
                nm("Qualification 60", local("2026-04-11T15:10:00")),
            ),
            LA,
        )
        assertEquals(2, timeline.size)
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `drops breaks after the last upcoming match`() {
        val timeline = MatchSchedule(listOf(testMatch(5), testMatch(20)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 5", local("2026-04-11T11:00:00")),
                nm("Qualification 10", local("2026-04-11T11:10:00")),
                nm("Qualification 15", local("2026-04-11T11:20:00")),
                nm("Qualification 20", local("2026-04-11T11:30:00")),
                nm("Qualification 21", local("2026-04-11T11:40:00")),
                nm("Qualification 22", local("2026-04-11T12:30:00")),
            ),
            LA,
        )
        assertEquals(listOf("2026test_qm5", "2026test_qm20"), timeline.matchKeys())
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `multiple breaks in one pair are chronological`() {
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(50)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 10", local("2026-04-10T11:00:00")),
                nm("Qualification 11", local("2026-04-10T12:45:00")),
                nm("Qualification 12", local("2026-04-11T12:55:00")),
                nm("Qualification 50", local("2026-04-11T13:05:00")),
            ),
            LA,
        )
        assertEquals(4, timeline.size)
        assertEquals(listOf(Kind.LUNCH, Kind.OVERNIGHT), timeline.breaks().map { it.kind })
    }

    @Test
    fun `upcoming match with no known time skips bracketing`() {
        val timeline = MatchSchedule(listOf(testMatch(10, time = null), testMatch(20)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 15", local("2026-04-11T11:30:00")),
                nm("Qualification 16", local("2026-04-11T12:45:00")),
                nm("Qualification 20", local("2026-04-11T13:00:00")),
            ),
            LA,
        )
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `bracketing uses nexus-correlated times over TBA times`() {
        val q10 = testMatch(10, time = localInstant("2026-04-11T10:00:00", LA).epochSecond)
        val q20 = testMatch(20, time = localInstant("2026-04-11T12:30:00", LA).epochSecond)
        val timeline = MatchSchedule(listOf(q10, q20), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 10", local("2026-04-11T14:00:00")),
                nm("Qualification 15", local("2026-04-11T15:00:00")),
                nm("Qualification 16", local("2026-04-11T15:30:00")),
                nm("Qualification 20", local("2026-04-11T15:40:00")),
            ),
            LA,
        )
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        assertEquals(2, timeline.breaks().size)
    }

    @Test
    fun `real cancmp fixture brackets lunch between Q58 and Q70`() {
        val event = PitWatchJson.decodeFromString<NexusEvent>(fixture("nexus_event_2026cancmp.json"))
        val timeline = MatchSchedule(listOf(testMatch(58, time = null), testMatch(70, time = null)), "frc1234")
            .upcomingTimeline(event, LA)
        assertEquals(listOf("2026test_qm58", "2026test_qm70"), timeline.matchKeys())
        val lunch = assertIs<BreakItem>(timeline[1]).scheduleBreak
        assertEquals(Kind.LUNCH, lunch.kind)
        assertEquals("Qualification 62", lunch.startsAfter)
        assertEquals("Qualification 63", lunch.endsBefore)
    }

    @Test
    fun `item ids are stable and distinct`() {
        val b = ScheduleBreak(Kind.LUNCH, "Q1", "Q2", java.time.Instant.EPOCH, java.time.Instant.EPOCH)
        assertEquals("match:2026test_qm10", MatchItem(testMatch(10)).id)
        assertEquals("break:Q1->Q2", BreakItem(b).id)
    }
}
