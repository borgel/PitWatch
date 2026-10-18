package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleDelayTest {
    private val t0 = 1_712_000_000L

    private fun played(n: Int, time: Long) = testMatch(n, time = time, actualTime = time + 600, redScore = 50, blueScore = 40)

    @Test
    fun `measures the event's next unplayed match against its schedule`() {
        // Q2 is the next unplayed match (any teams); TBA predicts it 12 minutes late.
        val matches = listOf(played(1, t0), testMatch(2, time = t0 + 420, predictedTime = t0 + 420 + 720), testMatch(3, time = t0 + 840))
        assertEquals(Duration.ofMinutes(12), ScheduleDelay.of(matches, null))
    }

    @Test
    fun `Nexus estimated start wins over TBA predicted`() {
        val matches = listOf(testMatch(2, time = t0, predictedTime = t0 + 600))
        val nexus = NexusEvent(0, null, listOf(NexusMatch("Qualification 2", times = NexusMatchTimes(estimatedStartTime = (t0 - 300) * 1000))))
        assertEquals(Duration.ofMinutes(-5), ScheduleDelay.of(matches, nexus))
    }

    @Test
    fun `order follows the schedule, not the list`() {
        val matches = listOf(testMatch(3, time = t0 + 840, predictedTime = t0 + 840), testMatch(2, time = t0 + 420, predictedTime = t0 + 420 + 300))
        assertEquals(Duration.ofMinutes(5), ScheduleDelay.of(matches, null))
    }

    @Test
    fun `unknown without an estimate, a schedule, or an unplayed match`() {
        assertNull(ScheduleDelay.of(listOf(testMatch(2, time = t0)), null))
        assertNull(ScheduleDelay.of(listOf(testMatch(2, time = null, predictedTime = t0)), null))
        assertNull(ScheduleDelay.of(listOf(played(1, t0)), null))
        assertNull(ScheduleDelay.of(emptyList(), null))
    }

    @Test
    fun `text rounds to minutes and calls small drift on time`() {
        assertEquals("On time", ScheduleDelay.text(Duration.ofSeconds(0)))
        assertEquals("On time", ScheduleDelay.text(Duration.ofMinutes(2)))
        assertEquals("On time", ScheduleDelay.text(Duration.ofMinutes(-2)))
        assertEquals("3m behind", ScheduleDelay.text(Duration.ofSeconds(150)))
        assertEquals("12m behind", ScheduleDelay.text(Duration.ofMinutes(12)))
        assertEquals("5m ahead", ScheduleDelay.text(Duration.ofMinutes(-5)))
        assertEquals("1h behind", ScheduleDelay.text(Duration.ofMinutes(60)))
        assertEquals("1h 5m behind", ScheduleDelay.text(Duration.ofMinutes(65)))
    }
}
