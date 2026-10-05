# Android `:core` Port Implementation Plan (Plan 1 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port `ios/TBAKit` (models, API clients, schedule/phase logic) to a pure-Kotlin/JVM Gradle module `android/core`, with the Swift test suite ported to JUnit 5 and asserting against the same fixture files.

**Architecture:** A standalone Gradle build in `android/` with one JVM module, `:core`. No Android dependencies, so tests run in seconds with `./gradlew :core:test`. Plan 2 adds the `:app` Android module (data layer + live notification); Plan 3 adds screens and widgets. Test resources point directly at the iOS fixture directory and the raw API captures, so there is exactly one copy of every fixture.

**Tech Stack:** Kotlin 2.4.0 (JVM toolchain 21), Gradle 9.7.1 wrapper, kotlinx.serialization-json, Ktor client (core only; tests use `ktor-client-mock`), kotlinx-coroutines, JUnit 5 via `kotlin.test`.

**Spec:** `docs/superpowers/specs/2026-10-05-android-app-design.md`

## Global Constraints

- `:core` is Kotlin/JVM only — **no Android dependencies**.
- Every time-dependent function takes `now: Instant` as a parameter — **no hidden clock reads** (`Instant.now()` is allowed only in tests).
- Field names, optionality, and JSON decoding behavior match the Swift models, including **dropping `null` Nexus team slots**. TBA timestamps are Unix **seconds**; Nexus timestamps are Unix **milliseconds**.
- Unknown JSON keys are ignored (Swift `Codable` behavior).
- UI concerns stay out of `:core`: no colors (Swift `Phase.color`, `MatchAlliance.dotColor`, `MatchesAwayDisplay.color` are **not** ported here).
- `UserConfig.effectiveTimeSource`: explicit choice, else Nexus if a Nexus key is configured, else TBA.
- Fixtures are **never copied**: curated fixtures live in `ios/TBAKit/Tests/TBAKitTests/Fixtures/`, raw captures in `scripts/fixtures/`; both are wired in as test resource dirs.
- Package root: `com.pitwatch.core`. Use `java.time.Instant` / `Duration` / `ZoneId` for time.

## Review Focus

1. **Coroutine cancellation swallowed by Nexus "silent degradation".** A catch-all `catch (e: Exception)` also catches `CancellationException`; Plan 2's foreground service would then be unable to stop an in-flight poll. Expected: cancellation propagates. → test in Task 5.
2. **Real-world payloads.** The Swift suite only decodes tiny synthetic fixtures; real TBA matches carry year-specific `score_breakdown` objects and many extra fields, and real Nexus events carry extra fields (`breakAfter`, `scheduledStartTime`, …). Expected: every file in both captured snapshots decodes. → test in Task 3.
3. **Last day of an event, in the evening, west of UTC.** iOS computes "active" in UTC days, so a Pacific-time event stops being "active" around 5 PM local on its final day and auto-detect jumps to another event. Expected: an event is active through local midnight of its end date in the event's own `timezone` (UTC if absent). **Deliberate divergence from iOS.** → tests in Task 2.
4. **Nexus labels with irregular whitespace** (`"Qualification  32"`, trailing space). Swift's `split` drops empty pieces, so these still correlate on iOS; a naive Kotlin `split(" ")` would not. Expected: still correlates. → test in Task 6.
5. **Nexus returns garbage with a 200** (HTML error page, truncated JSON). Expected: `null` (silent degradation), never a crash. → test in Task 5.

---

## File Structure

```
.gitignore                                          (modify: Android/Gradle ignores)
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/libs.versions.toml
├── gradlew, gradlew.bat, gradle/wrapper/*          (generated)
└── core/
    ├── build.gradle.kts
    └── src/
        ├── main/kotlin/com/pitwatch/core/
        │   ├── PitWatchJson.kt                     shared Json config
        │   ├── api/Endpoints.kt                    TBA path builders
        │   ├── api/FetchResult.kt                  Data | NotModified
        │   ├── api/TbaClient.kt                    TBA client, If-Modified-Since
        │   ├── api/NexusClient.kt                  Nexus client, silent degradation
        │   ├── model/Match.kt                      Match, Alliance, Video
        │   ├── model/Event.kt                      Event (+ zone-aware isActive)
        │   ├── model/Team.kt
        │   ├── model/Ranking.kt                    EventRankings, Ranking, WLTRecord, SortOrderInfo
        │   ├── model/EventOPRs.kt
        │   ├── model/Nexus.kt                      NexusEvent, NexusMatch, NexusMatchTimes, PhaseTime
        │   ├── model/NullDroppingStringListSerializer.kt
        │   ├── model/PitMap.kt
        │   ├── model/Phase.kt                      Phase, MatchAlliance, MatchesAwayDisplay
        │   ├── config/UserConfig.kt                UserConfig, LiveActivityMode, TimeSource
        │   ├── logic/EventSelection.kt             auto-detect active/next event
        │   ├── logic/NexusMatchMerge.kt
        │   ├── logic/PhaseDerivation.kt
        │   ├── logic/ScheduleBreakDetector.kt      ScheduleBreak + detector
        │   ├── logic/MatchSchedule.kt              MatchSchedule, UpcomingScheduleItem
        │   ├── store/EventCache.kt                 EventCache, RefreshState
        │   └── logic/ChangeDetector.kt
        └── test/kotlin/com/pitwatch/core/
            ├── TestSupport.kt                      fixture(), localInstant(), testMatch(), testEvent()
            ├── FixturesTest.kt
            ├── api/EndpointsTest.kt
            ├── api/TbaClientTest.kt
            ├── api/NexusClientTest.kt
            ├── model/ModelDecodingTest.kt
            ├── model/EventTest.kt
            ├── model/NexusModelDecodingTest.kt
            ├── model/RealCaptureDecodingTest.kt
            ├── model/PhaseTest.kt
            ├── config/UserConfigTest.kt
            ├── logic/EventSelectionTest.kt
            ├── logic/NexusMatchMergeTest.kt
            ├── logic/PhaseDerivationTest.kt
            ├── logic/ScheduleBreakDetectorTest.kt
            ├── logic/MatchScheduleTest.kt
            ├── logic/UpcomingTimelineTest.kt
            └── logic/ChangeDetectorTest.kt
```

All Gradle commands below are run from the repo root as `(cd android && ./gradlew …)`.

---

### Task 1: Gradle build, `:core` module, fixture wiring

**Files:**
- Modify: `.gitignore`
- Create: `android/settings.gradle.kts`, `android/build.gradle.kts`, `android/gradle.properties`, `android/gradle/libs.versions.toml`, `android/core/build.gradle.kts`
- Create: `android/core/src/main/kotlin/com/pitwatch/core/api/Endpoints.kt`
- Test: `android/core/src/test/kotlin/com/pitwatch/core/TestSupport.kt`, `.../FixturesTest.kt`, `.../api/EndpointsTest.kt`

**Interfaces:**
- Produces: `object Endpoints` (paths below); test helpers `fixture(path: String): String`, `LA: ZoneId`, `localInstant(iso: String, zone: ZoneId): Instant`, `Instant.unixMs: Long`.

- [ ] **Step 1: Append Android ignores to `.gitignore`**

```gitignore

# Android / Gradle
android/.gradle/
android/**/build/
android/build/
android/local.properties
android/.kotlin/
android/.idea/
```

- [ ] **Step 2: Create the build files**

`android/settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

plugins {
    // Auto-provisions the JDK 21 toolchain; the machine's default JDK (26) is newer than Kotlin targets.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PitWatch"
include(":core")
```

`android/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

`android/gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
kotlin.code.style=official
```

`android/gradle/libs.versions.toml`:
```toml
[versions]
kotlin = "2.4.0"
kotlinx-serialization = "1.9.0"
kotlinx-coroutines = "1.10.2"
ktor = "3.2.3"
junit = "5.13.4"

[libraries]
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "kotlinx-coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-mock = { module = "io.ktor:ktor-client-mock", version.ref = "ktor" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

`android/core/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.serialization.json)
    api(libs.ktor.client.core)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(kotlin("test"))
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}

// One copy of every fixture: curated test fixtures live with the Swift package,
// raw API captures live in scripts/. Both suites read the same files.
sourceSets.test {
    resources.srcDir(rootProject.file("../ios/TBAKit/Tests/TBAKitTests/Fixtures"))
    resources.srcDir(rootProject.file("../scripts/fixtures"))
}

tasks.test {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Generate the Gradle wrapper**

Run: `(cd android && gradle wrapper --gradle-version 9.7.1)`
Expected: `BUILD SUCCESSFUL`; `android/gradlew` and `android/gradle/wrapper/gradle-wrapper.properties` exist. If dependency resolution later fails for any version in `libs.versions.toml`, replace it with the newest release listed on Maven Central for that artifact — do not downgrade Kotlin below 2.4.0.

- [ ] **Step 4: Write the failing tests**

`android/core/src/test/kotlin/com/pitwatch/core/TestSupport.kt`:
```kotlin
package com.pitwatch.core

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** Reads a fixture from the shared fixture dirs wired into test resources (see core/build.gradle.kts). */
fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/$path")) { "Missing fixture: $path" }.readText()

val LA: ZoneId = ZoneId.of("America/Los_Angeles")

fun localInstant(iso: String, zone: ZoneId): Instant = LocalDateTime.parse(iso).atZone(zone).toInstant()

val Instant.unixMs: Long get() = toEpochMilli()
```

`android/core/src/test/kotlin/com/pitwatch/core/FixturesTest.kt`:
```kotlin
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
```

`android/core/src/test/kotlin/com/pitwatch/core/api/EndpointsTest.kt`:
```kotlin
package com.pitwatch.core.api

import kotlin.test.Test
import kotlin.test.assertEquals

class EndpointsTest {
    @Test
    fun `endpoint paths`() {
        assertEquals("/team/frc1234", Endpoints.team(1234))
        assertEquals("/team/frc1234/events/2026", Endpoints.teamEvents(1234, 2026))
        assertEquals("/event/2026miket", Endpoints.event("2026miket"))
        assertEquals("/event/2026miket/matches", Endpoints.eventMatches("2026miket"))
        assertEquals("/event/2026miket/rankings", Endpoints.eventRankings("2026miket"))
        assertEquals("/event/2026miket/oprs", Endpoints.eventOprs("2026miket"))
        assertEquals("/event/2026miket/teams", Endpoints.eventTeams("2026miket"))
        assertEquals("/match/2026miket_qm32", Endpoints.match("2026miket_qm32"))
        assertEquals("/status", Endpoints.STATUS)
    }
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test)`
Expected: FAIL — compilation error `Unresolved reference 'Endpoints'`.

- [ ] **Step 6: Implement `Endpoints`**

`android/core/src/main/kotlin/com/pitwatch/core/api/Endpoints.kt`:
```kotlin
package com.pitwatch.core.api

/** TBA API v3 paths, relative to the base URL. */
object Endpoints {
    fun team(number: Int) = "/team/frc$number"
    fun teamEvents(number: Int, year: Int) = "/team/frc$number/events/$year"
    fun event(key: String) = "/event/$key"
    fun eventMatches(key: String) = "/event/$key/matches"
    fun eventRankings(key: String) = "/event/$key/rankings"
    fun eventOprs(key: String) = "/event/$key/oprs"
    fun eventTeams(key: String) = "/event/$key/teams"
    fun match(key: String) = "/match/$key"
    const val STATUS = "/status"
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test)`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

- [ ] **Step 8: Commit**

```bash
git add .gitignore android/
git commit -m "build(android): scaffold Gradle build and :core module with shared fixtures"
```

---

### Task 2: TBA models, zone-aware `Event.isActive`, event auto-detect

**Files:**
- Create: `android/core/src/main/kotlin/com/pitwatch/core/PitWatchJson.kt`
- Create: `.../model/Match.kt`, `.../model/Event.kt`, `.../model/Team.kt`, `.../model/Ranking.kt`, `.../model/EventOPRs.kt`
- Create: `.../logic/EventSelection.kt`
- Modify: `android/core/src/test/kotlin/com/pitwatch/core/TestSupport.kt` (add `testMatch`, `testEvent`)
- Test: `.../model/ModelDecodingTest.kt`, `.../model/EventTest.kt`, `.../logic/EventSelectionTest.kt`

**Interfaces:**
- Consumes: `fixture()`, `LA`, `localInstant()` from Task 1.
- Produces:
  - `val PitWatchJson: Json`
  - `data class Match(key, compLevel, setNumber, matchNumber, eventKey, time: Long?, predictedTime: Long?, actualTime: Long?, alliances: Map<String, Alliance>, winningAlliance: String, scoreBreakdown: JsonObject?, videos: List<Video>)` with `label`, `shortLabel`, `isPlayed`, `sortOrder`, `matchDate(useScheduled: Boolean = true): Instant?`, `allianceColor(teamKey: String): String?`
  - `data class Alliance(score: Int, teamKeys: List<String>, surrogateTeamKeys, dqTeamKeys)`, `data class Video(type, key)`
  - `data class Event(...)` with `zone: ZoneId`, `startInstant: Instant?`, `endInstant: Instant?`, `isActive(now: Instant): Boolean`
  - `data class Team(...)`, `data class EventRankings(rankings, sortOrderInfo)`, `data class Ranking(teamKey, rank, record: WLTRecord?, qualAverage: Double?, matchesPlayed, dq, sortOrders)`, `data class WLTRecord(wins, losses, ties)` with `display`, `data class SortOrderInfo(name, precision)`, `data class EventOPRs(oprs, dprs, ccwms)` with `summedOpr(teamKeys: List<String>): Double?`
  - `object EventSelection { fun autoDetect(events: List<Event>, now: Instant): Event? }`
  - Test helpers `testMatch(...)`, `testEvent(...)`

- [ ] **Step 1: Add test helpers to `TestSupport.kt`** (append)

```kotlin
fun testMatch(
    number: Int,
    compLevel: String = "qm",
    setNumber: Int = 1,
    time: Long? = 1_712_000_000,
    predictedTime: Long? = null,
    actualTime: Long? = null,
    red: List<String> = listOf("frc1234", "frc5678", "frc9012"),
    blue: List<String> = listOf("frc3456", "frc7890", "frc1111"),
    redScore: Int = -1,
    blueScore: Int = -1,
    eventKey: String = "2026test",
): com.pitwatch.core.model.Match = com.pitwatch.core.model.Match(
    key = "${eventKey}_$compLevel$number",
    compLevel = compLevel,
    setNumber = setNumber,
    matchNumber = number,
    eventKey = eventKey,
    time = time,
    predictedTime = predictedTime,
    actualTime = actualTime,
    alliances = mapOf(
        "red" to com.pitwatch.core.model.Alliance(score = redScore, teamKeys = red),
        "blue" to com.pitwatch.core.model.Alliance(score = blueScore, teamKeys = blue),
    ),
    winningAlliance = "",
    videos = emptyList(),
)

