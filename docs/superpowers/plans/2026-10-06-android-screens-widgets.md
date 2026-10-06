# Android Screens, Widgets & Visual Design Implementation Plan (Plan 3 of 3)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the Android app's user-facing surfaces — Matches list (with named schedule breaks), event picker, pit map, restyled Settings, Material You navigation, and a resizable Glance home-screen widget.

**Architecture:** Everything a screen or widget shows is computed by pure models (`ScheduleBreaks` in `:core`; `MatchListModels`, `WidgetModels`, `PitMapGeometry`, `PromotionHint` in `:app`) from `(cache, config, now)` and unit-tested on the JVM. Compose screens are split into a stateless `…Content` (Robolectric Compose-tested) and a thin stateful `…Screen` that collects DataStore flows. The widget renders only from the persisted cache and is refreshed by a `Repository` change hook.

**Tech Stack:** Jetpack Compose Material 3 (BOM 2026.09.00, dynamic color), material-icons-core, Jetpack Glance 1.2.0 (+ glance-material3, glance-appwidget-testing), Compose UI test (Robolectric), existing `:core` / `:app`.

**Spec:** `docs/superpowers/specs/2026-10-05-android-screens-widgets-design.md` (and "Carry into Plan 3" at the end of `docs/superpowers/plans/2026-10-05-android-app-live.md`).

## Global Constraints

- Branch `android-screens` off `main` (already created; the spec is committed there). Modular commits; merge back to `main` when done.
- **Material You native:** Material 3 components, `dynamicLightColorScheme`/`dynamicDarkColorScheme`, light/dark follows the system. Phase colors only for status: Pre-queue `#636366`, Queueing `#FF9500`, On deck `#FF6B00`, On field `#30D158`; alliance dots red `#FF3B30`, blue `#1E6FFF`.
- **Breaks:** Nexus `breakAfter` markers when the event has any, named by the marker; otherwise `ScheduleBreakDetector` inference.
- **Navigation:** bottom `NavigationBar` with **Matches · Pit map · Settings**; event picker opens from the Matches top app bar.
- Widgets never touch the network; they render from the persisted cache.
- Day grouping and break times use the **event's** time zone (`Event.zone`); clock times shown to the user use the device zone.
- Promotion hint only when `Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1` and `!canPostPromotedNotifications()`.
- Never install on or drive the user's attached phone or their running `NotificationFlow_API36` emulator; on-device checks use a separate AVD by serial.
- Pure logic takes `now: Instant` and a `Locale` (default `Locale.getDefault()`; tests pass `Locale.US`).

## Review Focus

1. **Events without Nexus (TBA-only).** Expected: rows show TBA times, no phase badges, no break rows, no crash. → test in Task 3.
2. **Device time zone ≠ event time zone** (a parent watching from another coast). Expected: day headers and break placement follow the event's zone. → test in Task 3.
3. **Matches with no time yet** (late-scheduled playoffs). Expected: they still list, under the previous day or "Time TBD", never dropped. → test in Task 3.
4. **Next match's countdown already in the past** (delay, stale cache). Expected: the widget shows no negative chronometer. → test in Task 4.
5. **Team with no pit on the map / empty map.** Expected: map renders without a highlight, no crash. → test in Task 8.

---

## File Structure

```
android/core/src/main/kotlin/com/pitwatch/core/logic/
  ScheduleBreakDetector.kt        (modify: ScheduleBreak.label, nullable end/endsBefore, title)
  ScheduleBreaks.kt               (new: markers-first break source)
  MatchSchedule.kt                (modify: upcomingTimeline uses ScheduleBreaks)
android/gradle/libs.versions.toml (modify: icons, ui-test, glance)
android/app/build.gradle.kts      (modify: deps)
android/app/src/main/AndroidManifest.xml (modify: singleTop, widget receiver)
android/app/src/main/res/xml/pitwatch_widget_info.xml, layout/widget_countdown.xml,
  values/strings.xml, values/colors.xml, values-night/colors.xml   (new)
android/app/src/main/kotlin/com/pitwatch/app/
  AppContainer.kt                 (modify: updateWidgets)
  data/Repository.kt              (modify: onChanged hook, seasonEvents, pitMap)
  live/LiveMatchService.kt        (modify: tracking StateFlow, skip first onAvailable)
  live/LiveNotification.kt        (modify: accent color)
  ui/theme/Theme.kt               (new: PitWatchTheme, StatusColors)
  ui/PitWatchRoot.kt              (rewrite: bottom nav shell)
  ui/HomeScreen.kt                (delete)
  ui/SetupScreen.kt               (modify: ordering + finally)
  ui/SettingsScreen.kt            (rewrite: M3 sections), ui/PromotionHint.kt (new)
  ui/matches/MatchListModel.kt, MatchesScreen.kt        (new)
  ui/events/EventPicker.kt                                (new)
  ui/pitmap/PitMapGeometry.kt, PitMapScreen.kt            (new)
  widget/WidgetModel.kt, WidgetContent.kt, PitWatchWidget.kt (new)
```

Commands run from the repo root as `(cd android && ./gradlew …)`.

---

### Task 1: `:core` — named breaks from Nexus markers

**Files:**
- Modify: `android/core/src/main/kotlin/com/pitwatch/core/logic/ScheduleBreakDetector.kt`, `android/core/src/main/kotlin/com/pitwatch/core/logic/MatchSchedule.kt`
- Create: `android/core/src/main/kotlin/com/pitwatch/core/logic/ScheduleBreaks.kt`
- Test: `android/core/src/test/kotlin/com/pitwatch/core/logic/ScheduleBreaksTest.kt`

**Interfaces:**
- Produces: `ScheduleBreak(kind, startsAfter: String, endsBefore: String?, start: Instant, end: Instant?, label: String? = null)` with `duration: Duration?` and `title: String`; `object ScheduleBreaks { fun forEvent(nexusEvent: NexusEvent, zone: ZoneId): List<ScheduleBreak>; fun fromMarkers(matches: List<NexusMatch>): List<ScheduleBreak> }`. `MatchSchedule.upcomingTimeline` now uses `ScheduleBreaks.forEvent`.

- [ ] **Step 1: Write the failing tests**

`android/core/src/test/kotlin/com/pitwatch/core/logic/ScheduleBreaksTest.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.LA
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.fixture
import com.pitwatch.core.localInstant
import com.pitwatch.core.logic.ScheduleBreak.Kind
import com.pitwatch.core.logic.UpcomingScheduleItem.BreakItem
import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import com.pitwatch.core.model.NexusMatchTimes
import com.pitwatch.core.testMatch
import com.pitwatch.core.unixMs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScheduleBreaksTest {
    private val snapshot = PitWatchJson.decodeFromString<NexusEvent>(fixture("2026cancmp/2026-04-11T00-51-22Z/nexus_event.json"))
    private fun m(label: String, start: Long?, breakAfter: String? = null) =
        NexusMatch(label, times = NexusMatchTimes(estimatedStartTime = start), breakAfter = breakAfter)

    @Test
    fun `markers from the real event, named by Nexus`() {
        val breaks = ScheduleBreaks.forEvent(snapshot, LA)
        assertEquals(listOf("Lunch", "End of day", "Lunch", "End of day", "Alliance selection"), breaks.map { it.title })
        assertEquals(listOf("Practice 11", "Qualification 38", "Qualification 62", "Qualification 96", "Qualification 120"), breaks.map { it.startsAfter })
        assertEquals(listOf(Kind.LUNCH, Kind.OVERNIGHT, Kind.LUNCH, Kind.OVERNIGHT, Kind.SESSION_BREAK), breaks.map { it.kind })
        assertEquals("Qualification 1", breaks.first().endsBefore)
    }

    @Test
    fun `a marker after the last match has no end`() {
        val last = ScheduleBreaks.forEvent(snapshot, LA).last()
        assertNull(last.endsBefore)
        assertNull(last.end)
        assertNull(last.duration)
    }

    @Test
    fun `markers on matches without a start time are skipped`() {
        val t = localInstant("2026-04-11T11:00:00", LA).unixMs
        val breaks = ScheduleBreaks.fromMarkers(listOf(m("Q1", null, "Lunch"), m("Q2", t, "Break"), m("Q3", t + 60_000)))
        assertEquals(listOf("Break"), breaks.map { it.title })
    }

    @Test
    fun `without markers, falls back to inference with generic titles`() {
        val event = NexusEvent(0, matches = listOf(
            m("Q1", localInstant("2026-04-11T15:00:00", LA).unixMs),
            m("Q2", localInstant("2026-04-11T15:45:00", LA).unixMs),
        ))
        val only = ScheduleBreaks.forEvent(event, LA).single()
        assertEquals(Kind.SESSION_BREAK, only.kind)
        assertNull(only.label)
        assertEquals("Break", only.title)
    }

    @Test
    fun `upcoming timeline uses markers even for gaps inference would ignore`() {
        // 10-minute gap: inference (20-min threshold) finds nothing, but Nexus says "Field reset".
        val t = localInstant("2026-04-11T14:00:00", LA).unixMs
        val event = NexusEvent(0, matches = listOf(m("Qualification 10", t, "Field reset"), m("Qualification 20", t + 10 * 60_000)))
        val timeline = MatchSchedule(listOf(testMatch(10), testMatch(20)), "frc1234").upcomingTimeline(event, LA)
        assertEquals(listOf("Field reset"), timeline.filterIsInstance<BreakItem>().map { it.scheduleBreak.title })
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :core:test --console=plain)`
Expected: FAIL — `Unresolved reference 'ScheduleBreaks'`, `'title'`.

- [ ] **Step 3: Implement**

In `ScheduleBreakDetector.kt`, replace the `ScheduleBreak` class with:
```kotlin
/** A non-match interval: from Nexus's own markers, or inferred from gaps in estimated start times. */
data class ScheduleBreak(
    val kind: Kind,
    val startsAfter: String,
    /** Null for a marker after the last scheduled match (e.g. alliance selection). */
    val endsBefore: String?,
    val start: Instant,
    val end: Instant?,
    /** Nexus's name for the break ("Lunch", "Alliance selection"); null when inferred. */
    val label: String? = null,
) {
    enum class Kind {
        /** Same local day, overlapping 11:30–13:00. */
        LUNCH,
        /** Same local day, not overlapping midday. */
        SESSION_BREAK,
        /** Crosses a local-day boundary. */
        OVERNIGHT,
    }

    val duration: Duration? get() = end?.let { Duration.between(start, it) }

    /** What to call it in the UI. */
    val title: String
        get() = label ?: when (kind) {
            Kind.LUNCH -> "Lunch"
            Kind.OVERNIGHT -> "End of day"
            Kind.SESSION_BREAK -> "Break"
        }
}
```

`android/core/src/main/kotlin/com/pitwatch/core/logic/ScheduleBreaks.kt`:
```kotlin
package com.pitwatch.core.logic

import com.pitwatch.core.model.NexusEvent
import com.pitwatch.core.model.NexusMatch
import java.time.ZoneId

/** Where breaks come from: Nexus's explicit `breakAfter` markers when an event has any, else inference. */
object ScheduleBreaks {
    fun forEvent(nexusEvent: NexusEvent, zone: ZoneId): List<ScheduleBreak> =
        if (nexusEvent.matches.any { it.breakAfter != null }) {
            fromMarkers(nexusEvent.matches)
        } else {
            ScheduleBreakDetector.detectBreaks(nexusEvent.matches, zone)
        }

    /** Each marker becomes a break from its match's start until the next match's start (none after the last). */
    fun fromMarkers(matches: List<NexusMatch>): List<ScheduleBreak> {
        val stamped = matches.mapNotNull { m -> m.times.startDate?.let { m to it } }.sortedBy { it.second }
        return stamped.mapIndexedNotNull { index, (match, start) ->
            val label = match.breakAfter ?: return@mapIndexedNotNull null
            val next = stamped.getOrNull(index + 1)
            ScheduleBreak(kindOf(label), match.label, next?.first?.label, start, next?.second, label)
        }
    }

    private fun kindOf(label: String): ScheduleBreak.Kind {
        val text = label.lowercase()
        return when {
            "lunch" in text -> ScheduleBreak.Kind.LUNCH
            "end of day" in text -> ScheduleBreak.Kind.OVERNIGHT
            else -> ScheduleBreak.Kind.SESSION_BREAK
        }
    }
}
```

