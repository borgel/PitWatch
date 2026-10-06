package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.localInstant
import com.pitwatch.core.logic.ScheduleBreak.Kind
import com.pitwatch.core.logic.UpcomingScheduleItem.BreakItem
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import com.pitwatch.core.unixMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleBreaksTest {
    private val snapshot = PitWatchJson.decodeFromString<NexusEvent>(fixture("2026cancmp/2026-04-11T00-51-22Z/nexus_event.json"))
    private fun m(label: String, start: Long?, breakAfter: String? = null) =
        NexusMatch(label, times = NexusMatchTimes(estimatedStartTime = start), breakAfter = breakAfter)

    @Test
    fun `markers from the real event, named by Nexus`() {
        val breaks = ScheduleBreaks.forEvent(snapshot, LA)
        assertEquals(listOf("Lunch", "End of day", "Lunch", "End of day", "Alliance selection"), breaks.map { it.title })
        assertEquals(listOf("Practice 11", "Qualification 38", "Qualification 62", "Qualification 96", "Qualification 120"), breaks.map { it.startsAfter })
        assertEquals(listOf(Kind.LUNCH, Kind.OVERNIGHT, Kind.LUNCH, Kind.OVERNIGHT, Kind.SESSION_BREAK), breaks.map { it.kind })
        assertEquals("Qualification 1", breaks.first().endsBefore)
    }

    @Test
    fun `a marker after the last match has no end`() {
        val last = ScheduleBreaks.forEvent(snapshot, LA).last()
        assertNull(last.endsBefore)
        assertNull(last.end)
        assertNull(last.duration)
    }

    @Test
    fun `markers on matches without a start time are skipped`() {
        val t = localInstant("2026-04-11T11:00:00", LA).unixMs
        val breaks = ScheduleBreaks.fromMarkers(listOf(m("Q1", null, "Lunch"), m("Q2", t, "Break"), m("Q3", t + 60_000)))
        assertEquals(listOf("Break"), breaks.map { it.title })
    }

    @Test
    fun `without markers, falls back to inference with generic titles`() {
        val event = NexusEvent(0, matches = listOf(
            m("Q1", localInstant("2026-04-11T15:00:00", LA).unixMs),
            m("Q2", localInstant("2026-04-11T15:45:00", LA).unixMs),
        ))
        val only = ScheduleBreaks.forEvent(event, LA).single()
        assertEquals(Kind.SESSION_BREAK, only.kind)
        assertNull(only.label)
        assertEquals("Break", only.title)
    }

    @Test
    fun `upcoming timeline uses markers even for gaps inference would ignore`() {
        // 10-minute gap: inference (20-min threshold) finds nothing, but Nexus says "Field reset".
        val t = localInstant("2026-04-11T14:00:00", LA).unixMs
        val event = NexusEvent(0, matches = listOf(m("Qualification 10", t, "Field reset"), m("Qualification 20", t + 10 * 60_000)))
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(20)), "frc1234").upcomingTimeline(event, LA)
        assertEquals(listOf("Field reset"), timeline.filterIsInstance<BreakItem>().map { it.scheduleBreak.title })
    }
}