fun testEvent(
    key: String = "2026cancmp",
    startDate: String = "2026-04-09",
    endDate: String = "2026-04-12",
    timezone: String? = "America/Los_Angeles",
): com.pitwatch.core.model.Event = com.pitwatch.core.model.Event(
    key = key,
    name = "Test Event $key",
    eventCode = key.drop(4),
    eventType = 2,
    startDate = startDate,
    endDate = endDate,
    year = startDate.take(4).toInt(),
    timezone = timezone,
)
```

- [ ] **Step 2: Write the failing tests**

`.../model/ModelDecodingTest.kt`:
```kotlin
package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.testMatch
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelDecodingTest {
    @Test
    fun `decode matches`() {
        val matches = PitWatchJson.decodeFromString<List<Match>>(fixture("matches.json"))
        assertEquals(2, matches.size)

        val upcoming = matches[0]
        assertEquals("2026miket_qm32", upcoming.key)
        assertEquals("qm", upcoming.compLevel)
        assertEquals(32, upcoming.matchNumber)
        assertEquals(1712000000L, upcoming.time)
        assertEquals(1712000600L, upcoming.predictedTime)
        assertNull(upcoming.actualTime)
        assertEquals(listOf("frc1234", "frc5678", "frc9012"), upcoming.alliances["red"]?.teamKeys)
        assertEquals(-1, upcoming.alliances["red"]?.score)
        assertEquals("", upcoming.winningAlliance)

        val played = matches[1]
        assertEquals(1711996550L, played.actualTime)
        assertEquals(87, played.alliances["red"]?.score)
        assertEquals("red", played.winningAlliance)
    }

    @Test
    fun `decode rankings`() {
        val rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("rankings.json"))
        assertEquals(1, rankings.rankings.size)
        assertEquals(3, rankings.rankings[0].rank)
        assertEquals(5, rankings.rankings[0].record?.wins)
        assertEquals(2, rankings.rankings[0].record?.losses)
    }

    @Test
    fun `decode OPRs`() {
        val oprs = PitWatchJson.decodeFromString<EventOPRs>(fixture("oprs.json"))
        assertEquals(45.2, oprs.oprs["frc1234"])
        assertEquals(30.5, oprs.dprs["frc1234"])
        assertEquals(14.7, oprs.ccwms["frc1234"])
    }

    @Test
    fun `decodes event timezone`() {
        val json = """
            {"key":"2026cancmp","name":"Test Event","event_code":"cancmp","event_type":2,
             "city":"Daly City","state_prov":"CA","country":"USA",
             "start_date":"2026-04-09","end_date":"2026-04-12","year":2026,
             "short_name":"California Northern","event_type_string":"District Championship",
             "week":5,"location_name":"Cow Palace","timezone":"America/Los_Angeles"}
        """.trimIndent()
        assertEquals("America/Los_Angeles", PitWatchJson.decodeFromString<Event>(json).timezone)
    }

    @Test
    fun `decodes event without timezone`() {
        val json = """
            {"key":"2020test","name":"Test","event_code":"test","event_type":0,
             "city":null,"state_prov":null,"country":null,
             "start_date":"2020-01-01","end_date":"2020-01-02","year":2020,
             "short_name":null,"event_type_string":null,"week":null,"location_name":null}
        """.trimIndent()
        assertNull(PitWatchJson.decodeFromString<Event>(json).timezone)
    }

    @Test
    fun `unknown keys are ignored`() {
        val json = """
            {"key":"2026x_qm1","comp_level":"qm","set_number":1,"match_number":1,"event_key":"2026x",
             "time":null,"predicted_time":null,"actual_time":null,"post_result_time":123,
             "alliances":{"red":{"score":-1,"team_keys":[],"surrogate_team_keys":[],"dq_team_keys":[]}},
             "winning_alliance":"","score_breakdown":{"red":{"autoPoints":3}},"videos":[],"brand_new_field":true}
        """.trimIndent()
        assertEquals("2026x_qm1", PitWatchJson.decodeFromString<Match>(json).key)
    }

    @Test
    fun `labels, played state, and sort order`() {
        assertEquals("Qual 32", testMatch(32).label)
        assertEquals("Q32", testMatch(32).shortLabel)
        assertEquals("QF 2-1", testMatch(1, compLevel = "qf", setNumber = 2).label)
        assertEquals("SF1-3", testMatch(3, compLevel = "sf", setNumber = 1).shortLabel)
        assertEquals("F1", testMatch(1, compLevel = "f").shortLabel)

        assertFalse(testMatch(1).isPlayed)
        assertFalse(testMatch(1, actualTime = 5).isPlayed) // actual time but no score yet
        assertTrue(testMatch(1, actualTime = 5, redScore = 0, blueScore = 10).isPlayed)

        assertTrue(testMatch(99).sortOrder < testMatch(1, compLevel = "qf", setNumber = 1).sortOrder)
        assertEquals("red", testMatch(1).allianceColor("frc1234"))
        assertNull(testMatch(1).allianceColor("frc9999"))
    }

    @Test
    fun `matchDate prefers actual, then predicted, then scheduled`() {
        assertEquals(Instant.ofEpochSecond(30), testMatch(1, time = 10, predictedTime = 20, actualTime = 30).matchDate())
        assertEquals(Instant.ofEpochSecond(20), testMatch(1, time = 10, predictedTime = 20).matchDate())
        assertEquals(Instant.ofEpochSecond(10), testMatch(1, time = 10).matchDate())
        assertNull(testMatch(1, time = 10).matchDate(useScheduled = false))
    }

    @Test
    fun `summed OPR is null when any team is missing`() {
        val oprs = EventOPRs(oprs = mapOf("frc1" to 10.0, "frc2" to 5.5), dprs = emptyMap(), ccwms = emptyMap())
        assertEquals(15.5, oprs.summedOpr(listOf("frc1", "frc2")))
        assertNull(oprs.summedOpr(listOf("frc1", "frc3")))
    }
}
```

`.../model/EventTest.kt`:
```kotlin
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
```

`.../logic/EventSelectionTest.kt`:
```kotlin
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test)`
Expected: FAIL — compilation errors (`Unresolved reference 'Match'`, `'PitWatchJson'`, `'EventSelection'` …).

- [ ] **Step 4: Implement**

`android/core/src/main/kotlin/com/pitwatch/core/PitWatchJson.kt`:
```kotlin
package com.pitwatch.core

import kotlinx.serialization.json.Json

/** Shared JSON config. Mirrors Swift Codable: unknown keys ignored, absent optionals decode as null. */
val PitWatchJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
```

`.../model/Match.kt`:
```kotlin
package com.pitwatch.core.model

import java.time.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** An FRC match from The Blue Alliance API v3. Times are Unix seconds. */
@Serializable
data class Match(
    val key: String,
    @SerialName("comp_level") val compLevel: String,
    @SerialName("set_number") val setNumber: Int,
    @SerialName("match_number") val matchNumber: Int,
    @SerialName("event_key") val eventKey: String,
    val time: Long? = null,
    @SerialName("predicted_time") val predictedTime: Long? = null,
    @SerialName("actual_time") val actualTime: Long? = null,
    val alliances: Map<String, Alliance>,
    @SerialName("winning_alliance") val winningAlliance: String,
    @SerialName("score_breakdown") val scoreBreakdown: JsonObject? = null,
    val videos: List<Video> = emptyList(),
) {
    /** e.g. "Qual 32", "QF 2-1", "SF 1-3", "Final 1". */
    val label: String
        get() = when (compLevel) {
            "qm" -> "Qual $matchNumber"
            "qf" -> "QF $setNumber-$matchNumber"
            "sf" -> "SF $setNumber-$matchNumber"
            "f" -> "Final $matchNumber"
            else -> "${compLevel.uppercase()} $matchNumber"
        }

    /** e.g. "Q32", "QF2-1", "SF1-3", "F1". */
    val shortLabel: String
        get() = when (compLevel) {
            "qm" -> "Q$matchNumber"
            "qf" -> "QF$setNumber-$matchNumber"
            "sf" -> "SF$setNumber-$matchNumber"
            "f" -> "F$matchNumber"
            else -> "${compLevel.uppercase()}$matchNumber"
        }

    /** Played = has an actual time and at least one non-negative score. */
    val isPlayed: Boolean
        get() = actualTime != null && alliances.values.any { it.score >= 0 }

    /** Chronological sort key: comp level, then set, then match number. */
    val sortOrder: Int
        get() {
            val level = when (compLevel) {
                "qm" -> 0
                "ef" -> 1
                "qf" -> 2
                "sf" -> 3
                "f" -> 4
                else -> 5
            }
            return level * 1_000_000 + setNumber * 1_000 + matchNumber
        }

    /** Actual, then predicted, then (if [useScheduled]) scheduled time. */
    fun matchDate(useScheduled: Boolean = true): Instant? =
        actualTime?.let(Instant::ofEpochSecond)
            ?: predictedTime?.let(Instant::ofEpochSecond)
            ?: if (useScheduled) time?.let(Instant::ofEpochSecond) else null

    /** "red", "blue", or null. */
    fun allianceColor(teamKey: String): String? =
        alliances.entries.firstOrNull { teamKey in it.value.teamKeys }?.key
}

@Serializable
data class Alliance(
    val score: Int,
    @SerialName("team_keys") val teamKeys: List<String>,
    @SerialName("surrogate_team_keys") val surrogateTeamKeys: List<String> = emptyList(),
    @SerialName("dq_team_keys") val dqTeamKeys: List<String> = emptyList(),
)

@Serializable
data class Video(val type: String, val key: String)
```

`.../model/Event.kt`:
```kotlin
package com.pitwatch.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** An FRC event from The Blue Alliance API v3. */
@Serializable
data class Event(
    val key: String,
    val name: String,
    @SerialName("event_code") val eventCode: String,
    @SerialName("event_type") val eventType: Int,
    val city: String? = null,
    @SerialName("state_prov") val stateProv: String? = null,
    val country: String? = null,
    @SerialName("start_date") val startDate: String,
    @SerialName("end_date") val endDate: String,
    val year: Int,
    @SerialName("short_name") val shortName: String? = null,
    @SerialName("event_type_string") val eventTypeString: String? = null,
    val week: Int? = null,
    @SerialName("location_name") val locationName: String? = null,
    val timezone: String? = null,
) {
    /** The event's local zone; UTC when TBA omits it or sends an unknown id. */
    val zone: ZoneId
        get() = timezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC

    /** Local midnight at the start of [startDate]. */
    val startInstant: Instant?
        get() = parse(startDate)?.atStartOfDay(zone)?.toInstant()

    /** Local midnight at the start of [endDate]. */
    val endInstant: Instant?
        get() = parse(endDate)?.atStartOfDay(zone)?.toInstant()

    /**
     * True from local midnight of [startDate] until local midnight after [endDate], in the event's own
     * zone. Deliberately differs from iOS, which used UTC days and so ended west-coast events around
     * 5 PM local on their final day.
     */
    fun isActive(now: Instant): Boolean {
        val start = startInstant ?: return false
        val endExclusive = parse(endDate)?.plusDays(1)?.atStartOfDay(zone)?.toInstant() ?: return false
        return now >= start && now < endExclusive
    }

    private fun parse(date: String): LocalDate? = runCatching { LocalDate.parse(date) }.getOrNull()
}
```

`.../model/Team.kt`:
```kotlin
package com.pitwatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Team(
    val key: String,
    @SerialName("team_number") val teamNumber: Int,
    val name: String? = null,
    val nickname: String? = null,
    val city: String? = null,
    @SerialName("state_prov") val stateProv: String? = null,
    val country: String? = null,
    val website: String? = null,
    @SerialName("rookie_year") val rookieYear: Int? = null,
)
```

`.../model/Ranking.kt`:
```kotlin
package com.pitwatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EventRankings(
    val rankings: List<Ranking>,
    @SerialName("sort_order_info") val sortOrderInfo: List<SortOrderInfo> = emptyList(),
)