In `MatchSchedule.kt` `upcomingTimeline`, replace `ScheduleBreakDetector.detectBreaks(nexusEvent.matches, zone)` with `ScheduleBreaks.forEvent(nexusEvent, zone)`, and update its KDoc's first sentence to: `Upcoming matches with schedule breaks (Nexus markers, else inferred) inserted between consecutive matches.`

- [ ] **Step 4: Run all `:core` tests**

Run: `(cd android && ./gradlew :core:test --console=plain)`
Expected: `BUILD SUCCESSFUL`. Existing detector/timeline tests still pass (their fixtures either lack markers or the markers agree with inference).

- [ ] **Step 5: Run `:app` tests (ScheduleBreak shape changed)**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --console=plain)`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add android/core
git commit -m "feat(core): name schedule breaks from Nexus markers, inference as fallback"
```

---

### Task 2: Repository — change hook, season events, pit map

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/data/Repository.kt`, `android/app/src/test/kotlin/com/pitwatch/app/TestSupport.kt` (snapshotNexus map route)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/data/RepositoryExtrasTest.kt`

**Interfaces:**
- Produces: `Repository(stores, tbaClient, nexusClient, onChanged: suspend () -> Unit = {})`; `suspend fun seasonEvents(now: Instant): List<Event>` (throws on TBA failure); `suspend fun pitMap(): PitMap?`.

- [ ] **Step 1: Add the map route to the test Nexus fake**

In `TestSupport.kt`, change `snapshotNexus()` to:
```kotlin
fun snapshotNexus() = FakeApi().apply {
    on("/event/2026cancmp") { fixture("$SNAP/nexus_event.json") }
    on("/event/2026cancmp/map") { fixture("$SNAP/nexus_map.json") }
}
```

- [ ] **Step 2: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/data/RepositoryExtrasTest.kt`:
```kotlin
package com.pitwatch.app.data

import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.snapshotNexus
import com.pitwatch.app.snapshotTba
import com.pitwatch.core.api.NexusClient
import com.pitwatch.core.api.TbaClient
import com.pitwatch.core.config.UserConfig
import io.ktor.http.HttpStatusCode
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RepositoryExtrasTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val tba = snapshotTba()
    private val nexus = snapshotNexus()
    private var changes = 0
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
            onChanged = { changes++ },
        )
        runBlocking { stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
    }

    @After
    fun tearDown() = runBlocking { job.cancelAndJoin() }

    @Test
    fun `change hook fires on changed refreshes only`() = runBlocking {
        repo.refresh(SNAP_NOW)
        assertEquals(1, changes)
        repo.refresh(SNAP_NOW) // identical data
        assertEquals(1, changes)
    }

    @Test
    fun `season events come from TBA`() = runBlocking {
        assertEquals(listOf("2026cancmp"), repo.seasonEvents(SNAP_NOW).map { it.key })
    }

    @Test
    fun `season events surface TBA failures`() {
        tba.on("/team/frc5507/events/2026", HttpStatusCode.Unauthorized) { "" }
        assertFailsWith<Exception> { runBlocking { repo.seasonEvents(SNAP_NOW) } }
    }

    @Test
    fun `pit map for the cached event`() = runBlocking {
        repo.refresh(SNAP_NOW)
        assertEquals("C1", repo.pitMap()?.pit(forTeam = "5507")?.address)
    }

    @Test
    fun `no pit map without a Nexus key or event`() = runBlocking {
        assertNull(repo.pitMap()) // no event cached yet
        stores.config.updateData { it.copy(nexusApiKey = null) }
        repo.refresh(SNAP_NOW)
        assertNull(repo.pitMap())
    }
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.data.RepositoryExtrasTest' --console=plain)`
Expected: FAIL — `No parameter with name 'onChanged'`, `Unresolved reference 'seasonEvents'`.

- [ ] **Step 4: Implement**

In `Repository.kt`:
- Add the constructor parameter `private val onChanged: suspend () -> Unit = {},` after `nexusClient`.
- Rename the existing `refresh` function to `private suspend fun refreshLocked(now: Instant, force: Boolean, includeTba: Boolean): RefreshOutcome` (keep its body, including `= mutex.withLock { … }`), and add above it:
```kotlin
    /**
     * TBA endpoints use If-Modified-Since unless [force]. With [includeTba] false only Nexus is polled
     * (the live notification's fast path). Serialized: the worker and the live service may overlap.
     * [onChanged] (widgets) runs after any refresh that changed what we show.
     */
    suspend fun refresh(now: Instant, force: Boolean = false, includeTba: Boolean = true): RefreshOutcome {
        val outcome = refreshLocked(now, force, includeTba)
        if (outcome.changed) onChanged()
        return outcome
    }
```
  and delete the old KDoc above `refreshLocked`.
- Add before `private data class ResolvedEvent`:
```kotlin
    /** The team's events this season, for the event picker. Throws on TBA failure. */
    suspend fun seasonEvents(now: Instant): List<Event> {
        val config = stores.config.data.first()
        val apiKey = config.apiKey ?: return emptyList()
        val team = config.teamNumber ?: return emptyList()
        val result = tbaClient(apiKey).fetch<List<Event>>(Endpoints.teamEvents(team, now.atZone(ZoneOffset.UTC).year))
        return (result as? FetchResult.Data)?.value.orEmpty().sortedBy { it.startDate }
    }

    /** Nexus pit map for the cached event; null without a Nexus key, an event, or a map. */
    suspend fun pitMap(): PitMap? {
        val nexusKey = stores.config.data.first().nexusApiKey?.takeIf { it.isNotEmpty() } ?: return null
        val eventKey = stores.cache.data.first().event?.key ?: return null
        return nexusClient(nexusKey).fetchPitMap(eventKey)
    }
```
  and add `import com.pitwatch.core.model.PitMap`.

- [ ] **Step 5: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --console=plain)`
Expected: `BUILD SUCCESSFUL`; all `:app` tests pass.

- [ ] **Step 6: Commit**

```bash
git add android/app
git commit -m "feat(app): repository change hook, season events, and pit map"
```

---

### Task 3: MatchListModel — what the Matches screen shows

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchListModel.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchListModelTest.kt`

