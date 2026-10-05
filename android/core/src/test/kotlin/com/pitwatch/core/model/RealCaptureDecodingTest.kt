package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Review focus #2: real payloads carry year-specific score breakdowns and many fields we don't model. */
class RealCaptureDecodingTest {
    @ParameterizedTest
    @ValueSource(strings = ["2026-04-10T22-05-28Z", "2026-04-11T00-51-22Z"])
    fun `every captured payload decodes`(snapshot: String) {
        val dir = "2026cancmp/$snapshot"
        assertEquals("2026cancmp", PitWatchJson.decodeFromString<Event>(fixture("$dir/tba_event.json")).key)
        assertTrue(PitWatchJson.decodeFromString<List<Match>>(fixture("$dir/tba_matches.json")).isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<EventRankings>(fixture("$dir/tba_rankings.json")).rankings.isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<EventOPRs>(fixture("$dir/tba_oprs.json")).oprs.isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<List<Team>>(fixture("$dir/tba_teams.json")).isNotEmpty())
        val nexus = PitWatchJson.decodeFromString<NexusEvent>(fixture("$dir/nexus_event.json"))
        assertTrue(nexus.matches.isNotEmpty())
        assertTrue(nexus.matches.any { it.breakAfter != null })
        assertTrue(PitWatchJson.decodeFromString<PitMap>(fixture("$dir/nexus_map.json")).pits.isNotEmpty())
    }
}
