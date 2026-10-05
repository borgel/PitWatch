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
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(played, laterToday), near, null, null, now))
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played), near, null, null, now))
    }

    @Test
    fun `keeps an unplayed tracked match`() {
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(laterToday), near, laterToday.key, null, now))
    }

    @Test
    fun `keeps showing a result until the linger elapses`() {
        assertEquals(LiveDecision.Track(played.key), LiveLifecycle.decide(cache(played, laterToday), near, played.key, null, now))
        assertEquals(LiveDecision.Track(played.key), LiveLifecycle.decide(cache(played, laterToday), near, played.key, now.minusSeconds(14 * 60), now))
    }

    @Test
    fun `near-match mode stops after 15 minutes of result`() {
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played, laterToday), near, played.key, now.minusSeconds(16 * 60), now))
    }

    @Test
    fun `all-day mode rolls to a same-day match after 5 minutes`() {
        assertEquals(LiveDecision.Track(played.key), LiveLifecycle.decide(cache(played, laterToday), allDay, played.key, now.minusSeconds(4 * 60), now))
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(played, laterToday), allDay, played.key, now.minusSeconds(6 * 60), now))
    }

    @Test
    fun `all-day mode stops when the next match is another day in the event zone`() {
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played, tomorrow), allDay, played.key, now.minusSeconds(6 * 60), now))
    }
}