@Serializable
data class Ranking(
    @SerialName("team_key") val teamKey: String,
    val rank: Int,
    val record: WLTRecord? = null,
    @SerialName("qual_average") val qualAverage: Double? = null,
    @SerialName("matches_played") val matchesPlayed: Int,
    val dq: Int = 0,
    @SerialName("sort_orders") val sortOrders: List<Double>? = null,
)

@Serializable
data class WLTRecord(val wins: Int, val losses: Int, val ties: Int) {
    /** e.g. "5-2-0". */
    val display: String get() = "$wins-$losses-$ties"
}

@Serializable
data class SortOrderInfo(val name: String, val precision: Int)
```

`.../model/EventOPRs.kt`:
```kotlin
package com.pitwatch.core.model

import kotlinx.serialization.Serializable

@Serializable
data class EventOPRs(
    val oprs: Map<String, Double>,
    val dprs: Map<String, Double>,
    val ccwms: Map<String, Double>,
) {
    /** Sum of OPRs for [teamKeys]; null if any team is missing (early in an event). */
    fun summedOpr(teamKeys: List<String>): Double? {
        var total = 0.0
        for (key in teamKeys) total += oprs[key] ?: return null
        return total
    }
}
```

`.../logic/EventSelection.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.Event
import java.time.Instant

/** Port of iOS BackgroundRefresh.autoDetectEvent. */
object EventSelection {
    /** Active event, else soonest upcoming, else most recently ended; null only for an empty list. */
    fun autoDetect(events: List<Event>, now: Instant): Event? {
        events.firstOrNull { it.isActive(now) }?.let { return it }
        events.filter { (it.startInstant ?: Instant.MIN) > now }
            .minByOrNull { it.startInstant ?: Instant.MAX }
            ?.let { return it }
        return events.maxByOrNull { it.endInstant ?: Instant.MIN }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test)`
Expected: `BUILD SUCCESSFUL`; all tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "feat(core): port TBA models, zone-aware Event.isActive, event auto-detect"
```

---

### Task 3: Nexus models, PitMap, real-capture decoding

**Files:**
- Create: `.../model/NullDroppingStringListSerializer.kt`, `.../model/Nexus.kt`, `.../model/PitMap.kt`
- Test: `.../model/NexusModelDecodingTest.kt`, `.../model/RealCaptureDecodingTest.kt`

**Interfaces:**
- Consumes: `PitWatchJson`, all TBA models (Task 2).
- Produces:
  - `data class NexusEvent(dataAsOfTime: Long, nowQueuing: String?, matches: List<NexusMatch>)`
  - `data class NexusMatch(label: String, status: String? = null, redTeams: List<String> = emptyList(), blueTeams: List<String> = emptyList(), times: NexusMatchTimes = NexusMatchTimes(), replayOf: String? = null, breakAfter: String? = null)`
  - `data class NexusMatchTimes(estimatedQueueTime: Long? = null, estimatedOnDeckTime: Long? = null, estimatedOnFieldTime: Long? = null, estimatedStartTime: Long? = null, actualQueueTime: Long? = null)` with `queueDate`, `onDeckDate`, `onFieldDate`, `startDate`, `actualQueueDate: Instant?` and `nextPhaseDate(after: Instant): PhaseTime?`
  - `data class PhaseTime(label: String, date: Instant)`
  - `data class PitMap(...)` with nested `MapSize`, `Position`, `Pit`, `Area`, `MapLabel`, `Arrow`, `Wall`, `AssignedPit(address, pit)` and `pit(forTeam: String): AssignedPit?`

- [ ] **Step 1: Write the failing tests**

`.../model/NexusModelDecodingTest.kt`:
```kotlin
package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NexusModelDecodingTest {
    @Test
    fun `decode nexus event`() {
        val event = PitWatchJson.decodeFromString<NexusEvent>(fixture("nexus_event.json"))
        assertEquals(1712000000000L, event.dataAsOfTime)
        assertEquals("Qualification 33", event.nowQueuing)
        assertEquals(3, event.matches.size)

        val match = event.matches[0]
        assertEquals("Qualification 32", match.label)
        assertEquals("On deck", match.status)
        assertEquals(listOf("1234", "5678", "9012"), match.redTeams)
        assertEquals(listOf("3456", "7890", "1111"), match.blueTeams)
        assertEquals(1711999200000L, match.times.estimatedQueueTime)
        assertEquals(1711999500000L, match.times.estimatedOnDeckTime)
        assertEquals(1711999800000L, match.times.estimatedOnFieldTime)
        assertEquals(1712000000000L, match.times.estimatedStartTime)
        assertEquals(1711999250000L, match.times.actualQueueTime)

        val noTimes = event.matches[2]
        assertNull(noTimes.times.estimatedQueueTime)
        assertNull(noTimes.times.estimatedStartTime)
        assertNull(noTimes.replayOf)
    }

    @Test
    fun `null team slots are dropped`() {
        val json = """
            {"dataAsOfTime":1712000000000,"matches":[{"label":"Practice 1","status":"Queuing soon",
             "redTeams":["1234",null,"9012"],"blueTeams":["3456","7890",null],"times":{}}]}
        """.trimIndent()
        val event = PitWatchJson.decodeFromString<NexusEvent>(json)
        assertEquals(1, event.matches.size)
        assertEquals(listOf("1234", "9012"), event.matches[0].redTeams)
        assertEquals(listOf("3456", "7890"), event.matches[0].blueTeams)
    }

    @Test
    fun `times convert from unix millis`() {
        val times = NexusMatchTimes(
            estimatedQueueTime = 1712000000000,
            estimatedOnDeckTime = 1712000300000,
            estimatedOnFieldTime = 1712000600000,
            estimatedStartTime = 1712000900000,
        )
        assertEquals(Instant.ofEpochSecond(1712000000), times.queueDate)
        assertEquals(Instant.ofEpochSecond(1712000900), times.startDate)
    }

    @Test
    fun `nextPhaseDate returns first future phase`() {
        val times = NexusMatchTimes(1712000000000, 1712000300000, 1712000600000, 1712000900000)
        val result = times.nextPhaseDate(after = Instant.ofEpochSecond(1712000400))
        assertEquals("On Field", result?.label)
        assertEquals(Instant.ofEpochSecond(1712000600), result?.date)
    }

    @Test
    fun `nextPhaseDate is null when all phases are past`() {
        val times = NexusMatchTimes(1712000000000, 1712000300000, 1712000600000, 1712000900000)
        assertNull(times.nextPhaseDate(after = Instant.ofEpochSecond(1712001000)))
    }

    @Test
    fun `pit lookup by team number`() {
        val map = PitWatchJson.decodeFromString<PitMap>(fixture("2026cancmp/2026-04-11T00-51-22Z/nexus_map.json"))
        val found = map.pit(forTeam = "3598")
        assertEquals("A1", found?.address)
        assertNull(map.pit(forTeam = "0"))
    }
}
```

`.../model/RealCaptureDecodingTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test)`
Expected: FAIL — `Unresolved reference 'NexusEvent'`, `'PitMap'`.

- [ ] **Step 3: Implement**

`.../model/NullDroppingStringListSerializer.kt`:
```kotlin
package com.pitwatch.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Nexus sends `null` for unassigned team slots (practice matches, dropped teams).
 * Drop them so one null doesn't fail the whole event decode.
 */
object NullDroppingStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer().nullable)
    override val descriptor = delegate.descriptor
    override fun deserialize(decoder: Decoder): List<String> = delegate.deserialize(decoder).filterNotNull()
    override fun serialize(encoder: Encoder, value: List<String>) = delegate.serialize(encoder, value)
}
```

`.../model/Nexus.kt`:
```kotlin
package com.pitwatch.core.model

import java.time.Instant
import kotlinx.serialization.Serializable

/** FRC Nexus `GET /event/{eventKey}`. Timestamps are Unix milliseconds. */
@Serializable
data class NexusEvent(
    val dataAsOfTime: Long,
    val nowQueuing: String? = null,
    val matches: List<NexusMatch>,
)

@Serializable
data class NexusMatch(
    val label: String,
    val status: String? = null,
    @Serializable(with = NullDroppingStringListSerializer::class) val redTeams: List<String> = emptyList(),
    @Serializable(with = NullDroppingStringListSerializer::class) val blueTeams: List<String> = emptyList(),
    val times: NexusMatchTimes = NexusMatchTimes(),
    val replayOf: String? = null,
    /** Nexus's explicit break marker after this match, e.g. "Lunch", "End of day". Not yet used by logic. */
    val breakAfter: String? = null,
)

@Serializable
data class NexusMatchTimes(
    val estimatedQueueTime: Long? = null,
    val estimatedOnDeckTime: Long? = null,
    val estimatedOnFieldTime: Long? = null,
    val estimatedStartTime: Long? = null,
    val actualQueueTime: Long? = null,
) {
    val queueDate: Instant? get() = estimatedQueueTime?.let(Instant::ofEpochMilli)
    val onDeckDate: Instant? get() = estimatedOnDeckTime?.let(Instant::ofEpochMilli)
    val onFieldDate: Instant? get() = estimatedOnFieldTime?.let(Instant::ofEpochMilli)
    val startDate: Instant? get() = estimatedStartTime?.let(Instant::ofEpochMilli)
    val actualQueueDate: Instant? get() = actualQueueTime?.let(Instant::ofEpochMilli)

    /** First phase strictly after [after], in order queue → on deck → on field → start. */
    fun nextPhaseDate(after: Instant): PhaseTime? =
        listOf("Queue" to queueDate, "On Deck" to onDeckDate, "On Field" to onFieldDate, "Start" to startDate)
            .firstOrNull { (_, date) -> date != null && date > after }
            ?.let { (label, date) -> PhaseTime(label, date!!) }
}

data class PhaseTime(val label: String, val date: Instant)
```

`.../model/PitMap.kt`:
```kotlin
package com.pitwatch.core.model

import kotlinx.serialization.Serializable

/** FRC Nexus `GET /event/{eventKey}/map`. */
@Serializable
data class PitMap(
    val size: MapSize,
    val pits: Map<String, Pit>,
    val areas: Map<String, Area>? = null,
    val labels: Map<String, MapLabel>? = null,
    val arrows: Map<String, Arrow>? = null,
    val walls: Map<String, Wall>? = null,
) {
    @Serializable data class MapSize(val x: Double, val y: Double)
    @Serializable data class Position(val x: Double, val y: Double)
    @Serializable data class Pit(val position: Position, val size: MapSize, val team: String? = null)
    @Serializable data class Area(val label: String, val position: Position, val size: MapSize)
    @Serializable data class MapLabel(val label: String, val position: Position, val size: MapSize)
    @Serializable data class Arrow(val position: Position, val size: MapSize, val type: String? = null, val angle: Double? = null)
    @Serializable data class Wall(val position: Position, val size: MapSize)

    data class AssignedPit(val address: String, val pit: Pit)

    /** The pit assigned to [forTeam] (bare team number, e.g. "1700"). */
    fun pit(forTeam: String): AssignedPit? =
        pits.entries.firstOrNull { it.value.team == forTeam }?.let { AssignedPit(it.key, it.value) }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test)`
Expected: `BUILD SUCCESSFUL`. If `every captured payload decodes` fails, the failure names the field: make that model field nullable or defaulted (never delete the assertion).

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port Nexus and PitMap models; decode real API captures"
```

---

### Task 4: Phase, MatchAlliance, MatchesAwayDisplay, UserConfig

**Files:**
- Create: `.../model/Phase.kt`, `.../config/UserConfig.kt`
- Test: `.../model/PhaseTest.kt`, `.../config/UserConfigTest.kt`

**Interfaces:**
- Consumes: `PitWatchJson`.
- Produces:
  - `enum class Phase { PRE_QUEUE, QUEUEING, ON_DECK, ON_FIELD }` with `stateLabel`, `targetLabel`, `glyph`, `nextPhaseProse: String?`
  - `enum class MatchAlliance { BLUE, RED }` (serial names `blue`/`red`) with `displayName`
  - `object MatchesAwayDisplay { fun text(gap: Int): String }`
  - `data class UserConfig(teamNumber: Int? = null, apiKey: String? = null, eventKeyOverride: String? = null, useScheduledTime: Boolean = false, queueOffsetMinutes: Int = 0, liveActivityMode: LiveActivityMode = NEAR_MATCH, nexusApiKey: String? = null, timeSource: TimeSource? = null)` with `effectiveTimeSource`, `isConfigured`, `isNexusConfigured`, `teamKey: String?`, `queueOffset: Duration`
  - `enum class LiveActivityMode { NEAR_MATCH, ALL_DAY }` (serial `nearMatch`/`allDay`), `enum class TimeSource { NEXUS, TBA }` (serial `nexus`/`tba`)

- [ ] **Step 1: Write the failing tests**

`.../model/PhaseTest.kt`:
```kotlin
package com.pitwatch.core.model

import com.pitwatch.core.PitWatchJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhaseTest {
    @Test
    fun `phases are ordered`() {
        assertEquals(listOf(Phase.PRE_QUEUE, Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD), Phase.entries)
    }

    @Test
    fun `state labels`() {
        assertEquals(listOf("UPCOMING", "IN QUEUE", "ON DECK", "ON FIELD"), Phase.entries.map { it.stateLabel })
    }

    @Test
    fun `target labels`() {
        assertEquals(
            listOf("QUEUE STARTS", "MOVE TO DECK", "MOVE TO FIELD", "MATCH ENDS"),
            Phase.entries.map { it.targetLabel },
        )
    }

    @Test
    fun `glyphs are distinct single characters`() {
        assertEquals(listOf("U", "Q", "D", "F"), Phase.entries.map { it.glyph })
    }

    @Test
    fun `next phase prose is null only for on field`() {
        assertEquals("queue", Phase.PRE_QUEUE.nextPhaseProse)
        assertEquals("on deck", Phase.QUEUEING.nextPhaseProse)
        assertEquals("on field", Phase.ON_DECK.nextPhaseProse)
        assertNull(Phase.ON_FIELD.nextPhaseProse)
    }

    @Test
    fun `phase round-trips through JSON`() {
        assertEquals(Phase.ON_DECK, PitWatchJson.decodeFromString<Phase>(PitWatchJson.encodeToString(Phase.ON_DECK)))
    }

    @Test
    fun `alliance display names and JSON names`() {
        assertEquals("BLUE", MatchAlliance.BLUE.displayName)
        assertEquals("RED", MatchAlliance.RED.displayName)
        assertEquals("\"red\"", PitWatchJson.encodeToString(MatchAlliance.RED))
    }

    @Test
    fun `matches away text`() {
        assertEquals("5 AWAY", MatchesAwayDisplay.text(5))
        assertEquals("2 AWAY", MatchesAwayDisplay.text(2))
        assertEquals("NEXT", MatchesAwayDisplay.text(1))
        assertEquals("NOW", MatchesAwayDisplay.text(0))
        assertEquals("NOW", MatchesAwayDisplay.text(-1))
    }
}
```

`.../config/UserConfigTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test)`
Expected: FAIL — `Unresolved reference 'Phase'`, `'UserConfig'`.

- [ ] **Step 3: Implement**

`.../model/Phase.kt`:
```kotlin
package com.pitwatch.core.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Where the tracked team is in the queue → field pipeline. Colors live in the app layer. */
@Serializable
enum class Phase {
    PRE_QUEUE, QUEUEING, ON_DECK, ON_FIELD;