**Interfaces:**
- Consumes: `MatchSchedule.upcomingTimeline` (Task 1), `PhaseDerivation`, `NexusMatchMerge`.
- Produces: `data class MatchListModel(title, status: Status?, nowQueuing: String?, nexusUnavailable: Boolean, error: String?, days: List<Day>, results: List<Result>, empty: Empty?)` with nested `Status(teamNumber, rank, record)` (`text`), `Day(date: LocalDate?, label, items)`, `sealed interface Item { id; Upcoming(row: MatchRow); Break(title, start, end: Instant?) }`, `MatchRow(key, label, shortLabel, time: Instant?, estimated, alliance: MatchAlliance?, phase: Phase?, red: AllianceLine, blue: AllianceLine, isNext, countdown: Countdown?)` (`url`), `AllianceLine(teams: List<TeamChip>, summedOpr: Double?)`, `TeamChip(number, isUs)`, `Countdown(deadline: Instant, target: String)`, `Result(key, label, shortLabel, ourScore, theirScore, outcome)` (`url`), `enum Empty { NOT_CONFIGURED, NO_EVENT, NO_MATCHES }`; `object MatchListModels { fun build(cache, config, refreshState, now, locale = Locale.getDefault()): MatchListModel }`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchListModelTest.kt`:
```kotlin
package com.pitwatch.app.ui.matches

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.testEvent
import com.pitwatch.app.testMatch
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventOPRs
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.util.Locale
import java.util.TimeZone
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class MatchListModelTest {
    private val cache = snapshotCache().copy(
        rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json")),
        oprs = PitWatchJson.decodeFromString<EventOPRs>(fixture("$SNAP/tba_oprs.json")),
    )
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(c: EventCache = cache, cfg: UserConfig = config, state: RefreshState = RefreshState()) =
        MatchListModels.build(c, cfg, state, SNAP_NOW, Locale.US)

    private fun MatchListModel.Day.names() = items.map {
        when (it) {
            is MatchListModel.Item.Upcoming -> it.row.shortLabel
            is MatchListModel.Item.Break -> it.title
        }
    }

    @Test
    fun `header, status and queue line`() {
        val m = build()
        assertEquals("California Northern", m.title)
        assertEquals("Team 5507 · Rank #34 · 1-2-0", m.status?.text)
        assertEquals("Qualification 38", m.nowQueuing)
        assertFalse(m.nexusUnavailable)
        assertNull(m.empty)
    }

    @Test
    fun `upcoming matches are grouped by event-local day with named breaks`() {
        val days = build().days
        assertEquals(listOf("Friday, Apr 10", "Saturday, Apr 11", "Sunday, Apr 12"), days.map { it.label })
        assertEquals(listOf("Q36", "End of day"), days[0].names())
        assertEquals(listOf("Q43", "Q52", "Q61", "Lunch", "Q72", "Q81", "End of day"), days[1].names())
        assertEquals(listOf("Q99", "Q106", "Q116"), days[2].names())
    }

    @Test
    fun `next match carries phase, countdown and alliances`() {
        val next = (build().days[0].items[0] as MatchListModel.Item.Upcoming).row
        assertTrue(next.isNext)
        assertEquals(Phase.ON_FIELD, next.phase)
        assertEquals(MatchListModel.Countdown(Instant.ofEpochMilli(1775868749195).plusSeconds(150), "to match end"), next.countdown)
        assertTrue(next.red.teams.single { it.isUs }.number == "5507")
        assertEquals(cache.oprs!!.summedOpr(listOf("frc4698", "frc5507", "frc1678")), next.red.summedOpr)
        assertTrue(next.estimated)
        val later = (build().days[1].items[0] as MatchListModel.Item.Upcoming).row
        assertFalse(later.isNext)
        assertNull(later.countdown)
    }

    @Test
    fun `results are most recent first from our side`() {
        val results = build().results
        assertEquals(listOf("Qual 22", "Qual 14", "Qual 1"), results.map { it.label })
        assertEquals(listOf("W", "L", "L"), results.map { it.outcome })
        assertEquals(403 to 299, results[0].ourScore to results[0].theirScore)
        assertEquals("https://www.thebluealliance.com/match/2026cancmp_qm22", results[0].url)
    }

    @Test
    fun `empty states`() {
        assertEquals(MatchListModel.Empty.NOT_CONFIGURED, build(cfg = UserConfig()).empty)
        assertEquals(MatchListModel.Empty.NO_EVENT, build(c = EventCache()).empty)
        assertEquals(MatchListModel.Empty.NO_MATCHES, build(c = EventCache(event = cache.event)).empty)
    }

    @Test
    fun `nexus unavailable and errors are surfaced`() {
        val m = build(c = cache.copy(nexusEvent = null), state = RefreshState(lastError = "API error 500"))
        assertTrue(m.nexusUnavailable)
        assertEquals("API error 500", m.error)
    }

    @Test
    fun `TBA-only event - TBA times, no phases, no breaks`() {
        // Review focus #1
        val m = build(cfg = config.copy(timeSource = TimeSource.TBA, queueOffsetMinutes = 10))
        val rows = m.days.flatMap { it.items }
        assertTrue(rows.none { it is MatchListModel.Item.Break })
        val upcoming = rows.filterIsInstance<MatchListModel.Item.Upcoming>().map { it.row }
        assertTrue(upcoming.all { it.phase == null })
        val next = upcoming.first()
        assertEquals(Instant.ofEpochSecond(1775868749), next.time) // TBA predicted time
        assertEquals(MatchListModel.Countdown(Instant.ofEpochSecond(1775868749).minusSeconds(600), "to queue"), next.countdown)
    }

    @Test
    fun `days follow the event zone, not the device zone`() {
        // Review focus #2
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"))
            assertEquals(listOf("Friday, Apr 10", "Saturday, Apr 11", "Sunday, Apr 12"), build().days.map { it.label })
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `matches without a time are still listed`() {
        // Review focus #3
        val c = EventCache(event = testEvent(), matches = listOf(testMatch(1, time = null), testMatch(2, time = 1_775_900_000)))
        val m = MatchListModels.build(c, UserConfig(teamNumber = 1234, apiKey = "k"), RefreshState(), SNAP_NOW, Locale.US)
        assertEquals(listOf("Q1", "Q2"), m.days.flatMap { it.items }.map { (it as MatchListModel.Item.Upcoming).row.shortLabel })
        assertEquals("Time TBD", m.days.first().label)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.matches.*' --console=plain)`
Expected: FAIL — `Unresolved reference 'MatchListModels'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchListModel.kt`:
```kotlin
package com.pitwatch.app.ui.matches

import com.pitwatch.core.config.TimeSource
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.logic.MatchSchedule
import com.pitwatch.core.logic.NexusMatchMerge
import com.pitwatch.core.logic.PhaseDerivation
import com.pitwatch.core.logic.UpcomingScheduleItem
import com.pitwatch.core.model.Match
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Everything the Matches screen renders, derived from persisted state. */
data class MatchListModel(
    val title: String,
    val status: Status?,
    val nowQueuing: String?,
    val nexusUnavailable: Boolean,
    val error: String?,
    val days: List<Day>,
    val results: List<Result>,
    val empty: Empty?,
) {
    data class Status(val teamNumber: Int, val rank: Int?, val record: String?) {
        val text: String get() = listOfNotNull("Team $teamNumber", rank?.let { "Rank #$it" }, record).joinToString(" · ")
    }

    data class Day(val date: LocalDate?, val label: String, val items: List<Item>)

    sealed interface Item {
        val id: String

        data class Upcoming(val row: MatchRow) : Item {
            override val id: String get() = row.key
        }

        data class Break(val title: String, val start: Instant, val end: Instant?) : Item {
            override val id: String get() = "break:$title:$start"
        }
    }

    data class MatchRow(
        val key: String,
        val label: String,
        val shortLabel: String,
        val time: Instant?,
        /** True when [time] is an estimate (Nexus or TBA predicted), shown with "~". */
        val estimated: Boolean,
        val alliance: MatchAlliance?,
        val phase: Phase?,
        val red: AllianceLine,
        val blue: AllianceLine,
        val isNext: Boolean,
        val countdown: Countdown?,
    ) {
        val url: String get() = "https://www.thebluealliance.com/match/$key"
    }

    data class AllianceLine(val teams: List<TeamChip>, val summedOpr: Double?)
    data class TeamChip(val number: String, val isUs: Boolean)

    /** "12m to on deck": counts down to [deadline]. */
    data class Countdown(val deadline: Instant, val target: String)

    data class Result(val key: String, val label: String, val shortLabel: String, val ourScore: Int, val theirScore: Int, val outcome: String) {
        val url: String get() = "https://www.thebluealliance.com/match/$key"
    }

    enum class Empty { NOT_CONFIGURED, NO_EVENT, NO_MATCHES }
}

object MatchListModels {
    fun build(cache: EventCache, config: UserConfig, refreshState: RefreshState, now: Instant, locale: Locale = Locale.getDefault()): MatchListModel {
        val event = cache.event
        val title = event?.shortName ?: event?.name ?: "PitWatch"
        val error = refreshState.lastError
        val teamKey = config.teamKey
        val teamNumber = config.teamNumber
        if (!config.isConfigured || teamKey == null || teamNumber == null) {
            return MatchListModel(title, null, null, false, error, emptyList(), emptyList(), MatchListModel.Empty.NOT_CONFIGURED)
        }
        val ranking = cache.rankings?.rankings?.firstOrNull { it.teamKey == teamKey }
        val status = MatchListModel.Status(teamNumber, ranking?.rank, ranking?.record?.display)
        val nexusUnavailable = config.isNexusConfigured && event != null && cache.nexusEvent == null
        if (event == null) {
            return MatchListModel(title, status, null, nexusUnavailable, error, emptyList(), emptyList(), MatchListModel.Empty.NO_EVENT)
        }

        val nexus = cache.nexusEvent.takeIf { config.effectiveTimeSource == TimeSource.NEXUS }
        val schedule = MatchSchedule(cache.matches, teamKey)
        val nextKey = schedule.nextMatch?.key

        fun line(match: Match, color: String): MatchListModel.AllianceLine {
            val keys = match.alliances[color]?.teamKeys.orEmpty()
            return MatchListModel.AllianceLine(keys.map { MatchListModel.TeamChip(it.removePrefix("frc"), it == teamKey) }, cache.oprs?.summedOpr(keys))
        }

        fun row(match: Match): MatchListModel.MatchRow {
            val isNext = match.key == nextKey
            val nexusMatch = NexusMatchMerge.nexusInfo(match, nexus)
            val nexusStart = nexusMatch?.times?.startDate
            val time = nexusStart ?: if (config.useScheduledTime) {
                match.time?.let(Instant::ofEpochSecond) ?: match.matchDate()
            } else {
                match.matchDate()
            }
            val estimated = nexusStart != null || (!config.useScheduledTime && match.predictedTime != null && match.actualTime == null)
            val derived = nexusMatch?.let { PhaseDerivation.derivePhase(it, now) }
            val countdown = when {
                !isNext -> null
                derived != null -> derived.deadline?.let { MatchListModel.Countdown(it, "to " + (derived.phase.nextPhaseProse ?: "match end")) }
                else -> time?.let {
                    MatchListModel.Countdown(it.minus(config.queueOffset), if (config.queueOffsetMinutes > 0) "to queue" else "to match")
                }
            }
            val alliance = when (match.allianceColor(teamKey)) {
                "red" -> MatchAlliance.RED
                "blue" -> MatchAlliance.BLUE
                else -> null
            }
            return MatchListModel.MatchRow(
                match.key, match.label, match.shortLabel, time, estimated, alliance, derived?.phase,
                line(match, "red"), line(match, "blue"), isNext, countdown,
            )
        }

        val zone = event.zone
        val dayFormat = DateTimeFormatter.ofPattern("EEEE, MMM d", locale)
        val entries: List<Pair<Instant?, MatchListModel.Item>> = schedule.upcomingTimeline(nexus, zone).map { item ->
            when (item) {
                is UpcomingScheduleItem.MatchItem -> row(item.match).let { it.time to MatchListModel.Item.Upcoming(it) }
                is UpcomingScheduleItem.BreakItem -> item.scheduleBreak.let { it.start to MatchListModel.Item.Break(it.title, it.start, it.end) }
            }
        }
        val days = mutableListOf<MatchListModel.Day>()
        for ((time, item) in entries) {
            // Untimed matches stay with the day before them (or "Time TBD" at the top).
            val date = time?.atZone(zone)?.toLocalDate() ?: days.lastOrNull()?.date
            if (days.isNotEmpty() && days.last().date == date) {
                days[days.lastIndex] = days.last().let { it.copy(items = it.items + item) }
            } else {
                days += MatchListModel.Day(date, date?.format(dayFormat) ?: "Time TBD", listOf(item))
            }
        }

        val results = schedule.pastMatches.mapNotNull { match ->
            val ours = match.allianceColor(teamKey) ?: return@mapNotNull null
            val theirs = if (ours == "red") "blue" else "red"
            val our = match.alliances[ours]?.score ?: return@mapNotNull null
            val their = match.alliances[theirs]?.score ?: return@mapNotNull null
            val outcome = when {
                our > their -> "W"
                our < their -> "L"
                else -> "T"
            }
            MatchListModel.Result(match.key, match.label, match.shortLabel, our, their, outcome)
        }

        val empty = if (days.isEmpty() && results.isEmpty()) MatchListModel.Empty.NO_MATCHES else null
        return MatchListModel(title, status, nexus?.nowQueuing, nexusUnavailable, error, days, results, empty)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.matches.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 9 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): MatchListModel for the Matches screen"
```

---

### Task 4: WidgetModel

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetModel.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/widget/WidgetModelTest.kt`

**Interfaces:**
- Consumes: `MatchListModels` (Task 3).
- Produces: `data class WidgetModel(state: State, header: String, eventTitle: String?, next: MatchListModel.MatchRow?, later: List<MatchListModel.Item>, last: MatchListModel.Result?, message: String?, countdownDeadline: Instant?)` with `enum State { NOT_CONFIGURED, NO_EVENT, NO_UPCOMING, READY }`; `object WidgetModels { fun build(cache, config, now, locale = Locale.getDefault()): WidgetModel }`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/widget/WidgetModelTest.kt`:
```kotlin
package com.pitwatch.app.widget

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.time.Instant
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class WidgetModelTest {
    private val cache = snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json")))
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun build(c: EventCache = cache, cfg: UserConfig = config, now: Instant = SNAP_NOW) = WidgetModels.build(c, cfg, now, Locale.US)

    @Test
    fun `ready - header, next match, what follows, last result`() {
        val m = build()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("5507 · #34 · 1-2-0", m.header)
        assertEquals("California Northern", m.eventTitle)
        assertEquals("Q36", m.next?.shortLabel)
        val later = m.later.take(2).map {
            when (it) {
                is MatchListModel.Item.Upcoming -> it.row.shortLabel
                is MatchListModel.Item.Break -> it.title
            }
        }
        assertEquals(listOf("End of day", "Q43"), later)
        assertEquals("Q22", m.last?.shortLabel)
        assertEquals(Instant.ofEpochMilli(1775868749195).plusSeconds(150), m.countdownDeadline)
    }

    @Test
    fun `no countdown once the deadline has passed`() {
        // Review focus #4: never show a negative chronometer.
        assertNull(build(now = SNAP_NOW.plusSeconds(3600)).countdownDeadline)
    }

    @Test
    fun `not configured`() {
        val m = build(cfg = UserConfig())
        assertEquals(WidgetModel.State.NOT_CONFIGURED, m.state)
        assertEquals("Set up PitWatch", m.message)
    }

    @Test
    fun `no event yet`() {
        assertEquals("No event yet", build(c = EventCache()).message)
    }

    @Test
    fun `upcoming event without matches shows when it starts`() {
        val m = build(c = EventCache(event = cache.event), now = Instant.parse("2026-04-01T12:00:00Z"))
        assertEquals(WidgetModel.State.NO_UPCOMING, m.state)
        assertEquals("Next event: California Northern · Apr 9", m.message)
    }

    @Test
    fun `finished schedule shows the last result`() {
        val played = cache.matches.filter { it.isPlayed }
        val m = build(c = cache.copy(matches = played))
        assertEquals("No upcoming matches", m.message)
        assertEquals("Q22", m.last?.shortLabel)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.widget.*' --console=plain)`
Expected: FAIL — `Unresolved reference 'WidgetModels'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetModel.kt`:
```kotlin
package com.pitwatch.app.widget

import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What the home-screen widget shows, at any size. Built from the same model as the Matches screen. */
data class WidgetModel(
    val state: State,
    val header: String,
    val eventTitle: String?,
    val next: MatchListModel.MatchRow?,
    /** Everything after [next], in order: matches and breaks. */
    val later: List<MatchListModel.Item>,
    val last: MatchListModel.Result?,
    /** Shown instead of match content outside [State.READY]. */
    val message: String?,
    /** Live chronometer target; null once passed so the widget never counts negative. */
    val countdownDeadline: Instant?,
) {
    enum class State { NOT_CONFIGURED, NO_EVENT, NO_UPCOMING, READY }
}

object WidgetModels {
    fun build(cache: EventCache, config: UserConfig, now: Instant, locale: Locale = Locale.getDefault()): WidgetModel {
        val list = MatchListModels.build(cache, config, RefreshState(), now, locale)
        if (list.empty == MatchListModel.Empty.NOT_CONFIGURED) {
            return WidgetModel(WidgetModel.State.NOT_CONFIGURED, "PitWatch", null, null, emptyList(), null, "Set up PitWatch", null)
        }
        val header = listOfNotNull(config.teamNumber?.toString(), list.status?.rank?.let { "#$it" }, list.status?.record).joinToString(" · ")
        val event = cache.event
            ?: return WidgetModel(WidgetModel.State.NO_EVENT, header, null, null, emptyList(), null, "No event yet", null)

        val items = list.days.flatMap { it.items }
        val next = items.filterIsInstance<MatchListModel.Item.Upcoming>().firstOrNull()?.row
        val last = list.results.firstOrNull()
        if (next == null) {
            val start = event.startInstant?.takeIf { it > now }
            val message = start?.let {
                "Next event: ${list.title} · " + DateTimeFormatter.ofPattern("MMM d", locale).withZone(event.zone).format(it)
            } ?: "No upcoming matches"
            return WidgetModel(WidgetModel.State.NO_UPCOMING, header, list.title, null, emptyList(), last, message, null)
        }
        val later = items.dropWhile { !(it is MatchListModel.Item.Upcoming && it.row.key == next.key) }.drop(1)
        val deadline = next.countdown?.deadline?.takeIf { it > now }
        return WidgetModel(WidgetModel.State.READY, header, list.title, next, later, last, null, deadline)
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.widget.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 6 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): WidgetModel for every widget size and state"
```

---

### Task 5: Theme, status colors, tracking state, singleTop, quieter start

**Files:**
- Modify: `android/gradle/libs.versions.toml`, `android/app/build.gradle.kts`, `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/com/pitwatch/app/live/LiveMatchService.kt`
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/theme/Theme.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/theme/StatusColorsTest.kt`, `android/app/src/test/kotlin/com/pitwatch/app/live/LiveServiceStateTest.kt`

**Interfaces:**
- Produces: `@Composable fun PitWatchTheme(content)`; `object StatusColors { fun phase(Phase): Color; fun onPhase(Phase): Color; fun alliance(MatchAlliance): Color }`; `LiveMatchService.tracking: StateFlow<Boolean>`.

- [ ] **Step 1: Dependencies**

In `libs.versions.toml` add to `[versions]`: `glance = "1.2.0"`; to `[libraries]`:
```toml
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
compose-ui-test-junit4 = { module = "androidx.compose.ui:ui-test-junit4" }
compose-ui-test-manifest = { module = "androidx.compose.ui:ui-test-manifest" }
glance-appwidget = { module = "androidx.glance:glance-appwidget", version.ref = "glance" }
glance-material3 = { module = "androidx.glance:glance-material3", version.ref = "glance" }
glance-appwidget-testing = { module = "androidx.glance:glance-appwidget-testing", version.ref = "glance" }
```
In `android/app/build.gradle.kts` `dependencies { … }` add:
```kotlin
    implementation(libs.compose.material.icons.core)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.glance.appwidget.testing)
    debugImplementation(libs.compose.ui.test.manifest)
```

- [ ] **Step 2: Write the failing tests**

`android/app/src/test/kotlin/com/pitwatch/app/ui/theme/StatusColorsTest.kt`:
```kotlin
package com.pitwatch.app.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.pitwatch.core.model.Phase
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class StatusColorsTest {
    private fun contrast(a: androidx.compose.ui.graphics.Color, b: androidx.compose.ui.graphics.Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    @Test
    fun `phase colors match the spec`() {
        assertEquals(listOf(0xFF636366, 0xFFFF9500, 0xFFFF6B00, 0xFF30D158).map { it.toInt() },
            Phase.entries.map { StatusColors.phase(it).toArgb() })
    }

    @Test
    fun `badge text is readable on every phase color`() {
        for (phase in Phase.entries) assertTrue(contrast(StatusColors.phase(phase), StatusColors.onPhase(phase)) >= 4.5, "$phase")
    }
}
```

`android/app/src/test/kotlin/com/pitwatch/app/live/LiveServiceStateTest.kt`:
```kotlin
package com.pitwatch.app.live

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.net.ConnectivityManager
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.awaitMain
import com.pitwatch.app.installTestContainer
import com.pitwatch.app.snapshotNexus
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowNetwork

@RunWith(RobolectricTestRunner::class)
class LiveServiceStateTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val nexus = snapshotNexus()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root, nexus = nexus)
        runBlocking { container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") } }
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun startTracking(): LiveMatchService {
        val service = Robolectric.buildService(LiveMatchService::class.java).create().get()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_START), 0, 1)
        awaitMain {
            shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(LiveNotification.NOTIFICATION_ID)
                ?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.startsWith("Q36") == true
        }
        return service
    }

    private fun idle() = repeat(20) {
        ShadowLooper.idleMainLooper()
        Thread.sleep(10)
    }

    @Test
    fun `tracking state follows the service`() {
        val service = startTracking()
        assertTrue(LiveMatchService.tracking.value)
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP), 0, 2)
        assertFalse(LiveMatchService.tracking.value)
    }

    @Test
    fun `the network callback's immediate first onAvailable does not add a poll`() {
        startTracking()
        idle()
        val callback = shadowOf(context.getSystemService(ConnectivityManager::class.java)).networkCallbacks.single()
        val before = nexus.requests.size
        callback.onAvailable(ShadowNetwork.newInstance(1))
        idle()
        assertEquals(before, nexus.requests.size)
        callback.onAvailable(ShadowNetwork.newInstance(1)) // a real reconnect later does poll
        awaitMain { nexus.requests.size > before }
    }
}
```

- [ ] **Step 3: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.theme.*' --tests 'com.pitwatch.app.live.LiveServiceStateTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'StatusColors'`, `'tracking'`.

