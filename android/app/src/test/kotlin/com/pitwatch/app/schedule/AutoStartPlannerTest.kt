package com.pitwatch.app.schedule

import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AutoStartPlannerTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val config = UserConfig(teamNumber = 1234, apiKey = "k", useScheduledTime = true)
    private val next = testMatch(32, time = now.plusSeconds(3 * 3600).epochSecond)
    private val cache = EventCache(matches = listOf(next))

    @Test
    fun `arms two hours before the next match`() {
        assertEquals(now.plusSeconds(3600), AutoStartPlanner.nextStart(cache, config, LiveControl(), now))
    }

    @Test
    fun `stays disarmed while the user has stopped this match`() {
        assertNull(AutoStartPlanner.nextStart(cache, config, LiveControl(suppressedMatchKey = next.key), now))
    }

    @Test
    fun `suppression of an earlier match does not block the next one`() {
        assertEquals(now.plusSeconds(3600), AutoStartPlanner.nextStart(cache, config, LiveControl(suppressedMatchKey = "2026test_qm31"), now))
    }

    @Test
    fun `disarmed when not configured or nothing upcoming`() {
        assertNull(AutoStartPlanner.nextStart(cache, UserConfig(), LiveControl(), now))
        assertNull(AutoStartPlanner.nextStart(EventCache(), config, LiveControl(), now))
    }

    @Test
    fun `skips an unscored match long past its time`() {
        val stale = testMatch(31, time = now.minusSeconds(3 * 3600).epochSecond)
        assertEquals(now.plusSeconds(3600), AutoStartPlanner.nextStart(EventCache(matches = listOf(stale, next)), config, LiveControl(), now))
    }

    @Test
    fun `dismissing a finished match never blocks the next one`() {
        // Stop/dismiss while a result shows suppresses the played match's key; the next match still arms.
        val played = testMatch(30, time = now.minusSeconds(1800).epochSecond, actualTime = now.minusSeconds(1800).epochSecond, redScore = 1, blueScore = 0)
        assertEquals(now.plusSeconds(3600), AutoStartPlanner.nextStart(EventCache(matches = listOf(played, next)), config, LiveControl(suppressedMatchKey = played.key), now))
    }
}
