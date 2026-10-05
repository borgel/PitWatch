package com.pitwatch.app.live

import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

class LiveSnapshotTest {
    private val cache = snapshotCache()
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private val qm36 = "2026cancmp_qm36"

    @Test
    fun `nexus drives phase, deadline and field position`() {
        val s = assertNotNull(LiveSnapshots.build(cache, config, qm36, SNAP_NOW))
        assertEquals("Q36", s.matchLabel)
        assertEquals(MatchAlliance.RED, s.alliance)
        assertEquals(Phase.ON_FIELD, s.phase)
        assertEquals(Instant.ofEpochMilli(1775868749195).plusSeconds(150), s.deadline)
        assertEquals(Instant.ofEpochMilli(1775867343646), s.milestones.queue)
        assertEquals(36, s.onFieldNumber)
        assertEquals(0, s.matchesAway)
        assertNull(s.result)
    }

    @Test
    fun `TBA time source counts down to the match minus the queue offset`() {
        val cfg = config.copy(timeSource = TimeSource.TBA, queueOffsetMinutes = 10)
        val s = assertNotNull(LiveSnapshots.build(cache, cfg, qm36, SNAP_NOW))
        val matchTime = cache.matches.first { it.key == qm36 }.matchDate()!!
        assertEquals(Phase.PRE_QUEUE, s.phase)
        assertEquals(matchTime.minusSeconds(600), s.deadline)
        assertEquals(matchTime, s.milestones.start)
        assertNull(s.matchesAway)
    }

    @Test
    fun `played match carries the result from our side`() {
        val played = testMatch(32, actualTime = 100, red = listOf("frc1234", "frc2", "frc3"), redScore = 95, blueScore = 80)
        val s = assertNotNull(LiveSnapshots.build(EventCache(matches = listOf(played)), UserConfig(teamNumber = 1234, apiKey = "k"), played.key, SNAP_NOW))
        assertEquals(LiveSnapshot.Result(95, 80), s.result)
        assertEquals("W", s.result?.outcome)
        val blueSide = assertNotNull(LiveSnapshots.build(EventCache(matches = listOf(played)), UserConfig(teamNumber = 3456, apiKey = "k"), played.key, SNAP_NOW))
        assertEquals("L", blueSide.result?.outcome)
    }

    @Test
    fun `null for an unknown match or an unconfigured team`() {
        assertNull(LiveSnapshots.build(cache, config, "nope", SNAP_NOW))
        assertNull(LiveSnapshots.build(cache, UserConfig(), qm36, SNAP_NOW))
    }
}