- [ ] **Step 4: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/ui/theme/Theme.kt`:
```kotlin
package com.pitwatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase

/** Material You: wallpaper-derived colors, light/dark following the system (minSdk 36 always has dynamic color). */
@Composable
fun PitWatchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Fixed status colors — the only non-theme colors in the app. */
object StatusColors {
    fun phase(phase: Phase): Color = when (phase) {
        Phase.PRE_QUEUE -> Color(0xFF636366)
        Phase.QUEUEING -> Color(0xFFFF9500)
        Phase.ON_DECK -> Color(0xFFFF6B00)
        Phase.ON_FIELD -> Color(0xFF30D158)
    }

    /** Badge label color, chosen for contrast on [phase]. */
    fun onPhase(phase: Phase): Color = if (phase == Phase.PRE_QUEUE) Color.White else Color(0xFF1C1C1E)

    fun alliance(alliance: MatchAlliance): Color = when (alliance) {
        MatchAlliance.RED -> Color(0xFFFF3B30)
        MatchAlliance.BLUE -> Color(0xFF1E6FFF)
    }
}
```

In `LiveMatchService.kt`:
- In the `companion object`, add:
```kotlin
        private val trackingState = MutableStateFlow(false)
        /** Whether the service is tracking a match right now (drives the Start/Stop button). */
        val tracking: StateFlow<Boolean> = trackingState
```
  with imports `kotlinx.coroutines.flow.MutableStateFlow` and `kotlinx.coroutines.flow.StateFlow`.
- Where the loop is launched (`loop = scope.launch { runLoop() }`), add `trackingState.value = true` on the next line.
- In `stopTracking`, after `loop = null`, add `trackingState.value = false`; in `onDestroy`, before `scope.cancel()`, add `trackingState.value = false`.
- Add a field `@Volatile private var ignoreNextAvailable = false`; change the network callback body to:
```kotlin
        override fun onAvailable(network: Network) {
            // registerDefaultNetworkCallback reports the current network immediately; that's not a reconnect.
            if (ignoreNextAvailable) {
                ignoreNextAvailable = false
                return
            }
            signals.trySend(Signal.POKE)
        }
```
  and in `registerTriggers`, immediately before `registerDefaultNetworkCallback(networkCallback)`, add `ignoreNextAvailable = true`.

In `AndroidManifest.xml`, add `android:launchMode="singleTop"` to the `.MainActivity` `<activity>` (notification taps re-use the open activity instead of stacking copies).

- [ ] **Step 5: Run to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --console=plain)`
Expected: `BUILD SUCCESSFUL`; all `:app` tests pass. If Robolectric's `ShadowConnectivityManager` itself delivers an initial `onAvailable` on registration (consuming the ignore flag), the test's first manual call will poll; in that case change the test to assert that registration produced no extra poll (`before` measured right after `startTracking()` and compared after `idle()`), and record a ruling.

- [ ] **Step 6: Commit**

```bash
git add android/gradle/libs.versions.toml android/app
git commit -m "feat(app): Material You theme, status colors, live tracking state"
```

---

