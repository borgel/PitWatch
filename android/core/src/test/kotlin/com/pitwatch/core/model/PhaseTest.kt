package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhaseTest {
    @Test
    fun `phases are ordered`() {
        assertEquals(listOf(Phase.PRE_QUEUE, Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD), Phase.entries)
    }

    @Test
    fun `state labels`() {
        assertEquals(listOf("UPCOMING", "IN QUEUE", "ON DECK", "ON FIELD"), Phase.entries.map { it.stateLabel })
    }

    @Test
    fun `target labels`() {
        assertEquals(
            listOf("QUEUE STARTS", "MOVE TO DECK", "MOVE TO FIELD", "MATCH ENDS"),
            Phase.entries.map { it.targetLabel },
        )
    }

    @Test
    fun `glyphs are distinct single characters`() {
        assertEquals(listOf("U", "Q", "D", "F"), Phase.entries.map { it.glyph })
    }

    @Test
    fun `next phase prose is null only for on field`() {
        assertEquals("queue", Phase.PRE_QUEUE.nextPhaseProse)
        assertEquals("on deck", Phase.QUEUEING.nextPhaseProse)
        assertEquals("on field", Phase.ON_DECK.nextPhaseProse)
        assertNull(Phase.ON_FIELD.nextPhaseProse)
    }

    @Test
    fun `phase round-trips through JSON`() {
        assertEquals(Phase.ON_DECK, PitWatchJson.decodeFromString<Phase>(PitWatchJson.encodeToString(Phase.ON_DECK)))
    }

    @Test
    fun `alliance display names and JSON names`() {
        assertEquals("BLUE", MatchAlliance.BLUE.displayName)
        assertEquals("RED", MatchAlliance.RED.displayName)
        assertEquals("\"red\"", PitWatchJson.encodeToString(MatchAlliance.RED))
    }

    @Test
    fun `matches away text`() {
        assertEquals("5 AWAY", MatchesAwayDisplay.text(5))
        assertEquals("2 AWAY", MatchesAwayDisplay.text(2))
        assertEquals("NEXT", MatchesAwayDisplay.text(1))
        assertEquals("NOW", MatchesAwayDisplay.text(0))
        assertEquals("NOW", MatchesAwayDisplay.text(-1))
    }
}
