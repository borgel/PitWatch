package com.pitwatch.app.live

import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PollCadenceTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val config = UserConfig(teamNumber = 1234, apiKey = "k", nexusApiKey = "n")
    private val match = testMatch(32)

    /** Cache whose tracked match has Nexus phases at the given offsets (seconds from [now]). */
    private fun cacheWithPhases(queue: Long?, onDeck: Long? = null, onField: Long? = null, start: Long? = null): EventCache {
        fun ms(offset: Long?) = offset?.let { now.plusSeconds(it).toEpochMilli() }
        val nexus = NexusMatch(
            "Qualification 32", null, listOf("1234", "5678", "9012"), listOf("3456", "7890", "1111"),
            NexusMatchTimes(ms(queue), ms(onDeck), ms(onField), ms(start)),
        )
        return EventCache(matches = listOf(match), nexusEvent = NexusEvent(0, matches = listOf(nexus)))
    }

    private fun delay(cache: EventCache, cfg: UserConfig = config, failures: Int = 0, key: String? = match.key) =
        PollCadence.nextDelay(cache, cfg, key, now, failures)

    @Test
    fun `failures back off 30, 60, then 120 seconds`() {
        val cache = cacheWithPhases(queue = 300)
        assertEquals(Duration.ofSeconds(30), delay(cache, failures = 1))
        assertEquals(Duration.ofSeconds(60), delay(cache, failures = 2))
        assertEquals(Duration.ofSeconds(120), delay(cache, failures = 3))
        assertEquals(Duration.ofSeconds(120), delay(cache, failures = 9))
    }

    @Test
    fun `fast within ten minutes of the next phase`() {
        assertEquals(PollCadence.FAST, delay(cacheWithPhases(queue = 300)))
        assertEquals(PollCadence.FAST, delay(cacheWithPhases(queue = 600)))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = 660)))
    }

    @Test
    fun `fast for two minutes after a phase passes`() {
        assertEquals(PollCadence.FAST, delay(cacheWithPhases(queue = -60, onDeck = 1200)))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = -180, onDeck = 1200)))
    }

    @Test
    fun `slow without nexus correlation, with TBA time source, or for an unknown match`() {
        assertEquals(PollCadence.SLOW, delay(EventCache(matches = listOf(match))))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = 300), cfg = config.copy(timeSource = TimeSource.TBA)))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = 300), key = "nope"))
    }

    @Test
    fun `real snapshot - team 5507 about to start qm36 polls fast`() {
        val cfg = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
        assertEquals(PollCadence.FAST, PollCadence.nextDelay(snapshotCache(), cfg, "2026cancmp_qm36", SNAP_NOW, 0))
    }

    @Test
    fun `TBA is due every two minutes`() {
        assertTrue(PollCadence.isTbaDue(null, now))
        assertFalse(PollCadence.isTbaDue(now.minusSeconds(119), now))
        assertTrue(PollCadence.isTbaDue(now.minusSeconds(120), now))
    }
}