### Task 6: Matches screen

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchesScreen.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchesContentTest.kt`

**Interfaces:**
- Consumes: `MatchListModel` (Task 3), `PitWatchTheme`/`StatusColors` (Task 5), `LiveMatchService.tracking`.
- Produces: `@Composable fun MatchesContent(model: MatchListModel, now: Instant, tracking: Boolean, refreshing: Boolean, onRefresh: () -> Unit, onToggleTracking: () -> Unit, onOpenMatch: (url: String) -> Unit, onPickEvent: () -> Unit)`; `@Composable fun MatchesScreen(container: AppContainer, onPickEvent: () -> Unit)`; `object Countdowns { fun text(deadline: Instant, now: Instant): String }`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchesContentTest.kt`:
```kotlin
package com.pitwatch.app.ui.matches

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.RefreshState
import java.util.Locale
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MatchesContentTest {
    @get:Rule
    val compose = createComposeRule()

    private val model = MatchListModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), RefreshState(), SNAP_NOW, Locale.US,
    )

    private fun show(tracking: Boolean = false, onToggle: () -> Unit = {}, onOpen: (String) -> Unit = {}, onPick: () -> Unit = {}) {
        compose.setContent {
            MatchesContent(model, SNAP_NOW, tracking, refreshing = false, onRefresh = {}, onToggleTracking = onToggle, onOpenMatch = onOpen, onPickEvent = onPick)
        }
    }

    @Test
    fun `status card, queue line and next match`() {
        show()
        compose.onNodeWithText("California Northern").assertIsDisplayed()
        compose.onNodeWithText("Team 5507 · Rank #34 · 1-2-0").assertIsDisplayed()
        compose.onNodeWithText("Now queuing: Qualification 38").assertIsDisplayed()
        compose.onNodeWithText("Start live tracking").assertIsDisplayed()
        compose.onNodeWithText("Qual 36").assertIsDisplayed()
        compose.onNodeWithText("ON FIELD").assertIsDisplayed()
    }

    @Test
    fun `days, breaks and results are listed`() {
        show()
        val list = compose.onNodeWithTag("matches")
        for (text in listOf("Saturday, Apr 11", "Lunch", "End of day", "Results", "Qual 22")) {
            list.performScrollToNode(hasText(text, substring = true))
            compose.onNodeWithText(text, substring = true).assertIsDisplayed()
        }
    }

    @Test
    fun `tracking button reflects and toggles state`() {
        var toggles = 0
        show(tracking = true, onToggle = { toggles++ })
        compose.onNodeWithText("Stop live tracking").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun `tapping a result opens it on TBA`() {
        var opened: String? = null
        show(onOpen = { opened = it })
        compose.onNodeWithTag("matches").performScrollToNode(hasText("Qual 22"))
        compose.onNodeWithText("Qual 22").performClick()
        assertEquals("https://www.thebluealliance.com/match/2026cancmp_qm22", opened)
    }

    @Test
    fun `event picker opens from the top bar`() {
        var picks = 0
        show(onPick = { picks++ })
        compose.onNodeWithContentDescriptionSafe("Choose event").performClick()
        assertEquals(1, picks)
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onNodeWithContentDescriptionSafe(label: String) =
        onNode(androidx.compose.ui.test.hasContentDescription(label))

    @Test
    fun `countdown text`() {
        assertEquals("now", Countdowns.text(SNAP_NOW, SNAP_NOW))
        assertEquals("12m", Countdowns.text(SNAP_NOW.plusSeconds(12 * 60 - 30), SNAP_NOW))
        assertEquals("1h 5m", Countdowns.text(SNAP_NOW.plusSeconds(65 * 60), SNAP_NOW))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.matches.MatchesContentTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'MatchesContent'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchesScreen.kt`:
```kotlin
package com.pitwatch.app.ui.matches

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.data.LiveControl
import com.pitwatch.app.live.LiveMatchService
import com.pitwatch.app.schedule.rearmAutoStart
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.Phase
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object Countdowns {
    /** "now", "12m", "1h 5m" — minutes rounded up. */
    fun text(deadline: Instant, now: Instant): String {
        val seconds = Duration.between(now, deadline).seconds
        if (seconds <= 0) return "now"
        val minutes = (seconds + 59) / 60
        return if (minutes < 60) "${minutes}m" else "${minutes / 60}h ${minutes % 60}m"
    }
}

private val clockTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun timeText(time: Instant?, estimated: Boolean): String =
    time?.let { (if (estimated) "~" else "") + clockTime.format(it) } ?: "Time TBD"

/** Stateful wrapper: collects persisted state and wires actions. */
@Composable
fun MatchesScreen(container: AppContainer, onPickEvent: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cache by container.stores.cache.data.collectAsStateWithLifecycle(initialValue = EventCache())
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = UserConfig())
    val refreshState by container.stores.refreshState.data.collectAsStateWithLifecycle(initialValue = RefreshState())
    val tracking by LiveMatchService.tracking.collectAsStateWithLifecycle()
    val now by produceState(container.clock()) {
        while (true) {
            delay(30_000)
            value = container.clock()
        }
    }
    var refreshing by remember { mutableStateOf(false) }
    val model = remember(cache, config, refreshState, now) { MatchListModels.build(cache, config, refreshState, now) }

    MatchesContent(
        model = model,
        now = now,
        tracking = tracking,
        refreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                try {
                    container.repository.refresh(container.clock(), force = true)
                    rearmAutoStart(context, container)
                } finally {
                    refreshing = false
                }
            }
        },
        onToggleTracking = {
            if (tracking) {
                context.startService(LiveMatchService.intent(context, LiveMatchService.ACTION_STOP))
            } else {
                scope.launch { container.stores.liveControl.updateData { LiveControl() } }
                LiveMatchService.start(context)
            }
        },
        onOpenMatch = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
        onPickEvent = onPickEvent,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesContent(
    model: MatchListModel,
    now: Instant,
    tracking: Boolean,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onToggleTracking: () -> Unit,
    onOpenMatch: (url: String) -> Unit,
    onPickEvent: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(model.title) },
                actions = { IconButton(onClick = onPickEvent) { Icon(Icons.Filled.DateRange, contentDescription = "Choose event") } },
            )
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("matches"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "status") { StatusCard(model, tracking, onToggleTracking) }
                model.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                when (model.empty) {
                    MatchListModel.Empty.NO_EVENT -> item(key = "empty") {
                        EmptyState("No event yet", "Pick an event", onPickEvent)
                    }
                    MatchListModel.Empty.NO_MATCHES -> item(key = "empty") { EmptyState("No matches scheduled yet", null, null) }
                    else -> Unit
                }
                for (day in model.days) {
                    item(key = "day:${day.label}") { SectionHeader(day.label) }
                    items(day.items, key = { it.id }) { item ->
                        when (item) {
                            is MatchListModel.Item.Upcoming ->
                                if (item.row.isNext) NextMatchCard(item.row, now, onOpenMatch) else MatchRowItem(item.row, onOpenMatch)
                            is MatchListModel.Item.Break -> BreakRow(item)
                        }
                    }
                }
                if (model.results.isNotEmpty()) {
                    item(key = "results") { SectionHeader("Results") }
                    items(model.results, key = { "result:${it.key}" }) { ResultRow(it, onOpenMatch) }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(model: MatchListModel, tracking: Boolean, onToggleTracking: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            model.status?.let { Text(it.text, style = MaterialTheme.typography.titleMedium) }
            if (tracking) {
                OutlinedButton(onClick = onToggleTracking) { Text("Stop live tracking") }
            } else {
                Button(onClick = onToggleTracking) { Text("Start live tracking") }
            }
            model.nowQueuing?.let { Text("Now queuing: $it", style = MaterialTheme.typography.bodyMedium) }
            if (model.nexusUnavailable) {
                Text("Nexus unavailable — showing TBA times", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyState(title: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (action != null && onAction != null) OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
fun PhaseBadge(phase: Phase) {
    Surface(color = StatusColors.phase(phase), shape = RoundedCornerShape(50)) {
        Text(
            phase.stateLabel,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = StatusColors.onPhase(phase),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AllianceLineText(line: MatchListModel.AllianceLine, alliance: com.pitwatch.core.model.MatchAlliance) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(StatusColors.alliance(alliance), CircleShape))
        Spacer(Modifier.width(6.dp))
        line.teams.forEach { team ->
            Text(
                team.number,
                fontWeight = if (team.isUs) FontWeight.Bold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        line.summedOpr?.let { Text("Σ %.1f".format(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun NextMatchCard(row: MatchListModel.MatchRow, now: Instant, onOpenMatch: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onOpenMatch(row.url) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                row.phase?.let { PhaseBadge(it) }
            }
            Row {
                Text(timeText(row.time, row.estimated), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                row.countdown?.let {
                    Text("${Countdowns.text(it.deadline, now)} ${it.target}", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
                }
            }
            AllianceLineText(row.red, com.pitwatch.core.model.MatchAlliance.RED)
            AllianceLineText(row.blue, com.pitwatch.core.model.MatchAlliance.BLUE)
        }
    }
}

@Composable
private fun MatchRowItem(row: MatchListModel.MatchRow, onOpenMatch: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenMatch(row.url) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        row.alliance?.let { Box(Modifier.size(8.dp).background(StatusColors.alliance(it), CircleShape)) }
        Spacer(Modifier.width(8.dp))
        Text(row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        row.phase?.let {
            PhaseBadge(it)
            Spacer(Modifier.width(8.dp))
        }
        Text(timeText(row.time, row.estimated), style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
    HorizontalDivider()
}

@Composable
private fun BreakRow(item: MatchListModel.Item.Break) {
    val range = item.end?.let { " · ${clockTime.format(item.start)} – ${clockTime.format(it)}" } ?: ""
    Text(
        item.title + range,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp, horizontal = 16.dp),
    )
}

@Composable
private fun ResultRow(result: MatchListModel.Result, onOpenMatch: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenMatch(result.url) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(result.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text("${result.outcome} ${result.ourScore}–${result.theirScore}", style = MaterialTheme.typography.bodyLarge, fontFamily = FontFamily.Monospace)
    }
    HorizontalDivider()
}
```

Note: the test helper `onNodeWithContentDescriptionSafe` is just `onNode(hasContentDescription(…))`; if `onNodeWithContentDescription` is available, the executor may use it directly.

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.matches.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): Material 3 Matches screen with breaks, results and live control"
```

---

### Task 7: Event picker

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/events/EventPicker.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/events/EventPickerTest.kt`

