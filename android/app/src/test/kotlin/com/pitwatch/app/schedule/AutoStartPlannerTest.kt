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
}
