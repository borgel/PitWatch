package com.pitwatch.app.live

import com.pitwatch.app.LA
import com.pitwatch.app.localInstant
import com.pitwatch.app.testEvent
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import kotlin.test.assertEquals
import org.junit.Test

class LiveLifecycleTest {
    private val now = localInstant("2026-04-10T14:00:00", LA)
    private val near = UserConfig(teamNumber = 1234, apiKey = "k", liveActivityMode = LiveActivityMode.NEAR_MATCH)
    private val allDay = near.copy(liveActivityMode = LiveActivityMode.ALL_DAY)
    private val played = testMatch(10, time = now.minusSeconds(1800).epochSecond, actualTime = now.minusSeconds(1800).epochSecond, redScore = 50, blueScore = 40)
    private val laterToday = testMatch(20, time = localInstant("2026-04-10T16:00:00", LA).epochSecond)
    private val tomorrow = testMatch(30, time = localInstant("2026-04-11T09:00:00", LA).epochSecond)

    private fun cache(vararg matches: com.pitwatch.core.model.Match) = EventCache(event = testEvent(), matches = matches.toList())

    @Test
    fun `untracked follows the next match, or stops with nothing upcoming`() {
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(played, laterToday), near, null, now))
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played), near, null, now))
    }

    @Test
    fun `keeps an unplayed tracked match`() {
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(laterToday), near, laterToday.key, now))
    }

    @Test
    fun `near-match mode stops as soon as the tracked match is scored`() {
        // Asked for: no score in the live notification, so no lingering on the result.
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played, laterToday), near, played.key, now))
    }

    @Test
    fun `all-day mode moves to the same-day match as soon as the score arrives`() {
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(played, laterToday), allDay, played.key, now))
    }

    @Test
    fun `all-day mode stops when the next match is another day in the event zone`() {
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played, tomorrow), allDay, played.key, now))
    }

    private val unscored = testMatch(15, time = now.minusSeconds(2 * 3600).epochSecond) // never scored by TBA

    @Test
    fun `an unscored match long past its time is given up on`() {
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(unscored, laterToday), near, unscored.key, now))
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(unscored, laterToday), allDay, unscored.key, now))
    }

    @Test
    fun `a late-running match is not given up on`() {
        val late = testMatch(15, time = now.minusSeconds(40 * 60).epochSecond) // 40 min behind: within the hour of grace
        assertEquals(LiveDecision.Track(late.key), LiveLifecycle.decide(cache(late, laterToday), near, late.key, now))
    }
}