**Interfaces:**
- Consumes: `Repository.seasonEvents` (Task 2), `RefreshWorker.refreshNow`, `EventKeys`.
- Produces: `sealed interface EventPickerState { Loading; Error(message); Loaded(options: List<EventOption>) }`; `data class EventOption(key, name, dates, location: String?)` with `companion fun from(event: Event): EventOption`; `@Composable fun EventPickerContent(state, selectedKey: String?, onSelect: (String?) -> Unit, onBack: () -> Unit)`; `@Composable fun EventPickerScreen(container, onDone: () -> Unit)`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/ui/events/EventPickerTest.kt`:
```kotlin
package com.pitwatch.app.ui.events

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.pitwatch.app.testEvent
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EventPickerTest {
    @get:Rule
    val compose = createComposeRule()

    private val options = listOf(
        EventOption.from(testEvent(key = "2026cancmp").copy(name = "California Northern", city = "Daly City", stateProv = "CA")),
        EventOption.from(testEvent(key = "2026casf", startDate = "2026-03-20", endDate = "2026-03-22").copy(name = "San Francisco")),
    )

    @Test
    fun `option shows dates and location`() {
        assertEquals(EventOption("2026cancmp", "California Northern", "2026-04-09 – 2026-04-12", "Daly City, CA"), options[0])
        assertNull(options[1].location)
    }

    @Test
    fun `selecting an event or Auto reports the choice`() {
        val picks = mutableListOf<String?>()
        compose.setContent { EventPickerContent(EventPickerState.Loaded(options), selectedKey = "2026cancmp", onSelect = { picks += it }, onBack = {}) }
        compose.onNodeWithText("San Francisco").performClick()
        compose.onNodeWithText("Auto (current or next event)").performClick()
        assertEquals(listOf("2026casf", null), picks)
    }

    @Test
    fun `loading and error states`() {
        var state: EventPickerState by mutableStateOf(EventPickerState.Loading)
        compose.setContent { EventPickerContent(state, null, {}, {}) }
        compose.onNodeWithText("Loading events…").assertIsDisplayed()
        state = EventPickerState.Error("API error 401")
        compose.onNodeWithText("Couldn't load events: API error 401").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.events.*' --console=plain)`
Expected: FAIL — `Unresolved reference 'EventOption'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/ui/events/EventPicker.kt`:
```kotlin
package com.pitwatch.app.ui.events

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.schedule.RefreshWorker
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.Event
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch

data class EventOption(val key: String, val name: String, val dates: String, val location: String?) {
    companion object {
        fun from(event: Event) = EventOption(
            key = event.key,
            name = event.name,
            dates = "${event.startDate} – ${event.endDate}",
            location = listOfNotNull(event.city, event.stateProv).joinToString(", ").ifEmpty { null },
        )
    }
}

sealed interface EventPickerState {
    data object Loading : EventPickerState
    data class Error(val message: String) : EventPickerState
    data class Loaded(val options: List<EventOption>) : EventPickerState
}

@Composable
fun EventPickerScreen(container: AppContainer, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = UserConfig())
    var state by remember { mutableStateOf<EventPickerState>(EventPickerState.Loading) }
    LaunchedEffect(Unit) {
        state = try {
            EventPickerState.Loaded(container.repository.seasonEvents(container.clock()).map(EventOption::from))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            EventPickerState.Error(e.message ?: "Unknown error")
        }
    }
    EventPickerContent(
        state = state,
        selectedKey = config.eventKeyOverride,
        onSelect = { key ->
            container.scope.launch {
                container.stores.config.updateData { it.copy(eventKeyOverride = key) }
                RefreshWorker.refreshNow(context.applicationContext)
            }
            onDone()
        },
        onBack = onDone,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventPickerContent(state: EventPickerState, selectedKey: String?, onSelect: (String?) -> Unit, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Event") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
    ) { padding ->
        when (state) {
            EventPickerState.Loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
                Text("Loading events…", Modifier.padding(top = 72.dp))
            }
            is EventPickerState.Error -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Couldn't load events: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
            is EventPickerState.Loaded -> LazyColumn(Modifier.fillMaxSize().padding(padding)) {
                item {
                    ListItem(
                        headlineContent = { Text("Auto (current or next event)") },
                        trailingContent = { if (selectedKey == null) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                        modifier = Modifier.clickable { onSelect(null) },
                    )
                    HorizontalDivider()
                }
                items(state.options, key = { it.key }) { option ->
                    ListItem(
                        headlineContent = { Text(option.name) },
                        supportingContent = { Text(listOfNotNull(option.dates, option.location).joinToString(" · ")) },
                        trailingContent = { if (option.key == selectedKey) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                        modifier = Modifier.clickable { onSelect(option.key) },
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.events.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): event picker with Auto and season events"
```

---

### Task 8: Pit map

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/pitmap/PitMapGeometry.kt`, `android/app/src/main/kotlin/com/pitwatch/app/ui/pitmap/PitMapScreen.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/pitmap/PitMapTest.kt`

**Interfaces:**
- Consumes: `Repository.pitMap` (Task 2), `PitMap` (`:core`).
- Produces: `object PitMapGeometry { fun rect(position: PitMap.Position, size: PitMap.MapSize): Rect; fun fitScale(map: PitMap, width: Float, height: Float): Float; fun focus(map: PitMap, team: String?): PitMap.AssignedPit? }`; `sealed interface PitMapState { Loading; NoKey; Unavailable; Loaded(map) }`; `@Composable fun PitMapContent(state, teamNumber: String?, onOpenSettings: () -> Unit)`; `@Composable fun PitMapScreen(container, onOpenSettings)`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/ui/pitmap/PitMapTest.kt`:
```kotlin
package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.SNAP
import com.pitwatch.app.fixture
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.model.PitMap
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PitMapTest {
    @get:Rule
    val compose = createComposeRule()

    private val map = PitWatchJson.decodeFromString<PitMap>(fixture("$SNAP/nexus_map.json"))

    @Test
    fun `positions are top-left corners, like iOS`() {
        assertEquals(Rect(513.5f, 195f, 613.5f, 295f), PitMapGeometry.rect(PitMap.Position(513.5, 195.0), PitMap.MapSize(100.0, 100.0)))
    }

    @Test
    fun `fit scale keeps the whole map visible`() {
        assertEquals(minOf(500f / 979f, 1000f / 1209f), PitMapGeometry.fitScale(map, 500f, 1000f))
    }

    @Test
    fun `focus finds our pit and tolerates teams without one`() {
        // Review focus #5
        assertEquals("C1", PitMapGeometry.focus(map, "5507")?.address)
        assertNull(PitMapGeometry.focus(map, "9999"))
        assertNull(PitMapGeometry.focus(map.copy(pits = emptyMap()), "5507"))
        assertNull(PitMapGeometry.focus(map, null))
    }

    @Test
    fun `loaded map shows our pit address`() {
        compose.setContent { PitMapContent(PitMapState.Loaded(map), teamNumber = "5507", onOpenSettings = {}) }
        compose.onNodeWithText("Pit C1").assertIsDisplayed()
    }

    @Test
    fun `team without a pit still renders the map`() {
        compose.setContent { PitMapContent(PitMapState.Loaded(map), teamNumber = "9999", onOpenSettings = {}) }
        compose.onNodeWithText("Pit map").assertIsDisplayed()
    }

    @Test
    fun `explains missing key and missing map`() {
        compose.setContent { PitMapContent(PitMapState.NoKey, "5507", {}) }
        compose.onNodeWithText("Add a FRC Nexus API key in Settings to see the pit map.").assertIsDisplayed()
    }

    @Test
    fun `explains an event without a map`() {
        compose.setContent { PitMapContent(PitMapState.Unavailable, "5507", {}) }
        compose.onNodeWithText("This event has no pit map on Nexus.").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.pitmap.*' --console=plain)`
Expected: FAIL — `Unresolved reference 'PitMapGeometry'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/ui/pitmap/PitMapGeometry.kt`:
```kotlin
package com.pitwatch.app.ui.pitmap

import androidx.compose.ui.geometry.Rect
import com.pitwatch.core.model.PitMap

object PitMapGeometry {
    /** Nexus positions are treated as top-left corners (as the iOS app does). Change here if that proves wrong. */
    fun rect(position: PitMap.Position, size: PitMap.MapSize): Rect =
        Rect(position.x.toFloat(), position.y.toFloat(), (position.x + size.x).toFloat(), (position.y + size.y).toFloat())

    /** Scale that fits the whole map into a width × height viewport. */
    fun fitScale(map: PitMap, width: Float, height: Float): Float =
        minOf(width / map.size.x.toFloat(), height / map.size.y.toFloat())

    /** Our pit, if the team has one on this map. */
    fun focus(map: PitMap, team: String?): PitMap.AssignedPit? = team?.let { map.pit(forTeam = it) }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/ui/pitmap/PitMapScreen.kt`:
```kotlin
package com.pitwatch.app.ui.pitmap

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.PitMap

sealed interface PitMapState {
    data object Loading : PitMapState
    data object NoKey : PitMapState
    data object Unavailable : PitMapState
    data class Loaded(val map: PitMap) : PitMapState
}

@Composable
fun PitMapScreen(container: AppContainer, onOpenSettings: () -> Unit) {
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = UserConfig())
    var state by remember { mutableStateOf<PitMapState>(PitMapState.Loading) }
    LaunchedEffect(config.nexusApiKey) {
        state = if (!config.isNexusConfigured) {
            PitMapState.NoKey
        } else {
            container.repository.pitMap()?.let { PitMapState.Loaded(it) } ?: PitMapState.Unavailable
        }
    }
    PitMapContent(state, config.teamNumber?.toString(), onOpenSettings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitMapContent(state: PitMapState, teamNumber: String?, onOpenSettings: () -> Unit) {
    val ours = (state as? PitMapState.Loaded)?.let { PitMapGeometry.focus(it.map, teamNumber) }
    Scaffold(topBar = { TopAppBar(title = { Text(ours?.let { "Pit ${it.address}" } ?: "Pit map") }) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when (state) {
                PitMapState.Loading -> CircularProgressIndicator()
                PitMapState.NoKey -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Add a FRC Nexus API key in Settings to see the pit map.", Modifier.padding(24.dp))
                    OutlinedButton(onClick = onOpenSettings) { Text("Open Settings") }
                }
                PitMapState.Unavailable -> Text("This event has no pit map on Nexus.", Modifier.padding(24.dp))
                is PitMapState.Loaded -> PitMapCanvas(state.map, ours)
            }
        }
    }
}

@Composable
private fun PitMapCanvas(map: PitMap, ours: PitMap.AssignedPit?) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    var centered by remember { mutableStateOf(false) }
    val transform = rememberTransformableState { zoomChange, panChange, _ ->
        zoom = (zoom * zoomChange).coerceIn(0.5f, 8f)
        pan += panChange
    }
    val measurer = rememberTextMeasurer()
    val colors = MaterialTheme.colorScheme
    Canvas(Modifier.fillMaxSize().transformable(transform)) {
        val fit = PitMapGeometry.fitScale(map, size.width, size.height)
        if (!centered && ours != null) {
            // Open zoomed in on our pit.
            zoom = 2.5f
            val pit = PitMapGeometry.rect(ours.pit.position, ours.pit.size).center
            pan = Offset(size.width / 2 - pit.x * fit * zoom, size.height / 2 - pit.y * fit * zoom)
            centered = true
        }
        val scale = fit * zoom
        translate(pan.x, pan.y) {
            withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
                map.areas?.values?.forEach { area ->
                    val r = PitMapGeometry.rect(area.position, area.size)
                    drawRect(colors.surfaceVariant, r.topLeft, r.size)
                    drawText(measurer, area.label, r.topLeft + Offset(4f, 4f), TextStyle(color = colors.onSurfaceVariant, fontSize = 14.sp))
                }
                map.walls?.values?.forEach { wall ->
                    val r = PitMapGeometry.rect(wall.position, wall.size)
                    drawRect(colors.outline, r.topLeft, r.size)
                }
                map.arrows?.values?.forEach { arrow ->
                    val r = PitMapGeometry.rect(arrow.position, arrow.size)
                    drawRect(colors.outlineVariant, r.topLeft, r.size, style = Stroke(width = 2f))
                }
                map.labels?.values?.forEach { label ->
                    val r = PitMapGeometry.rect(label.position, label.size)
                    drawText(measurer, label.label, r.topLeft, TextStyle(color = colors.onSurface, fontSize = 14.sp))
                }
                map.pits.forEach { (address, pit) ->
                    val r = PitMapGeometry.rect(pit.position, pit.size)
                    val isOurs = address == ours?.address
                    drawRect(if (isOurs) colors.primary else colors.secondaryContainer, r.topLeft, r.size)
                    drawRect(colors.outline, r.topLeft, r.size, style = Stroke(width = 1f))
                    pit.team?.let {
                        drawText(measurer, it, r.topLeft + Offset(6f, 6f),
                            TextStyle(color = if (isOurs) colors.onPrimary else colors.onSecondaryContainer, fontSize = 18.sp))
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 4: Run to verify it passes**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.pitmap.*' --console=plain)`
Expected: `BUILD SUCCESSFUL`, 7 tests pass.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): zoomable pit map centered on our pit"
```

---

### Task 9: Settings restyle, promotion hint, setup ordering

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/PromotionHint.kt`
- Modify (rewrite): `android/app/src/main/kotlin/com/pitwatch/app/ui/SettingsScreen.kt`; modify `android/app/src/main/kotlin/com/pitwatch/app/ui/SetupScreen.kt`, `android/app/src/main/kotlin/com/pitwatch/app/AppContainer.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/PromotionHintTest.kt`

**Interfaces:**
- Produces: `object PromotionHint { fun shouldShow(sdkIntFull: Int, canPostPromoted: Boolean): Boolean }`; `AppContainer(…, updateWidgets: suspend () -> Unit = {})`; `SettingsScreen(container, config, onBack: (() -> Unit)? = null)`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/ui/PromotionHintTest.kt`:
```kotlin
package com.pitwatch.app.ui

import android.os.Build
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class PromotionHintTest {
    @Test
    fun `only on devices that can promote, when promotion is off`() {
        assertFalse(PromotionHint.shouldShow(Build.VERSION_CODES_FULL.BAKLAVA, canPostPromoted = false)) // 36.0: no promotion
        assertTrue(PromotionHint.shouldShow(Build.VERSION_CODES_FULL.BAKLAVA_1, canPostPromoted = false))
        assertFalse(PromotionHint.shouldShow(Build.VERSION_CODES_FULL.BAKLAVA_1, canPostPromoted = true))
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.PromotionHintTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'PromotionHint'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/ui/PromotionHint.kt`:
```kotlin
package com.pitwatch.app.ui

import android.os.Build

object PromotionHint {
    /** Live Update promotion exists from Android 16 QPR1 (36.1); before that the hint would be misleading. */
    fun shouldShow(sdkIntFull: Int, canPostPromoted: Boolean): Boolean =
        sdkIntFull >= Build.VERSION_CODES_FULL.BAKLAVA_1 && !canPostPromoted
}
```

In `AppContainer.kt`, add a constructor parameter `val updateWidgets: suspend () -> Unit = {},` after `clock` (Task 11 wires it).

Rewrite `android/app/src/main/kotlin/com/pitwatch/app/ui/SettingsScreen.kt`:
```kotlin
package com.pitwatch.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(container: AppContainer, config: UserConfig, onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val refreshState by container.stores.refreshState.data.collectAsStateWithLifecycle(initialValue = RefreshState())
    var eventOverride by rememberSaveable { mutableStateOf(config.eventKeyOverride.orEmpty()) }
    var apiKey by rememberSaveable { mutableStateOf(config.apiKey.orEmpty()) }
    var nexusKey by rememberSaveable { mutableStateOf(config.nexusApiKey.orEmpty()) }

    /** Saves a config change; [refetch] for changes that alter what to fetch (keys, event). */
    fun update(refetch: Boolean = false, transform: (UserConfig) -> UserConfig) = scope.launch {
        container.stores.config.updateData { transform(it) }
        rearmAutoStart(context, container)
        container.updateWidgets()
        if (refetch) RefreshWorker.refreshNow(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    onBack?.let { IconButton(onClick = it) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Section("Time source")
            Choice("FRC Nexus queue times", config.effectiveTimeSource == TimeSource.NEXUS, enabled = config.isNexusConfigured) {
                update { it.copy(timeSource = TimeSource.NEXUS) }
            }
            Choice("TBA match times", config.effectiveTimeSource == TimeSource.TBA) { update { it.copy(timeSource = TimeSource.TBA) } }
            ListItem(
                headlineContent = { Text("Use scheduled TBA times") },
                supportingContent = { Text("Instead of TBA's predicted times") },
                trailingContent = { Switch(config.useScheduledTime, { checked -> update { it.copy(useScheduledTime = checked) } }) },
            )
            ListItem(
                headlineContent = { Text("Queue offset") },
                supportingContent = { Text("${config.queueOffsetMinutes} min before the match (TBA times)") },
                trailingContent = {
                    Row {
                        TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes - 5).coerceAtLeast(0)) } }) { Text("−") }
                        TextButton(onClick = { update { it.copy(queueOffsetMinutes = (it.queueOffsetMinutes + 5).coerceAtMost(60)) } }) { Text("+") }
                    }
                },
            )
            HorizontalDivider()

            Section("Live tracking")
            Choice("Near match (starts 2 h before)", config.liveActivityMode == LiveActivityMode.NEAR_MATCH) {
                update { it.copy(liveActivityMode = LiveActivityMode.NEAR_MATCH) }
            }
            Choice("All day", config.liveActivityMode == LiveActivityMode.ALL_DAY) { update { it.copy(liveActivityMode = LiveActivityMode.ALL_DAY) } }
            if (PromotionHint.shouldShow(Build.VERSION.SDK_INT_FULL, NotificationManagerCompat.from(context).canPostPromotedNotifications())) {
                ListItem(
                    headlineContent = { Text("Live Updates are off for PitWatch") },
                    supportingContent = { Text("Tracking shows as a normal notification instead of in the status bar.") },
                    trailingContent = {
                        OutlinedButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                        }) { Text("Settings") }
                    },
                )
            }
            HorizontalDivider()

            Section("Event")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    eventOverride, { eventOverride = it.trim().lowercase() },
                    label = { Text("Event key override (blank = auto)") }, singleLine = true,
                    isError = eventOverride.isNotEmpty() && !EventKeys.isValid(eventOverride),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    enabled = eventOverride.isEmpty() || EventKeys.isValid(eventOverride),
                    onClick = { update(refetch = true) { it.copy(eventKeyOverride = eventOverride.ifEmpty { null }) } },
                ) { Text("Save event") }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))

            Section("API keys")
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = { update(refetch = true) { it.copy(apiKey = apiKey.trim(), nexusApiKey = nexusKey.trim().ifEmpty { null }) } }) {
                    Text("Save keys")
                }
            }
            HorizontalDivider(Modifier.padding(top = 16.dp))

            Section("Status")
            Text(RefreshStatusText.format(refreshState, container.clock()), Modifier.padding(horizontal = 16.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        container.repository.refresh(container.clock(), force = true)
                        rearmAutoStart(context, container)
                        RefreshWorker.ensureScheduled(context)
                    }
                },
                modifier = Modifier.padding(16.dp),
            ) { Text("Force refresh") }
            HorizontalDivider()

            Section("About")
            Text(
                "Queue data from frc.nexus · Match data from The Blue Alliance",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun Choice(label: String, selected: Boolean, enabled: Boolean = true, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = onSelect).padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled)
        Text(label, Modifier.padding(start = 12.dp))
    }
}
```

In `SetupScreen.kt`, replace the `is SetupValidator.Outcome.Valid -> { … }` branch with:
```kotlin
                        is SetupValidator.Outcome.Valid -> {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            // The write swaps this screen out (cancelling its scope), so finish on the app scope.
                            val appContext = context.applicationContext
                            container.scope.launch {
                                container.stores.config.updateData {
                                    it.copy(teamNumber = outcome.teamNumber, apiKey = outcome.apiKey, nexusApiKey = outcome.nexusApiKey)
                                }
                                RefreshWorker.refreshNow(appContext)
                            }
                        }
