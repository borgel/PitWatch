package com.pitwatch.core

import kotlin.test.Test
import kotlin.test.assertTrue

class FixturesTest {
    @Test
    fun `curated iOS fixtures are on the test classpath`() {
        assertTrue(fixture("matches.json").contains("2026miket_qm32"))
    }

    @Test
    fun `raw API captures are on the test classpath`() {
        assertTrue(fixture("2026cancmp/2026-04-10T22-05-28Z/tba_event.json").contains("2026cancmp"))
    }
}
