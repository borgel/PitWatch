package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.testMatch
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelDecodingTest {
    @Test
    fun `decode matches`() {
        val matches = PitWatchJson.decodeFromString<List<Match>>(fixture("matches.json"))
        assertEquals(2, matches.size)

        val upcoming = matches[0]
        assertEquals("2026miket_qm32", upcoming.key)
        assertEquals("qm", upcoming.compLevel)
        assertEquals(32, upcoming.matchNumber)
        assertEquals(1712000000L, upcoming.time)
        assertEquals(1712000600L, upcoming.predictedTime)
        assertNull(upcoming.actualTime)
        assertEquals(listOf("frc1234", "frc5678", "frc9012"), upcoming.alliances["red"]?.teamKeys)
        assertEquals(-1, upcoming.alliances["red"]?.score)
        assertEquals("", upcoming.winningAlliance)

        val played = matches[1]
        assertEquals(1711996550L, played.actualTime)
        assertEquals(87, played.alliances["red"]?.score)
        assertEquals("red", played.winningAlliance)
    }

    @Test
    fun `decode rankings`() {
        val rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("rankings.json"))
        assertEquals(1, rankings.rankings.size)
        assertEquals(3, rankings.rankings[0].rank)
        assertEquals(5, rankings.rankings[0].record?.wins)
        assertEquals(2, rankings.rankings[0].record?.losses)
    }

    @Test
    fun `decode OPRs`() {
        val oprs = PitWatchJson.decodeFromString<EventOPRs>(fixture("oprs.json"))
        assertEquals(45.2, oprs.oprs["frc1234"])
        assertEquals(30.5, oprs.dprs["frc1234"])
        assertEquals(14.7, oprs.ccwms["frc1234"])
    }

    @Test
    fun `decodes event timezone`() {
        val json = """
            {"key":"2026cancmp","name":"Test Event","event_code":"cancmp","event_type":2,
             "city":"Daly City","state_prov":"CA","country":"USA",
             "start_date":"2026-04-09","end_date":"2026-04-12","year":2026,
             "short_name":"California Northern","event_type_string":"District Championship",
             "week":5,"location_name":"Cow Palace","timezone":"America/Los_Angeles"}
        """.trimIndent()
        assertEquals("America/Los_Angeles", PitWatchJson.decodeFromString<Event>(json).timezone)
    }

    @Test
    fun `decodes event without timezone`() {
        val json = """
            {"key":"2020test","name":"Test","event_code":"test","event_type":0,
             "city":null,"state_prov":null,"country":null,
             "start_date":"2020-01-01","end_date":"2020-01-02","year":2020,
             "short_name":null,"event_type_string":null,"week":null,"location_name":null}
        """.trimIndent()
        assertNull(PitWatchJson.decodeFromString<Event>(json).timezone)
    }

    @Test
    fun `unknown keys are ignored`() {
        val json = """
            {"key":"2026x_qm1","comp_level":"qm","set_number":1,"match_number":1,"event_key":"2026x",
             "time":null,"predicted_time":null,"actual_time":null,"post_result_time":123,
             "alliances":{"red":{"score":-1,"team_keys":[],"surrogate_team_keys":[],"dq_team_keys":[]}},
             "winning_alliance":"","score_breakdown":{"red":{"autoPoints":3}},"videos":[],"brand_new_field":true}
        """.trimIndent()
        assertEquals("2026x_qm1", PitWatchJson.decodeFromString<Match>(json).key)
    }

    @Test
    fun `labels, played state, and sort order`() {
        assertEquals("Qual 32", testMatch(32).label)
        assertEquals("Q32", testMatch(32).shortLabel)
        assertEquals("QF 2-1", testMatch(1, compLevel = "qf", setNumber = 2).label)
        assertEquals("SF1-3", testMatch(3, compLevel = "sf", setNumber = 1).shortLabel)
        assertEquals("F1", testMatch(1, compLevel = "f").shortLabel)

        assertFalse(testMatch(1).isPlayed)
        assertFalse(testMatch(1, actualTime = 5).isPlayed) // actual time but no score yet
        assertTrue(testMatch(1, actualTime = 5, redScore = 0, blueScore = 10).isPlayed)

        assertTrue(testMatch(99).sortOrder < testMatch(1, compLevel = "qf", setNumber = 1).sortOrder)
        assertEquals("red", testMatch(1).allianceColor("frc1234"))
        assertNull(testMatch(1).allianceColor("frc9999"))
    }

    @Test
    fun `matchDate prefers actual, then predicted, then scheduled`() {
        assertEquals(Instant.ofEpochSecond(30), testMatch(1, time = 10, predictedTime = 20, actualTime = 30).matchDate())
        assertEquals(Instant.ofEpochSecond(20), testMatch(1, time = 10, predictedTime = 20).matchDate())
        assertEquals(Instant.ofEpochSecond(10), testMatch(1, time = 10).matchDate())
        assertNull(testMatch(1, time = 10).matchDate(useScheduled = false))
    }

    @Test
    fun `summed OPR is null when any team is missing`() {
        val oprs = EventOPRs(oprs = mapOf("frc1" to 10.0, "frc2" to 5.5), dprs = emptyMap(), ccwms = emptyMap())
        assertEquals(15.5, oprs.summedOpr(listOf("frc1", "frc2")))
        assertNull(oprs.summedOpr(listOf("frc1", "frc3")))
    }
}
