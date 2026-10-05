package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NexusMatchMergeTest {
    private val red = listOf("1234", "5678", "9012")
    private val blue = listOf("3456", "7890", "1111")

    private fun times(start: Long?) = NexusMatchTimes(
        estimatedQueueTime = start?.minus(800_000),
        estimatedOnDeckTime = start?.minus(500_000),
        estimatedOnFieldTime = start?.minus(200_000),
        estimatedStartTime = start,
    )

    private fun event(vararg matches: NexusMatch) = NexusEvent(dataAsOfTime = 0, matches = matches.toList())

    @Test
    fun `match by qual label`() {
        val nexus = NexusMatch("Qualification 32", "On deck", red, blue, times(1712000000000))
        val result = NexusMatchMerge.nexusInfo(testMatch(32), event(nexus))
        assertEquals("Qualification 32", result?.label)
        assertEquals("On deck", result?.status)
    }

    @Test
    fun `match by playoff label`() {
        val nexus = NexusMatch("Quarterfinal 2-1", null, red, blue, times(null))
        assertEquals("Quarterfinal 2-1", NexusMatchMerge.nexusInfo(testMatch(1, compLevel = "qf", setNumber = 2), event(nexus))?.label)
    }

    @Test
    fun `match by final label`() {
        val nexus = NexusMatch("Final 1", "On field", red, blue, times(1712000000000))
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(1, compLevel = "f"), event(nexus)))
    }

    @Test
    fun `falls back to team composition`() {
        val nexus = NexusMatch("Qualification 100", "Now queuing", red, blue, times(1712000000000))
        assertEquals("Now queuing", NexusMatchMerge.nexusInfo(testMatch(99), event(nexus))?.status)
    }

    @Test
    fun `team fallback accepts swapped alliance colors`() {
        val nexus = NexusMatch("Qualification 100", null, blue, red, times(null))
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(99), event(nexus)))
    }

    @Test
    fun `null when nothing correlates`() {
        val nexus = NexusMatch("Qualification 99", null, listOf("9999", "8888", "7777"), listOf("6666", "5555", "4444"), times(null))
        assertNull(NexusMatchMerge.nexusInfo(testMatch(50), event(nexus)))
    }

    @Test
    fun `null when nexus event is null`() {
        assertNull(NexusMatchMerge.nexusInfo(testMatch(32), null))
    }

    @Test
    fun `labels with irregular whitespace still correlate`() {
        // Review focus #4. Teams differ so only the label can match.
        val other = listOf("1", "2", "3")
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(32), event(NexusMatch("Qualification  32", null, other, other))))
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(32), event(NexusMatch("Qualification 32 ", null, other, other))))
    }
}
