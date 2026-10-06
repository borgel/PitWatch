package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import java.io.File
import java.util.stream.Stream
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/** Real payloads carry year-specific score breakdowns and many fields we don't model. */
class RealCaptureDecodingTest {
    @ParameterizedTest
    @MethodSource("snapshots")
    fun `every captured payload decodes`(dir: String) {
        val eventKey = dir.substringBefore('/')
        assertEquals(eventKey, PitWatchJson.decodeFromString<Event>(fixture("$dir/tba_event.json")).key)
        assertTrue(PitWatchJson.decodeFromString<List<Match>>(fixture("$dir/tba_matches.json")).isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<EventRankings>(fixture("$dir/tba_rankings.json")).rankings.isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<EventOPRs>(fixture("$dir/tba_oprs.json")).oprs.isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<List<Team>>(fixture("$dir/tba_teams.json")).isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<NexusEvent>(fixture("$dir/nexus_event.json")).matches.isNotEmpty())
        assertTrue(PitWatchJson.decodeFromString<PitMap>(fixture("$dir/nexus_map.json")).pits.isNotEmpty())
    }

    companion object {
        /** Every `<event>/<timestamp>/` capture under scripts/fixtures, so new captures are tested automatically. */
        @JvmStatic
        fun snapshots(): Stream<String> {
            val root = File(requireNotNull(RealCaptureDecodingTest::class.java.getResource("/2026cancmp")).toURI()).parentFile
            val dirs = root.listFiles().orEmpty().filter { it.isDirectory }
                .flatMap { event -> event.listFiles().orEmpty().filter { File(it, "nexus_event.json").exists() }.map { "${event.name}/${it.name}" } }
                .sorted()
            check(dirs.size >= 2) { "expected the captured snapshots, found $dirs" }
            return dirs.stream()
        }
    }
}
