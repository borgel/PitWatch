package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.localInstant
import com.pitwatch.core.logic.ScheduleBreak.Kind
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.unixMs
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleBreakDetectorTest {
    private val utc = ZoneOffset.UTC
    private fun m(label: String, start: Long?) = NexusMatch(label, times = NexusMatchTimes(estimatedStartTime = start))

    @Test
    fun `empty input`() {
        assertTrue(ScheduleBreakDetector.detectBreaks(emptyList(), utc).isEmpty())
    }

    @Test
    fun `single match`() {
        assertTrue(ScheduleBreakDetector.detectBreaks(listOf(m("Q1", 1_700_000_000_000)), utc).isEmpty())
    }

    @Test
    fun `gap below minimum is not a break`() {
        val t0 = 1_700_000_000_000
        assertTrue(ScheduleBreakDetector.detectBreaks(listOf(m("Q1", t0), m("Q2", t0 + 10 * 60_000)), utc).isEmpty())
    }

    @Test
    fun `respects custom minimum gap`() {
        val t0 = 1_700_000_000_000
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(m("Q1", t0), m("Q2", t0 + 15 * 60_000)), utc, minimumGap = Duration.ofMinutes(10),
        )
        assertEquals(1, breaks.size)
    }

    @Test
    fun `afternoon gap is a session break`() {
        val start = localInstant("2026-04-11T15:00:00", LA)
        val end = localInstant("2026-04-11T15:45:00", LA)
        val breaks = ScheduleBreakDetector.detectBreaks(listOf(m("Q1", start.unixMs), m("Q2", end.unixMs)), LA)
        assertEquals(1, breaks.size)
        assertEquals(ScheduleBreak(Kind.SESSION_BREAK, "Q1", "Q2", start, end), breaks.single())
    }

    @Test
    fun `gap straddling local noon is lunch`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q1", localInstant("2026-04-11T11:45:00", LA).unixMs),
                m("Q2", localInstant("2026-04-11T12:45:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals(Kind.LUNCH, breaks.single().kind)
    }

    @Test
    fun `gap crossing local midnight is overnight`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q1", localInstant("2026-04-10T17:30:00", LA).unixMs),
                m("Q2", localInstant("2026-04-11T09:00:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals(Kind.OVERNIGHT, breaks.single().kind)
    }

    @Test
    fun `time zone affects classification`() {
        val matches = listOf(
            m("Q1", localInstant("2026-04-11T11:45:00", LA).unixMs),
            m("Q2", localInstant("2026-04-11T12:45:00", LA).unixMs),
        )
        assertEquals(Kind.LUNCH, ScheduleBreakDetector.detectBreaks(matches, LA).single().kind)
        assertEquals(Kind.SESSION_BREAK, ScheduleBreakDetector.detectBreaks(matches, utc).single().kind)
    }

    @Test
    fun `skips matches without a start time`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q1", localInstant("2026-04-11T15:00:00", LA).unixMs),
                m("Q2", null),
                m("Q3", localInstant("2026-04-11T15:45:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals("Q1" to "Q3", breaks.single().let { it.startsAfter to it.endsBefore })
    }

    @Test
    fun `sorts unordered input`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q2", localInstant("2026-04-11T15:45:00", LA).unixMs),
                m("Q1", localInstant("2026-04-11T15:00:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals("Q1" to "Q2", breaks.single().let { it.startsAfter to it.endsBefore })
    }

    @Test
    fun `real cancmp fixture`() {
        val event = PitWatchJson.decodeFromString<NexusEvent>(fixture("nexus_event_2026cancmp.json"))
        val breaks = ScheduleBreakDetector.detectBreaks(event.matches, LA)
        assertEquals(
            listOf(
                Triple("Practice 11", "Qualification 1", Kind.LUNCH),
                Triple("Qualification 38", "Qualification 39", Kind.OVERNIGHT),
                Triple("Qualification 62", "Qualification 63", Kind.LUNCH),
                Triple("Qualification 96", "Qualification 97", Kind.OVERNIGHT),
            ),
            breaks.map { Triple(it.startsAfter, it.endsBefore, it.kind) },
        )
    }
}