    /** What is happening right now. */
    val stateLabel: String
        get() = when (this) {
            PRE_QUEUE -> "UPCOMING"
            QUEUEING -> "IN QUEUE"
            ON_DECK -> "ON DECK"
            ON_FIELD -> "ON FIELD"
        }

    /** What happens when the countdown hits zero. */
    val targetLabel: String
        get() = when (this) {
            PRE_QUEUE -> "QUEUE STARTS"
            QUEUEING -> "MOVE TO DECK"
            ON_DECK -> "MOVE TO FIELD"
            ON_FIELD -> "MATCH ENDS"
        }

    /** Single-letter glyph for compact surfaces (status-bar chip). */
    val glyph: String
        get() = when (this) {
            PRE_QUEUE -> "U"
            QUEUEING -> "Q"
            ON_DECK -> "D"
            ON_FIELD -> "F"
        }

    /** Lowercase name of the next phase, for "to on deck" subtitles; null for ON_FIELD. */
    val nextPhaseProse: String?
        get() = when (this) {
            PRE_QUEUE -> "queue"
            QUEUEING -> "on deck"
            ON_DECK -> "on field"
            ON_FIELD -> null
        }
}

@Serializable
enum class MatchAlliance {
    @SerialName("blue") BLUE,
    @SerialName("red") RED;

    val displayName: String get() = name
}

object MatchesAwayDisplay {
    fun text(gap: Int): String = when {
        gap <= 0 -> "NOW"
        gap == 1 -> "NEXT"
        else -> "$gap AWAY"
    }
}
```

`.../config/UserConfig.kt`:
```kotlin
package com.pitwatch.core.config

import java.time.Duration
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserConfig(
    val teamNumber: Int? = null,
    val apiKey: String? = null,
    val eventKeyOverride: String? = null,
    val useScheduledTime: Boolean = false,
    val queueOffsetMinutes: Int = 0,
    val liveActivityMode: LiveActivityMode = LiveActivityMode.NEAR_MATCH,
    val nexusApiKey: String? = null,
    val timeSource: TimeSource? = null,
) {
    /** Explicit choice, else Nexus if a Nexus key is configured, else TBA. */
    val effectiveTimeSource: TimeSource
        get() = timeSource ?: if (isNexusConfigured) TimeSource.NEXUS else TimeSource.TBA

    val isConfigured: Boolean get() = teamNumber != null && !apiKey.isNullOrEmpty()
    val isNexusConfigured: Boolean get() = !nexusApiKey.isNullOrEmpty()
    val teamKey: String? get() = teamNumber?.let { "frc$it" }
    val queueOffset: Duration get() = Duration.ofMinutes(queueOffsetMinutes.toLong())
}

@Serializable
enum class LiveActivityMode {
    @SerialName("nearMatch") NEAR_MATCH,
    @SerialName("allDay") ALL_DAY,
}

@Serializable
enum class TimeSource {
    @SerialName("nexus") NEXUS,
    @SerialName("tba") TBA,
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test)`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port Phase, MatchAlliance, MatchesAwayDisplay, UserConfig"
```

---

### Task 5: TBA and Nexus API clients

**Files:**
- Create: `.../api/FetchResult.kt`, `.../api/TbaClient.kt`, `.../api/NexusClient.kt`
- Test: `.../api/TbaClientTest.kt`, `.../api/NexusClientTest.kt`

**Interfaces:**
- Consumes: `Endpoints`, `PitWatchJson`, `Team`, `NexusEvent`, `PitMap`.
- Produces:
  - `sealed interface FetchResult<out T> { data class Data<T>(val value: T, val lastModified: String?); data object NotModified }`
  - `class TbaException(val statusCode: Int, message: String) : Exception`
  - `class TbaClient(apiKey: String, httpClient: HttpClient, baseUrl: String = TbaClient.DEFAULT_BASE_URL)` with `suspend fun <T> fetch(deserializer: DeserializationStrategy<T>, path: String, lastModified: String? = null): FetchResult<T>`, `suspend inline fun <reified T> fetch(path: String, lastModified: String? = null): FetchResult<T>`, `suspend fun validateTeam(number: Int): Team`
  - `class NexusClient(apiKey: String, httpClient: HttpClient, baseUrl: String = NexusClient.DEFAULT_BASE_URL)` with `suspend fun fetchEventStatus(eventKey: String): NexusEvent?`, `suspend fun fetchPitMap(eventKey: String): PitMap?`
  - The HTTP engine is supplied by the caller (Plan 2 uses OkHttp); `:core` depends on `ktor-client-core` only.

- [ ] **Step 1: Write the failing tests**

`.../api/TbaClientTest.kt`:
```kotlin
package com.pitwatch.core.api

import com.pitwatch.core.model.Team
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class TbaClientTest {
    private val teamJson = """{"key":"frc1234","team_number":1234,"nickname":"Bots"}"""
    private var captured: HttpRequestData? = null

    private fun client(status: HttpStatusCode, body: String = "", lastModified: String? = null): TbaClient {
        val engine = MockEngine { request ->
            captured = request
            val headers = if (lastModified != null) headersOf(HttpHeaders.LastModified, lastModified) else headersOf()
            respond(body, status, headers)
        }
        return TbaClient("test-key-123", HttpClient(engine), "https://example.com/api/v3")
    }

    @Test
    fun `sends auth, user agent, and If-Modified-Since to the joined URL`() = runTest {
        client(HttpStatusCode.OK, teamJson).fetch<Team>("/team/frc1234", lastModified = "Mon, 01 Jan 2026 00:00:00 GMT")
        val request = captured!!
        assertEquals("https://example.com/api/v3/team/frc1234", request.url.toString())
        assertEquals("test-key-123", request.headers["X-TBA-Auth-Key"])
        assertEquals("PitWatch", request.headers[HttpHeaders.UserAgent])
        assertEquals("Mon, 01 Jan 2026 00:00:00 GMT", request.headers[HttpHeaders.IfModifiedSince])
    }

    @Test
    fun `200 decodes and returns Last-Modified`() = runTest {
        val result = client(HttpStatusCode.OK, teamJson, lastModified = "LM").fetch<Team>("/team/frc1234")
        val data = result as? FetchResult.Data ?: error("expected Data, got $result")
        assertEquals(1234, data.value.teamNumber)
        assertEquals("LM", data.lastModified)
    }

    @Test
    fun `304 is NotModified`() = runTest {
        assertEquals(FetchResult.NotModified, client(HttpStatusCode.NotModified).fetch<Team>("/team/frc1234", "LM"))
    }

    @Test
    fun `other statuses throw with the status code`() = runTest {
        val e = assertFailsWith<TbaException> { client(HttpStatusCode.Unauthorized, "bad key").fetch<Team>("/x") }
        assertEquals(401, e.statusCode)
    }

    @Test
    fun `validateTeam returns the team`() = runTest {
        assertEquals("frc1234", client(HttpStatusCode.OK, teamJson).validateTeam(1234).key)
        assertEquals("https://example.com/api/v3/team/frc1234", captured!!.url.toString())
    }
}
```

`.../api/NexusClientTest.kt`:
```kotlin
package com.pitwatch.core.api

import com.pitwatch.core.fixture
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class NexusClientTest {
    private var captured: HttpRequestData? = null

    private fun client(status: HttpStatusCode, body: String) = NexusClient(
        "nexus-test-key",
        HttpClient(MockEngine { request -> captured = request; respond(body, status) }),
        "https://example.com/api/v1",
    )

    @Test
    fun `sends key and user agent to the event URL`() = runTest {
        client(HttpStatusCode.OK, fixture("nexus_event.json")).fetchEventStatus("2026miket")
        val request = captured!!
        assertEquals("https://example.com/api/v1/event/2026miket", request.url.toString())
        assertEquals("nexus-test-key", request.headers["Nexus-Api-Key"])
        assertEquals("PitWatch", request.headers[HttpHeaders.UserAgent])
    }

    @Test
    fun `decodes event status`() = runTest {
        assertEquals("Qualification 33", client(HttpStatusCode.OK, fixture("nexus_event.json")).fetchEventStatus("e")?.nowQueuing)
    }

    @Test
    fun `fetches the pit map from the map URL`() = runTest {
        val map = client(HttpStatusCode.OK, fixture("2026cancmp/2026-04-11T00-51-22Z/nexus_map.json")).fetchPitMap("2026cancmp")
        assertEquals("https://example.com/api/v1/event/2026cancmp/map", captured!!.url.toString())
        assertEquals("A1", map?.pit(forTeam = "3598")?.address)
    }

    @Test
    fun `non-200 degrades to null`() = runTest {
        assertNull(client(HttpStatusCode.NotFound, "no such event").fetchEventStatus("e"))
    }

    @Test
    fun `garbage body with 200 degrades to null`() = runTest {
        // Review focus #5
        assertNull(client(HttpStatusCode.OK, "<html>502 Bad Gateway</html>").fetchEventStatus("e"))
        assertNull(client(HttpStatusCode.OK, """{"dataAsOfTime": 1, "matches": [""").fetchEventStatus("e"))
    }

    @Test
    fun `cancellation propagates instead of degrading to null`() = runTest {
        // Review focus #1: a catch-all must not swallow CancellationException.
        val nexus = NexusClient("k", HttpClient(MockEngine { awaitCancellation() }), "https://example.com/api/v1")
        var returnedNormally = false
        val job = launch {
            nexus.fetchEventStatus("e")
            returnedNormally = true
        }
        runCurrent()
        job.cancel()
        job.join()
        assertFalse(returnedNormally)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.api.*')`
Expected: FAIL — `Unresolved reference 'TbaClient'`, `'NexusClient'`.

- [ ] **Step 3: Implement**

`.../api/FetchResult.kt`:
```kotlin
package com.pitwatch.core.api

sealed interface FetchResult<out T> {
    data class Data<T>(val value: T, val lastModified: String?) : FetchResult<T>
    data object NotModified : FetchResult<Nothing>
}

class TbaException(val statusCode: Int, message: String) : Exception(message)
```

`.../api/TbaClient.kt`:
```kotlin
package com.pitwatch.core.api

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.Team
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

/** The Blue Alliance API v3. Send the stored Last-Modified as [lastModified] to get [FetchResult.NotModified]. */
class TbaClient(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun <T> fetch(
        deserializer: DeserializationStrategy<T>,
        path: String,
        lastModified: String? = null,
    ): FetchResult<T> {
        val response = httpClient.get(baseUrl.trimEnd('/') + path) {
            header("X-TBA-Auth-Key", apiKey)
            header(HttpHeaders.UserAgent, "PitWatch")
            lastModified?.let { header(HttpHeaders.IfModifiedSince, it) }
        }
        return when (val code = response.status.value) {
            304 -> FetchResult.NotModified
            200 -> FetchResult.Data(
                PitWatchJson.decodeFromString(deserializer, response.bodyAsText()),
                response.headers[HttpHeaders.LastModified],
            )
            else -> throw TbaException(code, "API error $code: ${response.bodyAsText()}")
        }
    }

    suspend inline fun <reified T> fetch(path: String, lastModified: String? = null): FetchResult<T> =
        fetch(serializer<T>(), path, lastModified)

    suspend fun validateTeam(number: Int): Team =
        when (val result = fetch<Team>(Endpoints.team(number))) {
            is FetchResult.Data -> result.value
            FetchResult.NotModified -> throw TbaException(304, "Got 304 on team validation")
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://www.thebluealliance.com/api/v3"
    }
}
```

