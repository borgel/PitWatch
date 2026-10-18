package com.pitwatch.app.live

import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.ScheduleDelay
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
        assertEquals(Instant.ofEpochMilli(1775868749195), s.deadline)
        assertEquals(Instant.ofEpochMilli(1775867343646), s.milestones.queue)
        assertEquals("to match start", s.target) // on the field before the start: it counts to the start
        assertEquals(36, s.onFieldNumber)
        assertEquals(0, s.matchesAway)
    }

    @Test
    fun `carries how far ahead or behind the event is running`() {
        val s = assertNotNull(LiveSnapshots.build(cache, config, qm36, SNAP_NOW))
        val expected = assertNotNull(ScheduleDelay.of(cache.matches, cache.nexusEvent))
        assertEquals(expected, s.scheduleDelay)
        // TBA time source: Nexus is ignored, so the delay comes from TBA's predicted times.
        val tba = assertNotNull(LiveSnapshots.build(cache, config.copy(timeSource = TimeSource.TBA), qm36, SNAP_NOW))
        assertEquals(ScheduleDelay.of(cache.matches, null), tba.scheduleDelay)
    }

    @Test
    fun `TBA time source counts down to the match minus the queue offset`() {
        val cfg = config.copy(timeSource = TimeSource.TBA, queueOffsetMinutes = 10)
        val s = assertNotNull(LiveSnapshots.build(cache, cfg, qm36, SNAP_NOW))
        val matchTime = cache.matches.first { it.key == qm36 }.matchDate()!!
        assertEquals(Phase.PRE_QUEUE, s.phase)
        assertEquals("to queue", s.target)
        assertEquals(matchTime.minusSeconds(600), s.deadline)
        assertEquals(matchTime, s.milestones.start)
        assertNull(s.matchesAway)
    }

    @Test
    fun `TBA time source with no queue offset counts down to the match`() {
        val s = assertNotNull(LiveSnapshots.build(cache, config.copy(timeSource = TimeSource.TBA), qm36, SNAP_NOW))
        assertEquals("to match", s.target)
    }

    @Test
    fun `before quals start, a practice match on the field isn't our field position`() {
        // On-device: "Q5 · BLUE · UPCOMING" over "NOW · ON FIELD #9" — Practice 9 was on the field.
        val nexus = assertNotNull(cache.nexusEvent)
        val beforeQuals = nexus.copy(
            matches = nexus.matches.map { m ->
                when {
                    m.label.startsWith("Practice") -> m.copy(status = "On field")
                    else -> m.copy(status = "Queuing soon")
                }
            },
        )
        val s = assertNotNull(LiveSnapshots.build(cache.copy(nexusEvent = beforeQuals), config, qm36, SNAP_NOW))
        assertNull(s.onFieldNumber)
        assertNull(s.matchesAway)
        // Only the event's schedule delay is left on the line: no "NOW", no "ON FIELD #9".
        assertEquals(ScheduleDelay.text(assertNotNull(s.scheduleDelay)), LiveNotification.text(s))
    }

    @Test
    fun `null for an unknown match or an unconfigured team`() {
        assertNull(LiveSnapshots.build(cache, config, "nope", SNAP_NOW))
        assertNull(LiveSnapshots.build(cache, UserConfig(), qm36, SNAP_NOW))
    }
}
