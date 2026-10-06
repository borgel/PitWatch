# Android `:app` — Data Layer + Live Notification Implementation Plan (Plan 2 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An installable Android app that fetches TBA + Nexus data in the background and shows a promoted Live Update notification for the tracked team's next match, polled on our own adaptive cadence, plus minimal Setup/Home/Settings screens.

**Architecture:** New `:app` module (AGP 9 built-in Kotlin, Compose) on top of `:core`. All decisions are pure functions of `(cache, config, now)` — `PollCadence`, `LiveSnapshots`, `LiveLifecycle`, `AutoStartPlanner` — unit-tested on the JVM. Thin Android shells (`LiveMatchService`, `RefreshWorker`, `AutoStartReceiver`, Compose UI) wire them to the OS and are tested with Robolectric. Persistence is DataStore with a JSON serializer over `PitWatchJson`. A manual-test fake API server replays the captured snapshots.

**Tech Stack:** AGP 9.4.1, compileSdk 37 / minSdk 36 / targetSdk 36, Kotlin 2.4.0, Jetpack Compose (BOM 2026.09.00, Material 3), AndroidX core 1.19.1 (`NotificationCompat` Live Update APIs), WorkManager 2.12.0, DataStore 1.2.1, Ktor OkHttp engine, Robolectric 4.17, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-05-android-app-design.md` (also honor "Carry into Plan 2" at the end of `docs/superpowers/plans/2026-10-05-android-core-port.md`).

## Global Constraints

- Branch `android-app` off `main`; small modular commits; merge back to `main` when done.
- `minSdk 36`, `targetSdk 36`, **`compileSdk 37`** (promotion APIs — `setRequestPromotedOngoing`, `POST_PROMOTED_NOTIFICATIONS` — are only in the 37 SDK; base API 36 lacks them; use `NotificationCompat`, which gates them).
- Sideloaded: `USE_EXACT_ALARM` and FGS type **`specialUse`** are fine.
- `:core` stays Android-free. All app logic takes `now: Instant`; the only clock is `AppContainer.clock`.
- Persist with `PitWatchJson` (never a second Json config). Fixtures are never copied — `:app` tests read the same dirs as `:core`.
- Poll cadence: **30 s** when the tracked match's next Nexus phase is ≤ 10 min away or one passed < 2 min ago; otherwise **2 min**; TBA at most every 2 min; failure backoff 30 → 60 → 120 s.
- Notification: promoted Live Update; chip = `setShortCriticalText` (e.g. `Q32 12m`); title `Q32 · RED · IN QUEUE`; 4 `ProgressStyle` segments (Queue, On deck, On field, Match); chronometer countdown; stale subtext after 5 min; actions Refresh/Stop; dismissal = Stop.
- Stop rules: near-match mode stops 15 min after the result appears; all-day rolls to the next match after 5 min if it's the same local day (event zone), else stops. User Stop/dismiss suppresses auto-start until the *next* match.
- Robolectric JVM args (JDK 17+): `--add-opens=java.base/java.io=ALL-UNNAMED`, `--add-exports=java.base/jdk.internal.access=ALL-UNNAMED`. `local.properties` needs `sdk.dir` (not committed).
- Test helpers are duplicated from `:core`'s `TestSupport` into `:app`'s (accepted: ~40 lines; avoids a test-fixtures build change).

## Review Focus

1. **Process death.** Android may kill the process and restart the `START_STICKY` service with a `null` intent. Expected: tracking resumes with a foreground notification, no crash. → test in Task 9.
2. **Worker and live service refreshing at the same time.** Expected: refreshes serialize (no interleaved writes / lost `Last-Modified`). → test in Task 4.
3. **A transient Nexus blip mid-match.** Expected: the notification keeps the last Nexus data for up to 10 min instead of flapping to TBA times; the failure still drives backoff. → test in Task 4.
4. **User taps Stop, then the alarm or an app launch fires for the same match.** Expected: tracking does not restart until the following match. → tests in Tasks 8 and 9.
5. **Notification permission denied.** Expected: the service still starts and polls without crashing (the notification is just hidden). → test in Task 9.

---

## File Structure

```
android/
├── settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml     (modify)
├── core/src/main/kotlin/com/pitwatch/core/
│   ├── PitWatchJson.kt                     (modify: coerceInputValues)
│   ├── config/EventKeys.kt                 event-key validation
│   └── logic/MatchSchedule.kt, NexusMatchMerge.kt   (modify: window start; KDoc)
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/AndroidManifest.xml
        ├── main/res/drawable/ic_stat_pitwatch.xml
        ├── main/kotlin/com/pitwatch/app/
        │   ├── PitWatchApp.kt, AppContainer.kt, MainActivity.kt
        │   ├── data/   JsonSerializer.kt, Stores.kt (+LiveControl), Repository.kt
        │   ├── live/   PollCadence.kt, LiveSnapshot.kt, LiveNotification.kt, LiveLifecycle.kt, LiveMatchService.kt
        │   ├── schedule/ AutoStartPlanner.kt, AutoStartAlarm.kt, AutoStartReceiver.kt, RefreshWorker.kt
        │   └── ui/     PitWatchRoot.kt, SetupScreen.kt, HomeScreen.kt, SettingsScreen.kt, SetupValidator.kt, RefreshStatusText.kt
        ├── debug/AndroidManifest.xml       cleartext for the local fake API
        └── test/
            ├── resources/robolectric.properties
            └── kotlin/com/pitwatch/app/ TestSupport.kt + *Test.kt per unit
scripts/fake-api.py                        snapshot replay server (manual test)
docs/android-manual-test.md
```

All Gradle commands run from the repo root as `(cd android && ./gradlew …)`.

---

### Task 1: `:core` additions — live window start, event-key validation, lenient nulls, KDoc fix

**Files:**
- Modify: `android/core/src/main/kotlin/com/pitwatch/core/logic/MatchSchedule.kt`, `.../PitWatchJson.kt`, `.../logic/NexusMatchMerge.kt`
- Create: `android/core/src/main/kotlin/com/pitwatch/core/config/EventKeys.kt`
- Test: `android/core/src/test/kotlin/com/pitwatch/core/logic/MatchScheduleTest.kt` (append), `.../model/NexusModelDecodingTest.kt` (append), `android/core/src/test/kotlin/com/pitwatch/core/config/EventKeysTest.kt`

**Interfaces:**
- Produces: `MatchSchedule.liveActivityWindowStart(now: Instant, mode: LiveActivityMode, useScheduledTime: Boolean, nexusEvent: NexusEvent? = null): Instant?`; `object EventKeys { fun isValid(key: String): Boolean }`.

- [ ] **Step 1: Create the branch**

Run: `git checkout -b android-app main`

- [ ] **Step 2: Write the failing tests**

Append inside `class MatchScheduleTest` (before its final `}`):
```kotlin
    private fun windowStart(matchIn: Long, mode: LiveActivityMode, nexus: NexusEvent? = null) =
        MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(matchIn).epochSecond)), "frc1234")
            .liveActivityWindowStart(now, mode, useScheduledTime = true, nexusEvent = nexus)

    @Test
    fun `window opens two hours before the TBA time`() {
        assertEquals(now.plusSeconds(3600), windowStart(3 * 3600, LiveActivityMode.NEAR_MATCH))
        assertEquals(now.plusSeconds(3600), windowStart(3 * 3600, LiveActivityMode.ALL_DAY))
    }

    @Test
    fun `window start is now when already inside the window`() {
        assertEquals(now, windowStart(3600, LiveActivityMode.NEAR_MATCH))
    }

    @Test
    fun `near-match window closes at the TBA time, all-day stays open`() {
        assertNull(windowStart(-60, LiveActivityMode.NEAR_MATCH))
        assertEquals(now, windowStart(-60, LiveActivityMode.ALL_DAY))
    }

    @Test
    fun `nexus window closes 15 minutes after the queue time`() {
        assertEquals(now, windowStart(5 * 3600, LiveActivityMode.NEAR_MATCH, nexusFor(now.minusSeconds(600), null)))
        assertNull(windowStart(5 * 3600, LiveActivityMode.NEAR_MATCH, nexusFor(now.minusSeconds(1200), null)))
        assertEquals(now.plusSeconds(3600), windowStart(5 * 3600, LiveActivityMode.NEAR_MATCH, nexusFor(now.plusSeconds(3 * 3600), null)))
    }

    @Test
    fun `window start agrees with shouldStartLiveActivity`() {
        for (matchIn in listOf(-1200L, -60, 0, 60, 3600, 7200, 7201, 4 * 3600)) {
            for (mode in LiveActivityMode.entries) {
                val schedule = MatchSchedule(listOf(testMatch(32, time = now.plusSeconds(matchIn).epochSecond)), "frc1234")
                val start = schedule.liveActivityWindowStart(now, mode, useScheduledTime = true) ?: continue
                assertTrue(schedule.shouldStartLiveActivity(start, mode, true, false), "matchIn=$matchIn mode=$mode")
                if (start > now) assertFalse(schedule.shouldStartLiveActivity(start.minusSeconds(1), mode, true, false))
            }
        }
    }
```

Append inside `class NexusModelDecodingTest`:
```kotlin
    @Test
    fun `explicit null in a defaulted field falls back to the default`() {
        val json = """{"dataAsOfTime":1,"matches":[{"label":"Playoff 1","redTeams":null,"blueTeams":null,"times":null}]}"""
        val match = PitWatchJson.decodeFromString<NexusEvent>(json).matches.single()
        assertEquals(emptyList(), match.redTeams)
        assertNull(match.times.startDate)
    }
```

`android/core/src/test/kotlin/com/pitwatch/core/config/EventKeysTest.kt`:
```kotlin
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
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `(cd android && ./gradlew :core:test --console=plain)`
Expected: FAIL — compilation errors `Unresolved reference 'liveActivityWindowStart'`, `'EventKeys'`.

- [ ] **Step 4: Implement**

`android/core/src/main/kotlin/com/pitwatch/core/config/EventKeys.kt`:
```kotlin
package com.pitwatch.core.config

/** TBA event keys look like "2026cancmp": a 4-digit year, then lowercase letters/digits. */
object EventKeys {
    private val PATTERN = Regex("^\\d{4}[a-z0-9]+$")

    fun isValid(key: String): Boolean = PATTERN.matches(key)
}
```

In `PitWatchJson.kt`, add `coerceInputValues = true` after `encodeDefaults = true`, and extend the KDoc's sentence with: `An explicit JSON null in a non-null field with a default decodes as that default.`

In `NexusMatchMerge.kt`, replace the KDoc line
`/** "Qualification 32" → "qm-1-32", "Quarterfinal 2-1" → "qf-2-1". Whitespace-tolerant like Swift's split. */`
with
`/** "Qualification 32" → "qm-1-32", "Quarterfinal 2-1" → "qf-2-1". Tolerates extra whitespace — deliberately more lenient than iOS, which keeps it and falls back to team matching. */`

In `MatchSchedule.kt`, replace the body of `shouldStartLiveActivity` (from `if (hasActiveLiveActivity) return false` through its final `}` of the TBA `when`) with:
```kotlin
        if (hasActiveLiveActivity) return false
        val next = nextMatch ?: return false
        val ref = liveReference(next, useScheduledTime, nexusEvent) ?: return false
        val until = secondsBetween(now, ref.date)
        return if (ref.fromNexus) {
            when (mode) {
                LiveActivityMode.NEAR_MATCH -> until > -900 && until <= 7200
                LiveActivityMode.ALL_DAY -> until > -900
            }
        } else {
            when (mode) {
                LiveActivityMode.NEAR_MATCH -> until > 0 && until <= 7200
                LiveActivityMode.ALL_DAY -> until <= 7200
            }
        }
    }

    /**
     * Earliest instant at or after [now] when [shouldStartLiveActivity] (nothing active) is true, or null
     * once that window has closed. Arms the auto-start alarm.
     */
    fun liveActivityWindowStart(
        now: Instant,
        mode: LiveActivityMode,
        useScheduledTime: Boolean,
        nexusEvent: NexusEvent? = null,
    ): Instant? {
        val next = nextMatch ?: return null
        val ref = liveReference(next, useScheduledTime, nexusEvent) ?: return null
        val opens = ref.date.minus(LIVE_LEAD)
        if (ref.fromNexus) {
            if (!now.isBefore(ref.date.plus(NEXUS_GRACE))) return null
            return if (mode == LiveActivityMode.ALL_DAY) now else maxOf(now, opens)
        }
        if (mode == LiveActivityMode.NEAR_MATCH && !now.isBefore(ref.date)) return null
        return maxOf(now, opens)
    }

    private data class LiveReference(val date: Instant, val fromNexus: Boolean)

    /** Liveness is measured against the earliest correlated Nexus phase time, else the TBA match time. */
    private fun liveReference(next: Match, useScheduledTime: Boolean, nexusEvent: NexusEvent?): LiveReference? {
        NexusMatchMerge.nexusInfo(next, nexusEvent)?.times
            ?.let { it.queueDate ?: it.onDeckDate ?: it.onFieldDate ?: it.startDate }
            ?.let { return LiveReference(it, fromNexus = true) }
        return referenceDate(next, useScheduledTime)?.let { LiveReference(it, fromNexus = false) }
    }
```
and in its `private companion object` add:
```kotlin
        val LIVE_LEAD: Duration = Duration.ofHours(2)
        val NEXUS_GRACE: Duration = Duration.ofMinutes(15)
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `(cd android && ./gradlew :core:test --console=plain)`
Expected: `BUILD SUCCESSFUL`, all `:core` tests pass (124 + 8 new).

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "feat(core): live window start, event-key validation, lenient nulls"
```

---

### Task 2: `:app` module scaffold

