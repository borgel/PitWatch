package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NexusModelDecodingTest {
    @Test
    fun `decode nexus event`() {
        val event = PitWatchJson.decodeFromString<NexusEvent>(fixture("nexus_event.json"))
        assertEquals(1712000000000L, event.dataAsOfTime)
        assertEquals("Qualification 33", event.nowQueuing)
        assertEquals(3, event.matches.size)

        val match = event.matches[0]
        assertEquals("Qualification 32", match.label)
        assertEquals("On deck", match.status)
        assertEquals(listOf("1234", "5678", "9012"), match.redTeams)
        assertEquals(listOf("3456", "7890", "1111"), match.blueTeams)
        assertEquals(1711999200000L, match.times.estimatedQueueTime)
        assertEquals(1711999500000L, match.times.estimatedOnDeckTime)
        assertEquals(1711999800000L, match.times.estimatedOnFieldTime)
        assertEquals(1712000000000L, match.times.estimatedStartTime)
        assertEquals(1711999250000L, match.times.actualQueueTime)

        val noTimes = event.matches[2]
        assertNull(noTimes.times.estimatedQueueTime)
        assertNull(noTimes.times.estimatedStartTime)
        assertNull(noTimes.replayOf)
    }

    @Test
    fun `null team slots are dropped`() {
        val json = """
            {"dataAsOfTime":1712000000000,"matches":[{"label":"Practice 1","status":"Queuing soon",
             "redTeams":["1234",null,"9012"],"blueTeams":["3456","7890",null],"times":{}}]}
        """.trimIndent()
        val event = PitWatchJson.decodeFromString<NexusEvent>(json)
        assertEquals(1, event.matches.size)
        assertEquals(listOf("1234", "9012"), event.matches[0].redTeams)
        assertEquals(listOf("3456", "7890"), event.matches[0].blueTeams)
    }

    @Test
    fun `times convert from unix millis`() {
        val times = NexusMatchTimes(
            estimatedQueueTime = 1712000000000,
            estimatedOnDeckTime = 1712000300000,
            estimatedOnFieldTime = 1712000600000,
            estimatedStartTime = 1712000900000,
        )
        assertEquals(Instant.ofEpochSecond(1712000000), times.queueDate)
        assertEquals(Instant.ofEpochSecond(1712000900), times.startDate)
    }

    @Test
    fun `nextPhaseDate returns first future phase`() {
        val times = NexusMatchTimes(1712000000000, 1712000300000, 1712000600000, 1712000900000)
        val result = times.nextPhaseDate(after = Instant.ofEpochSecond(1712000400))
        assertEquals("On Field", result?.label)
        assertEquals(Instant.ofEpochSecond(1712000600), result?.date)
    }

    @Test
    fun `nextPhaseDate is null when all phases are past`() {
        val times = NexusMatchTimes(1712000000000, 1712000300000, 1712000600000, 1712000900000)
        assertNull(times.nextPhaseDate(after = Instant.ofEpochSecond(1712001000)))
    }

    @Test
    fun `pit lookup by team number`() {
        val map = PitWatchJson.decodeFromString<PitMap>(fixture("2026cancmp/2026-04-11T00-51-22Z/nexus_map.json"))
        val found = map.pit(forTeam = "3598")
        assertEquals("A1", found?.address)
        assertNull(map.pit(forTeam = "0"))
    }
}