`.../api/NexusClient.kt`:
```kotlin
package com.pitwatch.core.api

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.PitMap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.DeserializationStrategy

/** FRC Nexus API. Every failure degrades to null so Nexus can never block TBA data. */
class NexusClient(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) {
    suspend fun fetchEventStatus(eventKey: String): NexusEvent? =
        getOrNull("/event/$eventKey", NexusEvent.serializer())

    suspend fun fetchPitMap(eventKey: String): PitMap? =
        getOrNull("/event/$eventKey/map", PitMap.serializer())

    private suspend fun <T> getOrNull(path: String, deserializer: DeserializationStrategy<T>): T? =
        try {
            val response = httpClient.get(baseUrl.trimEnd('/') + path) {
                header("Nexus-Api-Key", apiKey)
                header(HttpHeaders.UserAgent, "PitWatch")
            }
            if (response.status != HttpStatusCode.OK) null
            else PitWatchJson.decodeFromString(deserializer, response.bodyAsText())
        } catch (e: CancellationException) {
            throw e // never swallow cancellation: the live service must be able to stop a poll
        } catch (e: Exception) {
            null
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://frc.nexus/api/v1"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.api.*')`
Expected: `BUILD SUCCESSFUL`. Sanity-check the cancellation test is real: temporarily delete the `catch (e: CancellationException)` clause, re-run, confirm `cancellation propagates…` FAILS, then restore it.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port TBA and Nexus clients on Ktor; never swallow cancellation"
```

---

### Task 6: NexusMatchMerge and PhaseDerivation

**Files:**
- Create: `.../logic/NexusMatchMerge.kt`, `.../logic/PhaseDerivation.kt`
- Test: `.../logic/NexusMatchMergeTest.kt`, `.../logic/PhaseDerivationTest.kt`

**Interfaces:**
- Consumes: `Match`, `NexusEvent`, `NexusMatch`, `NexusMatchTimes`, `Phase`, `testMatch()`.
- Produces:
  - `object NexusMatchMerge { fun nexusInfo(match: Match, nexusEvent: NexusEvent?): NexusMatch? }`
  - `object PhaseDerivation` with `data class Result(phase: Phase, deadline: Instant?, phaseStart: Instant, queueDeadline: Instant?, onDeckDeadline: Instant?, onFieldDeadline: Instant?, matchStartDeadline: Instant?, matchEndDeadline: Instant?)`, `val MATCH_DURATION: Duration` (150 s), `fun derivePhase(nexusMatch: NexusMatch, now: Instant): Result`, `fun phaseFor(match: Match, nexusEvent: NexusEvent?, now: Instant): Phase?`, `fun currentMatchOnField(matches: List<NexusMatch>, fallbackMatchNumber: Int): Int`, `fun extractMatchNumber(label: String): Int?`

- [ ] **Step 1: Write the failing tests**

`.../logic/NexusMatchMergeTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class NexusMatchMergeTest {
    private val red = listOf("1234", "5678", "9012")
    private val blue = listOf("3456", "7890", "1111")

    private fun times(start: Long?) = NexusMatchTimes(
        estimatedQueueTime = start?.minus(800_000),
        estimatedOnDeckTime = start?.minus(500_000),
        estimatedOnFieldTime = start?.minus(200_000),
        estimatedStartTime = start,
    )

    private fun event(vararg matches: NexusMatch) = NexusEvent(dataAsOfTime = 0, matches = matches.toList())

    @Test
    fun `match by qual label`() {
        val nexus = NexusMatch("Qualification 32", "On deck", red, blue, times(1712000000000))
        val result = NexusMatchMerge.nexusInfo(testMatch(32), event(nexus))
        assertEquals("Qualification 32", result?.label)
        assertEquals("On deck", result?.status)
    }

    @Test
    fun `match by playoff label`() {
        val nexus = NexusMatch("Quarterfinal 2-1", null, red, blue, times(null))
        assertEquals("Quarterfinal 2-1", NexusMatchMerge.nexusInfo(testMatch(1, compLevel = "qf", setNumber = 2), event(nexus))?.label)
    }

    @Test
    fun `match by final label`() {
        val nexus = NexusMatch("Final 1", "On field", red, blue, times(1712000000000))
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(1, compLevel = "f"), event(nexus)))
    }

    @Test
    fun `falls back to team composition`() {
        val nexus = NexusMatch("Qualification 100", "Now queuing", red, blue, times(1712000000000))
        assertEquals("Now queuing", NexusMatchMerge.nexusInfo(testMatch(99), event(nexus))?.status)
    }

    @Test
    fun `team fallback accepts swapped alliance colors`() {
        val nexus = NexusMatch("Qualification 100", null, blue, red, times(null))
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(99), event(nexus)))
    }

    @Test
    fun `null when nothing correlates`() {
        val nexus = NexusMatch("Qualification 99", null, listOf("9999", "8888", "7777"), listOf("6666", "5555", "4444"), times(null))
        assertNull(NexusMatchMerge.nexusInfo(testMatch(50), event(nexus)))
    }

    @Test
    fun `null when nexus event is null`() {
        assertNull(NexusMatchMerge.nexusInfo(testMatch(32), null))
    }

    @Test
    fun `labels with irregular whitespace still correlate`() {
        // Review focus #4. Teams differ so only the label can match.
        val other = listOf("1", "2", "3")
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(32), event(NexusMatch("Qualification  32", null, other, other))))
        assertNotNull(NexusMatchMerge.nexusInfo(testMatch(32), event(NexusMatch("Qualification 32 ", null, other, other))))
    }
}
```

`.../logic/PhaseDerivationTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.model.Phase
import com.pitwatch.core.testMatch
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhaseDerivationTest {
    private val ref: Instant = Instant.ofEpochSecond(1000)

    /** Offsets are seconds relative to [ref]. */
    private fun nexus(
        queue: Long? = null, onDeck: Long? = null, onField: Long? = null, start: Long? = null,
        status: String? = null,
    ): NexusMatch {
        fun ms(offset: Long?) = offset?.let { (ref.epochSecond + it) * 1000 }
        return NexusMatch(
            label = "Qualification 1", status = status,
            redTeams = listOf("1", "2", "3"), blueTeams = listOf("4", "5", "6"),
            times = NexusMatchTimes(ms(queue), ms(onDeck), ms(onField), ms(start)),
        )
    }

    @Test
    fun `all times in the future is pre-queue with deadline at queue time`() {
        val n = nexus(300, 600, 900, 1200)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.PRE_QUEUE, result.phase)
        assertEquals(n.times.queueDate, result.deadline)
    }

    @Test
    fun `queue passed is queueing with deadline at on-deck`() {
        val n = nexus(-60, 300, 600, 900)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.QUEUEING, result.phase)
        assertEquals(n.times.onDeckDate, result.deadline)
    }

    @Test
    fun `on-deck passed is on deck with deadline at on-field`() {
        val n = nexus(-300, -60, 300, 600)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.ON_DECK, result.phase)
        assertEquals(n.times.onFieldDate, result.deadline)
    }

    @Test
    fun `on-field passed is on field with deadline at start plus 150s`() {
        val n = nexus(-600, -300, -120, -60)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.ON_FIELD, result.phase)
        assertEquals(n.times.startDate!!.plusSeconds(150), result.deadline)
    }

    @Test
    fun `nexus status On Field overrides times`() {
        assertEquals(Phase.ON_FIELD, PhaseDerivation.derivePhase(nexus(300, 600, 900, 1200, "On Field"), ref).phase)
    }

    @Test
    fun `Queuing soon falls through to time-based`() {
        val n = nexus(300, 600, 900, 1200, "Queuing soon")
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(Phase.PRE_QUEUE, result.phase)
        assertEquals(n.times.queueDate, result.deadline)
    }

    @Test
    fun `On deck soon is not on deck`() {
        assertEquals(Phase.PRE_QUEUE, PhaseDerivation.derivePhase(nexus(300, 600, 900, 1200, "On deck soon"), ref).phase)
    }

    @Test
    fun `result carries match start and end deadlines`() {
        val n = nexus(-600, -300, -60, 30)
        val result = PhaseDerivation.derivePhase(n, ref)
        assertEquals(n.times.startDate, result.matchStartDeadline)
        assertEquals(n.times.startDate!!.plusSeconds(150), result.matchEndDeadline)
    }

    @Test
    fun `no times is pre-queue with no deadline`() {
        val result = PhaseDerivation.derivePhase(NexusMatch("Qualification 1"), ref)
        assertEquals(Phase.PRE_QUEUE, result.phase)
        assertNull(result.deadline)
    }

    @Test
    fun `current match on field comes from statuses`() {
        val onField = NexusMatch("Qualification 42", status = "On Field")
        assertEquals(42, PhaseDerivation.currentMatchOnField(listOf(onField), fallbackMatchNumber = 1))
        assertEquals(7, PhaseDerivation.currentMatchOnField(emptyList(), fallbackMatchNumber = 7))
    }

    private fun correlated(status: String) = NexusEvent(
        dataAsOfTime = 0,
        matches = listOf(
            NexusMatch(
                "Qualification 32", status, listOf("1234", "5678", "9012"), listOf("3456", "7890", "1111"),
                NexusMatchTimes(1712000000000, 1712000300000, 1712000600000, 1712000900000),
            ),
        ),
    )

    @Test
    fun `phaseFor is null without a nexus event`() {
        assertNull(PhaseDerivation.phaseFor(testMatch(32), null, ref))
    }

    @Test
    fun `phaseFor is null when nothing correlates`() {
        val unrelated = NexusEvent(0, matches = listOf(NexusMatch("Qualification 99", null, listOf("9999"), listOf("6666"))))
        assertNull(PhaseDerivation.phaseFor(testMatch(32), unrelated, ref))
    }

    @Test
    fun `phaseFor maps statuses`() {
        assertEquals(Phase.QUEUEING, PhaseDerivation.phaseFor(testMatch(32), correlated("Now queuing"), ref))
        assertEquals(Phase.ON_FIELD, PhaseDerivation.phaseFor(testMatch(32), correlated("On field"), ref))
        assertEquals(Phase.ON_DECK, PhaseDerivation.phaseFor(testMatch(32), correlated("On deck"), ref))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.*')`
Expected: FAIL — `Unresolved reference 'NexusMatchMerge'`, `'PhaseDerivation'`.

- [ ] **Step 3: Implement**

`.../logic/NexusMatchMerge.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch

/** Correlates Nexus matches to TBA matches: normalized label first, then alliance composition. */
object NexusMatchMerge {
    private val WHITESPACE = Regex("\\s+")

    fun nexusInfo(match: Match, nexusEvent: NexusEvent?): NexusMatch? {
        if (nexusEvent == null) return null

        val canonical = "${match.compLevel}-${match.setNumber}-${match.matchNumber}"
        nexusEvent.matches.firstOrNull { parseNexusLabel(it.label) == canonical }?.let { return it }

        val tbaRed = match.alliances["red"]?.teamKeys.orEmpty().map(::stripFrc).toSet()
        val tbaBlue = match.alliances["blue"]?.teamKeys.orEmpty().map(::stripFrc).toSet()
        if (tbaRed.isEmpty()) return null
        return nexusEvent.matches.firstOrNull { nexus ->
            val red = nexus.redTeams.toSet()
            val blue = nexus.blueTeams.toSet()
            (tbaRed == red && tbaBlue == blue) || (tbaRed == blue && tbaBlue == red)
        }
    }

    /** "Qualification 32" → "qm-1-32", "Quarterfinal 2-1" → "qf-2-1". Whitespace-tolerant like Swift's split. */
    private fun parseNexusLabel(label: String): String? {
        val parts = label.trim().split(WHITESPACE, limit = 2)
        if (parts.size != 2) return null
        val level = when (val raw = parts[0].lowercase()) {
            "practice" -> "p"
            "qualification" -> "qm"
            "eighthfinal" -> "ef"
            "quarterfinal" -> "qf"
            "semifinal" -> "sf"
            "final" -> "f"
            else -> raw
        }
        val number = parts[1].trim()
        if ('-' !in number) return "$level-1-$number"
        val nums = number.split('-')
        return if (nums.size == 2) "$level-${nums[0]}-${nums[1]}" else null
    }

    private fun stripFrc(key: String) = key.replace("frc", "")
}
```

`.../logic/PhaseDerivation.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant

object PhaseDerivation {
    val MATCH_DURATION: Duration = Duration.ofSeconds(150)

    data class Result(
        val phase: Phase,
        val deadline: Instant?,
        val phaseStart: Instant,
        val queueDeadline: Instant?,
        val onDeckDeadline: Instant?,
        val onFieldDeadline: Instant?,
        val matchStartDeadline: Instant?,
        val matchEndDeadline: Instant?,
    )

    /**
     * Current phase and countdown deadline. Nexus's discrete status wins over times, except statuses
     * containing "soon" ("Queuing soon", "On deck soon"), which mean the phase hasn't started.
     */
    fun derivePhase(nexusMatch: NexusMatch, now: Instant): Result {
        val times = nexusMatch.times
        val matchEnd = times.startDate?.plus(MATCH_DURATION)

        fun result(phase: Phase, deadline: Instant?, phaseStart: Instant) = Result(
            phase, deadline, phaseStart,
            queueDeadline = times.queueDate,
            onDeckDeadline = times.onDeckDate,
            onFieldDeadline = times.onFieldDate,
            matchStartDeadline = times.startDate,
            matchEndDeadline = matchEnd,
        )

        val status = nexusMatch.status?.lowercase()
        if (status != null && "soon" !in status) {
            if ("field" in status || "playing" in status) {
                return result(Phase.ON_FIELD, matchEnd, times.onFieldDate ?: now)
            }
            if ("deck" in status) {
                return result(Phase.ON_DECK, times.onFieldDate, times.onDeckDate ?: now)
            }
            if ("queu" in status) { // "queuing" or "queue"
                return result(Phase.QUEUEING, times.onDeckDate, times.queueDate ?: now)
            }
        }

        times.onFieldDate?.takeIf { it <= now }?.let { return result(Phase.ON_FIELD, matchEnd, it) }
        times.onDeckDate?.takeIf { it <= now }?.let { return result(Phase.ON_DECK, times.onFieldDate, it) }
        times.queueDate?.takeIf { it <= now }?.let { return result(Phase.QUEUEING, times.onDeckDate, it) }
        return result(Phase.PRE_QUEUE, times.queueDate, now)
    }

    /** Phase of [match] via its correlated Nexus match; null when uncorrelated. */
    fun phaseFor(match: Match, nexusEvent: NexusEvent?, now: Instant): Phase? =
        NexusMatchMerge.nexusInfo(match, nexusEvent)?.let { derivePhase(it, now).phase }

    /** Match number currently on the field, from the last Nexus match whose status says so. */
    fun currentMatchOnField(matches: List<NexusMatch>, fallbackMatchNumber: Int): Int =
        matches.lastOrNull { m ->
            val s = m.status?.lowercase() ?: return@lastOrNull false
            "field" in s || "playing" in s
        }?.let { extractMatchNumber(it.label) } ?: fallbackMatchNumber

    /** "Qualification 42" → 42. */
    fun extractMatchNumber(label: String): Int? =
        label.split(' ').filter { it.isNotEmpty() }.lastOrNull()?.toIntOrNull()
}
```

Note: Swift checks `status.contains("queuing") || status.contains("queue")`; `"queu" in status` is equivalent (both contain "queu") and covers "Now queueing" spelling variants.

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.*')`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port NexusMatchMerge and PhaseDerivation"
```

---

### Task 7: ScheduleBreakDetector

**Files:**
- Create: `.../logic/ScheduleBreakDetector.kt`
- Test: `.../logic/ScheduleBreakDetectorTest.kt`

**Interfaces:**
- Consumes: `NexusMatch`, `NexusMatchTimes`, `PitWatchJson`, `fixture()`, `LA`, `localInstant()`, `unixMs`.
- Produces: `data class ScheduleBreak(kind: Kind, startsAfter: String, endsBefore: String, start: Instant, end: Instant)` with `enum class Kind { LUNCH, SESSION_BREAK, OVERNIGHT }` and `duration: Duration`; `object ScheduleBreakDetector { val DEFAULT_MINIMUM_GAP: Duration; fun detectBreaks(matches: List<NexusMatch>, zone: ZoneId, minimumGap: Duration = DEFAULT_MINIMUM_GAP): List<ScheduleBreak> }`

- [ ] **Step 1: Write the failing tests**

`.../logic/ScheduleBreakDetectorTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.localInstant
import com.pitwatch.core.logic.ScheduleBreak.Kind
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.unixMs
import java.time.Duration
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduleBreakDetectorTest {
    private val utc = ZoneOffset.UTC
    private fun m(label: String, start: Long?) = NexusMatch(label, times = NexusMatchTimes(estimatedStartTime = start))

    @Test
    fun `empty input`() {
        assertTrue(ScheduleBreakDetector.detectBreaks(emptyList(), utc).isEmpty())
    }

    @Test
    fun `single match`() {
        assertTrue(ScheduleBreakDetector.detectBreaks(listOf(m("Q1", 1_700_000_000_000)), utc).isEmpty())
    }

    @Test
    fun `gap below minimum is not a break`() {
        val t0 = 1_700_000_000_000
        assertTrue(ScheduleBreakDetector.detectBreaks(listOf(m("Q1", t0), m("Q2", t0 + 10 * 60_000)), utc).isEmpty())
    }

    @Test
    fun `respects custom minimum gap`() {
        val t0 = 1_700_000_000_000
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(m("Q1", t0), m("Q2", t0 + 15 * 60_000)), utc, minimumGap = Duration.ofMinutes(10),
        )
        assertEquals(1, breaks.size)
    }

    @Test
    fun `afternoon gap is a session break`() {
        val start = localInstant("2026-04-11T15:00:00", LA)
        val end = localInstant("2026-04-11T15:45:00", LA)
        val breaks = ScheduleBreakDetector.detectBreaks(listOf(m("Q1", start.unixMs), m("Q2", end.unixMs)), LA)
        assertEquals(1, breaks.size)
        assertEquals(ScheduleBreak(Kind.SESSION_BREAK, "Q1", "Q2", start, end), breaks.single())
    }

    @Test
    fun `gap straddling local noon is lunch`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q1", localInstant("2026-04-11T11:45:00", LA).unixMs),
                m("Q2", localInstant("2026-04-11T12:45:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals(Kind.LUNCH, breaks.single().kind)
    }

    @Test
    fun `gap crossing local midnight is overnight`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q1", localInstant("2026-04-10T17:30:00", LA).unixMs),
                m("Q2", localInstant("2026-04-11T09:00:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals(Kind.OVERNIGHT, breaks.single().kind)
    }

    @Test
    fun `time zone affects classification`() {
        val matches = listOf(
            m("Q1", localInstant("2026-04-11T11:45:00", LA).unixMs),
            m("Q2", localInstant("2026-04-11T12:45:00", LA).unixMs),
        )
        assertEquals(Kind.LUNCH, ScheduleBreakDetector.detectBreaks(matches, LA).single().kind)
        assertEquals(Kind.SESSION_BREAK, ScheduleBreakDetector.detectBreaks(matches, utc).single().kind)
    }

    @Test
    fun `skips matches without a start time`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q1", localInstant("2026-04-11T15:00:00", LA).unixMs),
                m("Q2", null),
                m("Q3", localInstant("2026-04-11T15:45:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals("Q1" to "Q3", breaks.single().let { it.startsAfter to it.endsBefore })
    }

    @Test
    fun `sorts unordered input`() {
        val breaks = ScheduleBreakDetector.detectBreaks(
            listOf(
                m("Q2", localInstant("2026-04-11T15:45:00", LA).unixMs),
                m("Q1", localInstant("2026-04-11T15:00:00", LA).unixMs),
            ),
            LA,
        )
        assertEquals("Q1" to "Q2", breaks.single().let { it.startsAfter to it.endsBefore })
    }

    @Test
    fun `real cancmp fixture`() {
        val event = PitWatchJson.decodeFromString<NexusEvent>(fixture("nexus_event_2026cancmp.json"))
        val breaks = ScheduleBreakDetector.detectBreaks(event.matches, LA)
        assertEquals(
            listOf(
                Triple("Practice 11", "Qualification 1", Kind.LUNCH),
                Triple("Qualification 38", "Qualification 39", Kind.OVERNIGHT),
                Triple("Qualification 62", "Qualification 63", Kind.LUNCH),
                Triple("Qualification 96", "Qualification 97", Kind.OVERNIGHT),
            ),
            breaks.map { Triple(it.startsAfter, it.endsBefore, it.kind) },
        )
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.ScheduleBreakDetectorTest')`
Expected: FAIL — `Unresolved reference 'ScheduleBreakDetector'`.

- [ ] **Step 3: Implement**

`.../logic/ScheduleBreakDetector.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusMatch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A non-match interval inferred from gaps in Nexus estimated start times. */
data class ScheduleBreak(
    val kind: Kind,
    val startsAfter: String,
    val endsBefore: String,
    val start: Instant,
    val end: Instant,
) {
    enum class Kind {
        /** Same local day, overlapping 11:30–13:00. */
        LUNCH,
        /** Same local day, not overlapping midday. */
        SESSION_BREAK,
        /** Crosses a local-day boundary. */
        OVERNIGHT,
    }

    val duration: Duration get() = Duration.between(start, end)
}

object ScheduleBreakDetector {
    /** Roughly 2.5× a typical FRC cycle. */
    val DEFAULT_MINIMUM_GAP: Duration = Duration.ofMinutes(20)

    /**
     * Gaps of at least [minimumGap] between consecutive estimated start times. [zone] must be the
     * event's local zone: classification asks "did we cross midnight?" and "do we straddle noon?".
     */
    fun detectBreaks(
        matches: List<NexusMatch>,
        zone: ZoneId,
        minimumGap: Duration = DEFAULT_MINIMUM_GAP,
    ): List<ScheduleBreak> {
        val stamped = matches
            .mapNotNull { m -> m.times.startDate?.let { m.label to it } }
            .sortedBy { it.second }
        if (stamped.size < 2) return emptyList()

        return stamped.zipWithNext().mapNotNull { (prev, curr) ->
            if (Duration.between(prev.second, curr.second) < minimumGap) return@mapNotNull null
            val kind = when {
                prev.second.atZone(zone).toLocalDate() != curr.second.atZone(zone).toLocalDate() -> ScheduleBreak.Kind.OVERNIGHT
                straddlesLocalLunch(prev.second, curr.second, zone) -> ScheduleBreak.Kind.LUNCH
                else -> ScheduleBreak.Kind.SESSION_BREAK
            }
            ScheduleBreak(kind, prev.first, curr.first, prev.second, curr.second)
        }
    }

    /** True if [start, end] overlaps 11:30–13:00 on start's local day. */
    private fun straddlesLocalLunch(start: Instant, end: Instant, zone: ZoneId): Boolean {
        val day = start.atZone(zone).toLocalDate()
        val lunchStart = day.atTime(11, 30).atZone(zone).toInstant()
        val lunchEnd = day.atTime(13, 0).atZone(zone).toInstant()
        return start <= lunchEnd && end >= lunchStart
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.ScheduleBreakDetectorTest')`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port ScheduleBreakDetector"
```

---

### Task 8: MatchSchedule (next/last, refresh interval, live-start window, upcoming timeline)

**Files:**
- Create: `.../logic/MatchSchedule.kt`
- Test: `.../logic/MatchScheduleTest.kt`, `.../logic/UpcomingTimelineTest.kt`

**Interfaces:**
- Consumes: `Match`, `NexusEvent`, `NexusMatchMerge`, `ScheduleBreakDetector`, `ScheduleBreak`, `LiveActivityMode`, test helpers.
- Produces:
  - `class MatchSchedule(matches: List<Match>, teamKey: String)` with `allMatches`, `teamMatches`, `upcomingMatches`, `pastMatches` (most recent first), `nextMatch: Match?`, `lastPlayedMatch: Match?`
  - `fun refreshInterval(now: Instant, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Duration`
  - `fun nextReloadTime(now: Instant, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Instant`
  - `fun shouldStartLiveActivity(now: Instant, mode: LiveActivityMode, useScheduledTime: Boolean, hasActiveLiveActivity: Boolean, nexusEvent: NexusEvent? = null): Boolean`
  - `fun upcomingTimeline(nexusEvent: NexusEvent?, zone: ZoneId): List<UpcomingScheduleItem>`
  - `sealed interface UpcomingScheduleItem { val id: String; data class MatchItem(val match: Match); data class BreakItem(val scheduleBreak: ScheduleBreak) }`

- [ ] **Step 1: Write the failing tests**

`.../logic/MatchScheduleTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.fixture
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MatchScheduleTest {
    private val fixtureMatches = PitWatchJson.decodeFromString<List<Match>>(fixture("matches.json"))
    private fun secs(n: Long) = Duration.ofSeconds(n)

    @Test
    fun `next and last match`() {
        val schedule = MatchSchedule(fixtureMatches, "frc1234")
        assertEquals("2026miket_qm32", schedule.nextMatch?.key)
        assertEquals("2026miket_qm31", schedule.lastPlayedMatch?.key)
    }

    @Test
    fun `team matches`() {
        assertEquals(2, MatchSchedule(fixtureMatches, "frc1234").teamMatches.size)
    }

    @Test
    fun `upcoming and past split`() {
        val schedule = MatchSchedule(fixtureMatches, "frc1234")
        assertEquals(listOf("2026miket_qm32"), schedule.upcomingMatches.map { it.key })
        assertEquals(listOf("2026miket_qm31"), schedule.pastMatches.map { it.key })
    }

    @Test
    fun `past matches are most recent first`() {
        val played = (1..3).map { testMatch(it, actualTime = it.toLong(), redScore = 1, blueScore = 0) }
        assertEquals(listOf(3, 2, 1), MatchSchedule(played, "frc1234").pastMatches.map { it.matchNumber })
    }

    @Test
    fun `no matches`() {
        val schedule = MatchSchedule(emptyList(), "frc1234")
        assertNull(schedule.nextMatch)
        assertNull(schedule.lastPlayedMatch)
        assertTrue(schedule.teamMatches.isEmpty())
    }

    @Test
    fun `adaptive refresh interval from TBA time`() {
        val schedule = MatchSchedule(fixtureMatches, "frc1234")
        val matchDate = Instant.ofEpochSecond(1712000000)
        assertEquals(secs(3600), schedule.refreshInterval(matchDate.minusSeconds(10800), useScheduledTime = true))
        assertEquals(secs(1800), schedule.refreshInterval(matchDate.minusSeconds(5400), useScheduledTime = true))
        assertEquals(secs(900), schedule.refreshInterval(matchDate.minusSeconds(1200), useScheduledTime = true))
        assertEquals(secs(600), schedule.refreshInterval(matchDate.plusSeconds(300), useScheduledTime = true))
    }

    @Test
    fun `refresh interval is a day with no upcoming match`() {
        assertEquals(Duration.ofDays(1), MatchSchedule(emptyList(), "frc1234").refreshInterval(Instant.EPOCH, true))
    }

    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)

    private fun nexusFor(queue: Instant?, start: Instant?) = NexusEvent(
        dataAsOfTime = 0,
        matches = listOf(
            NexusMatch(
                "Qualification 32", null, listOf("1234", "5678", "9012"), listOf("3456", "7890", "1111"),
                NexusMatchTimes(estimatedQueueTime = queue?.toEpochMilli(), estimatedStartTime = start?.toEpochMilli()),
            ),
        ),
    )

    @Test
    fun `refresh interval tightens with nexus times`() {
        val matchTime = now.plusSeconds(3600)
        val schedule = MatchSchedule(listOf(testMatch(32, time = matchTime.epochSecond)), "frc1234")
        val interval = schedule.refreshInterval(now, useScheduledTime = false, nexusEvent = nexusFor(now.plusSeconds(1200), matchTime))
        assertTrue(interval <= secs(600), "was $interval")
    }

    @Test
    fun `refresh interval falls back without nexus`() {
        val schedule = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(3600).epochSecond)), "frc1234")
        assertEquals(secs(1800), schedule.refreshInterval(now, useScheduledTime = true))
    }

    @Test
    fun `next reload time adds the interval`() {
        val schedule = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(3600).epochSecond)), "frc1234")
        assertEquals(now.plusSeconds(1800), schedule.nextReloadTime(now, useScheduledTime = true))
    }

    private fun startCheck(
        matchIn: Long, mode: LiveActivityMode, active: Boolean = false, nexus: NexusEvent? = null,
    ) = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(matchIn).epochSecond)), "frc1234")
        .shouldStartLiveActivity(now, mode, useScheduledTime = true, hasActiveLiveActivity = active, nexusEvent = nexus)

    @Test
    fun `near match mode starts within two hours of the TBA time`() {
        assertTrue(startCheck(3600, LiveActivityMode.NEAR_MATCH))
        assertFalse(startCheck(3 * 3600, LiveActivityMode.NEAR_MATCH))
        assertFalse(startCheck(-60, LiveActivityMode.NEAR_MATCH))
    }

    @Test
    fun `all day mode also starts for a match already past its TBA time`() {
        assertTrue(startCheck(-60, LiveActivityMode.ALL_DAY))
        assertFalse(startCheck(3 * 3600, LiveActivityMode.ALL_DAY))
    }

    @Test
    fun `never starts while one is active or with nothing upcoming`() {
        assertFalse(startCheck(3600, LiveActivityMode.NEAR_MATCH, active = true))
        assertFalse(
            MatchSchedule(emptyList(), "frc1234")
                .shouldStartLiveActivity(now, LiveActivityMode.ALL_DAY, true, false),
        )
    }

    @Test
    fun `nexus queue time drives the start window when available`() {
        // TBA says 5 h away, Nexus queue is in 1 h → start.
        assertTrue(startCheck(5 * 3600, LiveActivityMode.NEAR_MATCH, nexus = nexusFor(now.plusSeconds(3600), null)))
        // Queue 10 min ago is still within the 15-min grace.
        assertTrue(startCheck(5 * 3600, LiveActivityMode.NEAR_MATCH, nexus = nexusFor(now.minusSeconds(600), null)))
        // Queue 20 min ago is past the grace.
        assertFalse(startCheck(5 * 3600, LiveActivityMode.NEAR_MATCH, nexus = nexusFor(now.minusSeconds(1200), null)))
    }
}
```

`.../logic/UpcomingTimelineTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.localInstant
import com.pitwatch.core.logic.ScheduleBreak.Kind
import com.pitwatch.core.logic.UpcomingScheduleItem.BreakItem
import com.pitwatch.core.logic.UpcomingScheduleItem.MatchItem
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import com.pitwatch.core.unixMs
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpcomingTimelineTest {
    private val utc = ZoneOffset.UTC
    private fun nm(label: String, start: Long?) = NexusMatch(label, times = NexusMatchTimes(estimatedStartTime = start))
    private fun nexus(vararg matches: NexusMatch) = NexusEvent(dataAsOfTime = 0, matches = matches.toList())
    private fun local(iso: String) = localInstant(iso, LA).unixMs
    private fun List<UpcomingScheduleItem>.matchKeys() = filterIsInstance<MatchItem>().map { it.match.key }
    private fun List<UpcomingScheduleItem>.breaks() = filterIsInstance<BreakItem>().map { it.scheduleBreak }

    @Test
    fun `no nexus returns matches only`() {
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(20)), "frc1234").upcomingTimeline(null, utc)
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `empty upcoming returns empty`() {
        assertTrue(MatchSchedule(emptyList(), "frc1234").upcomingTimeline(nexus(), utc).isEmpty())
    }

    @Test
    fun `single upcoming returns single match`() {
        val timeline = MatchSchedule(listOf(testMatch(10)), "frc1234")
            .upcomingTimeline(nexus(nm("Qualification 10", 1_700_000_000_000)), utc)
        assertEquals(listOf("2026test_qm10"), timeline.matchKeys())
        assertEquals(1, timeline.size)
    }

    @Test
    fun `inserts break between bracketing matches`() {
        val q10 = local("2026-04-11T11:00:00")
        val q20 = local("2026-04-11T13:00:00")
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(20)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 10", q10),
                nm("Qualification 15", q10 + 10 * 60_000),
                nm("Qualification 16", q20 - 10 * 60_000),
                nm("Qualification 20", q20),
            ),
            LA,
        )
        assertEquals(3, timeline.size)
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        val middle = assertIs<BreakItem>(timeline[1]).scheduleBreak
        assertEquals(Kind.LUNCH, middle.kind)
        assertEquals("Qualification 15", middle.startsAfter)
        assertEquals("Qualification 16", middle.endsBefore)
    }

    @Test
    fun `drops breaks before the first upcoming match`() {
        val timeline = MatchSchedule(listOf(testMatch(50), testMatch(60)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 45", local("2026-04-11T11:00:00")),
                nm("Qualification 46", local("2026-04-11T12:30:00")),
                nm("Qualification 50", local("2026-04-11T15:00:00")),
                nm("Qualification 60", local("2026-04-11T15:10:00")),
            ),
            LA,
        )
        assertEquals(2, timeline.size)
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `drops breaks after the last upcoming match`() {
        val timeline = MatchSchedule(listOf(testMatch(5), testMatch(20)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 5", local("2026-04-11T11:00:00")),
                nm("Qualification 10", local("2026-04-11T11:10:00")),
                nm("Qualification 15", local("2026-04-11T11:20:00")),
                nm("Qualification 20", local("2026-04-11T11:30:00")),
                nm("Qualification 21", local("2026-04-11T11:40:00")),
                nm("Qualification 22", local("2026-04-11T12:30:00")),
            ),
            LA,
        )
        assertEquals(listOf("2026test_qm5", "2026test_qm20"), timeline.matchKeys())
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `multiple breaks in one pair are chronological`() {
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(50)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 10", local("2026-04-10T11:00:00")),
                nm("Qualification 11", local("2026-04-10T12:45:00")),
                nm("Qualification 12", local("2026-04-11T12:55:00")),
                nm("Qualification 50", local("2026-04-11T13:05:00")),
            ),
            LA,
        )
        assertEquals(4, timeline.size)
        assertEquals(listOf(Kind.LUNCH, Kind.OVERNIGHT), timeline.breaks().map { it.kind })
    }

    @Test
    fun `upcoming match with no known time skips bracketing`() {
        val timeline = MatchSchedule(listOf(testMatch(10, time = null), testMatch(20)), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 15", local("2026-04-11T11:30:00")),
                nm("Qualification 16", local("2026-04-11T12:45:00")),
                nm("Qualification 20", local("2026-04-11T13:00:00")),
            ),
            LA,
        )
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        assertTrue(timeline.breaks().isEmpty())
    }

    @Test
    fun `bracketing uses nexus-correlated times over TBA times`() {
        val q10 = testMatch(10, time = localInstant("2026-04-11T10:00:00", LA).epochSecond)
        val q20 = testMatch(20, time = localInstant("2026-04-11T12:30:00", LA).epochSecond)
        val timeline = MatchSchedule(listOf(q10, q20), "frc1234").upcomingTimeline(
            nexus(
                nm("Qualification 10", local("2026-04-11T14:00:00")),
                nm("Qualification 15", local("2026-04-11T15:00:00")),
                nm("Qualification 16", local("2026-04-11T15:30:00")),
                nm("Qualification 20", local("2026-04-11T15:40:00")),
            ),
            LA,
        )
        assertEquals(listOf("2026test_qm10", "2026test_qm20"), timeline.matchKeys())
        assertEquals(2, timeline.breaks().size)
    }

    @Test
    fun `real cancmp fixture brackets lunch between Q58 and Q70`() {
        val event = PitWatchJson.decodeFromString<NexusEvent>(fixture("nexus_event_2026cancmp.json"))
        val timeline = MatchSchedule(listOf(testMatch(58, time = null), testMatch(70, time = null)), "frc1234")
            .upcomingTimeline(event, LA)
        assertEquals(listOf("2026test_qm58", "2026test_qm70"), timeline.matchKeys())
        val lunch = assertIs<BreakItem>(timeline[1]).scheduleBreak
        assertEquals(Kind.LUNCH, lunch.kind)
        assertEquals("Qualification 62", lunch.startsAfter)
        assertEquals("Qualification 63", lunch.endsBefore)
    }

    @Test
    fun `item ids are stable and distinct`() {
        val b = ScheduleBreak(Kind.LUNCH, "Q1", "Q2", java.time.Instant.EPOCH, java.time.Instant.EPOCH)
        assertEquals("match:2026test_qm10", MatchItem(testMatch(10)).id)
        assertEquals("break:Q1->Q2", BreakItem(b).id)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.MatchScheduleTest' --tests 'com.pitwatch.core.logic.UpcomingTimelineTest')`
Expected: FAIL — `Unresolved reference 'MatchSchedule'`.

- [ ] **Step 3: Implement**

`.../logic/MatchSchedule.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Schedule intelligence for one tracked team. */
class MatchSchedule(matches: List<Match>, teamKey: String) {
    val allMatches: List<Match> = matches.sortedBy { it.sortOrder }
    val teamMatches: List<Match> = allMatches.filter { m -> m.alliances.values.any { teamKey in it.teamKeys } }
    val upcomingMatches: List<Match> = teamMatches.filter { !it.isPlayed }
    /** Most recent first. */
    val pastMatches: List<Match> = teamMatches.filter { it.isPlayed }.reversed()

    val nextMatch: Match? get() = upcomingMatches.firstOrNull()
    val lastPlayedMatch: Match? get() = pastMatches.firstOrNull()

    /** Background refresh cadence; tighter as the next match (or its next Nexus phase) approaches. */
    fun refreshInterval(now: Instant, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Duration {
        val next = nextMatch ?: return ONE_DAY

        if (nexusEvent != null) {
            val nextPhase = NexusMatchMerge.nexusInfo(next, nexusEvent)?.times?.nextPhaseDate(after = now)
            if (nextPhase != null) {
                val until = secondsBetween(now, nextPhase.date)
                return Duration.ofSeconds(
                    when {
                        until < 0 && until > -900 -> 300L // phase just passed
                        until <= 600 -> 300L
                        until <= 1800 -> 600L
                        else -> 900L
                    },
                )
            }
        }

        val matchDate = referenceDate(next, useScheduledTime) ?: return ONE_DAY
        val until = secondsBetween(now, matchDate)
        return Duration.ofSeconds(
            when {
                until < 0 && until > -900 -> 600L // match just completed
                until <= 1800 -> 900L
                until <= 7200 -> 1800L
                else -> 3600L
            },
        )
    }

    fun nextReloadTime(now: Instant, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Instant =
        now.plus(refreshInterval(now, useScheduledTime, nexusEvent))

    /**
     * Whether live tracking should auto-start now. With correlated Nexus data the earliest Nexus phase
     * time is the reference (15-min grace after it); otherwise the TBA match time.
     */
    fun shouldStartLiveActivity(
        now: Instant,
        mode: LiveActivityMode,
        useScheduledTime: Boolean,
        hasActiveLiveActivity: Boolean,
        nexusEvent: NexusEvent? = null,
    ): Boolean {
        if (hasActiveLiveActivity) return false
        val next = nextMatch ?: return false

        val nexusDate = NexusMatchMerge.nexusInfo(next, nexusEvent)?.times
            ?.let { it.queueDate ?: it.onDeckDate ?: it.onFieldDate ?: it.startDate }
        if (nexusDate != null) {
            val until = secondsBetween(now, nexusDate)
            return when (mode) {
                LiveActivityMode.NEAR_MATCH -> until > -900 && until <= 7200
                LiveActivityMode.ALL_DAY -> until > -900
            }
        }

        val matchDate = referenceDate(next, useScheduledTime) ?: return false
        val until = secondsBetween(now, matchDate)
        return when (mode) {
            LiveActivityMode.NEAR_MATCH -> until > 0 && until <= 7200
            LiveActivityMode.ALL_DAY -> until <= 7200
        }
    }

    /**
     * Upcoming matches with inferred schedule breaks inserted between consecutive matches. Brackets use
     * Nexus-correlated start times, falling back to TBA times; pairs with an unknown time get no breaks.
     */
    fun upcomingTimeline(nexusEvent: NexusEvent?, zone: ZoneId): List<UpcomingScheduleItem> {
        if (nexusEvent == null) return upcomingMatches.map { UpcomingScheduleItem.MatchItem(it) }

        val allBreaks = ScheduleBreakDetector.detectBreaks(nexusEvent.matches, zone)
        fun effectiveTime(match: Match): Instant? =
            NexusMatchMerge.nexusInfo(match, nexusEvent)?.times?.startDate ?: match.matchDate(useScheduled = true)

        return buildList {
            upcomingMatches.forEachIndexed { index, match ->
                add(UpcomingScheduleItem.MatchItem(match))
                val following = upcomingMatches.getOrNull(index + 1) ?: return@forEachIndexed
                val prevTime = effectiveTime(match) ?: return@forEachIndexed
                val nextTime = effectiveTime(following) ?: return@forEachIndexed
                // Half-open: a break starting exactly at nextTime belongs to the next pair.
                allBreaks.filter { it.start >= prevTime && it.start < nextTime }
                    .forEach { add(UpcomingScheduleItem.BreakItem(it)) }
            }
        }
    }

    private fun referenceDate(match: Match, useScheduledTime: Boolean): Instant? {
        if (useScheduledTime) match.time?.let { return Instant.ofEpochSecond(it) }
        return match.matchDate(useScheduled = false)
    }

    private companion object {
        val ONE_DAY: Duration = Duration.ofDays(1)

        /** Fractional seconds, matching Swift's TimeInterval comparisons at the thresholds. */
        fun secondsBetween(from: Instant, to: Instant): Double = Duration.between(from, to).toMillis() / 1000.0
    }
}

sealed interface UpcomingScheduleItem {
    val id: String

    data class MatchItem(val match: Match) : UpcomingScheduleItem {
        override val id: String get() = "match:${match.key}"
    }

    data class BreakItem(val scheduleBreak: ScheduleBreak) : UpcomingScheduleItem {
        override val id: String get() = "break:${scheduleBreak.startsAfter}->${scheduleBreak.endsBefore}"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.*')`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port MatchSchedule with refresh, live-start window, and upcoming timeline"
```

---

### Task 9: EventCache, RefreshState, ChangeDetector

**Files:**
- Create: `.../store/EventCache.kt`, `.../logic/ChangeDetector.kt`
- Test: `.../logic/ChangeDetectorTest.kt`

**Interfaces:**
- Consumes: `Match`, `Event`, `EventRankings`, `Ranking`, `WLTRecord`, `EventOPRs`, `Team`, `NexusEvent`, `testMatch()`.
- Produces:
  - `data class EventCache(event: Event? = null, matches: List<Match> = emptyList(), rankings: EventRankings? = null, oprs: EventOPRs? = null, teams: List<Team> = emptyList(), nexusEvent: NexusEvent? = null)` (`@Serializable`)
  - `data class RefreshState(lastRefreshEpochMs: Long? = null, lastModifiedHeaders: Map<String, String> = emptyMap(), lastError: String? = null, nexusLastRefreshEpochMs: Long? = null, nexusLastError: String? = null)` with `lastModified(path): String?` and `withLastModified(value: String?, path: String): RefreshState` (`@Serializable`). (Swift's unused `isRefreshing` is dropped.)
  - `enum class ChangeReason { SCORE_CHANGED, PREDICTED_TIME_SHIFTED, RANK_CHANGED, ALLIANCE_CHANGED }`, `data class ChangeResult(reasons: Set<ChangeReason>)` with `shouldReloadWidgets`, `object ChangeDetector { fun detect(old: EventCache, new: EventCache, teamKey: String): ChangeResult }`

- [ ] **Step 1: Write the failing tests**

`.../logic/ChangeDetectorTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.Ranking
import com.pitwatch.core.model.WLTRecord
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import com.pitwatch.core.testMatch
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChangeDetectorTest {
    private val matches = PitWatchJson.decodeFromString<List<Match>>(fixture("matches.json"))
    private val rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("rankings.json"))
    private fun detect(old: EventCache, new: EventCache) = ChangeDetector.detect(old, new, "frc1234")

    @Test
    fun `no change`() {
        val cache = EventCache(matches = matches, rankings = rankings)
        assertFalse(detect(cache, cache.copy()).shouldReloadWidgets)
    }

    @Test
    fun `score posted`() {
        val scored = testMatch(
            32, eventKey = "2026miket", time = 1712000000, predictedTime = 1712000600, actualTime = 1712000700,
            redScore = 95, blueScore = 80,
        )
        val result = detect(EventCache(matches = matches), EventCache(matches = listOf(scored, matches[1])))
        assertTrue(result.shouldReloadWidgets)
        assertTrue(ChangeReason.SCORE_CHANGED in result.reasons)
    }

    @Test
    fun `rank changed`() {
        val newRankings = EventRankings(
            listOf(Ranking("frc1234", rank = 5, record = WLTRecord(5, 3, 0), qualAverage = 78.0, matchesPlayed = 8, dq = 0)),
        )
        val result = detect(EventCache(rankings = rankings), EventCache(rankings = newRankings))
        assertTrue(ChangeReason.RANK_CHANGED in result.reasons)
    }

    @Test
    fun `predicted time shift over five minutes`() {
        val old = testMatch(32, predictedTime = 1_000_000)
        assertTrue(ChangeReason.PREDICTED_TIME_SHIFTED in detect(EventCache(matches = listOf(old)), EventCache(matches = listOf(old.copy(predictedTime = 1_000_301)))).reasons)
        assertFalse(detect(EventCache(matches = listOf(old)), EventCache(matches = listOf(old.copy(predictedTime = 1_000_300)))).shouldReloadWidgets)
    }

    @Test
    fun `alliance composition changed`() {
        val old = testMatch(32)
        val new = testMatch(32, blue = listOf("frc3456", "frc7890", "frc2222"))
        assertTrue(ChangeReason.ALLIANCE_CHANGED in detect(EventCache(matches = listOf(old)), EventCache(matches = listOf(new))).reasons)
    }

    @Test
    fun `new team match appearing counts as a change`() {
        assertTrue(detect(EventCache(), EventCache(matches = listOf(testMatch(1)))).shouldReloadWidgets)
    }

    @Test
    fun `other teams' matches are ignored`() {
        val other = testMatch(1, red = listOf("frc1", "frc2", "frc3"), blue = listOf("frc4", "frc5", "frc6"))
        assertFalse(detect(EventCache(), EventCache(matches = listOf(other))).shouldReloadWidgets)
    }

    @Test
    fun `refresh state keeps last-modified per path and ignores null`() {
        val state = RefreshState().withLastModified("LM1", "/a").withLastModified(null, "/a").withLastModified("LM2", "/b")
        assertEquals("LM1", state.lastModified("/a"))
        assertEquals("LM2", state.lastModified("/b"))
        assertNull(state.lastModified("/c"))
    }

    @Test
    fun `event cache round-trips through JSON`() {
        val cache = EventCache(matches = matches, rankings = rankings)
        assertEquals(cache, PitWatchJson.decodeFromString<EventCache>(PitWatchJson.encodeToString(cache)))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.ChangeDetectorTest')`
Expected: FAIL — `Unresolved reference 'EventCache'`, `'ChangeDetector'`.

- [ ] **Step 3: Implement**

`.../store/EventCache.kt`:
```kotlin
package com.pitwatch.core.store

import com.pitwatch.core.model.Event
import com.pitwatch.core.model.EventOPRs
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.Team
import kotlinx.serialization.Serializable

/** Everything fetched for the active event. Persisted by the app layer (Plan 2). */
@Serializable
data class EventCache(
    val event: Event? = null,
    val matches: List<Match> = emptyList(),
    val rankings: EventRankings? = null,
    val oprs: EventOPRs? = null,
    val teams: List<Team> = emptyList(),
    val nexusEvent: NexusEvent? = null,
)

@Serializable
data class RefreshState(
    val lastRefreshEpochMs: Long? = null,
    val lastModifiedHeaders: Map<String, String> = emptyMap(),
    val lastError: String? = null,
    val nexusLastRefreshEpochMs: Long? = null,
    val nexusLastError: String? = null,
) {
    fun lastModified(path: String): String? = lastModifiedHeaders[path]

    /** Records [value] for [path]; a null value (e.g. a 304) keeps the previous header. */
    fun withLastModified(value: String?, path: String): RefreshState =
        if (value == null) this else copy(lastModifiedHeaders = lastModifiedHeaders + (path to value))
}
```

`.../logic/ChangeDetector.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.Match
import com.pitwatch.core.store.EventCache
import kotlin.math.abs

enum class ChangeReason { SCORE_CHANGED, PREDICTED_TIME_SHIFTED, RANK_CHANGED, ALLIANCE_CHANGED }

data class ChangeResult(val reasons: Set<ChangeReason>) {
    val shouldReloadWidgets: Boolean get() = reasons.isNotEmpty()
}

/** Did anything widget-visible change for [teamKey] between two caches? */
object ChangeDetector {
    fun detect(old: EventCache, new: EventCache, teamKey: String): ChangeResult {
        val reasons = mutableSetOf<ChangeReason>()
        val oldByKey = old.matches.associateBy { it.key }

        for (match in new.matches) {
            if (match.alliances.values.none { teamKey in it.teamKeys }) continue
            val oldMatch = oldByKey[match.key]
            if (oldMatch == null) {
                reasons += ChangeReason.SCORE_CHANGED // new match appeared
                continue
            }
            if (match.isPlayed != oldMatch.isPlayed) {
                reasons += ChangeReason.SCORE_CHANGED
            } else if (match.isPlayed && (score(match, "red") != score(oldMatch, "red") || score(match, "blue") != score(oldMatch, "blue"))) {
                reasons += ChangeReason.SCORE_CHANGED
            }
            val newPredicted = match.predictedTime
            val oldPredicted = oldMatch.predictedTime
            if (newPredicted != null && oldPredicted != null && abs(newPredicted - oldPredicted) > 300) {
                reasons += ChangeReason.PREDICTED_TIME_SHIFTED
            }
            if (teams(match) != teams(oldMatch)) reasons += ChangeReason.ALLIANCE_CHANGED
        }

        val newRank = new.rankings?.rankings?.firstOrNull { it.teamKey == teamKey }
        val oldRank = old.rankings?.rankings?.firstOrNull { it.teamKey == teamKey }
        if (newRank != null && oldRank != null) {
            if (newRank.rank != oldRank.rank || newRank.record != oldRank.record || newRank.matchesPlayed != oldRank.matchesPlayed) {
                reasons += ChangeReason.RANK_CHANGED
            }
        } else if ((new.rankings != null) != (old.rankings != null)) {
            reasons += ChangeReason.RANK_CHANGED
        }

        return ChangeResult(reasons)
    }

    private fun score(match: Match, color: String) = match.alliances[color]?.score ?: -1

    private fun teams(match: Match): Set<String> =
        (match.alliances["red"]?.teamKeys.orEmpty() + match.alliances["blue"]?.teamKeys.orEmpty()).toSet()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test --tests 'com.pitwatch.core.logic.ChangeDetectorTest')`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/core
git commit -m "feat(core): port EventCache, RefreshState, and ChangeDetector"
```

---

### Task 10: Full-suite verification and README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Run the whole suite from clean**

Run: `(cd android && ./gradlew clean :core:test)`
Expected: `BUILD SUCCESSFUL`, zero failures.

- [ ] **Step 2: Confirm test count covers the Swift suite**

Run: `ls android/core/build/test-results/test/*.xml | xargs grep -ho 'tests="[0-9]*"' | grep -o '[0-9]*' | paste -sd+ - | bc`
Expected: a number **≥ 85** (the Swift suite has 85; several Swift cases were merged into single Kotlin tests and new tests were added for the review-focus items). If lower, find the Swift test file whose cases are missing and port them.

- [ ] **Step 3: Confirm the Swift suite still passes (fixtures untouched)**

Run: `(cd ios/TBAKit && swift test 2>&1 | tail -1)`
Expected: `Test run with 85 tests … passed`.

- [ ] **Step 4: Update the README layout line**

In `README.md`, replace:
```
android/    Android app (planned — see docs/superpowers/specs)
```
with:
```
android/    Android app (Gradle). :core = pure-Kotlin port of TBAKit (`cd android && ./gradlew :core:test`)
```

- [ ] **Step 5: Commit**

```bash
git add README.md
git commit -m "docs: document android/ :core module"
```

---

## Next Plans

- **Plan 2 — `:app` data layer + live notification:** Android module (minSdk 36), OkHttp Ktor engine, `Repository.refresh()` (port of `BackgroundRefresh.performRefresh`, using `EventSelection.autoDetect`), DataStore/JSON persistence of `UserConfig`/`EventCache`/`RefreshState`, `RefreshWorker`, `PollCadence`, `LiveMatchService` + `LiveNotificationBuilder`, exact-alarm auto-start, minimal Setup + Settings screens.
- **Plan 3 — screens + Glance widgets.**
- **Open decision (not in any plan yet):** Nexus's explicit `breakAfter` markers (decoded in Task 3) vs. the inferred `ScheduleBreakDetector`.
