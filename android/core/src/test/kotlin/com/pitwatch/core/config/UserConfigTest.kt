package com.pitwatch.core.config

import com.pitwatch.core.PitWatchJson
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UserConfigTest {
    @Test
    fun `defaults`() {
        val config = UserConfig()
        assertNull(config.teamNumber)
        assertNull(config.apiKey)
        assertNull(config.eventKeyOverride)
        assertFalse(config.useScheduledTime)
        assertEquals(0, config.queueOffsetMinutes)
        assertEquals(LiveActivityMode.NEAR_MATCH, config.liveActivityMode)
    }

    @Test
    fun `round trip`() {
        val config = UserConfig(
            teamNumber = 1234, apiKey = "test-key", useScheduledTime = true,
            queueOffsetMinutes = 20, liveActivityMode = LiveActivityMode.ALL_DAY,
        )
        assertEquals(config, PitWatchJson.decodeFromString<UserConfig>(PitWatchJson.encodeToString(config)))
    }

    @Test
    fun `enums use the iOS raw values on the wire`() {
        val json = PitWatchJson.encodeToString(
            UserConfig(liveActivityMode = LiveActivityMode.ALL_DAY, timeSource = TimeSource.NEXUS),
        )
        assertTrue("\"allDay\"" in json, json)
        assertTrue("\"nexus\"" in json, json)
    }

    @Test
    fun `saved config records settings even when they equal today's defaults`() {
        // Otherwise a future change of default would silently override a user's explicit choice.
        val json = PitWatchJson.encodeToString(UserConfig(teamNumber = 1, liveActivityMode = LiveActivityMode.NEAR_MATCH))
        assertTrue("\"liveActivityMode\":\"nearMatch\"" in json, json)
        assertTrue("\"queueOffsetMinutes\":0" in json, json)
    }

    @Test
    fun `is configured needs team and non-empty key`() {
        assertFalse(UserConfig().isConfigured)
        assertFalse(UserConfig(teamNumber = 1234).isConfigured)
        assertFalse(UserConfig(teamNumber = 1234, apiKey = "").isConfigured)
        assertTrue(UserConfig(teamNumber = 1234, apiKey = "key").isConfigured)
    }

    @Test
    fun `team key`() {
        assertNull(UserConfig().teamKey)
        assertEquals("frc1234", UserConfig(teamNumber = 1234).teamKey)
    }

    @Test
    fun `queue offset`() {
        assertEquals(Duration.ZERO, UserConfig().queueOffset)
        assertEquals(Duration.ofSeconds(1200), UserConfig(queueOffsetMinutes = 20).queueOffset)
    }

    @Test
    fun `nexus key config`() {
        assertNull(UserConfig().nexusApiKey)
        assertFalse(UserConfig().isNexusConfigured)
        assertTrue(UserConfig(nexusApiKey = "test-nexus-key").isNexusConfigured)
    }

    @Test
    fun `effective time source`() {
        assertEquals(TimeSource.TBA, UserConfig().effectiveTimeSource)
        assertEquals(TimeSource.NEXUS, UserConfig(nexusApiKey = "k").effectiveTimeSource)
        assertEquals(TimeSource.TBA, UserConfig(nexusApiKey = "k", timeSource = TimeSource.TBA).effectiveTimeSource)
    }
}
