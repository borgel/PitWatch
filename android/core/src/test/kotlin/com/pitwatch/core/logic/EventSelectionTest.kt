package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.localInstant
import com.pitwatch.core.testEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EventSelectionTest {
    private val past = testEvent(key = "2026past", startDate = "2026-03-01", endDate = "2026-03-03")
    private val active = testEvent(key = "2026now", startDate = "2026-04-09", endDate = "2026-04-12")
    private val soon = testEvent(key = "2026soon", startDate = "2026-04-20", endDate = "2026-04-22")
    private val later = testEvent(key = "2026late", startDate = "2026-05-01", endDate = "2026-05-03")
    private val now = localInstant("2026-04-10T12:00:00", LA)

    @Test
    fun `prefers the active event`() {
        assertEquals("2026now", EventSelection.autoDetect(listOf(later, past, active, soon), now)?.key)
    }

    @Test
    fun `otherwise the soonest upcoming event`() {
        assertEquals("2026soon", EventSelection.autoDetect(listOf(later, past, soon), now)?.key)
    }

    @Test
    fun `otherwise the most recently ended event`() {
        val older = testEvent(key = "2026old", startDate = "2026-02-01", endDate = "2026-02-03")
        assertEquals("2026past", EventSelection.autoDetect(listOf(older, past), now)?.key)
    }

    @Test
    fun `null when there are no events`() {
        assertNull(EventSelection.autoDetect(emptyList(), now))
    }
}
