package com.pitwatch.core.model

import com.pitwatch.core.LA
import com.pitwatch.core.localInstant
import com.pitwatch.core.testEvent
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventTest {
    private val event = testEvent(startDate = "2026-04-09", endDate = "2026-04-12", timezone = "America/Los_Angeles")

    @Test
    fun `active just after local midnight on the first day`() {
        assertTrue(event.isActive(localInstant("2026-04-09T00:30:00", LA)))
    }

    @Test
    fun `active on the evening of the last day in the event's own zone`() {
        // 8 PM PDT on Apr 12 is 03:00 UTC Apr 13 — iOS (UTC days) wrongly reports inactive here.
        assertTrue(event.isActive(localInstant("2026-04-12T20:00:00", LA)))
    }

    @Test
    fun `inactive after local midnight following the last day`() {
        assertFalse(event.isActive(localInstant("2026-04-13T00:30:00", LA)))
    }

    @Test
    fun `inactive before the first day`() {
        assertFalse(event.isActive(localInstant("2026-04-08T23:30:00", LA)))
    }

    @Test
    fun `falls back to UTC days when timezone is missing or invalid`() {
        val noZone = testEvent(timezone = null)
        assertTrue(noZone.isActive(localInstant("2026-04-12T23:30:00", ZoneOffset.UTC)))
        assertFalse(noZone.isActive(localInstant("2026-04-13T00:30:00", ZoneOffset.UTC)))
        val badZone = testEvent(timezone = "Not/AZone")
        assertTrue(badZone.isActive(localInstant("2026-04-12T23:30:00", ZoneOffset.UTC)))
    }
}
