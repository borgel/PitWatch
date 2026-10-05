package com.pitwatch.core.logic

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.Ranking
import com.pitwatch.core.model.WLTRecord
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import com.pitwatch.core.testMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeDetectorTest {
    private val matches = PitWatchJson.decodeFromString<List<Match>>(fixture("matches.json"))
    private val rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("rankings.json"))
    private fun detect(old: EventCache, new: EventCache) = ChangeDetector.detect(old, new, "frc1234")

    @Test
    fun `no change`() {
        val cache = EventCache(matches = matches, rankings = rankings)
        assertFalse(detect(cache, cache.copy()).shouldReloadWidgets)
    }

    @Test
    fun `score posted`() {
        val scored = testMatch(
            32, eventKey = "2026miket", time = 1712000000, predictedTime = 1712000600, actualTime = 1712000700,
            redScore = 95, blueScore = 80,
        )
        val result = detect(EventCache(matches = matches), EventCache(matches = listOf(scored, matches[1])))
        assertTrue(result.shouldReloadWidgets)
        assertTrue(ChangeReason.SCORE_CHANGED in result.reasons)
    }

    @Test
    fun `rank changed`() {
        val newRankings = EventRankings(
            listOf(Ranking("frc1234", rank = 5, record = WLTRecord(5, 3, 0), qualAverage = 78.0, matchesPlayed = 8, dq = 0)),
        )
        val result = detect(EventCache(rankings = rankings), EventCache(rankings = newRankings))
        assertTrue(ChangeReason.RANK_CHANGED in result.reasons)
    }

    @Test
    fun `predicted time shift over five minutes`() {
        val old = testMatch(32, predictedTime = 1_000_000)
        assertTrue(ChangeReason.PREDICTED_TIME_SHIFTED in detect(EventCache(matches = listOf(old)), EventCache(matches = listOf(old.copy(predictedTime = 1_000_301)))).reasons)
        assertFalse(detect(EventCache(matches = listOf(old)), EventCache(matches = listOf(old.copy(predictedTime = 1_000_300)))).shouldReloadWidgets)
    }

    @Test
    fun `alliance composition changed`() {
        val old = testMatch(32)
        val new = testMatch(32, blue = listOf("frc3456", "frc7890", "frc2222"))
        assertTrue(ChangeReason.ALLIANCE_CHANGED in detect(EventCache(matches = listOf(old)), EventCache(matches = listOf(new))).reasons)
    }

    @Test
    fun `new team match appearing counts as a change`() {
        assertTrue(detect(EventCache(), EventCache(matches = listOf(testMatch(1)))).shouldReloadWidgets)
    }

    @Test
    fun `other teams' matches are ignored`() {
        val other = testMatch(1, red = listOf("frc1", "frc2", "frc3"), blue = listOf("frc4", "frc5", "frc6"))
        assertFalse(detect(EventCache(), EventCache(matches = listOf(other))).shouldReloadWidgets)
    }

    @Test
    fun `refresh state keeps last-modified per path and ignores null`() {
        val state = RefreshState().withLastModified("LM1", "/a").withLastModified(null, "/a").withLastModified("LM2", "/b")
        assertEquals("LM1", state.lastModified("/a"))
        assertEquals("LM2", state.lastModified("/b"))
        assertNull(state.lastModified("/c"))
    }

    @Test
    fun `event cache round-trips through JSON`() {
        val cache = EventCache(matches = matches, rankings = rankings)
        assertEquals(cache, PitWatchJson.decodeFromString<EventCache>(PitWatchJson.encodeToString(cache)))
    }
}