```
and replace
```kotlin
                    val http = HttpClient(OkHttp)
                    val outcome = SetupValidator.validate(apiKey, team, nexusKey) { TbaClient(it, http, BuildConfig.TBA_BASE_URL) }
                    http.close()
```
with
```kotlin
                    val http = HttpClient(OkHttp)
                    val outcome = try {
                        SetupValidator.validate(apiKey, team, nexusKey) { TbaClient(it, http, BuildConfig.TBA_BASE_URL) }
                    } finally {
                        http.close()
                    }
```

- [ ] **Step 4: Run tests and build**

Run: `(cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL` (the existing `PitWatchRoot` still calls `SettingsScreen(container, current, onBack = …)`, which remains valid).

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): Material 3 settings, accurate Live Updates hint, safer setup completion"
```

---

### Task 10: Navigation shell

**Files:**
- Modify (rewrite): `android/app/src/main/kotlin/com/pitwatch/app/ui/PitWatchRoot.kt`, `android/app/src/main/kotlin/com/pitwatch/app/MainActivity.kt`
- Delete: `android/app/src/main/kotlin/com/pitwatch/app/ui/HomeScreen.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/NavigationTest.kt`

**Interfaces:**
- Consumes: `MatchesScreen` (6), `EventPickerScreen` (7), `PitMapScreen` (8), `SettingsScreen` (9), `PitWatchTheme` (5).
- Produces: `enum class Tab { MATCHES, PIT_MAP, SETTINGS }` (`label`, `icon`); `@Composable fun PitWatchScaffold(selected: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit)`; `@Composable fun PitWatchRoot(container)`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/ui/NavigationTest.kt`:
```kotlin
package com.pitwatch.app.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlin.test.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class NavigationTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `three destinations, current one selected`() {
        val picks = mutableListOf<Tab>()
        compose.setContent { PitWatchScaffold(Tab.MATCHES, onSelect = { picks += it }) { Text("body") } }
        compose.onNodeWithText("Matches").assertIsSelected()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithText("body").assertIsDisplayed()
        compose.onNodeWithText("Pit map").performClick()
        assertEquals(listOf(Tab.PIT_MAP), picks)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.ui.NavigationTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'PitWatchScaffold'`.

- [ ] **Step 3: Implement**

Rewrite `android/app/src/main/kotlin/com/pitwatch/app/ui/PitWatchRoot.kt`:
```kotlin
package com.pitwatch.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitwatch.app.AppContainer
import com.pitwatch.app.ui.events.EventPickerScreen
import com.pitwatch.app.ui.matches.MatchesScreen
import com.pitwatch.app.ui.pitmap.PitMapScreen

enum class Tab(val label: String, val icon: ImageVector) {
    MATCHES("Matches", Icons.AutoMirrored.Filled.List),
    PIT_MAP("Pit map", Icons.Filled.LocationOn),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun PitWatchScaffold(selected: Tab, onSelect: (Tab) -> Unit, content: @Composable () -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).consumeWindowInsets(padding)) { content() }
    }
}

@Composable
fun PitWatchRoot(container: AppContainer) {
    val config by container.stores.config.data.collectAsStateWithLifecycle(initialValue = null)
    val current = config ?: return
    if (!current.isConfigured) {
        SetupScreen(container, current)
        return
    }
    var tab by rememberSaveable { mutableStateOf(Tab.MATCHES) }
    var pickingEvent by rememberSaveable { mutableStateOf(false) }
    PitWatchScaffold(tab, onSelect = { tab = it; pickingEvent = false }) {
        when (tab) {
            Tab.MATCHES -> if (pickingEvent) {
                BackHandler { pickingEvent = false }
                EventPickerScreen(container, onDone = { pickingEvent = false })
            } else {
                MatchesScreen(container, onPickEvent = { pickingEvent = true })
            }
            Tab.PIT_MAP -> PitMapScreen(container, onOpenSettings = { tab = Tab.SETTINGS })
            Tab.SETTINGS -> SettingsScreen(container, current)
        }
    }
}
```

Delete `android/app/src/main/kotlin/com/pitwatch/app/ui/HomeScreen.kt`.

In `MainActivity.kt`, replace the `setContent { … }` block with:
```kotlin
        setContent {
            PitWatchTheme {
                Surface { PitWatchRoot(container) }
            }
        }
