package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.model.Phase
import com.pitwatch.core.testMatch
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhaseDerivationTest {
    private val ref: Instant = Instant.ofEpochSecond(1000)

    /** Offsets are seconds relative to [ref]. */
    private fun nexus(
        queue: Long? = null, onDeck: Long? = null, onField: Long? = null, start: Long? = null,
        status: String? = null,
    ): NexusMatch {
        fun ms(offset: Long?) = offset?.let { (ref.epochSecond + it) * 1000 }
        return NexusMatch(
            label = "Qualification 1", status = status,
            redTeams = listOf("1", "2", "3"), blueTeams = listOf("4", "5", "6"),
            times = NexusMatchTimes(ms(queue), ms(onDeck), ms(onField), ms(start)),
        )
    }

    @Test
    fun `all times in the future is pre-queue with deadline at queue time`() {
        val n = nexus(300, 600, 900, 1200)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.PRE_QUEUE, result.phase)
        assertEquals(n.times.queueDate, result.deadline)
    }

    @Test
    fun `queue passed is queueing with deadline at on-deck`() {
        val n = nexus(-60, 300, 600, 900)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.QUEUEING, result.phase)
        assertEquals(n.times.onDeckDate, result.deadline)
    }

    @Test
    fun `on-deck passed is on deck with deadline at on-field`() {
        val n = nexus(-300, -60, 300, 600)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.ON_DECK, result.phase)
        assertEquals(n.times.onFieldDate, result.deadline)
    }

    @Test
    fun `on-field passed is on field with deadline at start plus 150s`() {
        val n = nexus(-600, -300, -120, -60)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.ON_FIELD, result.phase)
        assertEquals(n.times.startDate!!.plusSeconds(150), result.deadline)
    }

    @Test
    fun `nexus status On Field overrides times`() {
        assertEquals(Phase.ON_FIELD, PhaseDerivation.derivePhase(nexus(300, 600, 900, 1200, "On Field"), ref).phase)
    }

    @Test
    fun `Queuing soon falls through to time-based`() {
        val n = nexus(300, 600, 900, 1200, "Queuing soon")
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.PRE_QUEUE, result.phase)
        assertEquals(n.times.queueDate, result.deadline)
    }

    @Test
    fun `On deck soon is not on deck`() {
        assertEquals(Phase.PRE_QUEUE, PhaseDerivation.derivePhase(nexus(300, 600, 900, 1200, "On deck soon"), ref).phase)
    }

    @Test
    fun `result carries match start and end deadlines`() {
        val n = nexus(-600, -300, -60, 30)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(n.times.startDate, result.matchStartDeadline)
        assertEquals(n.times.startDate!!.plusSeconds(150), result.matchEndDeadline)
    }

    @Test
    fun `no times is pre-queue with no deadline`() {
        val result = PhaseDerivation.derivePhase(NexusMatch("Qualification 1"), ref)
        assertEquals(Phase.PRE_QUEUE, result.phase)
        assertNull(result.deadline)
    }

    @Test
    fun `current match on field comes from statuses`() {
        val onField = NexusMatch("Qualification 42", status = "On Field")
        assertEquals(42, PhaseDerivation.currentMatchOnField(listOf(onField), fallbackMatchNumber = 1))
        assertEquals(7, PhaseDerivation.currentMatchOnField(emptyList(), fallbackMatchNumber = 7))
    }

    private fun correlated(status: String) = NexusEvent(
        dataAsOfTime = 0,
        matches = listOf(
            NexusMatch(
                "Qualification 32", status, listOf("1234", "5678", "9012"), listOf("3456", "7890", "1111"),
                NexusMatchTimes(1712000000000, 1712000300000, 1712000600000, 1712000900000),
            ),
        ),
    )

    @Test
    fun `phaseFor is null without a nexus event`() {
        assertNull(PhaseDerivation.phaseFor(testMatch(32), null, ref))
    }

    @Test
    fun `phaseFor is null when nothing correlates`() {
        val unrelated = NexusEvent(0, matches = listOf(NexusMatch("Qualification 99", null, listOf("9999"), listOf("6666"))))
        assertNull(PhaseDerivation.phaseFor(testMatch(32), unrelated, ref))
    }

    @Test
    fun `phaseFor maps statuses`() {
        assertEquals(Phase.QUEUEING, PhaseDerivation.phaseFor(testMatch(32), correlated("Now queuing"), ref))
        assertEquals(Phase.ON_FIELD, PhaseDerivation.phaseFor(testMatch(32), correlated("On field"), ref))
        assertEquals(Phase.ON_DECK, PhaseDerivation.phaseFor(testMatch(32), correlated("On deck"), ref))
    }
}