**Files:**
- Modify: `android/gradle/libs.versions.toml`, `android/build.gradle.kts`, `android/settings.gradle.kts`
- Create: `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/res/drawable/ic_stat_pitwatch.xml`, `android/app/src/main/kotlin/com/pitwatch/app/MainActivity.kt`, `android/app/src/test/resources/robolectric.properties`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/SmokeTest.kt`

**Interfaces:**
- Produces: module `:app` (namespace `com.pitwatch.app`), drawable `R.drawable.ic_stat_pitwatch`, `MainActivity` placeholder.

- [ ] **Step 1: Point Gradle at the Android SDK** (not committed; `local.properties` is git-ignored)

Run: `echo "sdk.dir=$HOME/Library/Android/sdk" > android/local.properties`

- [ ] **Step 2: Version catalog, root plugins, settings**

In `android/gradle/libs.versions.toml`, add to `[versions]`:
```toml
agp = "9.4.1"
compose-bom = "2026.09.00"
androidx-core = "1.19.1"
androidx-activity = "1.13.0"
androidx-lifecycle = "2.11.0"
androidx-work = "2.12.0"
androidx-datastore = "1.2.1"
androidx-test-core = "1.7.0"
robolectric = "4.17"
junit4 = "4.13.2"
```
add to `[libraries]`:
```toml
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "androidx-lifecycle" }
androidx-work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "androidx-work" }
androidx-work-testing = { module = "androidx.work:work-testing", version.ref = "androidx-work" }
androidx-datastore = { module = "androidx.datastore:datastore", version.ref = "androidx-datastore" }
androidx-test-core = { module = "androidx.test:core", version.ref = "androidx-test-core" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "compose-bom" }
compose-material3 = { module = "androidx.compose.material3:material3" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinx-coroutines" }
kotlin-test-junit = { module = "org.jetbrains.kotlin:kotlin-test-junit", version.ref = "kotlin" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
junit4 = { module = "junit:junit", version.ref = "junit4" }
```
add to `[plugins]`:
```toml
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

`android/build.gradle.kts` becomes:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
```

In `android/settings.gradle.kts`, after `include(":core")` add `include(":app")`.

- [ ] **Step 3: Module build file, manifest, resources**

`android/app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.pitwatch.app"
    // 37 for NotificationCompat's Live Update promotion APIs (they postdate base API 36); runs on 36+.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.pitwatch.app"
        minSdk = 36
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Robolectric reaches into JDK internals that JDK 17+ hides by default.
        unitTests.all {
            it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    // Same fixtures as :core — never copied.
    sourceSets.getByName("test").resources.srcDirs(
        rootProject.file("../ios/TBAKit/Tests/TBAKitTests/Fixtures"),
        rootProject.file("../scripts/fixtures"),
    )
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.datastore)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`android/app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:icon="@drawable/ic_stat_pitwatch"
        android:label="PitWatch">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

`android/app/src/main/res/drawable/ic_stat_pitwatch.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FFFFFFFF"
        android:pathData="M12,2A10,10 0,1 1,2 12A10,10 0,0 1,12 2ZM11,6v7l5,3 0.8,-1.3 -4.3,-2.5V6z" />
</vector>
```

`android/app/src/test/resources/robolectric.properties`:
```properties
sdk=36
```

- [ ] **Step 4: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/SmokeTest.kt`:
```kotlin
package com.pitwatch.app

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlin.test.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmokeTest {
    @Test
    fun `runs on API 36 with our package`() {
        assertEquals(36, Build.VERSION.SDK_INT)
        assertEquals("com.pitwatch.app", ApplicationProvider.getApplicationContext<Context>().packageName)
    }

    @Test
    fun `main activity launches`() {
        Robolectric.buildActivity(MainActivity::class.java).setup().get()
    }
}
```

- [ ] **Step 5: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --console=plain)`
Expected: FAIL — `Unresolved reference 'MainActivity'`.

- [ ] **Step 6: Implement the placeholder activity**

`android/app/src/main/kotlin/com/pitwatch/app/MainActivity.kt`:
```kotlin
package com.pitwatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Text("PitWatch") }
    }
}
```

- [ ] **Step 7: Run tests and build the APK**

Run: `(cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL`; 2 tests pass; `android/app/build/outputs/apk/debug/app-debug.apk` exists. If `sourceSets.getByName("test").resources.srcDirs(...)` is rejected by AGP 9's DSL, use `sourceSets { getByName("test") { resources.directories.add(...) } }` with the same two paths and record a ruling.

- [ ] **Step 8: Commit**

```bash
git add android/gradle/libs.versions.toml android/build.gradle.kts android/settings.gradle.kts android/app
git commit -m "build(android): add :app module (AGP 9, Compose, Robolectric on API 36)"
```

---

### Task 3: Persistence — JSON DataStores

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/data/JsonSerializer.kt`, `android/app/src/main/kotlin/com/pitwatch/app/data/Stores.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/data/StoresTest.kt`

**Interfaces:**
- Produces: `class JsonSerializer<T>(serializer: KSerializer<T>, defaultValue: T) : androidx.datastore.core.Serializer<T>`; `@Serializable data class LiveControl(suppressedMatchKey: String? = null)`; `class Stores(dir: File, scope: CoroutineScope)` with `config: DataStore<UserConfig>`, `cache: DataStore<EventCache>`, `refreshState: DataStore<RefreshState>`, `liveControl: DataStore<LiveControl>` (files `team_config.json`, `event_cache.json`, `last_refresh.json`, `live_control.json`).

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/data/StoresTest.kt`:
```kotlin
package com.pitwatch.app.data

import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class StoresTest {
    @get:Rule
    val tmp = TemporaryFolder()

    /** Opens the stores, runs [block], then closes them so the files can be reopened. */
    private fun <R> withStores(block: suspend (Stores) -> R): R = runBlocking {
        val job = SupervisorJob()
        try {
            block(Stores(tmp.root, CoroutineScope(Dispatchers.IO + job)))
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `missing files read as defaults`() = withStores { stores ->
        assertEquals(UserConfig(), stores.config.data.first())
        assertEquals(EventCache(), stores.cache.data.first())
        assertEquals(LiveControl(), stores.liveControl.data.first())
    }

    @Test
    fun `writes survive reopening`() {
        withStores { it.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k") } }
        assertEquals(5507, withStores { it.config.data.first().teamNumber })
    }

    @Test
    fun `corrupt file falls back to the default`() {
        File(tmp.root, "team_config.json").writeText("{not json")
        assertEquals(UserConfig(), withStores { it.config.data.first() })
    }

    @Test
    fun `config is stored as readable JSON`() {
        withStores { it.config.updateData { UserConfig(teamNumber = 5507) } }
        assertTrue("\"teamNumber\":5507" in File(tmp.root, "team_config.json").readText())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.data.StoresTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'Stores'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/data/JsonSerializer.kt`:
```kotlin
package com.pitwatch.app.data

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.pitwatch.core.PitWatchJson
import java.io.InputStream
import java.io.OutputStream
import kotlinx.serialization.KSerializer

/** DataStore serializer persisting [T] as JSON with the shared [PitWatchJson] config. */
class JsonSerializer<T>(
    private val serializer: KSerializer<T>,
    override val defaultValue: T,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T =
        try {
            PitWatchJson.decodeFromString(serializer, input.readBytes().decodeToString())
        } catch (e: IllegalArgumentException) { // includes SerializationException
            throw CorruptionException("Unreadable ${serializer.descriptor.serialName}", e)
        }

    override suspend fun writeTo(t: T, output: OutputStream) {
        output.write(PitWatchJson.encodeToString(serializer, t).encodeToByteArray())
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/data/Stores.kt`:
```kotlin
package com.pitwatch.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class LiveControl(
    /** Auto-start stays off while this is the next match (the user pressed Stop or dismissed). */
    val suppressedMatchKey: String? = null,
)

/** All persisted state, one JSON file per store under [dir]. A corrupt file resets to its default. */
class Stores(dir: File, scope: CoroutineScope) {
    val config: DataStore<UserConfig> = create(dir, "team_config.json", UserConfig.serializer(), UserConfig(), scope)
    val cache: DataStore<EventCache> = create(dir, "event_cache.json", EventCache.serializer(), EventCache(), scope)
    val refreshState: DataStore<RefreshState> = create(dir, "last_refresh.json", RefreshState.serializer(), RefreshState(), scope)
    val liveControl: DataStore<LiveControl> = create(dir, "live_control.json", LiveControl.serializer(), LiveControl(), scope)

    private companion object {
        fun <T> create(dir: File, name: String, serializer: KSerializer<T>, default: T, scope: CoroutineScope): DataStore<T> =
            DataStoreFactory.create(
                serializer = JsonSerializer(serializer, default),
                corruptionHandler = ReplaceFileCorruptionHandler { default },
                scope = scope,
                produceFile = { File(dir, name) },
            )
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.data.StoresTest' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): JSON DataStore persistence for config, cache, refresh and live state"
```

---

### Task 4: Repository — the single refresh path

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/data/Repository.kt`
- Create: `android/app/src/test/kotlin/com/pitwatch/app/TestSupport.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/data/RepositoryTest.kt`

**Interfaces:**
- Consumes: `Stores` (Task 3); `TbaClient`, `NexusClient`, `Endpoints`, `FetchResult`, `EventSelection`, `ChangeDetector`, `EventKeys` from `:core`.
- Produces:
  - `data class RefreshOutcome(changed: Boolean, error: String?, nexusError: String? = null)`
  - `class Repository(stores: Stores, tbaClient: (apiKey: String) -> TbaClient, nexusClient: (apiKey: String) -> NexusClient)` with `val cache: Flow<EventCache>` and `suspend fun refresh(now: Instant, force: Boolean = false, includeTba: Boolean = true): RefreshOutcome`; `Repository.NOT_CONFIGURED`, `Repository.NEXUS_UNAVAILABLE`, `Repository.NEXUS_STALE_AFTER` (10 min).
  - Test helpers: `SNAP`, `SNAP_NOW`, `LA`, `fixture()`, `localInstant()`, `testMatch()`, `testEvent()`, `snapshotCache()`, `class FakeApi`, `snapshotTba()`, `snapshotNexus()`.

- [ ] **Step 1: Write the test support**

`android/app/src/test/kotlin/com/pitwatch/app/TestSupport.kt`:
```kotlin
package com.pitwatch.app

import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.Alliance
import com.pitwatch.core.model.Event
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.store.EventCache
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.coroutines.delay

// Duplicated from :core's TestSupport (accepted in the plan's Global Constraints).

const val SNAP = "2026cancmp/2026-04-11T00-51-22Z"

/** Nexus dataAsOfTime of [SNAP]: mid-event; team 5507 (red) is on the field for qm36, starting 65 s later. */
val SNAP_NOW: Instant = Instant.ofEpochMilli(1775868683705)

val LA: ZoneId = ZoneId.of("America/Los_Angeles")

fun fixture(path: String): String =
    requireNotNull(object {}.javaClass.getResource("/$path")) { "Missing fixture: $path" }.readText()

fun localInstant(iso: String, zone: ZoneId): Instant = LocalDateTime.parse(iso).atZone(zone).toInstant()

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
): Match = Match(
    key = "${eventKey}_$compLevel$number",
    compLevel = compLevel,
    setNumber = setNumber,
    matchNumber = number,
    eventKey = eventKey,
    time = time,
    predictedTime = predictedTime,
    actualTime = actualTime,
    alliances = mapOf(
        "red" to Alliance(score = redScore, teamKeys = red),
        "blue" to Alliance(score = blueScore, teamKeys = blue),
    ),
    winningAlliance = "",
)

fun testEvent(
    key: String = "2026test",
    startDate: String = "2026-04-09",
    endDate: String = "2026-04-12",
    timezone: String? = "America/Los_Angeles",
): Event = Event(
    key = key, name = "Test $key", eventCode = key.drop(4), eventType = 2,
    startDate = startDate, endDate = endDate, year = startDate.take(4).toInt(), timezone = timezone,
)

/** The [SNAP] event, matches and Nexus status as a cache. */
fun snapshotCache(): EventCache = EventCache(
    event = PitWatchJson.decodeFromString<Event>(fixture("$SNAP/tba_event.json")),
    matches = PitWatchJson.decodeFromString<List<Match>>(fixture("$SNAP/tba_matches.json")),
    nexusEvent = PitWatchJson.decodeFromString<NexusEvent>(fixture("$SNAP/nexus_event.json")),
)

/** A MockEngine-backed HttpClient that routes by URL path suffix and records every request. */
class FakeApi {
    private val routes = mutableMapOf<String, () -> Pair<HttpStatusCode, String>>()
    private val lock = Any()
    val requests: MutableList<HttpRequestData> = java.util.Collections.synchronizedList(mutableListOf())
    @Volatile var delayMs = 0L
    private var inFlight = 0
    @Volatile var maxInFlight = 0
        private set

    fun on(pathSuffix: String, status: HttpStatusCode = HttpStatusCode.OK, body: () -> String) {
        routes[pathSuffix] = { status to body() }
    }

    val client = HttpClient(
        MockEngine { request ->
            synchronized(lock) {
                requests += request
                inFlight++
                maxInFlight = maxOf(maxInFlight, inFlight)
            }
            try {
                if (delayMs > 0) delay(delayMs)
                val route = routes.entries.firstOrNull { request.url.encodedPath.endsWith(it.key) }?.value
                val (status, body) = route?.invoke() ?: (HttpStatusCode.NotFound to "")
                respond(body, status, headersOf(HttpHeaders.LastModified, "LM-${request.url.encodedPath}"))
            } finally {
                synchronized(lock) { inFlight-- }
            }
        },
    )
}

fun snapshotTba() = FakeApi().apply {
    on("/team/frc5507/events/2026") { "[" + fixture("$SNAP/tba_event.json") + "]" }
    on("/event/2026cancmp") { fixture("$SNAP/tba_event.json") }
    on("/event/2026cancmp/matches") { fixture("$SNAP/tba_matches.json") }
    on("/event/2026cancmp/rankings") { fixture("$SNAP/tba_rankings.json") }
    on("/event/2026cancmp/oprs") { fixture("$SNAP/tba_oprs.json") }
}

fun snapshotNexus() = FakeApi().apply {
    on("/event/2026cancmp") { fixture("$SNAP/nexus_event.json") }
}
```

- [ ] **Step 2: Write the failing tests**

`android/app/src/test/kotlin/com/pitwatch/app/data/RepositoryTest.kt`:
```kotlin
package com.pitwatch.app.data

import com.pitwatch.app.FakeApi
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotNexus
import com.pitwatch.app.snapshotTba
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val now = SNAP_NOW
    private val tba = snapshotTba()
    private val nexus = snapshotNexus()
    private lateinit var job: Job
    private lateinit var stores: Stores
    private lateinit var repo: Repository

    @Before
    fun setUp() {
        job = SupervisorJob()
        stores = Stores(tmp.root, CoroutineScope(Dispatchers.IO + job))
        repo = Repository(
            stores,
            { TbaClient(it, tba.client, "https://tba.test/api/v3") },
            { NexusClient(it, nexus.client, "https://nexus.test/api/v1") },
        )
    }

    @After
    fun tearDown() = runBlocking { job.cancelAndJoin() }

    private suspend fun configure(config: UserConfig = UserConfig(teamNumber = 5507, apiKey = "tba-key", nexusApiKey = "nexus-key")) {
        stores.config.updateData { config }
    }

    private suspend fun cache() = stores.cache.data.first()

    @Test
    fun `not configured makes no requests`() = runBlocking {
        val outcome = repo.refresh(now)
        assertEquals(Repository.NOT_CONFIGURED, outcome.error)
        assertTrue(tba.requests.isEmpty())
    }

    @Test
    fun `first refresh auto-detects the event and fills the cache`() = runBlocking {
        configure()
        val outcome = repo.refresh(now)
        assertNull(outcome.error)
        assertTrue(outcome.changed)
        val cache = cache()
        assertEquals("2026cancmp", cache.event?.key)
        assertTrue(cache.matches.isNotEmpty())
        assertNotNull(cache.rankings)
        assertNotNull(cache.oprs)
        assertNotNull(cache.nexusEvent)
        val state = stores.refreshState.data.first()
        assertEquals(now.toEpochMilli(), state.lastRefreshEpochMs)
        assertNull(state.lastError)
    }

    @Test
    fun `second refresh sends If-Modified-Since, keeps data on 304, and does not re-detect`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.on("/event/2026cancmp/matches", HttpStatusCode.NotModified) { "" }
        tba.requests.clear()
        val outcome = repo.refresh(now.plusSeconds(60))
        assertNull(outcome.error)
        val matchesRequest = tba.requests.single { it.url.encodedPath.endsWith("/matches") }
        assertEquals("LM-/api/v3/event/2026cancmp/matches", matchesRequest.headers[HttpHeaders.IfModifiedSince])
        assertTrue(cache().matches.isNotEmpty())
        assertTrue(tba.requests.none { "/team/" in it.url.encodedPath })
    }

    @Test
    fun `force skips If-Modified-Since`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.requests.clear()
        repo.refresh(now, force = true)
        assertTrue(tba.requests.all { it.headers[HttpHeaders.IfModifiedSince] == null })
    }

    @Test
    fun `literal null rankings body keeps the other data`() = runBlocking {
        tba.on("/event/2026cancmp/rankings") { "null" }
        configure()
        val outcome = repo.refresh(now)
        assertNull(outcome.error)
        assertNull(cache().rankings)
        assertTrue(cache().matches.isNotEmpty())
    }

    @Test
    fun `event list failure is recorded and leaves the cache alone`() = runBlocking {
        tba.on("/team/frc5507/events/2026", HttpStatusCode.InternalServerError) { "boom" }
        configure()
        val outcome = repo.refresh(now)
        assertNotNull(outcome.error)
        assertEquals(EventCache(), cache())
        assertEquals(outcome.error, stores.refreshState.data.first().lastError)
    }

    @Test
    fun `nexus blip keeps recent nexus data and reports it`() = runBlocking {
        configure()
        repo.refresh(now)
        nexus.on("/event/2026cancmp", HttpStatusCode.BadGateway) { "<html>" }
        val outcome = repo.refresh(now.plusSeconds(60))
        assertNull(outcome.error)
        assertEquals(Repository.NEXUS_UNAVAILABLE, outcome.nexusError)
        assertNotNull(cache().nexusEvent)
        assertEquals(Repository.NEXUS_UNAVAILABLE, stores.refreshState.data.first().nexusLastError)
    }

    @Test
    fun `nexus data older than ten minutes is dropped on failure`() = runBlocking {
        configure()
        repo.refresh(now)
        nexus.on("/event/2026cancmp", HttpStatusCode.BadGateway) { "<html>" }
        repo.refresh(now.plusSeconds(11 * 60))
        assertNull(cache().nexusEvent)
    }

    @Test
    fun `switching the override clears the old event`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.on("/event/2026other") { fixture("$SNAP/tba_event.json").replace("2026cancmp", "2026other") }
        stores.config.updateData { it.copy(eventKeyOverride = "2026other") }
        repo.refresh(now)
        assertEquals("2026other", cache().event?.key)
        assertTrue(cache().matches.isEmpty())
    }

    @Test
    fun `invalid override is ignored`() = runBlocking {
        configure(UserConfig(teamNumber = 5507, apiKey = "k", eventKeyOverride = "2026ca?x=1#f"))
        repo.refresh(now)
        assertEquals("2026cancmp", cache().event?.key)
        assertTrue(tba.requests.none { it.url.parameters.names().isNotEmpty() })
    }

    @Test
    fun `nexus-only refresh makes no TBA requests`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.requests.clear()
        nexus.requests.clear()
        repo.refresh(now.plusSeconds(30), includeTba = false)
        assertTrue(tba.requests.isEmpty())
        assertEquals(1, nexus.requests.size)
    }

    @Test
    fun `ended event is re-detected`() = runBlocking {
        configure()
        repo.refresh(now)
        tba.requests.clear()
        repo.refresh(Instant.parse("2026-05-01T00:00:00Z"))
        assertTrue(tba.requests.any { it.url.encodedPath.endsWith("/team/frc5507/events/2026") })
    }

    @Test
    fun `concurrent refreshes are serialized`() = runBlocking {
        configure()
        tba.delayMs = 20
        coroutineScope { repeat(3) { launch(Dispatchers.IO) { repo.refresh(now) } } }
        assertEquals(1, tba.maxInFlight)
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.data.RepositoryTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'Repository'`.

- [ ] **Step 4: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/data/Repository.kt`:
```kotlin
package com.pitwatch.app.data

import com.pitwatch.core.api.Endpoints
import com.pitwatch.core.api.FetchResult
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.EventKeys
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.ChangeDetector
import com.pitwatch.core.logic.EventSelection
import com.pitwatch.core.model.Event
import com.pitwatch.core.model.EventOPRs
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Match
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

data class RefreshOutcome(val changed: Boolean, val error: String?, val nexusError: String? = null)

/** The only fetch path. Port of iOS BackgroundRefresh.performRefresh. */
class Repository(
    private val stores: Stores,
    private val tbaClient: (apiKey: String) -> TbaClient,
    private val nexusClient: (apiKey: String) -> NexusClient,
) {
    private val mutex = Mutex()

    val cache: Flow<EventCache> get() = stores.cache.data

    /**
     * TBA endpoints use If-Modified-Since unless [force]. With [includeTba] false only Nexus is polled
     * (the live notification's fast path). Serialized: the worker and the live service may overlap.
     */
    suspend fun refresh(now: Instant, force: Boolean = false, includeTba: Boolean = true): RefreshOutcome = mutex.withLock {
        val config = stores.config.data.first()
        val apiKey = config.apiKey
        if (!config.isConfigured || apiKey == null) return@withLock RefreshOutcome(changed = false, error = NOT_CONFIGURED)

        val old = stores.cache.data.first()
        var state = stores.refreshState.data.first()
        try {
            var cache = old
            if (includeTba) {
                val client = tbaClient(apiKey)
                val resolved = resolveEvent(config, old.event, client, now)
                    ?: return@withLock RefreshOutcome(changed = false, error = null) // no events this season
                if (old.event?.key != resolved.key) cache = EventCache() // switching events: start clean
                val event = resolved.event ?: cache.event
                    ?: fetchOrNull { (client.fetch<Event>(Endpoints.event(resolved.key)) as? FetchResult.Data)?.value }
                cache = cache.copy(event = event)

                // Conditional GET only when we already hold the data a 304 would refer to.
                suspend fun <T> conditional(path: String, deserializer: DeserializationStrategy<T>, have: Boolean): T? {
                    val lastModified = if (force || !have) null else state.lastModified(path)
                    val result = fetchOrNull { client.fetch(deserializer, path, lastModified) } as? FetchResult.Data ?: return null
                    state = state.withLastModified(result.lastModified, path)
                    return result.value
                }

                val key = resolved.key
                conditional(Endpoints.eventMatches(key), ListSerializer(Match.serializer()), cache.matches.isNotEmpty())
                    ?.let { cache = cache.copy(matches = it) }
                // TBA returns a literal `null` body for rankings/OPRs early in an event: keep what we had.
                conditional(Endpoints.eventRankings(key), EventRankings.serializer().nullable, cache.rankings != null)
                    ?.let { cache = cache.copy(rankings = it) }
                conditional(Endpoints.eventOprs(key), EventOPRs.serializer().nullable, cache.oprs != null)
                    ?.let { cache = cache.copy(oprs = it) }
                state = state.copy(lastRefreshEpochMs = now.toEpochMilli(), lastError = null)
            }

            var nexusError: String? = null
            val eventKey = cache.event?.key ?: config.eventKeyOverride?.takeIf(EventKeys::isValid)
            val nexusKey = config.nexusApiKey
            if (!nexusKey.isNullOrEmpty() && eventKey != null) {
                val fresh = nexusClient(nexusKey).fetchEventStatus(eventKey)
                if (fresh == null) nexusError = NEXUS_UNAVAILABLE
                // A short blip keeps recent data so the live view doesn't flap to TBA times.
                val kept = fresh ?: cache.nexusEvent?.takeIf { now.toEpochMilli() - it.dataAsOfTime < NEXUS_STALE_AFTER.toMillis() }
                cache = cache.copy(nexusEvent = kept)
                state = state.copy(nexusLastRefreshEpochMs = now.toEpochMilli(), nexusLastError = nexusError)
            } else {
                cache = cache.copy(nexusEvent = null)
            }

            val finalCache = cache
            val finalState = state
            stores.cache.updateData { finalCache }
            stores.refreshState.updateData { finalState }
            val changed = force || old.event != cache.event || old.nexusEvent != cache.nexusEvent ||
                ChangeDetector.detect(old, cache, config.teamKey.orEmpty()).shouldReloadWidgets
            RefreshOutcome(changed, error = null, nexusError = nexusError)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = e.message ?: e::class.simpleName ?: "Refresh failed"
            stores.refreshState.updateData { it.copy(lastError = message) }
            RefreshOutcome(changed = false, error = message)
        }
    }

    private data class ResolvedEvent(val key: String, val event: Event?)

    private suspend fun resolveEvent(config: UserConfig, cached: Event?, client: TbaClient, now: Instant): ResolvedEvent? {
        config.eventKeyOverride?.takeIf(EventKeys::isValid)?.let { key ->
            return ResolvedEvent(key, cached?.takeIf { it.key == key })
        }
        // Deliberate divergence from iOS, which kept the first detected event forever: re-detect once it's over.
        if (cached != null && (cached.isActive(now) || (cached.startInstant ?: Instant.MIN) > now)) {
            return ResolvedEvent(cached.key, cached)
        }
        val team = config.teamNumber ?: return null
        val year = now.atZone(ZoneOffset.UTC).year
        val events = when (val result = client.fetch<List<Event>>(Endpoints.teamEvents(team, year))) {
            is FetchResult.Data -> result.value
            FetchResult.NotModified -> return cached?.let { ResolvedEvent(it.key, it) }
        }
        return EventSelection.autoDetect(events, now)?.let { ResolvedEvent(it.key, it) }
    }

    private suspend fun <T> fetchOrNull(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }

    companion object {
        const val NOT_CONFIGURED = "Not configured"
        const val NEXUS_UNAVAILABLE = "Nexus data unavailable"
        val NEXUS_STALE_AFTER: Duration = Duration.ofMinutes(10)
    }
}
```

- [ ] **Step 5: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.data.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`; 13 Repository + 4 Stores tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app
git commit -m "feat(app): Repository refresh with conditional GETs, re-detect, and Nexus blip tolerance"
```

---

### Task 5: PollCadence

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/live/PollCadence.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/PollCadenceTest.kt`

**Interfaces:**
- Consumes: `snapshotCache()`, `SNAP_NOW`, `testMatch()` (Task 4).
- Produces: `object PollCadence { FAST, SLOW, TBA_INTERVAL: Duration; fun nextDelay(cache: EventCache, config: UserConfig, trackedMatchKey: String?, now: Instant, consecutiveFailures: Int): Duration; fun isTbaDue(lastTbaPoll: Instant?, now: Instant): Boolean }`

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/live/PollCadenceTest.kt`:
```kotlin
package com.pitwatch.app.live

import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PollCadenceTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val config = UserConfig(teamNumber = 1234, apiKey = "k", nexusApiKey = "n")
    private val match = testMatch(32)

    /** Cache whose tracked match has Nexus phases at the given offsets (seconds from [now]). */
    private fun cacheWithPhases(queue: Long?, onDeck: Long? = null, onField: Long? = null, start: Long? = null): EventCache {
        fun ms(offset: Long?) = offset?.let { now.plusSeconds(it).toEpochMilli() }
        val nexus = NexusMatch(
            "Qualification 32", null, listOf("1234", "5678", "9012"), listOf("3456", "7890", "1111"),
            NexusMatchTimes(ms(queue), ms(onDeck), ms(onField), ms(start)),
        )
        return EventCache(matches = listOf(match), nexusEvent = NexusEvent(0, matches = listOf(nexus)))
    }

    private fun delay(cache: EventCache, cfg: UserConfig = config, failures: Int = 0, key: String? = match.key) =
        PollCadence.nextDelay(cache, cfg, key, now, failures)

    @Test
    fun `failures back off 30, 60, then 120 seconds`() {
        val cache = cacheWithPhases(queue = 300)
        assertEquals(Duration.ofSeconds(30), delay(cache, failures = 1))
        assertEquals(Duration.ofSeconds(60), delay(cache, failures = 2))
        assertEquals(Duration.ofSeconds(120), delay(cache, failures = 3))
        assertEquals(Duration.ofSeconds(120), delay(cache, failures = 9))
    }

    @Test
    fun `fast within ten minutes of the next phase`() {
        assertEquals(PollCadence.FAST, delay(cacheWithPhases(queue = 300)))
        assertEquals(PollCadence.FAST, delay(cacheWithPhases(queue = 600)))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = 660)))
    }

    @Test
    fun `fast for two minutes after a phase passes`() {
        assertEquals(PollCadence.FAST, delay(cacheWithPhases(queue = -60, onDeck = 1200)))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = -180, onDeck = 1200)))
    }

    @Test
    fun `slow without nexus correlation, with TBA time source, or for an unknown match`() {
        assertEquals(PollCadence.SLOW, delay(EventCache(matches = listOf(match))))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = 300), cfg = config.copy(timeSource = TimeSource.TBA)))
        assertEquals(PollCadence.SLOW, delay(cacheWithPhases(queue = 300), key = "nope"))
    }

    @Test
    fun `real snapshot - team 5507 about to start qm36 polls fast`() {
        val cfg = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
        assertEquals(PollCadence.FAST, PollCadence.nextDelay(snapshotCache(), cfg, "2026cancmp_qm36", SNAP_NOW, 0))
    }

    @Test
    fun `TBA is due every two minutes`() {
        assertTrue(PollCadence.isTbaDue(null, now))
        assertFalse(PollCadence.isTbaDue(now.minusSeconds(119), now))
        assertTrue(PollCadence.isTbaDue(now.minusSeconds(120), now))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.PollCadenceTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'PollCadence'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/live/PollCadence.kt`:
```kotlin
package com.pitwatch.app.live

import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant

/** How often the live service polls. Fast only around the tracked match's Nexus phase changes. */
object PollCadence {
    val FAST: Duration = Duration.ofSeconds(30)
    val SLOW: Duration = Duration.ofMinutes(2)
    val TBA_INTERVAL: Duration = Duration.ofMinutes(2)
    private val NEAR_AHEAD: Duration = Duration.ofMinutes(10)
    private val NEAR_BEHIND: Duration = Duration.ofMinutes(2)
    private val BACKOFF = listOf(30L, 60L, 120L).map(Duration::ofSeconds)

    fun nextDelay(
        cache: EventCache,
        config: UserConfig,
        trackedMatchKey: String?,
        now: Instant,
        consecutiveFailures: Int,
    ): Duration {
        if (consecutiveFailures > 0) return BACKOFF[minOf(consecutiveFailures, BACKOFF.size) - 1]
        if (config.effectiveTimeSource != TimeSource.NEXUS) return SLOW
        val match = cache.matches.firstOrNull { it.key == trackedMatchKey } ?: return SLOW
        val times = NexusMatchMerge.nexusInfo(match, cache.nexusEvent)?.times ?: return SLOW
        val near = listOfNotNull(times.queueDate, times.onDeckDate, times.onFieldDate, times.startDate).any { phase ->
            val until = Duration.between(now, phase)
            if (until.isNegative) until > NEAR_BEHIND.negated() else until <= NEAR_AHEAD
        }
        return if (near) FAST else SLOW
    }

    fun isTbaDue(lastTbaPoll: Instant?, now: Instant): Boolean =
        lastTbaPoll == null || Duration.between(lastTbaPoll, now) >= TBA_INTERVAL
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.PollCadenceTest' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): adaptive live poll cadence with failure backoff"
```

---

### Task 6: LiveSnapshot — what the notification shows

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveSnapshot.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/LiveSnapshotTest.kt`

**Interfaces:**
- Consumes: `NexusMatchMerge`, `PhaseDerivation`, `MatchAlliance`, `Phase` from `:core`; test helpers.
- Produces: `data class LiveSnapshot(matchKey: String, matchLabel: String, alliance: MatchAlliance?, phase: Phase, deadline: Instant?, milestones: Milestones, matchesAway: Int?, onFieldNumber: Int?, result: Result?)` with nested `Milestones(queue, onDeck, onField, start, end: Instant?)` and `Result(ourScore: Int, theirScore: Int)` (`outcome`: "W"/"L"/"T"); `object LiveSnapshots { fun build(cache: EventCache, config: UserConfig, trackedMatchKey: String, now: Instant): LiveSnapshot? }`

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/live/LiveSnapshotTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveSnapshotTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'LiveSnapshots'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/live/LiveSnapshot.kt`:
```kotlin
package com.pitwatch.app.live

import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.logic.PhaseDerivation
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import java.time.Instant

/** Everything the live notification renders for one tracked match at one instant. */
data class LiveSnapshot(
    val matchKey: String,
    val matchLabel: String,
    val alliance: MatchAlliance?,
    val phase: Phase,
    /** When the current phase's countdown ends; null when unknown. */
    val deadline: Instant?,
    val milestones: Milestones,
    /** Matches between the one on the field and ours (quals only); null without a Nexus field status. */
    val matchesAway: Int?,
    val onFieldNumber: Int?,
    /** Set once the tracked match is scored. */
    val result: Result?,
) {
    data class Milestones(val queue: Instant?, val onDeck: Instant?, val onField: Instant?, val start: Instant?, val end: Instant?)

    data class Result(val ourScore: Int, val theirScore: Int) {
        val outcome: String
            get() = when {
                ourScore > theirScore -> "W"
                ourScore < theirScore -> "L"
                else -> "T"
            }
    }
}

object LiveSnapshots {
    private const val NO_FIELD = Int.MIN_VALUE

    /** Port of the content-state logic in iOS LiveActivityManager, with a real TBA fallback deadline. */
    fun build(cache: EventCache, config: UserConfig, trackedMatchKey: String, now: Instant): LiveSnapshot? {
        val teamKey = config.teamKey ?: return null
        val match = cache.matches.firstOrNull { it.key == trackedMatchKey } ?: return null
        val color = match.allianceColor(teamKey)
        val alliance = when (color) {
            "red" -> MatchAlliance.RED
            "blue" -> MatchAlliance.BLUE
            else -> null
        }
        val result = if (match.isPlayed && color != null) {
            val other = if (color == "red") "blue" else "red"
            LiveSnapshot.Result(match.alliances[color]?.score ?: 0, match.alliances[other]?.score ?: 0)
        } else {
            null
        }

        val nexusEvent = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        val onField = nexusEvent
            ?.let { PhaseDerivation.currentMatchOnField(it.matches, fallbackMatchNumber = NO_FIELD) }
            ?.takeIf { it != NO_FIELD }
        val away = onField?.takeIf { match.compLevel == "qm" }?.let { match.matchNumber - it }

        val nexusMatch = NexusMatchMerge.nexusInfo(match, nexusEvent)
        if (nexusMatch != null) {
            val d = PhaseDerivation.derivePhase(nexusMatch, now)
            return LiveSnapshot(
                match.key, match.shortLabel, alliance, d.phase, d.deadline,
                LiveSnapshot.Milestones(d.queueDeadline, d.onDeckDeadline, d.onFieldDeadline, d.matchStartDeadline, d.matchEndDeadline),
                away, onField, result,
            )
        }

        // TBA fallback: count down to the match, or to queueing when a queue offset is set.
        val matchTime = if (config.useScheduledTime) {
            match.time?.let(Instant::ofEpochSecond) ?: match.matchDate()
        } else {
            match.matchDate()
        }
        return LiveSnapshot(
            match.key, match.shortLabel, alliance, Phase.PRE_QUEUE, matchTime?.minus(config.queueOffset),
            LiveSnapshot.Milestones(null, null, null, matchTime, matchTime?.plus(PhaseDerivation.MATCH_DURATION)),
            away, onField, result,
        )
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveSnapshotTest' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): LiveSnapshot derives what the live notification shows"
```

---

### Task 7: LiveNotification — the promoted Live Update

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveNotification.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/LiveNotificationTest.kt`

**Interfaces:**
- Consumes: `LiveSnapshot` (Task 6), `R.drawable.ic_stat_pitwatch` (Task 2).
- Produces: `object LiveNotification { CHANNEL_ID, NOTIFICATION_ID, STALE_AFTER; data class Actions(content: PendingIntent?, refresh: PendingIntent?, stop: PendingIntent?); fun ensureChannel(context); fun build(context, snapshot: LiveSnapshot?, lastSuccess: Instant?, now: Instant, actions: Actions): Notification; fun title(s); fun text(s); fun shortText(s, now); fun staleText(lastSuccess, now) }`

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/live/LiveNotificationTest.kt`:
```kotlin
package com.pitwatch.app.live

import android.app.Notification
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LiveNotificationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now: Instant = Instant.ofEpochSecond(1_800_000_000)
    private val noActions = LiveNotification.Actions(null, null, null)
    private val noMilestones = LiveSnapshot.Milestones(null, null, null, null, null)

    private fun snapshot(
        phase: Phase = Phase.QUEUEING,
        deadline: Instant? = now.plusSeconds(600),
        milestones: LiveSnapshot.Milestones = noMilestones,
        result: LiveSnapshot.Result? = null,
    ) = LiveSnapshot("2026test_qm32", "Q32", MatchAlliance.RED, phase, deadline, milestones, 3, 29, result)

    private fun build(s: LiveSnapshot?, lastSuccess: Instant? = now) =
        LiveNotification.build(context, s, lastSuccess, now, noActions)

    private fun style(n: Notification) = Notification.Builder.recoverBuilder(context, n).style as Notification.ProgressStyle

    @Test
    fun `requests promotion with title, text and status-bar chip`() {
        val n = build(snapshot())
        assertTrue(n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
        assertEquals("Q32 · RED · IN QUEUE", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("3 AWAY · on field #29", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Q32 10m", n.shortCriticalText)
        assertTrue(n.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `counts down to the phase deadline`() {
        val n = build(snapshot())
        assertEquals(now.plusSeconds(600).toEpochMilli(), n.`when`)
        assertTrue(n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue(n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
    }

    @Test
    fun `segments follow real durations when every milestone is known`() {
        val m = LiveSnapshot.Milestones(
            queue = now.minusSeconds(60), onDeck = now.plusSeconds(240), onField = now.plusSeconds(540),
            start = now.plusSeconds(840), end = now.plusSeconds(990),
        )
        val s = style(build(snapshot(milestones = m)))
        assertEquals(listOf(300, 300, 300, 150), s.progressSegments.map { it.length })
        assertEquals(listOf(300, 600, 900), s.progressPoints.map { it.position })
        assertEquals(60, s.progress)
    }

    @Test
    fun `falls back to equal segments without milestones`() {
        val s = style(build(snapshot(phase = Phase.QUEUEING)))
        assertEquals(listOf(100, 100, 100, 100), s.progressSegments.map { it.length })
        assertEquals(50, s.progress)
    }

    @Test
    fun `result shows the outcome and fills the bar`() {
        val n = build(snapshot(result = LiveSnapshot.Result(95, 80)))
        assertEquals("Q32 · RED · FINAL", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals("W 95–80", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Q32 W", n.shortCriticalText)
        assertEquals(400, style(n).progress)
        assertFalse(n.extras.getBoolean(Notification.EXTRA_SHOW_WHEN))
    }

    @Test
    fun `stale data says when it was last updated`() {
        assertEquals("Updated 6m ago", build(snapshot(), lastSuccess = now.minusSeconds(6 * 60)).extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString())
        assertNull(build(snapshot(), lastSuccess = now.minusSeconds(60)).extras.getCharSequence(Notification.EXTRA_SUB_TEXT))
    }

    @Test
    fun `chip switches to hours when far out`() {
        assertEquals("Q32 2h", LiveNotification.shortText(snapshot(deadline = now.plusSeconds(125 * 60)), now))
        assertEquals("Q32 1m", LiveNotification.shortText(snapshot(deadline = now.plusSeconds(1)), now))
        assertEquals("Q32", LiveNotification.shortText(snapshot(deadline = null), now))
    }

    @Test
    fun `no snapshot shows a waiting notification`() {
        val n = build(null, lastSuccess = null)
        assertEquals("Waiting for match data", n.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals("Waiting for data", n.extras.getCharSequence(Notification.EXTRA_SUB_TEXT).toString())
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveNotificationTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'LiveNotification'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/live/LiveNotification.kt`:
```kotlin
package com.pitwatch.app.live

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pitwatch.app.R
import com.pitwatch.core.model.MatchesAwayDisplay
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant

/** Builds the promoted Live Update for the tracked match. Pure apart from the Context it builds with. */
object LiveNotification {
    const val CHANNEL_ID = "live_match"
    const val NOTIFICATION_ID = 1001
    val STALE_AFTER: Duration = Duration.ofMinutes(5)

    /** Queue, on deck, on field (iOS phase colors), then match. */
    private val SEGMENT_COLORS = listOf(0xFFFF9500, 0xFFFF6B00, 0xFF30D158, 0xFF0A84FF).map { it.toInt() }

    data class Actions(val content: PendingIntent?, val refresh: PendingIntent?, val stop: PendingIntent?)

    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName("Live match")
                .setDescription("Queue status for the match you're tracking")
                .setShowBadge(false)
                .build(),
        )
    }

    fun build(context: Context, snapshot: LiveSnapshot?, lastSuccess: Instant?, now: Instant, actions: Actions): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitwatch)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setContentIntent(actions.content)
            .setDeleteIntent(actions.stop) // swiping it away stops tracking
        actions.refresh?.let { builder.addAction(0, "Refresh", it) }
        actions.stop?.let { builder.addAction(0, "Stop", it) }
        staleText(lastSuccess, now)?.let(builder::setSubText)

        if (snapshot == null) {
            return builder.setContentTitle("PitWatch").setContentText("Waiting for match data").setShowWhen(false).build()
        }
        builder.setContentTitle(title(snapshot))
            .setContentText(text(snapshot))
            .setShortCriticalText(shortText(snapshot, now))
            .setStyle(progress(snapshot, now))
        val deadline = snapshot.deadline
        if (snapshot.result == null && deadline != null && deadline > now) {
            builder.setWhen(deadline.toEpochMilli()).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(true)
        } else {
            builder.setShowWhen(false)
        }
        return builder.build()
    }

    /** "Q32 · RED · IN QUEUE", or "… · FINAL" once scored. */
    fun title(s: LiveSnapshot): String =
        listOfNotNull(s.matchLabel, s.alliance?.displayName, if (s.result != null) "FINAL" else s.phase.stateLabel)
            .joinToString(" · ")

    /** "3 AWAY · on field #29", or "W 95–80" once scored. */
    fun text(s: LiveSnapshot): String? {
        s.result?.let { return "${it.outcome} ${it.ourScore}–${it.theirScore}" }
        return listOfNotNull(s.matchesAway?.let(MatchesAwayDisplay::text), s.onFieldNumber?.let { "on field #$it" })
            .joinToString(" · ")
            .ifEmpty { null }
    }

    /** Status-bar chip: "Q32 12m" counting down, "Q32 2h" when far out, "Q32 W" after the result. */
    fun shortText(s: LiveSnapshot, now: Instant): String {
        s.result?.let { return "${s.matchLabel} ${it.outcome}" }
        val deadline = s.deadline ?: return s.matchLabel
        val remaining = Duration.between(now, deadline)
        val minutes = if (remaining.isNegative || remaining.isZero) 0 else (remaining.seconds + 59) / 60
        return if (minutes >= 60) "${s.matchLabel} ${minutes / 60}h" else "${s.matchLabel} ${minutes}m"
    }

    fun staleText(lastSuccess: Instant?, now: Instant): String? {
        lastSuccess ?: return "Waiting for data"
        val age = Duration.between(lastSuccess, now)
        return if (age > STALE_AFTER) "Updated ${age.toMinutes()}m ago" else null
    }

    /** Four segments — queue, on deck, on field, match — sized by real durations when Nexus provides them all. */
    private fun progress(s: LiveSnapshot, now: Instant): NotificationCompat.ProgressStyle {
        val m = s.milestones
        val bounds = listOf(m.queue, m.onDeck, m.onField, m.start, m.end)
        val known = bounds.filterNotNull()
        val lengths: List<Int>
        val position: Int
        if (known.size == bounds.size && known.zipWithNext().all { (a, b) -> a <= b }) {
            lengths = known.zipWithNext { a, b -> maxOf(1, Duration.between(a, b).seconds.toInt()) }
            position = Duration.between(known.first(), now).seconds.toInt().coerceIn(0, lengths.sum())
        } else {
            lengths = List(4) { 100 }
            position = when (s.phase) {
                Phase.PRE_QUEUE -> 0
                Phase.QUEUEING -> 50
                Phase.ON_DECK -> 150
                Phase.ON_FIELD -> 250
            }
        }
        val segments = lengths.zip(SEGMENT_COLORS) { length, color -> NotificationCompat.ProgressStyle.Segment(length).setColor(color) }
        val points = lengths.runningReduce(Int::plus).dropLast(1).map { NotificationCompat.ProgressStyle.Point(it) }
        return NotificationCompat.ProgressStyle()
            .setProgressSegments(segments)
            .setProgressPoints(points)
            .setProgress(if (s.result != null) lengths.sum() else position)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveNotificationTest' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 8 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): promoted Live Update notification with segmented progress and countdown chip"
```

---

### Task 8: LiveLifecycle and AutoStartPlanner

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveLifecycle.kt`, `android/app/src/main/kotlin/com/pitwatch/app/schedule/AutoStartPlanner.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/LiveLifecycleTest.kt`, `android/app/src/test/kotlin/com/pitwatch/app/schedule/AutoStartPlannerTest.kt`

**Interfaces:**
- Consumes: `LiveControl` (Task 3); `MatchSchedule.liveActivityWindowStart` (Task 1).
- Produces: `sealed interface LiveDecision { data class Track(matchKey: String); data object Stop }`; `object LiveLifecycle { NEAR_MATCH_LINGER (15 min), ALL_DAY_LINGER (5 min); fun decide(cache, config, trackedMatchKey: String?, resultSeenAt: Instant?, now): LiveDecision }`; `object AutoStartPlanner { fun nextStart(cache: EventCache, config: UserConfig, control: LiveControl, now: Instant): Instant? }`

- [ ] **Step 1: Write the failing tests**

`android/app/src/test/kotlin/com/pitwatch/app/live/LiveLifecycleTest.kt`:
```kotlin
package com.pitwatch.app.live

import com.pitwatch.app.LA
import com.pitwatch.app.localInstant
import com.pitwatch.app.testEvent
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import kotlin.test.assertEquals
import org.junit.Test

class LiveLifecycleTest {
    private val now = localInstant("2026-04-10T14:00:00", LA)
    private val near = UserConfig(teamNumber = 1234, apiKey = "k", liveActivityMode = LiveActivityMode.NEAR_MATCH)
    private val allDay = near.copy(liveActivityMode = LiveActivityMode.ALL_DAY)
    private val played = testMatch(10, time = now.minusSeconds(1800).epochSecond, actualTime = now.minusSeconds(1800).epochSecond, redScore = 50, blueScore = 40)
    private val laterToday = testMatch(20, time = localInstant("2026-04-10T16:00:00", LA).epochSecond)
    private val tomorrow = testMatch(30, time = localInstant("2026-04-11T09:00:00", LA).epochSecond)

    private fun cache(vararg matches: com.pitwatch.core.model.Match) = EventCache(event = testEvent(), matches = matches.toList())

    @Test
    fun `untracked follows the next match, or stops with nothing upcoming`() {
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(played, laterToday), near, null, null, now))
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played), near, null, null, now))
    }

    @Test
    fun `keeps an unplayed tracked match`() {
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(laterToday), near, laterToday.key, null, now))
    }

    @Test
    fun `keeps showing a result until the linger elapses`() {
        assertEquals(LiveDecision.Track(played.key), LiveLifecycle.decide(cache(played, laterToday), near, played.key, null, now))
        assertEquals(LiveDecision.Track(played.key), LiveLifecycle.decide(cache(played, laterToday), near, played.key, now.minusSeconds(14 * 60), now))
    }

    @Test
    fun `near-match mode stops after 15 minutes of result`() {
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played, laterToday), near, played.key, now.minusSeconds(16 * 60), now))
    }

    @Test
    fun `all-day mode rolls to a same-day match after 5 minutes`() {
        assertEquals(LiveDecision.Track(played.key), LiveLifecycle.decide(cache(played, laterToday), allDay, played.key, now.minusSeconds(4 * 60), now))
        assertEquals(LiveDecision.Track(laterToday.key), LiveLifecycle.decide(cache(played, laterToday), allDay, played.key, now.minusSeconds(6 * 60), now))
    }

    @Test
    fun `all-day mode stops when the next match is another day in the event zone`() {
        assertEquals(LiveDecision.Stop, LiveLifecycle.decide(cache(played, tomorrow), allDay, played.key, now.minusSeconds(6 * 60), now))
    }
}
```

`android/app/src/test/kotlin/com/pitwatch/app/schedule/AutoStartPlannerTest.kt`:
```kotlin
package com.pitwatch.app.schedule

import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AutoStartPlannerTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)
    private val config = UserConfig(teamNumber = 1234, apiKey = "k", useScheduledTime = true)
    private val next = testMatch(32, time = now.plusSeconds(3 * 3600).epochSecond)
    private val cache = EventCache(matches = listOf(next))

    @Test
    fun `arms two hours before the next match`() {
        assertEquals(now.plusSeconds(3600), AutoStartPlanner.nextStart(cache, config, LiveControl(), now))
    }

    @Test
    fun `stays disarmed while the user has stopped this match`() {
        assertNull(AutoStartPlanner.nextStart(cache, config, LiveControl(suppressedMatchKey = next.key), now))
    }

    @Test
    fun `suppression of an earlier match does not block the next one`() {
        assertEquals(now.plusSeconds(3600), AutoStartPlanner.nextStart(cache, config, LiveControl(suppressedMatchKey = "2026test_qm31"), now))
    }

    @Test
    fun `disarmed when not configured or nothing upcoming`() {
        assertNull(AutoStartPlanner.nextStart(cache, UserConfig(), LiveControl(), now))
        assertNull(AutoStartPlanner.nextStart(EventCache(), config, LiveControl(), now))
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveLifecycleTest' --tests 'com.pitwatch.app.schedule.AutoStartPlannerTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'LiveLifecycle'`, `'AutoStartPlanner'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/live/LiveLifecycle.kt`:
```kotlin
package com.pitwatch.app.live

import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

sealed interface LiveDecision {
    data class Track(val matchKey: String) : LiveDecision
    data object Stop : LiveDecision
}

object LiveLifecycle {
    val NEAR_MATCH_LINGER: Duration = Duration.ofMinutes(15)
    val ALL_DAY_LINGER: Duration = Duration.ofMinutes(5)

    /**
     * Which match the live notification follows next. [resultSeenAt] is when the tracked match's score
     * first appeared (null = not yet seen).
     */
    fun decide(cache: EventCache, config: UserConfig, trackedMatchKey: String?, resultSeenAt: Instant?, now: Instant): LiveDecision {
        val teamKey = config.teamKey ?: return LiveDecision.Stop
        val schedule = MatchSchedule(cache.matches, teamKey)
        val tracked = cache.matches.firstOrNull { it.key == trackedMatchKey }
            ?: return schedule.nextMatch?.let { LiveDecision.Track(it.key) } ?: LiveDecision.Stop
        if (!tracked.isPlayed) return LiveDecision.Track(tracked.key)

        val nearMatch = config.liveActivityMode == LiveActivityMode.NEAR_MATCH
        val linger = if (nearMatch) NEAR_MATCH_LINGER else ALL_DAY_LINGER
        if (resultSeenAt == null || now < resultSeenAt.plus(linger)) return LiveDecision.Track(tracked.key)
        if (nearMatch) return LiveDecision.Stop

        val next = schedule.nextMatch ?: return LiveDecision.Stop
        val zone = cache.event?.zone ?: ZoneOffset.UTC
        val nextDay = next.matchDate()?.atZone(zone)?.toLocalDate()
        return if (nextDay == null || nextDay == now.atZone(zone).toLocalDate()) LiveDecision.Track(next.key) else LiveDecision.Stop
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/schedule/AutoStartPlanner.kt`:
```kotlin
package com.pitwatch.app.schedule

import com.pitwatch.app.data.LiveControl
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Instant

object AutoStartPlanner {
    /** When to arm the auto-start alarm; null leaves it disarmed. */
    fun nextStart(cache: EventCache, config: UserConfig, control: LiveControl, now: Instant): Instant? {
        val teamKey = config.teamKey
        if (!config.isConfigured || teamKey == null) return null
        val schedule = MatchSchedule(cache.matches, teamKey)
        val next = schedule.nextMatch ?: return null
        if (next.key == control.suppressedMatchKey) return null
        val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        return schedule.liveActivityWindowStart(now, config.liveActivityMode, config.useScheduledTime, nexus)
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveLifecycleTest' --tests 'com.pitwatch.app.schedule.AutoStartPlannerTest' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 10 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): live lifecycle rules and auto-start planning with user suppression"
```

---

### Task 9: App container, LiveMatchService, auto-start alarm

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/AppContainer.kt`, `android/app/src/main/kotlin/com/pitwatch/app/PitWatchApp.kt`, `android/app/src/main/kotlin/com/pitwatch/app/live/LiveMatchService.kt`, `android/app/src/main/kotlin/com/pitwatch/app/schedule/AutoStartAlarm.kt`, `android/app/src/main/kotlin/com/pitwatch/app/schedule/AutoStartReceiver.kt`
- Modify: `android/app/src/main/AndroidManifest.xml` (replace whole file), `android/app/src/test/kotlin/com/pitwatch/app/TestSupport.kt` (append)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/LiveMatchServiceTest.kt`, `android/app/src/test/kotlin/com/pitwatch/app/schedule/AutoStartAlarmTest.kt`

**Interfaces:**
- Consumes: Tasks 3–8.
- Produces: `class AppContainer(stores: Stores, repository: Repository, scope: CoroutineScope, clock: () -> Instant = Instant::now)` with `companion fun create(context: Context): AppContainer`; `class PitWatchApp : Application` with `lateinit var container: AppContainer`; `class LiveMatchService : Service` with `ACTION_START/ACTION_REFRESH/ACTION_STOP`, `fun start(context)`, `fun intent(context, action)`; `object AutoStartAlarm { fun arm(context, at: Instant?) }`; `suspend fun rearmAutoStart(context: Context, container: AppContainer)`; `class AutoStartReceiver : BroadcastReceiver`; test helper `installTestContainer(dir, tba, nexus, clock): AppContainer`.

- [ ] **Step 1: Append the container test helper to `TestSupport.kt`**

```kotlin
/** Replaces the Robolectric app's container with one backed by temp files and fake HTTP. */
fun installTestContainer(
    dir: java.io.File,
    tba: FakeApi = snapshotTba(),
    nexus: FakeApi = snapshotNexus(),
    clock: () -> Instant = { SNAP_NOW },
): AppContainer {
    val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    val stores = com.pitwatch.app.data.Stores(dir, scope)
    val repository = com.pitwatch.app.data.Repository(
        stores,
        { com.pitwatch.core.api.TbaClient(it, tba.client, "https://tba.test/api/v3") },
        { com.pitwatch.core.api.NexusClient(it, nexus.client, "https://nexus.test/api/v1") },
    )
    val container = AppContainer(stores, repository, scope, clock)
    androidx.test.core.app.ApplicationProvider.getApplicationContext<PitWatchApp>().container = container
    return container
}

/** Polls [condition] while letting the Robolectric main looper run, for up to [timeoutMs]. */
fun awaitMain(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        check(System.currentTimeMillis() < deadline) { "Timed out waiting for condition" }
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(10)
    }
}
```

- [ ] **Step 2: Write the failing tests**

`android/app/src/test/kotlin/com/pitwatch/app/live/LiveMatchServiceTest.kt`:
```kotlin
package com.pitwatch.app.live

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class LiveMatchServiceTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun startService(intentAction: String? = LiveMatchService.ACTION_START): LiveMatchService {
        val controller = Robolectric.buildService(LiveMatchService::class.java).create()
        val service = controller.get()
        service.onStartCommand(intentAction?.let { LiveMatchService.intent(context, it) }, 0, 1)
        return service
    }

    private fun posted(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(LiveNotification.NOTIFICATION_ID)

    @Test
    fun `start goes foreground with a promoted notification`() {
        val service = startService()
        val foreground = assertNotNull(shadowOf(service).lastForegroundNotification)
        assertTrue(foreground.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `polls and shows the tracked match`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        startService()
        awaitMain { posted()?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Q36") == true }
        assertEquals("Q36 · RED · ON FIELD", posted()!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `restart after process death resumes tracking`() {
        // START_STICKY redelivers a null intent.
        val service = startService(intentAction = null)
        assertNotNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `runs without notification permission`() {
        // POST_NOTIFICATIONS deliberately not granted.
        val service = startService()
        awaitMain { runBlocking { container.stores.refreshState.data.first().lastRefreshEpochMs != null } }
        assertNotNull(shadowOf(service).lastForegroundNotification)
    }

    @Test
    fun `stop suppresses the next match and stops the service`() {
        // Stop arrives before the first poll finishes, so the service suppresses the schedule's next match.
        runBlocking { container.repository.refresh(com.pitwatch.app.SNAP_NOW) }
        val service = startService()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        assertTrue(shadowOf(service).isStoppedBySelf)
        val suppressed = runBlocking {
            withTimeout(5_000) { container.stores.liveControl.data.first { it.suppressedMatchKey != null } }
        }
        assertEquals("2026cancmp_qm36", suppressed.suppressedMatchKey)
    }
}
```

`android/app/src/test/kotlin/com/pitwatch/app/schedule/AutoStartAlarmTest.kt`:
```kotlin
package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.UserConfig
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AutoStartAlarmTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `arming schedules an exact alarm and null cancels it`() {
        val at = Instant.ofEpochSecond(1_900_000_000)
        AutoStartAlarm.arm(context, at)
        assertEquals(at.toEpochMilli(), alarms.nextScheduledAlarm.triggerAtMs)
        AutoStartAlarm.arm(context, null)
        assertNull(alarms.nextScheduledAlarm)
    }

    @Test
    fun `receiver starts the live service inside the window`() {
        runBlocking {
            container.stores.config.updateData {
                UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n", liveActivityMode = LiveActivityMode.ALL_DAY, timeSource = com.pitwatch.core.config.TimeSource.TBA)
            }
            container.repository.refresh(SNAP_NOW)
        }
        AutoStartReceiver().onReceive(context, Intent(context, AutoStartReceiver::class.java))
        awaitMain { shadowOf(context as Application).peekNextStartedService() != null }
        assertEquals(LiveMatchService::class.java.name, shadowOf(context as Application).nextStartedService.component?.className)
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveMatchServiceTest' --tests 'com.pitwatch.app.schedule.AutoStartAlarmTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'AppContainer'`, `'LiveMatchService'`, `'AutoStartAlarm'`.

- [ ] **Step 4: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/AppContainer.kt`:
```kotlin
package com.pitwatch.app

import android.content.Context
import com.pitwatch.app.data.Repository
import com.pitwatch.app.data.Stores
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import java.io.File
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-rolled DI: everything long-lived, created once per process. Tests swap it on [PitWatchApp]. */
class AppContainer(
    val stores: Stores,
    val repository: Repository,
    val scope: CoroutineScope,
    val clock: () -> Instant = Instant::now,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            // Bounded requests: a hung poll degrades (IOException → null/error) instead of stalling the live loop.
            val http = HttpClient(OkHttp) {
                install(HttpTimeout) {
                    requestTimeoutMillis = 15_000
                    connectTimeoutMillis = 10_000
                }
            }
            val stores = Stores(File(context.filesDir, "pitwatch"), scope)
            val repository = Repository(
                stores,
                { TbaClient(it, http, BuildConfig.TBA_BASE_URL) },
                { NexusClient(it, http, BuildConfig.NEXUS_BASE_URL) },
            )
            return AppContainer(stores, repository, scope)
        }
    }
}
```

In `android/app/build.gradle.kts`, inside `defaultConfig { … }` add:
```kotlin
        // Overridable for the local fake API (see docs/android-manual-test.md).
        buildConfigField("String", "TBA_BASE_URL", "\"${providers.gradleProperty("pitwatch.tbaBaseUrl").getOrElse("https://www.thebluealliance.com/api/v3")}\"")
        buildConfigField("String", "NEXUS_BASE_URL", "\"${providers.gradleProperty("pitwatch.nexusBaseUrl").getOrElse("https://frc.nexus/api/v1")}\"")
```

`android/app/src/main/kotlin/com/pitwatch/app/PitWatchApp.kt`:
```kotlin
package com.pitwatch.app

import android.app.Application
import com.pitwatch.app.live.LiveNotification

class PitWatchApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        LiveNotification.ensureChannel(this)
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/schedule/AutoStartAlarm.kt`:
```kotlin
package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pitwatch.app.AppContainer
import java.time.Instant
import kotlinx.coroutines.flow.first

object AutoStartAlarm {
    private const val REQUEST_CODE = 42

    /** Arms (or with null, cancels) the exact alarm that starts live tracking. */
    fun arm(context: Context, at: Instant?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE, Intent(context, AutoStartReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        if (at == null) {
            alarms.cancel(pending)
        } else {
            // USE_EXACT_ALARM (sideloaded) makes exact alarms available, and exact alarms may start an FGS.
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pending)
        }
    }
}

/** Re-plans the auto-start alarm from current state. Call after every refresh and whenever tracking stops. */
suspend fun rearmAutoStart(context: Context, container: AppContainer) {
    val at = AutoStartPlanner.nextStart(
        container.stores.cache.data.first(),
        container.stores.config.data.first(),
        container.stores.liveControl.data.first(),
        container.clock(),
    )
    AutoStartAlarm.arm(context, at)
}
```

`android/app/src/main/kotlin/com/pitwatch/app/schedule/AutoStartReceiver.kt`:
```kotlin
package com.pitwatch.app.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.live.LiveMatchService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Fired by [AutoStartAlarm]: start tracking if we're inside the window, otherwise re-arm. */
class AutoStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync() // null when invoked directly (tests)
        val container = (context.applicationContext as PitWatchApp).container
        container.scope.launch {
            try {
                val now = container.clock()
                val start = AutoStartPlanner.nextStart(
                    container.stores.cache.data.first(),
                    container.stores.config.data.first(),
                    container.stores.liveControl.data.first(),
                    now,
                )
                if (start != null && start <= now) LiveMatchService.start(context) else AutoStartAlarm.arm(context, start)
            } finally {
                pending?.finish()
            }
        }
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/live/LiveMatchService.kt`:
```kotlin
package com.pitwatch.app.live

import android.Manifest
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.logic.MatchSchedule
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the Live Update while tracking a match: polls on [PollCadence], re-renders at least every minute
 * (chip countdown), and follows [LiveLifecycle]. Unlike iOS, the cadence is ours — not the OS's.
 */
class LiveMatchService : Service() {
    private val container get() = (application as PitWatchApp).container
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val pokes = Channel<Unit>(Channel.CONFLATED)
    private var loop: Job? = null
    private var tracked: String? = null
    private var resultSeenAt: Instant? = null
    private var lastSuccess: Instant? = null
    private var lastTbaPoll: Instant? = null
    private var failures = 0
    private var triggersRegistered = false

    private val unlockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            pokes.trySend(Unit)
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            pokes.trySend(Unit)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking(suppress = true)
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> pokes.trySend(Unit)
        }
        // A null intent is a START_STICKY restart after process death: resume tracking.
        val now = container.clock()
        ServiceCompat.startForeground(
            this, LiveNotification.NOTIFICATION_ID,
            LiveNotification.build(this, null, lastSuccess, now, actions()),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        if (loop == null) {
            registerTriggers()
            loop = scope.launch { runLoop() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterTriggers()
        super.onDestroy()
    }

    private suspend fun runLoop() {
        var nextPollAt = Instant.MIN
        while (currentCoroutineContext().isActive) {
            val now = container.clock()
            if (now >= nextPollAt) {
                val includeTba = PollCadence.isTbaDue(lastTbaPoll, now)
                val outcome = container.repository.refresh(now, includeTba = includeTba)
                if (outcome.error == null && outcome.nexusError == null) {
                    failures = 0
                    lastSuccess = now
                    if (includeTba) lastTbaPoll = now
                } else {
                    failures++
                }
                val cache = container.repository.cache.first()
                val config = container.stores.config.data.first()
                when (val decision = LiveLifecycle.decide(cache, config, tracked, resultSeenAt, now)) {
                    LiveDecision.Stop -> {
                        stopTracking(suppress = false)
                        return
                    }
                    is LiveDecision.Track -> if (decision.matchKey != tracked) {
                        tracked = decision.matchKey
                        resultSeenAt = null
                    }
                }
                nextPollAt = now.plus(PollCadence.nextDelay(cache, config, tracked, now, failures))
            }
            render(now)
            val untilPoll = Duration.between(container.clock(), nextPollAt)
            val wait = minOf(untilPoll, RENDER_TICK).coerceAtLeast(Duration.ZERO)
            if (withTimeoutOrNull(wait.toMillis()) { pokes.receive() } != null) nextPollAt = Instant.MIN
        }
    }

    private suspend fun render(now: Instant) {
        val cache = container.repository.cache.first()
        val config = container.stores.config.data.first()
        val snapshot = tracked?.let { LiveSnapshots.build(cache, config, it, now) }
        if (snapshot?.result != null && resultSeenAt == null) resultSeenAt = now
        // Without the permission the notification is hidden but tracking (and the FGS) continue.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this)
                .notify(LiveNotification.NOTIFICATION_ID, LiveNotification.build(this, snapshot, lastSuccess, now, actions()))
        }
    }

    private fun stopTracking(suppress: Boolean) {
        val key = tracked
        loop?.cancel()
        loop = null
        val container = container
        val appContext = applicationContext
        container.scope.launch {
            if (suppress) {
                val target = key ?: MatchSchedule(
                    container.stores.cache.data.first().matches,
                    container.stores.config.data.first().teamKey.orEmpty(),
                ).nextMatch?.key
                container.stores.liveControl.updateData { it.copy(suppressedMatchKey = target) }
            }
            rearmAutoStart(appContext, container)
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun registerTriggers() {
        if (triggersRegistered) return
        ContextCompat.registerReceiver(this, unlockReceiver, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_NOT_EXPORTED)
        getSystemService(ConnectivityManager::class.java).registerDefaultNetworkCallback(networkCallback)
        triggersRegistered = true
    }

    private fun unregisterTriggers() {
        if (!triggersRegistered) return
        unregisterReceiver(unlockReceiver)
        getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(networkCallback)
        triggersRegistered = false
    }

    private fun actions() = LiveNotification.Actions(
        content = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        refresh = PendingIntent.getService(this, 1, intent(this, ACTION_REFRESH), PendingIntent.FLAG_IMMUTABLE),
        stop = PendingIntent.getService(this, 2, intent(this, ACTION_STOP), PendingIntent.FLAG_IMMUTABLE),
    )

    companion object {
        const val ACTION_START = "com.pitwatch.app.live.START"
        const val ACTION_REFRESH = "com.pitwatch.app.live.REFRESH"
        const val ACTION_STOP = "com.pitwatch.app.live.STOP"
        private val RENDER_TICK: Duration = Duration.ofSeconds(60)

        fun intent(context: Context, action: String): Intent = Intent(context, LiveMatchService::class.java).setAction(action)

        fun start(context: Context) = ContextCompat.startForegroundService(context, intent(context, ACTION_START))
    }
}
```

Replace `android/app/src/main/AndroidManifest.xml` with:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.POST_PROMOTED_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <!-- Sideloaded: granted at install. Google Play would restrict this permission. -->
    <uses-permission android:name="android.permission.USE_EXACT_ALARM" />

    <application
        android:name=".PitWatchApp"
        android:icon="@drawable/ic_stat_pitwatch"
        android:label="PitWatch">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".live.LiveMatchService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Live queue status of a robotics match the user chose to track" />
        </service>

        <receiver
            android:name=".schedule.AutoStartReceiver"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 5: Run to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --console=plain)`
Expected: `BUILD SUCCESSFUL`; all `:app` tests pass. If Robolectric's `ScheduledAlarm` exposes the trigger time under a different name than `triggerAtMs` in 4.17, use that accessor and record a ruling.

- [ ] **Step 6: Commit**

```bash
git add android/app
git commit -m "feat(app): live tracking foreground service, auto-start alarm, app container"
```

---

### Task 10: RefreshWorker

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/schedule/RefreshWorker.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/schedule/RefreshWorkerTest.kt`

**Interfaces:**
- Consumes: `AppContainer`, `rearmAutoStart` (Task 9), `MatchSchedule.refreshInterval`.
- Produces: `class RefreshWorker : CoroutineWorker` with `UNIQUE_NAME`, `MIN_DELAY` (15 min), `fun nextDelay(cache, config, now): Duration`, `fun enqueue(context, delay, policy)`, `fun ensureScheduled(context)`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/schedule/RefreshWorkerTest.kt`:
```kotlin
package com.pitwatch.app.schedule

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.pitwatch.app.AppContainer
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.testMatch
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class RefreshWorkerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    /** qm36's Nexus queue time minus 3 h: inside the event, before team 5507's live window opens. */
    private val queue36 = Instant.ofEpochMilli(1775867343646)
    private val now = queue36.minus(Duration.ofHours(3))
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        container = installTestContainer(tmp.root, clock = { now })
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `refreshes, reschedules itself, and arms auto-start`() = runBlocking {
        val worker = TestListenableWorkerBuilder<RefreshWorker>(context).build()
        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(container.stores.cache.data.first().matches.isNotEmpty())
        val queued = WorkManager.getInstance(context).getWorkInfosForUniqueWork(RefreshWorker.UNIQUE_NAME).get()
        assertTrue(queued.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED })
        val alarm = shadowOf(context.getSystemService(AlarmManager::class.java)).nextScheduledAlarm
        assertEquals(queue36.minus(Duration.ofHours(2)).toEpochMilli(), alarm.triggerAtMs)
    }

    @Test
    fun `next delay is never below 15 minutes and daily without matches`() {
        val config = UserConfig(teamNumber = 1234, apiKey = "k", useScheduledTime = true)
        val soon = EventCache(matches = listOf(testMatch(1, time = now.plusSeconds(600).epochSecond)))
        assertEquals(RefreshWorker.MIN_DELAY, RefreshWorker.nextDelay(soon, config, now))
        assertEquals(Duration.ofDays(1), RefreshWorker.nextDelay(EventCache(), config, now))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.schedule.RefreshWorkerTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'RefreshWorker'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/schedule/RefreshWorker.kt`:
```kotlin
package com.pitwatch.app.schedule

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pitwatch.app.PitWatchApp
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/** Background refresh on the adaptive schedule; re-enqueues itself and re-arms auto-start every run. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PitWatchApp).container
        val now = container.clock()
        container.repository.refresh(now)
        val cache = container.stores.cache.data.first()
        val config = container.stores.config.data.first()
        // APPEND: replacing would cancel this very run.
        enqueue(applicationContext, nextDelay(cache, config, now), ExistingWorkPolicy.APPEND_OR_REPLACE)
        rearmAutoStart(applicationContext, container)
        return Result.success() // failures are recorded in RefreshState; the next run is already queued
    }

    companion object {
        const val UNIQUE_NAME = "pitwatch-refresh"
        /** WorkManager's own floor for background work. */
        val MIN_DELAY: Duration = Duration.ofMinutes(15)

        fun nextDelay(cache: EventCache, config: UserConfig, now: Instant): Duration {
            val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
            val interval = MatchSchedule(cache.matches, config.teamKey.orEmpty()).refreshInterval(now, config.useScheduledTime, nexus)
            return maxOf(MIN_DELAY, interval)
        }

        fun enqueue(context: Context, delay: Duration, policy: ExistingWorkPolicy) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setInitialDelay(delay)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_NAME, policy, request)
        }

        /** App launch / after setup: refresh now unless a run is already scheduled. */
        fun ensureScheduled(context: Context) = enqueue(context, Duration.ZERO, ExistingWorkPolicy.KEEP)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.schedule.RefreshWorkerTest' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 2 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): self-rescheduling background RefreshWorker"
```

---

### Task 11: Minimal UI — Setup, Home, Settings

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/SetupValidator.kt`, `.../ui/RefreshStatusText.kt`, `.../ui/PitWatchRoot.kt`, `.../ui/SetupScreen.kt`, `.../ui/HomeScreen.kt`, `.../ui/SettingsScreen.kt` (all under `android/app/src/main/kotlin/com/pitwatch/app/`)
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/MainActivity.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/SetupValidatorTest.kt`, `android/app/src/test/kotlin/com/pitwatch/app/ui/RefreshStatusTextTest.kt`

**Interfaces:**
- Consumes: `AppContainer`, `RefreshWorker`, `LiveMatchService`, `rearmAutoStart`, `AutoStartPlanner`, `EventKeys`.
- Produces: `object SetupValidator { sealed interface Outcome { Valid(teamNumber, apiKey, nexusApiKey: String?, teamName: String?); Invalid(message) }; suspend fun validate(apiKey: String, teamNumberText: String, nexusKey: String, client: (String) -> TbaClient): Outcome }`; `object RefreshStatusText { fun format(state: RefreshState, now: Instant): String; fun ago(then: Instant, now: Instant): String }`; composables `PitWatchRoot(container)`, `SetupScreen`, `HomeScreen`, `SettingsScreen`.

- [ ] **Step 1: Write the failing tests**

`android/app/src/test/kotlin/com/pitwatch/app/ui/SetupValidatorTest.kt`:
```kotlin
package com.pitwatch.app.ui

import com.pitwatch.app.FakeApi
import com.pitwatch.core.api.TbaClient
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SetupValidatorTest {
    private val tba = FakeApi().apply { on("/team/frc5507") { """{"key":"frc5507","team_number":5507,"nickname":"Robotic Eagles"}""" } }
    private val client: (String) -> TbaClient = { TbaClient(it, tba.client, "https://tba.test/api/v3") }

    @Test
    fun `valid key and team`() = runBlocking {
        val outcome = assertIs<SetupValidator.Outcome.Valid>(SetupValidator.validate(" key ", " 5507 ", "  ", client))
        assertEquals(5507, outcome.teamNumber)
        assertEquals("key", outcome.apiKey)
        assertEquals(null, outcome.nexusApiKey)
        assertEquals("Robotic Eagles", outcome.teamName)
    }

    @Test
    fun `local checks happen before any request`() = runBlocking {
        assertEquals("Enter your TBA API key", (SetupValidator.validate("", "5507", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals("Enter a valid team number", (SetupValidator.validate("k", "abc", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals("Enter a valid team number", (SetupValidator.validate("k", "0", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals(0, tba.requests.size)
    }

    @Test
    fun `TBA rejections become readable messages`() = runBlocking {
        tba.on("/team/frc5507", HttpStatusCode.Unauthorized) { "" }
        assertEquals("TBA rejected that API key", (SetupValidator.validate("bad", "5507", "", client) as SetupValidator.Outcome.Invalid).message)
        assertEquals("Team 9999 not found", (SetupValidator.validate("k", "9999", "", client) as SetupValidator.Outcome.Invalid).message)
    }
}
```

`android/app/src/test/kotlin/com/pitwatch/app/ui/RefreshStatusTextTest.kt`:
```kotlin
package com.pitwatch.app.ui

import com.pitwatch.core.store.RefreshState
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.Test

class RefreshStatusTextTest {
    private val now = Instant.ofEpochSecond(1_800_000_000)

    @Test
    fun `relative ages`() {
        assertEquals("just now", RefreshStatusText.ago(now.minusSeconds(30), now))
        assertEquals("5m ago", RefreshStatusText.ago(now.minusSeconds(300), now))
        assertEquals("2h ago", RefreshStatusText.ago(now.minusSeconds(7300), now))
        assertEquals("3d ago", RefreshStatusText.ago(now.minusSeconds(3 * 86400 + 5), now))
    }

    @Test
    fun `status includes errors when present`() {
        assertEquals("Last refresh: never", RefreshStatusText.format(RefreshState(), now))
        val state = RefreshState(lastRefreshEpochMs = now.minusSeconds(300).toEpochMilli(), lastError = "API error 401", nexusLastError = "Nexus data unavailable")
        assertEquals("Last refresh: 5m ago\nError: API error 401\nNexus: Nexus data unavailable", RefreshStatusText.format(state, now))
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.*' --console=plain)`
Expected: FAIL — `Unresolved reference 'SetupValidator'`, `'RefreshStatusText'`.

- [ ] **Step 3: Implement the tested logic**

`android/app/src/main/kotlin/com/pitwatch/app/ui/SetupValidator.kt`:
```kotlin
package com.pitwatch.app.ui

import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.api.TbaException
import kotlin.coroutines.cancellation.CancellationException

object SetupValidator {
    sealed interface Outcome {
        data class Valid(val teamNumber: Int, val apiKey: String, val nexusApiKey: String?, val teamName: String?) : Outcome
        data class Invalid(val message: String) : Outcome
    }

    /** Local checks first, then confirms key + team against TBA (`/team/frc{n}`). */
    suspend fun validate(apiKey: String, teamNumberText: String, nexusKey: String, client: (String) -> TbaClient): Outcome {
        val key = apiKey.trim()
        if (key.isEmpty()) return Outcome.Invalid("Enter your TBA API key")
        val team = teamNumberText.trim().toIntOrNull()?.takeIf { it > 0 } ?: return Outcome.Invalid("Enter a valid team number")
        return try {
            val found = client(key).validateTeam(team)
            Outcome.Valid(team, key, nexusKey.trim().ifEmpty { null }, found.nickname)
        } catch (e: CancellationException) {
            throw e
        } catch (e: TbaException) {
            Outcome.Invalid(
                when (e.statusCode) {
                    401 -> "TBA rejected that API key"
                    404 -> "Team $team not found"
                    else -> "TBA error ${e.statusCode}"
                },
            )
        } catch (e: Exception) {
            Outcome.Invalid("Couldn't reach TBA: ${e.message}")
        }
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/ui/RefreshStatusText.kt`:
```kotlin
package com.pitwatch.app.ui

import com.pitwatch.core.store.RefreshState
import java.time.Duration
import java.time.Instant

object RefreshStatusText {
    fun format(state: RefreshState, now: Instant): String = buildString {
        append("Last refresh: ")
        append(state.lastRefreshEpochMs?.let { ago(Instant.ofEpochMilli(it), now) } ?: "never")
        state.lastError?.let { append("\nError: ").append(it) }
        state.nexusLastError?.let { append("\nNexus: ").append(it) }
    }

    fun ago(then: Instant, now: Instant): String {
        val minutes = Duration.between(then, now).toMinutes()
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            else -> "${minutes / (24 * 60)}d ago"
        }
    }
}
```

- [ ] **Step 4: Run to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 4 tests pass.

- [ ] **Step 5: Implement the screens** (functional only; visual design is deferred per the spec)

`android/app/src/main/kotlin/com/pitwatch/app/ui/PitWatchRoot.kt`:
```kotlin
package com.pitwatch.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer

@Composable
fun PitWatchRoot(container: AppContainer) {
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = null)
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val current = config ?: return
    when {
        !current.isConfigured -> SetupScreen(container, current)
        showSettings -> {
            BackHandler { showSettings = false }
            SettingsScreen(container, current, onBack = { showSettings = false })
        }
        else -> HomeScreen(container, current, onOpenSettings = { showSettings = true })
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/ui/SetupScreen.kt`:
```kotlin
package com.pitwatch.app.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pitwatch.app.AppContainer
import com.pitwatch.app.BuildConfig
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(container: AppContainer, config: UserConfig) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var team by rememberSaveable { mutableStateOf(config.teamNumber?.toString().orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by rememberSaveable { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Set up PitWatch", style = MaterialTheme.typography.headlineSmall)
        Text("Get a read API key from your account page on thebluealliance.com.")
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true)
        OutlinedTextField(team, { team = it }, label = { Text("Team number") }, singleLine = true)
        OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key (optional)") }, singleLine = true)
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !busy,
            onClick = {
                busy = true
                scope.launch {
                    val http = HttpClient(OkHttp)
                    val outcome = SetupValidator.validate(apiKey, team, nexusKey) { TbaClient(it, http, BuildConfig.TBA_BASE_URL) }
                    http.close()
                    busy = false
                    when (outcome) {
                        is SetupValidator.Outcome.Invalid -> message = outcome.message
                        is SetupValidator.Outcome.Valid -> {
                            container.stores.config.updateData {
                                it.copy(teamNumber = outcome.teamNumber, apiKey = outcome.apiKey, nexusApiKey = outcome.nexusApiKey)
                            }
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            RefreshWorker.ensureScheduled(context)
                        }
                    }
                }
            },
        ) { Text(if (busy) "Checking…" else "Continue") }
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/ui/HomeScreen.kt`:
```kotlin
package com.pitwatch.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.store.EventCache
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

/** Placeholder home until Plan 3's match list: event, next match, status, actions. */
@Composable
fun HomeScreen(container: AppContainer, config: UserConfig, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache by container.stores.cache.data.collectAsStateWithLifecycle(initialValue = EventCache())
    val next = MatchSchedule(cache.matches, config.teamKey.orEmpty()).nextMatch
    val time = DateTimeFormatter.ofPattern("EEE h:mm a").withZone(ZoneId.systemDefault())

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Team ${config.teamNumber}", style = MaterialTheme.typography.headlineSmall)
        Text(cache.event?.name ?: "No event yet")
        Text(
            next?.let { "Next: ${it.label}" + (it.matchDate()?.let { at -> " · ~${time.format(at)}" } ?: "") }
                ?: "No upcoming matches",
            style = MaterialTheme.typography.titleMedium,
        )
        Button(onClick = {
            scope.launch { container.stores.liveControl.updateData { LiveControl() } }
            LiveMatchService.start(context)
        }) { Text("Start live tracking") }
        OutlinedButton(onClick = {
            scope.launch {
                container.repository.refresh(container.clock(), force = true)
                rearmAutoStart(context, container)
            }
        }) { Text("Refresh now") }
        OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/ui/SettingsScreen.kt`:
```kotlin
package com.pitwatch.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.core.config.EventKeys
import com.pitwatch.core.config.LiveActivityMode
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.RefreshState
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(container: AppContainer, config: UserConfig, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshState by container.stores.refreshState.data.collectAsStateWithLifecycle(initialValue = RefreshState())
    var eventOverride by rememberSaveable { mutableStateOf(config.eventKeyOverride.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }

    fun update(transform: (UserConfig) -> UserConfig) = scope.launch {
        container.stores.config.updateData { transform(it) }
        rearmAutoStart(context, container)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) { Text("‹ Back") }
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Text("Time source", style = MaterialTheme.typography.titleMedium)
        Choice("FRC Nexus (queue times)", config.effectiveTimeSource == TimeSource.NEXUS, enabled = config.isNexusConfigured) {
            update { it.copy(timeSource = TimeSource.NEXUS) }
        }
        Choice("TBA (match times)", config.effectiveTimeSource == TimeSource.TBA) { update { it.copy(timeSource = TimeSource.TBA) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Use scheduled (not predicted) TBA times", Modifier.weight(1f))
            Switch(config.useScheduledTime, { checked -> update { it.copy(useScheduledTime = checked) } })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Queue offset: ${config.queueOffsetMinutes} min", Modifier.weight(1f))
            TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes - 5).coerceAtLeast(0)) } }) { Text("−") }
            TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes + 5).coerceAtMost(60)) } }) { Text("+") }
        }

        Text("Live tracking", style = MaterialTheme.typography.titleMedium)
        Choice("Near match (2 h before)", config.liveActivityMode == LiveActivityMode.NEAR_MATCH) {
            update { it.copy(liveActivityMode = LiveActivityMode.NEAR_MATCH) }
        }
        Choice("All day", config.liveActivityMode == LiveActivityMode.ALL_DAY) { update { it.copy(liveActivityMode = LiveActivityMode.ALL_DAY) } }
        if (!NotificationManagerCompat.from(context).canPostPromotedNotifications()) {
            Text("Live Updates are turned off for PitWatch, so tracking shows as a normal notification.")
            OutlinedButton(onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }) { Text("Notification settings") }
        }

        Text("Event", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            eventOverride, { eventOverride = it.trim().lowercase() },
            label = { Text("Event key override (blank = auto)") }, singleLine = true,
            isError = eventOverride.isNotEmpty() && !EventKeys.isValid(eventOverride),
        )
        Button(
            enabled = eventOverride.isEmpty() || EventKeys.isValid(eventOverride),
            onClick = { update { it.copy(eventKeyOverride = eventOverride.ifEmpty { null }) } },
        ) { Text("Save event") }

        Text("API keys", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true)
        OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true)
        Button(onClick = { update { it.copy(apiKey = apiKey.trim(), nexusApiKey = nexusKey.trim().ifEmpty { null }) } }) { Text("Save keys") }

        Text("Status", style = MaterialTheme.typography.titleMedium)
        Text(RefreshStatusText.format(refreshState, container.clock()))
        OutlinedButton(onClick = {
            scope.launch {
                container.repository.refresh(container.clock(), force = true)
                rearmAutoStart(context, container)
                RefreshWorker.ensureScheduled(context)
            }
        }) { Text("Force refresh") }

        Text("Queue data from frc.nexus · Match data from The Blue Alliance", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = onSelect, enabled = enabled)
        Text(label)
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/MainActivity.kt` becomes:
```kotlin
package com.pitwatch.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.AutoStartPlanner
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.app.ui.PitWatchRoot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as PitWatchApp).container
        RefreshWorker.ensureScheduled(this)
        // Opening the app inside the live window starts tracking (unless the user stopped this match).
        container.scope.launch {
            val now = container.clock()
            val start = AutoStartPlanner.nextStart(
                container.stores.cache.data.first(), container.stores.config.data.first(),
                container.stores.liveControl.data.first(), now,
            )
            if (start != null && start <= now) LiveMatchService.start(this@MainActivity)
        }
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Surface { PitWatchRoot(container) }
            }
        }
    }
}
```

The Task 2 `SmokeTest` "main activity launches" now needs WorkManager initialized under Robolectric. Add to `SmokeTest`:
```kotlin
    @org.junit.Before
    fun initWorkManager() {
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(
            ApplicationProvider.getApplicationContext(),
            androidx.work.Configuration.Builder().setExecutor(androidx.work.testing.SynchronousExecutor()).build(),
        )
    }
```

- [ ] **Step 6: Run the whole `:app` suite and build**

Run: `(cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL`; all tests pass; APK builds.

- [ ] **Step 7: Commit**

```bash
git add android/app
git commit -m "feat(app): minimal setup, home and settings screens"
```

---

### Task 12: Fake API server and manual on-device test

**Files:**
- Create: `scripts/fake-api.py`, `android/app/src/debug/AndroidManifest.xml`, `docs/android-manual-test.md`
- Modify: `README.md`

**Interfaces:**
- Consumes: the snapshot dirs in `scripts/fixtures/2026cancmp/`; `BuildConfig.TBA_BASE_URL`/`NEXUS_BASE_URL` (Task 9).
- Produces: `python3 scripts/fake-api.py [--port 8765] SNAPSHOT_DIR [SNAPSHOT_DIR …] [--switch-after SECONDS]` serving `/api/v3/...` (TBA) and `/api/v1/...` (Nexus) with every timestamp shifted so the snapshot's `dataAsOfTime` is "now" and the event spans today.

- [ ] **Step 1: Write the server**

`scripts/fake-api.py`:
```python
#!/usr/bin/env python3
"""Serve captured TBA + Nexus snapshots as a local fake API, shifted to "now".

Every timestamp is moved so the snapshot's Nexus dataAsOfTime equals the moment it is served, and the
event's dates are rewritten to span today, so the Android app sees a live event. With several snapshot
dirs, the server moves to the next one every --switch-after seconds (replaying an event's progress).

Usage: python3 scripts/fake-api.py [--port 8765] [--switch-after 300] DIR [DIR ...]
Point the debug build at it with -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3
and -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1 (10.0.2.2 = host, from the emulator).
"""
import argparse
import datetime as dt
import json
import pathlib
import re
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

TBA_SECONDS = {"time", "predicted_time", "actual_time", "post_result_time"}
STARTED = time.time()


def load(directory):
    d = pathlib.Path(directory)
    return {f.stem: json.loads(f.read_text()) for f in d.glob("*.json")}


def shift(value, delta_ms):
    """Shift TBA second-timestamps and every Nexus millisecond field named *Time."""
    if isinstance(value, list):
        return [shift(v, delta_ms) for v in value]
    if not isinstance(value, dict):
        return value
    out = {}
    for k, v in value.items():
        if isinstance(v, (int, float)) and not isinstance(v, bool):
            if k in TBA_SECONDS:
                v = int(v + delta_ms / 1000)
            elif k.endswith("Time"):
                v = int(v + delta_ms)
        out[k] = shift(v, delta_ms)
    return out


def current(snapshots, switch_after):
    index = min(len(snapshots) - 1, int((time.time() - STARTED) // switch_after))
    snap = snapshots[index]
    delta_ms = int(time.time() * 1000) - snap["nexus_event"]["dataAsOfTime"]
    today = dt.date.today()
    event = dict(snap["tba_event"], start_date=str(today - dt.timedelta(days=1)), end_date=str(today + dt.timedelta(days=2)), year=today.year)
    return snap, delta_ms, event


class Handler(BaseHTTPRequestHandler):
    snapshots = []
    switch_after = 300

    def do_GET(self):
        snap, delta, event = current(self.snapshots, self.switch_after)
        key = event["key"]
        routes = {
            rf"/api/v3/team/frc\d+/events/\d+": [event],
            rf"/api/v3/team/frc(\d+)": None,
            rf"/api/v3/event/{key}": event,
            rf"/api/v3/event/{key}/matches": shift(snap["tba_matches"], delta),
            rf"/api/v3/event/{key}/rankings": snap["tba_rankings"],
            rf"/api/v3/event/{key}/oprs": snap["tba_oprs"],
            rf"/api/v1/event/{key}": shift(snap["nexus_event"], delta),
            rf"/api/v1/event/{key}/map": snap["nexus_map"],
        }
        for pattern, body in routes.items():
            match = re.fullmatch(pattern, self.path)
            if not match:
                continue
            if body is None:  # team validation: echo any team number
                body = {"key": f"frc{match.group(1)}", "team_number": int(match.group(1)), "nickname": "Fake Team"}
            data = json.dumps(body).encode()
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)
            return
        self.send_error(404)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("dirs", nargs="+")
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--switch-after", type=int, default=300)
    args = parser.parse_args()
    Handler.snapshots = [load(d) for d in args.dirs]
    Handler.switch_after = args.switch_after
    print(f"Serving {len(args.dirs)} snapshot(s) on http://0.0.0.0:{args.port}")
    ThreadingHTTPServer(("0.0.0.0", args.port), Handler).serve_forever()


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Verify the server shifts time and serves every route**

Run:
```bash
python3 scripts/fake-api.py --port 8765 scripts/fixtures/2026cancmp/2026-04-11T00-51-22Z & SERVER=$!; sleep 1
NOW_MS=$(python3 -c 'import time; print(int(time.time()*1000))')
curl -sf localhost:8765/api/v1/event/2026cancmp | python3 -c "import json,sys; d=json.load(sys.stdin); assert abs(d['dataAsOfTime']-$NOW_MS) < 5000, d['dataAsOfTime']; print('nexus shifted OK')"
for p in /api/v3/team/frc5507 /api/v3/team/frc5507/events/2026 /api/v3/event/2026cancmp /api/v3/event/2026cancmp/matches /api/v3/event/2026cancmp/rankings /api/v3/event/2026cancmp/oprs /api/v1/event/2026cancmp/map; do curl -sf -o /dev/null "localhost:8765$p" && echo "200 $p"; done
kill $SERVER
```
Expected: `nexus shifted OK`, then `200` for all seven paths.

- [ ] **Step 3: Allow cleartext to the local server in debug builds only**

`android/app/src/debug/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- Debug only: talk to scripts/fake-api.py over plain HTTP. -->
    <application android:usesCleartextTraffic="true" />
</manifest>
```

- [ ] **Step 4: Write the manual test guide**

`docs/android-manual-test.md`:
````markdown
# Android manual test: live notification against a fake event

1. Start an Android 16 (API 36) emulator, e.g. `~/Library/Android/sdk/emulator/emulator -avd NotificationFlow_API36 &`.
2. Serve the captured event, shifted to now:
   ```bash
   python3 scripts/fake-api.py scripts/fixtures/2026cancmp/2026-04-10T22-05-28Z scripts/fixtures/2026cancmp/2026-04-11T00-51-22Z --switch-after 120
   ```
3. Install a debug build pointed at it:
   ```bash
   (cd android && ./gradlew :app:installDebug -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3 -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1)
   ```
4. Open PitWatch: any TBA key, team **5507**, any Nexus key → Continue → allow notifications.
5. Tap **Start live tracking**. Expect a Live Update chip in the status bar (`Q… Nm`) and, in the shade,
   the title/text/segmented progress. After ~2 min the server switches snapshots; within ~30 s the
   notification should change phase (Refresh in the notification forces it immediately).
6. Tap **Stop** (or swipe the notification away). Reopening the app must not restart tracking for the
   same match.
````

- [ ] **Step 5: Update the README layout line**

In `README.md`, replace the `android/` line with:
```
android/    Android app (Gradle). :core = Kotlin port of TBAKit; :app = data layer + live notification
            (`cd android && ./gradlew :core:test :app:testDebugUnitTest`; manual test: docs/android-manual-test.md)
```
and replace the `scripts/` line with:
```
scripts/    Fixture capture + fake-api.py (local TBA/Nexus replay server)
```

- [ ] **Step 6: Full verification**

Run: `(cd android && ./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL`; all tests pass; debug APK built.

- [ ] **Step 7: Commit**

```bash
git add scripts/fake-api.py android/app/src/debug docs/android-manual-test.md README.md
git commit -m "test(android): fake API replay server and manual on-device guide"
```

---

## Next

- **Plan 3:** match list (with schedule breaks), event picker, pit map, Glance widgets, and the visual design pass on the notification and screens.
- **Open decision:** Nexus `breakAfter` markers vs the inferred `ScheduleBreakDetector`.

## Carry into Plan 3 (from the final review of this plan)

Deferred minors — pick up when touching the code nearby:
- Nexus blip window compares server `dataAsOfTime` with the device clock; stamp the last successful Nexus fetch with device time instead.
- The live service decodes TBA JSON on `Dispatchers.Main` (`Repository.refresh` should `withContext(Dispatchers.Default)` for decode).
- `registerDefaultNetworkCallback` fires `onAvailable` immediately → a redundant second poll at start.
- `MainActivity` isn't `singleTop`; repeated notification taps can stack instances.
- `SetupScreen`: do the permission request / `refreshNow` before the config write (the write recomposes away the screen's scope); close the setup `HttpClient` in `finally`.
- Settings' "Live Updates are off" hint shows on API 36.0, where promotion doesn't exist (needs 36.1+).
- `RefreshWorker`: if `rearmAutoStart` throws after the APPEND enqueue the chain stalls; changing time source / `useScheduledTime` doesn't replan cadence.
- Dismissing a FINAL card suppresses the played match, so the following match may auto-start immediately — confirm UX.
- No BOOT_COMPLETED receiver: the exact alarm is lost on reboot until the worker re-arms (≤ 1 h on event days).
- Near-match mode tracks an unscored match indefinitely if TBA never posts the score; consider a time-based roll-forward.
- Screen-off polling now uses `LiveWakeAlarm` (exact alarm, ≥ 1 min apart) + a poll-scoped wake lock. Deep Doze still throttles allow-while-idle alarms to ~1 per 9 min; only `setAlarmClock` (shows an alarm icon) beats that.

On-device notes: API 36.0 emulators lack Live Update promotion (needs 36.1+); emulator clocks can drift — sync before judging countdowns.
