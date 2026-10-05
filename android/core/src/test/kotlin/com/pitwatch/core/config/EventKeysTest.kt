package com.pitwatch.core.config

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventKeysTest {
    @Test
    fun `accepts TBA event keys`() {
        assertTrue(EventKeys.isValid("2026cancmp"))
        assertTrue(EventKeys.isValid("2026cmptx"))
    }

    @Test
    fun `rejects keys that would break a URL path or are malformed`() {
        for (bad in listOf("", "cancmp", "2026", "2026 ca", "2026ca?x=1#f", "2026CANCMP", "2026ca/x")) {
            assertFalse(EventKeys.isValid(bad), bad)
        }
    }
}