```
and replace the imports `androidx.compose.foundation.isSystemInDarkTheme`, `androidx.compose.material3.MaterialTheme`, `androidx.compose.material3.darkColorScheme`, `androidx.compose.material3.lightColorScheme` with `com.pitwatch.app.ui.theme.PitWatchTheme`.

- [ ] **Step 4: Run all tests and build**

Run: `(cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL`; all tests pass (including `SmokeTest`'s activity launch).

- [ ] **Step 5: Commit**

```bash
git add -A android/app
git commit -m "feat(app): bottom-nav shell with Matches, Pit map and Settings"
```

---

### Task 11: Glance home-screen widget

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetContent.kt`, `android/app/src/main/kotlin/com/pitwatch/app/widget/PitWatchWidget.kt`, `android/app/src/main/res/xml/pitwatch_widget_info.xml`, `android/app/src/main/res/layout/widget_countdown.xml`, `android/app/src/main/res/values/strings.xml`, `android/app/src/main/res/values/colors.xml`, `android/app/src/main/res/values-night/colors.xml`
- Modify: `android/app/src/main/AndroidManifest.xml`, `android/app/src/main/kotlin/com/pitwatch/app/AppContainer.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/widget/WidgetContentTest.kt`

**Interfaces:**
- Consumes: `WidgetModel` (Task 4), `StatusColors` (Task 5), `Repository(onChanged)` (Task 2), `AppContainer.updateWidgets` (Task 9).
- Produces: `class PitWatchWidget : GlanceAppWidget` (`SMALL`, `MEDIUM`, `LARGE: DpSize`), `class PitWatchWidgetReceiver : GlanceAppWidgetReceiver`, `@Composable fun WidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit)`.

- [ ] **Step 1: Write the failing test**

`android/app/src/test/kotlin/com/pitwatch/app/widget/WidgetContentTest.kt`:
```kotlin
package com.pitwatch.app.widget

import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.glance.text.Text
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetContentTest {
    private val ready = WidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), SNAP_NOW, Locale.US,
    )

    @Test
    fun `small - next match and countdown`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("5507 · #34 · 1-2-0")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onNode(hasText("LAST")).assertDoesNotExist()
    }

    @Test
    fun `medium - adds alliances and the last result`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.MEDIUM)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("LAST")).assertExists()
        onNode(hasText("W 403–299")).assertExists()
        onNode(hasText("5507", substring = true)).assertExists()
    }

    @Test
    fun `large - upcoming list with breaks`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.LARGE)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("UPCOMING")).assertExists()
        onNode(hasText("End of day")).assertExists()
        onNode(hasText("Q43")).assertExists()
    }

    @Test
    fun `message states`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(WidgetModels.build(com.pitwatch.core.store.EventCache(), UserConfig(), SNAP_NOW)) { } } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.widget.WidgetContentTest' --console=plain)`
Expected: FAIL — `Unresolved reference 'WidgetContent'`, `'PitWatchWidget'`.

- [ ] **Step 3: Implement**

`android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetContent.kt`:
```kotlin
package com.pitwatch.app.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pitwatch.app.MainActivity
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.Phase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val clockTime: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

private fun time(row: MatchListModel.MatchRow): String =
    row.time?.let { (if (row.estimated) "~" else "") + clockTime.format(it) } ?: "Time TBD"

/** The widget at whatever size it's placed; [countdown] renders the live chronometer (swappable in tests). */
@Composable
fun WidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit) {
    val size = LocalSize.current
    val wide = size.width >= PitWatchWidget.MEDIUM.width
    val tall = size.height >= PitWatchWidget.LARGE.height
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(16.dp).padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(model.header, style = muted, maxLines = 1)
        val next = model.next
        if (model.state != WidgetModel.State.READY || next == null) {
            Spacer(GlanceModifier.height(8.dp))
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp))
            model.last?.let { if (wide) LastResult(it) }
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth()) {
            Column(GlanceModifier.defaultWeight()) {
                NextMatch(next, model.countdownDeadline, countdown)
                if (wide) {
                    AllianceText(next.red, "red")
                    AllianceText(next.blue, "blue")
                }
            }
            if (wide && !tall) model.last?.let { LastResult(it) }
        }
        if (tall) {
            Spacer(GlanceModifier.height(8.dp))
            Text("UPCOMING", style = muted)
            model.later.take(6).forEach { item ->
                when (item) {
                    is MatchListModel.Item.Upcoming -> Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(item.row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), modifier = GlanceModifier.defaultWeight())
                        Text(time(item.row), style = muted)
                    }
                    is MatchListModel.Item.Break -> Text(item.title, style = muted, modifier = GlanceModifier.padding(vertical = 2.dp))
                }
            }
            model.last?.let { LastResult(it) }
        }
    }
}

@Composable
private fun NextMatch(row: MatchListModel.MatchRow, deadline: Instant?, countdown: @Composable (Instant) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 28.sp, fontWeight = FontWeight.Bold))
        row.phase?.let {
            Spacer(GlanceModifier.width(6.dp))
            PhaseBadge(it)
        }
    }
    Text(time(row), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    if (deadline != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            countdown(deadline)
            row.countdown?.let { Text(" ${it.target}", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp)) }
        }
    }
}

@Composable
private fun PhaseBadge(phase: Phase) {
    Box(GlanceModifier.background(ColorProvider(StatusColors.phase(phase))).cornerRadius(8.dp).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(phase.stateLabel, style = TextStyle(color = ColorProvider(StatusColors.onPhase(phase)), fontSize = 10.sp, fontWeight = FontWeight.Bold))
    }
}

@Composable
private fun AllianceText(line: MatchListModel.AllianceLine, color: String) {
    val dot = if (color == "red") "🔴" else "🔵"
    val teams = line.teams.joinToString(" ") { it.number }
    val opr = line.summedOpr?.let { "  Σ%.0f".format(it) } ?: ""
    Text("$dot $teams$opr", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1)
}

@Composable
private fun LastResult(result: MatchListModel.Result) {
    Column(GlanceModifier.padding(start = 8.dp, top = 4.dp)) {
        Text("LAST", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
        Text(result.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
        Text("${result.outcome} ${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold))
    }
}
```

`android/app/src/main/kotlin/com/pitwatch/app/widget/PitWatchWidget.kt`:
```kotlin
package com.pitwatch.app.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.R
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first

/** One responsive widget; renders only from the persisted cache (never network). */
class PitWatchWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val now = container.clock()
        val model = WidgetModels.build(container.stores.cache.data.first(), container.stores.config.data.first(), now)
        provideContent {
            GlanceTheme { WidgetContent(model) { deadline -> ChronometerCountdown(deadline, now) } }
        }
    }

    companion object {
        val SMALL = DpSize(110.dp, 110.dp)
        val MEDIUM = DpSize(250.dp, 110.dp)
        val LARGE = DpSize(250.dp, 250.dp)
    }
}

class PitWatchWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PitWatchWidget()
}

/** A platform Chronometer: ticks on the home screen with no app updates. */
@Composable
private fun ChronometerCountdown(deadline: Instant, now: Instant) {
    val context = LocalContext.current
    val remaining = Duration.between(now, deadline).toMillis()
    val views = RemoteViews(context.packageName, R.layout.widget_countdown).apply {
        setChronometer(R.id.countdown, SystemClock.elapsedRealtime() + remaining, null, true)
        setChronometerCountDown(R.id.countdown, true)
    }
    AndroidRemoteViews(views)
}
```

`android/app/src/main/res/layout/widget_countdown.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<Chronometer xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/countdown"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:fontFeatureSettings="tnum"
    android:textColor="@color/widget_countdown"
    android:textSize="22sp" />
```

`android/app/src/main/res/values/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="widget_countdown">@android:color/system_neutral1_900</color>
</resources>
```

`android/app/src/main/res/values-night/colors.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="widget_countdown">@android:color/system_neutral1_50</color>
</resources>
```

`android/app/src/main/res/values/strings.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="widget_description">Your next match at a glance</string>
</resources>
```

`android/app/src/main/res/xml/pitwatch_widget_info.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/widget_description"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:maxResizeHeight="450dp"
    android:maxResizeWidth="530dp"
    android:minHeight="110dp"
    android:minResizeHeight="110dp"
    android:minResizeWidth="110dp"
    android:minWidth="110dp"
    android:resizeMode="horizontal|vertical"
    android:targetCellHeight="2"
    android:targetCellWidth="2"
    android:updatePeriodMillis="0"
    android:widgetCategory="home_screen" />
```

In `AndroidManifest.xml`, inside `<application>` after the `LiveWakeReceiver` receiver, add:
```xml
        <receiver
            android:name=".widget.PitWatchWidgetReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/pitwatch_widget_info" />
        </receiver>
```

In `AppContainer.create`, add imports `androidx.glance.appwidget.updateAll` and `com.pitwatch.app.widget.PitWatchWidget`, and replace the repository construction and `return` with:
```kotlin
            val appContext = context.applicationContext
            val updateWidgets: suspend () -> Unit = { PitWatchWidget().updateAll(appContext) }
            val repository = Repository(
                stores,
                { TbaClient(it, http, BuildConfig.TBA_BASE_URL) },
                { NexusClient(it, http, BuildConfig.NEXUS_BASE_URL) },
                onChanged = updateWidgets,
            )
            return AppContainer(stores, repository, scope, updateWidgets = updateWidgets)
```

- [ ] **Step 4: Run tests and build**

Run: `(cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL`; widget tests pass. If `glance-appwidget-testing` cannot render under Robolectric with the SDK 36 config, record a ruling and keep `WidgetModelTest` as the widget's logic coverage, relying on the Task 12 on-device screenshots for layout.

- [ ] **Step 5: Commit**

```bash
git add android/app
git commit -m "feat(app): responsive Glance home-screen widget with live countdown"
```

---

### Task 12: Notification accent, docs, full verification, on-device screenshots

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveNotification.kt`, `android/app/src/test/kotlin/com/pitwatch/app/live/LiveNotificationTest.kt`, `README.md`, `docs/android-manual-test.md`

- [ ] **Step 1: Write the failing test** — append inside `class LiveNotificationTest`:
```kotlin
    @Test
    fun `accent follows the Material You system palette`() {
        assertEquals(context.getColor(android.R.color.system_accent1_600), build(snapshot()).color)
    }
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests 'com.pitwatch.app.live.LiveNotificationTest' --console=plain)`
Expected: FAIL — `expected:<…> but was:<0>`.

- [ ] **Step 3: Implement** — in `LiveNotification.build`, add to the builder chain after `.setCategory(NotificationCompat.CATEGORY_PROGRESS)`:
```kotlin
            .setColor(context.getColor(android.R.color.system_accent1_600)) // Material You accent for the icon
```

- [ ] **Step 4: Docs**

In `README.md`, replace the two-line `android/` entry with:
```
android/    Android app (Gradle). :core = Kotlin port of TBAKit; :app = Matches/Pit map/Settings,
            live notification, home-screen widget (`cd android && ./gradlew :core:test :app:testDebugUnitTest`;
            manual test: docs/android-manual-test.md)
```
In `docs/android-manual-test.md`, append:
```markdown
7. Check the screens: Matches (status card, next-match card with phase badge, break rows, results),
   the event picker (calendar icon), Pit map (zoomed on your pit), Settings — in light and dark mode
   (`adb shell cmd uimode night yes|no`).
8. Add the PitWatch widget to the home screen; resize it through small, medium and large.
```

- [ ] **Step 5: Full verification**

Run: `(cd android && ./gradlew clean :core:test :app:testDebugUnitTest :app:assembleDebug --console=plain)`
Expected: `BUILD SUCCESSFUL`, 0 failures.

- [ ] **Step 6: On-device screenshots** (a separate AVD; never the user's attached devices)

Boot `preset-alarm-root-api36` on `-port 5580` headless, sync its clock to the host (`adb -s emulator-5580 root; adb -s emulator-5580 shell date -u $(date -u +%m%d%H%M%Y.%S)`), run `python3 scripts/fake-api.py --port 8765 scripts/fixtures/2026cancmp/2026-04-11T00-51-22Z`, install a debug build pointed at it (`-Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3 -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1`), complete setup for team 5507, and capture with `adb -s emulator-5580 exec-out screencap -p`:
Matches (light + dark), event picker, Pit map, Settings, and the widget at small/medium/large (`adb shell appwidget` is not available; add the widget via the launcher long-press if it can be driven, otherwise record that widget layout was verified only by the Glance unit tests). View each image; fix layout defects (each with a failing test where one is possible). Shut down the emulator and server afterwards.

- [ ] **Step 7: Commit**

```bash
git add android/app README.md docs/android-manual-test.md
git commit -m "feat(app): Material You notification accent; docs for the new screens and widget"
```
