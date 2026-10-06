# Schedule Widget, Previews, Schedule Notification, API Key Copy — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a schedule-only widget, generated picker previews for both widgets, an opt-in schedule notification, a "keep notifications pinned" toggle, and clearer API key copy.

**Architecture:**
- **Shared data:** everything renders from the persisted cache through `MatchListModels`/`WidgetModels`.
- **Schedule widget:** a second `GlanceAppWidget` that reuses the main widget's list composables and measured-height planning.
- **Schedule notification:** a plain ongoing notification. `ScheduleNotifier` posts it from the same hook that refreshes widgets (`AppContainer.updateWidgets`).
- **Pinning:** implemented by re-posting on dismissal, both in a broadcast receiver for the schedule notification and as a new live-service action.
- **New prefs:** a new app-side DataStore holds the new settings.

**Tech Stack:**
- Kotlin, Compose (Material 3), Glance 1.2.0 (including `providePreview` and `GlanceAppWidgetManager.setWidgetPreviews`), NotificationCompat.
- Tests: Robolectric 4.17, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-06-schedule-widget-notification-design.md`

## Global Constraints

- **Branch:** `schedule-features` (branched off `main`). Make modular commits, merge back to `main` locally, and never push.
- **Tests:** `make test` is the full suite. It must be green at the end of every task.
- **Devices:** emulator-5580 only, via `make fake-install DEVICE=emulator-5580`. Never use RFGL82WFV7K or emulator-5554.
- **Running processes:** keep emulator-5580 and `fake-api.py` (port 8765) running.
- **Network:** no new polling or network calls. The widget and notification render from the persisted cache only.
- **Copy (exact strings):**
  - Labels: "TBA API key" and "FRC Nexus API key".
  - TBA help: "Required. Match schedule, times, scores, rankings and alliances. Get a read key from your account page on thebluealliance.com."
  - Nexus help: "Optional, recommended at events. Live queue status (queuing → on deck → on field), more accurate times, breaks, the pit map and live tracking's phase timeline. Without it PitWatch uses TBA's estimated times."
- **Schedule notification:**
  - channel `schedule` ("Match schedule"), low importance;
  - ID 1002 (the live notification is 1001);
  - not promoted, no chronometer, at most 6 inbox lines.
- **Both settings default off:** `scheduleEnabled = false`, `pinned = false`.
- **Glance limit:** at most 10 children per Column or Row.

## Review Focus

1. **Notification permission denied.** The schedule switch must not crash and must not claim it's showing anything. Instead it shows the "notifications are off" hint. *(Task 6 test.)*
2. **Schedule widget or notification with no event, or not configured.** Show the same message the main widget shows, never an empty card. *(Task 2 and Task 5 tests.)*
3. **Swiping the schedule notification with pinning off.** It must turn the setting off, so it doesn't reappear on the next refresh. *(Task 5 test.)*
4. **Live notification dismissed when not tracking** (a stale intent). This must not resurrect tracking. *(Task 7 test.)*
5. **Preview registration rate-limited.** The version must not be recorded, so registration retries on a later launch. *(Task 4 test.)*

---

### Task 1: Notification prefs store and API key copy

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/data/Stores.kt`
- Create: `android/app/src/main/kotlin/com/pitwatch/app/data/NotificationPrefs.kt`
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/ApiKeyHelp.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/SetupScreen.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/SettingsScreen.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/SetupScreenTest.kt` (new); `SettingsScreenTest.kt` (extend); `data/StoresTest.kt` (extend)

**Interfaces:**
- Produces:
  - `@Serializable data class NotificationPrefs(val scheduleEnabled: Boolean = false, val pinned: Boolean = false, val previewsVersion: Int = 0)`;
  - `Stores.notificationPrefs: DataStore<NotificationPrefs>`;
  - `object ApiKeyHelp { const val TBA: String; const val NEXUS: String }`.

- [ ] **Step 1: Write the failing tests**

`SetupScreenTest.kt`:

```kotlin
package com.pitwatch.app.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.AppContainer
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SetupScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        container = installTestContainer(tmp.root)
    }

    @After
    fun tearDown() = container.scope.cancel()

    @Test
    fun `each key explains what it's for, and Nexus isn't labeled optional`() {
        compose.setContent { SetupScreen(container, UserConfig()) }
        compose.onNodeWithText("FRC Nexus API key").assertIsDisplayed()
        compose.onAllNodes(hasText("(optional)", substring = true)).assertCountEquals(0)
        compose.onNodeWithText(ApiKeyHelp.TBA).assertIsDisplayed()
        compose.onNodeWithText(ApiKeyHelp.NEXUS).assertIsDisplayed()
    }
}
```

Append to `SettingsScreenTest`:

```kotlin
    @Test
    fun `API key fields carry the same help text`() {
        compose.setContent { SettingsScreen(container, config) }
        compose.onNode(hasText(ApiKeyHelp.TBA)).performScrollTo().assertIsDisplayed()
        compose.onNode(hasText(ApiKeyHelp.NEXUS)).performScrollTo().assertIsDisplayed()
    }
```

Append to `StoresTest`. Use the file's existing temp-dir and scope setup; read the file first and match its helper names.

```kotlin
    @Test
    fun `notification prefs default to off`() = runBlocking {
        assertEquals(NotificationPrefs(), stores.notificationPrefs.data.first())
        assertEquals(false, stores.notificationPrefs.data.first().scheduleEnabled)
    }
```

- [ ] **Step 2: Run them to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*SetupScreenTest' --tests '*SettingsScreenTest' --tests '*StoresTest')`
Expected: compile FAIL, because `ApiKeyHelp`, `NotificationPrefs` and `notificationPrefs` don't exist.

- [ ] **Step 3: Implement**

`NotificationPrefs.kt`:

```kotlin
package com.pitwatch.app.data

import kotlinx.serialization.Serializable

/** App-side notification and preview settings (not part of :core's UserConfig). */
@Serializable
data class NotificationPrefs(
    /** The opt-in schedule notification. */
    val scheduleEnabled: Boolean = false,
    /** Re-post notifications when swiped away; they stop only from their own actions. */
    val pinned: Boolean = false,
    /** App version code whose widget-picker previews were last registered. */
    val previewsVersion: Int = 0,
)
```

In `Stores`, add:

```kotlin
    val notificationPrefs: DataStore<NotificationPrefs> =
        create(dir, "notification_prefs.json", NotificationPrefs.serializer(), NotificationPrefs(), scope)
```

`ApiKeyHelp.kt`:

```kotlin
package com.pitwatch.app.ui

/** What each API key powers; shown under the fields on Setup and Settings. */
object ApiKeyHelp {
    const val TBA = "Required. Match schedule, times, scores, rankings and alliances. " +
        "Get a read key from your account page on thebluealliance.com."
    const val NEXUS = "Optional, recommended at events. Live queue status (queuing → on deck → on field), more accurate times, " +
        "breaks, the pit map and live tracking's phase timeline. Without it PitWatch uses TBA's estimated times."
}
```

**`SetupScreen`:**
- Delete the `Text("Get a read API key from your account page on thebluealliance.com.")` line.
- Fields:

```kotlin
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("TBA API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(ApiKeyHelp.TBA) })
        OutlinedTextField(team, { team = it }, label = { Text("Team number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(nexusKey, { nexusKey = it }, label = { Text("FRC Nexus API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text(ApiKeyHelp.NEXUS) })
```

**`SettingsScreen`:** in the "API keys" section, give the two `OutlinedTextField`s the same `supportingText` slots.

- [ ] **Step 4: Run to verify they pass, then the full suite**

Run: `make test`. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src && git commit -m "feat(app): API key help text on Setup and Settings; notification prefs store"
```

---

### Task 2: Schedule widget model and size plan

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/widget/ScheduleWidgetModel.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetModel.kt`: make the `WidgetPlans` part constants `internal`.
- Test: `android/app/src/test/kotlin/com/pitwatch/app/widget/ScheduleWidgetModelTest.kt`

**Interfaces:**
- Consumes: `WidgetModels.build`, `MatchListModels.build`, `WidgetModel.State`, `WidgetPlans` constants.
- Produces:
  - `data class ScheduleWidgetModel(state: WidgetModel.State, header: String, days: List<MatchListModel.Day>, last: MatchListModel.Result?, message: String?, timeZone: ZoneId, zoneLabel: String?)`;
  - `object ScheduleWidgetModels { fun build(cache: EventCache, config: UserConfig, now: Instant, locale: Locale = Locale.getDefault(), zone: ZoneId = ZoneId.systemDefault()): ScheduleWidgetModel }`;
  - `data class SchedulePlan(header: Boolean, lastLine: Boolean, listLines: Int)`;
  - `object SchedulePlans { fun plan(height: Dp, hasLast: Boolean): SchedulePlan }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.pitwatch.app.widget

import androidx.compose.ui.unit.dp
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ScheduleWidgetModelTest {
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private fun ready() = ScheduleWidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        config, SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `ready - the schedule starts with the next match and keeps the last result`() {
        val m = ready()
        assertEquals(WidgetModel.State.READY, m.state)
        assertEquals("5507 · #34 · 1-2-0", m.header)
        val first = m.days.first().items.filterIsInstance<MatchListModel.Item.Upcoming>().first().row
        assertEquals("Q36", first.shortLabel)
        assertEquals("Q22", m.last?.shortLabel)
    }

    @Test
    fun `not configured and no event show the main widget's messages`() {
        assertEquals("Set up PitWatch", ScheduleWidgetModels.build(EventCache(), UserConfig(), SNAP_NOW).message)
        val noEvent = ScheduleWidgetModels.build(EventCache(), config, SNAP_NOW)
        assertEquals(WidgetModel.State.NO_EVENT, noEvent.state)
        assertEquals("No event yet", noEvent.message)
    }

    @Test
    fun `size plan keeps at least a day and a row, then adds header and last result`() {
        val tiny = SchedulePlans.plan(110.dp, hasLast = true)
        assertTrue(tiny.listLines >= 2)
        val twoByTwo = SchedulePlans.plan(223.dp, hasLast = true)
        assertTrue(twoByTwo.header && twoByTwo.lastLine)
        assertTrue(twoByTwo.listLines >= 5)
        assertTrue(SchedulePlans.plan(450.dp, hasLast = true).listLines > twoByTwo.listLines)
    }
}
```

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*ScheduleWidgetModelTest')`
Expected: compile FAIL with unresolved `ScheduleWidgetModels` and `SchedulePlans`.

- [ ] **Step 3: Implement** `ScheduleWidgetModel.kt`:

```kotlin
package com.pitwatch.app.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.matches.MatchListModels
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import com.pitwatch.core.store.RefreshState
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

/** The schedule widget (and schedule notification): upcoming matches from the next one on, and the last result. */
data class ScheduleWidgetModel(
    val state: WidgetModel.State,
    val header: String,
    val days: List<MatchListModel.Day>,
    val last: MatchListModel.Result?,
    val message: String?,
    val timeZone: ZoneId = ZoneId.systemDefault(),
    val zoneLabel: String? = null,
)

object ScheduleWidgetModels {
    fun build(
        cache: EventCache,
        config: UserConfig,
        now: Instant,
        locale: Locale = Locale.getDefault(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ScheduleWidgetModel {
        // Same states and messages as the main widget; only READY needs the full schedule.
        val widget = WidgetModels.build(cache, config, now, locale, zone)
        if (widget.state != WidgetModel.State.READY) {
            return ScheduleWidgetModel(widget.state, widget.header, emptyList(), widget.last, widget.message, widget.timeZone, widget.zoneLabel)
        }
        val list = MatchListModels.build(cache, config, RefreshState(), now, locale, zone)
        return ScheduleWidgetModel(WidgetModel.State.READY, widget.header, list.days, widget.last, null, list.timeZone, list.zoneLabel)
    }
}

data class SchedulePlan(val header: Boolean, val lastLine: Boolean, val listLines: Int)

object SchedulePlans {
    /** A day header plus one row always fit first; then the team header, the last result, and more rows. */
    fun plan(height: Dp, hasLast: Boolean): SchedulePlan {
        val minimum = WidgetPlans.ROW * 2
        var left = height - WidgetPlans.PADDING - minimum
        fun take(wanted: Boolean, cost: Dp): Boolean = (wanted && left >= cost).also { if (it) left -= cost }
        val header = take(true, WidgetPlans.HEADER)
        val last = take(hasLast, WidgetPlans.LAST_LINE - 12.dp) // no divider above it here
        val lines = ((left + minimum) / WidgetPlans.ROW).toInt().coerceAtLeast(2)
        return SchedulePlan(header, last, lines)
    }
}
```

In `WidgetPlans`, change `private val PADDING`, `HEADER`, `LAST_LINE` and `ROW` to `internal val`.

- [ ] **Step 4: Run to verify it passes, then the full suite.** Run: `make test`. Expected: all pass.

- [ ] **Step 5: Commit:** `git add android/app/src && git commit -m "feat(widget): schedule widget model and size plan"`

---

### Task 3: Schedule widget UI, receiver and refresh

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/widget/ScheduleWidget.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetContent.kt`: extract `internal fun UpcomingList(lines, times)`, make `LastLine` `internal`, and add the in-motion pill to rows.
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetRefresh.kt`: render both widgets.
- Create: `android/app/src/main/res/xml/schedule_widget_info.xml` and `android/app/src/main/res/layout/schedule_widget_preview.xml`
- Modify: `android/app/src/main/AndroidManifest.xml` and `android/app/src/main/res/values/strings.xml`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/widget/ScheduleWidgetContentTest.kt`

**Interfaces:**
- Consumes: Task 2's models and plans.
- Produces:
  - `@Composable fun ScheduleWidgetContent(model: ScheduleWidgetModel)`;
  - `class ScheduleWidget : GlanceAppWidget` and `class ScheduleWidgetReceiver : GlanceAppWidgetReceiver`;
  - `internal fun UpcomingList(lines: List<WidgetLine>, times: TimeFormat)` and `internal fun LastLine(result, compact)`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.pitwatch.app.widget

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.glance.testing.unit.hasText
import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.util.Locale
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScheduleWidgetContentTest {
    private val ready = ScheduleWidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `2x2 - header, last result and the schedule from the next match`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ready) } }
        onNode(hasText("5507 · #34 · 1-2-0")).assertExists()
        onNode(hasText("403–299")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("ON FIELD")).assertExists() // the next match is in motion
        onNode(hasText("End of day")).assertExists()
    }

    @Test
    fun `tall - more of the schedule, capped, under the child limit`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(360.dp, 450.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ready) } }
        onNode(hasText("Q43")).assertExists()
        onNode(hasText("LAST · Q22")).assertExists()
        onNode(hasTestTag("schedule-root")).onChildren().assertCountEquals(3) // header, last, list
    }

    @Test
    fun `not configured shows the setup message`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(DpSize(172.dp, 223.dp))
        provideComposable { GlanceTheme { ScheduleWidgetContent(ScheduleWidgetModels.build(EventCache(), UserConfig(), SNAP_NOW)) } }
        onNode(hasText("Set up PitWatch")).assertExists()
    }
}
```

- [ ] **Step 2: Run to verify it fails.** Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*ScheduleWidgetContentTest')`. Expected: compile FAIL with unresolved `ScheduleWidgetContent`.

- [ ] **Step 3: Implement**

**In `WidgetContent.kt`:**
- Move the body of the tall-widget `Column { for (line in lines) … }` into `@Composable internal fun UpcomingList(lines: List<WidgetLine>, times: TimeFormat)`, keeping its own `Column`. Call it from `WidgetContent`.
- In the match row, between the label and the time, add:

```kotlin
                                item.row.phase?.takeIf { it != Phase.PRE_QUEUE }?.let {
                                    Pill(it.stateLabel, ColorProvider(StatusColors.phase(it)), ColorProvider(StatusColors.onPhase(it)))
                                    Spacer(GlanceModifier.width(6.dp))
                                }
```

- Make `LastLine` `internal`.

**`ScheduleWidget.kt`:**

```kotlin
package com.pitwatch.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pitwatch.app.MainActivity
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.store.EventCache
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** Upcoming schedule and the last result only — no countdown. Renders from the persisted cache. */
class ScheduleWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val container = (context.applicationContext as PitWatchApp).container
        val stores = container.stores
        val initialCache = stores.cache.data.first()
        val initialConfig = stores.config.data.first()
        provideContent { GlanceTheme { LiveScheduleWidget(stores.cache.data, stores.config.data, initialCache, initialConfig, container.clock) } }
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()
}

@Composable
fun LiveScheduleWidget(cacheFlow: Flow<EventCache>, configFlow: Flow<UserConfig>, initialCache: EventCache, initialConfig: UserConfig, clock: () -> Instant) {
    val cache by cacheFlow.collectAsState(initialCache)
    val config by configFlow.collectAsState(initialConfig)
    ScheduleWidgetContent(ScheduleWidgetModels.build(cache, config, clock()))
}

@Composable
fun ScheduleWidgetContent(model: ScheduleWidgetModel) {
    val size = LocalSize.current
    val wide = size.width >= PitWatchWidget.MEDIUM.width
    val plan = SchedulePlans.plan(size.height, hasLast = model.last != null)
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed"))
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    // At most 3 children: header, last result, list (its own Column).
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>())
            .semantics { testTag = "schedule-root" },
    ) {
        if (plan.header || model.state != WidgetModel.State.READY) Text(model.header, style = muted, maxLines = 1)
        if (model.state != WidgetModel.State.READY) {
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp), modifier = GlanceModifier.padding(top = 8.dp))
            return@Column
        }
        if (plan.lastLine) model.last?.let { LastLine(it, compact = !wide) }
        UpcomingList(WidgetLines.fit(model.days, plan.listLines), times)
    }
}
```

**`WidgetRefresh.run`:** change the default `render` to

```kotlin
{ PitWatchWidget().updateAll(context); ScheduleWidget().updateAll(context) }
```

**`strings.xml`:** add `<string name="schedule_widget_description">Your upcoming matches and the last result</string>`.

**`schedule_widget_info.xml`:** copy `pitwatch_widget_info.xml` and change these attributes:
- `android:description="@string/schedule_widget_description"`;
- `android:previewLayout="@layout/schedule_widget_preview"`.

**`schedule_widget_preview.xml`:** a vertical `LinearLayout`, built the same way as `widget_preview.xml`, with `TextView`s for "5507 · #3 · 7-2-0", "LAST · Q22  403–299 WIN", "Saturday", "● Q43   ~9:27 AM", "● Q52   ~10:41 AM", "Lunch" and "● Q61   ~11:54 AM".

**Manifest:** add the receiver next to `PitWatchWidgetReceiver`:

```xml
        <receiver
            android:name=".widget.ScheduleWidgetReceiver"
            android:exported="true"
            android:label="PitWatch schedule">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/schedule_widget_info" />
        </receiver>
```

- [ ] **Step 4: Run to verify, then the full suite.** Run: `make test`. Expected: all pass, including the existing `WidgetContentTest`. The new row pill adds one Row child, 4 in total.

- [ ] **Step 5: Commit:** `git add android/app/src && git commit -m "feat(widget): schedule widget — upcoming matches and the last result"`

---

### Task 4: Generated picker previews

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/widget/SampleWidgetData.kt`
- Create: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetPreviews.kt`
- Modify: `PitWatchWidget.kt` and `ScheduleWidget.kt` (`providePreview`), and `PitWatchApp.kt` (register on launch)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/widget/WidgetPreviewsTest.kt`

**Interfaces:**
- Consumes: `NotificationPrefs.previewsVersion` (Task 1) and `ScheduleWidgetModel` (Task 2).
- Produces:
  - `object SampleWidgetData { fun main(now: Instant): WidgetModel; fun schedule(now: Instant): ScheduleWidgetModel }`;
  - `fun interface PreviewPublisher { suspend fun publish(receiver: KClass<out GlanceAppWidgetReceiver>): Int }`;
  - `object WidgetPreviews { suspend fun registerOnce(prefs: DataStore<NotificationPrefs>, versionCode: Int, publisher: PreviewPublisher) }`.

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.pitwatch.app.widget

import androidx.glance.appwidget.GlanceAppWidgetManager
import com.pitwatch.app.data.NotificationPrefs
import com.pitwatch.app.data.Stores
import java.time.Instant
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class WidgetPreviewsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val stores by lazy { Stores(tmp.root, CoroutineScope(SupervisorJob() + Dispatchers.IO)) }

    @Test
    fun `sample data is a ready widget with a schedule`() {
        val now = Instant.parse("2026-04-11T00:00:00Z")
        assertEquals(WidgetModel.State.READY, SampleWidgetData.main(now).state)
        assertEquals("Q36", SampleWidgetData.main(now).next?.shortLabel)
        assertEquals(WidgetModel.State.READY, SampleWidgetData.schedule(now).state)
        kotlin.test.assertTrue(SampleWidgetData.schedule(now).days.isNotEmpty())
    }

    @Test
    fun `previews are published once per version`() = runBlocking {
        val calls = mutableListOf<String>()
        val ok = PreviewPublisher { calls += it.simpleName!!; GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }
        WidgetPreviews.registerOnce(stores.notificationPrefs, 7, ok)
        WidgetPreviews.registerOnce(stores.notificationPrefs, 7, ok)
        assertEquals(listOf("PitWatchWidgetReceiver", "ScheduleWidgetReceiver"), calls)
        assertEquals(7, stores.notificationPrefs.data.first().previewsVersion)
    }

    @Test
    fun `a rate-limited publish is retried on a later launch`() = runBlocking {
        WidgetPreviews.registerOnce(stores.notificationPrefs, 7) { GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_RATE_LIMITED }
        assertEquals(NotificationPrefs().previewsVersion, stores.notificationPrefs.data.first().previewsVersion)
    }
}
```

- [ ] **Step 2: Run to verify it fails.** Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*WidgetPreviewsTest')`. Expected: compile FAIL.

- [ ] **Step 3: Implement**

`SampleWidgetData.kt`, built from literals:

```kotlin
package com.pitwatch.app.widget

import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** Fixed sample content for the widget picker's generated previews. */
object SampleWidgetData {
    private fun line(vararg teams: String, opr: Double? = null) =
        MatchListModel.AllianceLine(teams.map { MatchListModel.TeamChip(it, it == "5507") }, opr)

    private fun row(n: Int, at: Instant, alliance: MatchAlliance, phase: Phase?, next: Boolean) = MatchListModel.MatchRow(
        key = "sample_qm$n", label = "Qual $n", shortLabel = "Q$n", time = at, estimated = true, alliance = alliance, phase = phase,
        red = line("4698", "5507", "1678", opr = 351.0), blue = line("6036", "9470", "6814", opr = 539.0), isNext = next,
        countdown = if (next) MatchListModel.Countdown(at.plus(Duration.ofMinutes(4)), "to match end") else null,
    )

    private val last = MatchListModel.Result(
        "sample_qm22", "Qual 22", "Q22", 403, 299, "W", line("9400", "6418", "5104"), line("5507", "2813", "8033"), 299, 403,
    )

    private fun days(now: Instant): List<MatchListModel.Day> {
        val zone = ZoneId.systemDefault()
        val today = now.atZone(zone).toLocalDate()
        return listOf(MatchListModel.Day(today, "Today", listOf(
            MatchListModel.Item.Upcoming(row(36, now.plus(Duration.ofMinutes(2)), MatchAlliance.RED, Phase.ON_FIELD, next = true)),
            MatchListModel.Item.Upcoming(row(43, now.plus(Duration.ofMinutes(74)), MatchAlliance.RED, null, next = false)),
            MatchListModel.Item.Break("Lunch", now.plus(Duration.ofMinutes(110)), now.plus(Duration.ofMinutes(170))),
            MatchListModel.Item.Upcoming(row(52, now.plus(Duration.ofMinutes(190)), MatchAlliance.BLUE, null, next = false)),
        )))
    }

    fun main(now: Instant): WidgetModel {
        val days = days(now)
        val next = (days[0].items[0] as MatchListModel.Item.Upcoming).row
        return WidgetModel(
            WidgetModel.State.READY, "5507 · #34 · 1-2-0", "California Northern", next,
            listOf(days[0].copy(items = days[0].items.drop(1))), last, null, next.countdown?.deadline,
        )
    }

    fun schedule(now: Instant): ScheduleWidgetModel =
        ScheduleWidgetModel(WidgetModel.State.READY, "5507 · #34 · 1-2-0", days(now), last, null)
}
```

`WidgetPreviews.kt`:

```kotlin
package com.pitwatch.app.widget

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.pitwatch.app.data.NotificationPrefs
import kotlin.reflect.KClass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

fun interface PreviewPublisher {
    suspend fun publish(receiver: KClass<out GlanceAppWidgetReceiver>): Int
}

/** Registers both widgets' generated picker previews once per app version (the platform rate-limits this call). */
object WidgetPreviews {
    private val RECEIVERS = listOf(PitWatchWidgetReceiver::class, ScheduleWidgetReceiver::class)

    suspend fun registerOnce(prefs: DataStore<NotificationPrefs>, versionCode: Int, publisher: PreviewPublisher) {
        if (prefs.data.first().previewsVersion == versionCode) return
        val results = try {
            RECEIVERS.map { publisher.publish(it) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("WidgetPreviews", "preview registration failed", e)
            return
        }
        if (results.all { it == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS }) {
            prefs.updateData { it.copy(previewsVersion = versionCode) }
        }
    }

    fun publisher(manager: GlanceAppWidgetManager) = PreviewPublisher { manager.setWidgetPreviews(it) }
}
```

**`providePreview`:** in `PitWatchWidget`,

```kotlin
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent {
            GlanceTheme {
                WidgetContent(SampleWidgetData.main(Instant.now())) {
                    Text("3:27", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily("sans-serif-condensed")))
                }
            }
        }
    }
```

In `ScheduleWidget`:

```kotlin
    override suspend fun providePreview(context: Context, widgetCategory: Int) {
        provideContent { GlanceTheme { ScheduleWidgetContent(SampleWidgetData.schedule(Instant.now())) } }
    }
```

**`PitWatchApp.onCreate`**, after the container is created:

```kotlin
        container.scope.launch {
            WidgetPreviews.registerOnce(container.stores.notificationPrefs, BuildConfig.VERSION_CODE, WidgetPreviews.publisher(GlanceAppWidgetManager(this@PitWatchApp)))
        }
```

- [ ] **Step 4: Run to verify, then the full suite.** Run: `make test`. Expected: all pass.

- [ ] **Step 5: Commit:** `git add android/app/src && git commit -m "feat(widget): generated widget-picker previews for both widgets"`

---

### Task 5: Schedule notification

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/notify/ScheduleNotification.kt` (pure content and builder)
- Create: `android/app/src/main/kotlin/com/pitwatch/app/notify/ScheduleNotifier.kt` (post/cancel/enable, plus the receiver)
- Modify: `AppContainer.kt`, so that `updateWidgets` also runs `ScheduleNotifier.update`.
- Modify: `PitWatchApp.kt` (the channel), `BootReceiver.kt` (re-post) and `AndroidManifest.xml` (the receiver)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/notify/ScheduleNotificationTest.kt` and `ScheduleNotifierTest.kt`

**Interfaces:**
- Consumes: `ScheduleWidgetModels` and `ScheduleWidgetModel` (Task 2), `NotificationPrefs` (Task 1), and `TimeFormat`.
- Produces:
  - `object ScheduleNotification`:
    - `CHANNEL_ID = "schedule"` and `NOTIFICATION_ID = 1002`;
    - `data class Content(title: String, text: String?, lines: List<String>, summary: String?)`;
    - `fun content(model: ScheduleWidgetModel, locale: Locale = Locale.getDefault()): Content`;
    - `fun ensureChannel(context: Context)`;
    - `fun build(context: Context, content: Content): Notification`.
  - `object ScheduleNotifier`:
    - `suspend fun update(context: Context, container: AppContainer)`;
    - `suspend fun setEnabled(context: Context, container: AppContainer, enabled: Boolean)`;
    - `ACTION_TURN_OFF` and `ACTION_DISMISSED`.
  - `class ScheduleNotificationReceiver`.

- [ ] **Step 1: Write the failing tests**

`ScheduleNotificationTest.kt` (pure):

```kotlin
package com.pitwatch.app.notify

import com.pitwatch.app.SNAP
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.fixture
import com.pitwatch.app.snapshotCache
import com.pitwatch.app.widget.ScheduleWidgetModels
import com.pitwatch.core.PitWatchJson
import com.pitwatch.core.config.UserConfig
import com.pitwatch.core.model.EventRankings
import com.pitwatch.core.store.EventCache
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ScheduleNotificationTest {
    private val config = UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n")
    private val ready = ScheduleWidgetModels.build(
        snapshotCache().copy(rankings = PitWatchJson.decodeFromString<EventRankings>(fixture("$SNAP/tba_rankings.json"))),
        config, SNAP_NOW, Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `collapsed - next match, the one after, and the last result`() {
        val c = ScheduleNotification.content(ready, Locale.US)
        assertTrue(c.title.startsWith("Next: Q36 · "), c.title)
        assertTrue(c.text!!.startsWith("then Q"), c.text)
        assertTrue(c.text!!.endsWith("Last Q22 W 403–299"), c.text)
        assertEquals("Last Q22 W 403–299", c.summary)
    }

    @Test
    fun `expanded - at most six lines, in-motion phase, breaks, day prefixes`() {
        val c = ScheduleNotification.content(ready, Locale.US)
        assertTrue(c.lines.size <= 6)
        assertTrue(c.lines.first().startsWith("Q36 · ") && c.lines.first().endsWith(" · ON FIELD"), c.lines.first())
        assertTrue(c.lines.any { it.startsWith("End of day") }, c.lines.toString())
        assertTrue(c.lines.any { it.startsWith("Sat · Q") }, c.lines.toString()) // first row of a later day
    }

    @Test
    fun `not configured shows the setup message and nothing else`() {
        val c = ScheduleNotification.content(ScheduleWidgetModels.build(EventCache(), UserConfig(), SNAP_NOW), Locale.US)
        assertEquals("Set up PitWatch", c.title)
        assertEquals(emptyList(), c.lines)
    }
}
```

`ScheduleNotifierTest.kt` (Robolectric, with the `installTestContainer` pattern used in `LiveMatchServiceTest`):

```kotlin
package com.pitwatch.app.notify

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.pitwatch.app.AppContainer
import com.pitwatch.app.SNAP_NOW
import com.pitwatch.app.installTestContainer
import com.pitwatch.core.config.UserConfig
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
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
class ScheduleNotifierTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var container: AppContainer

    @Before
    fun setUp() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        container = installTestContainer(tmp.root)
        ScheduleNotification.ensureChannel(context)
        runBlocking {
            container.stores.config.updateData { UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n") }
            container.repository.refresh(SNAP_NOW)
        }
    }

    @After
    fun tearDown() = container.scope.cancel()

    private fun posted(): Notification? =
        shadowOf(context.getSystemService(NotificationManager::class.java)).getNotification(ScheduleNotification.NOTIFICATION_ID)

    @Test
    fun `off by default - nothing posted`() = runBlocking {
        ScheduleNotifier.update(context, container)
        assertNull(posted())
    }

    @Test
    fun `enabled - posts a silent ongoing schedule on its channel`() = runBlocking {
        ScheduleNotifier.setEnabled(context, container, true)
        val n = assertNotNull(posted())
        assertEquals(ScheduleNotification.CHANNEL_ID, n.channelId)
        assertEquals(true, n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertEquals(false, n.extras.getBoolean(Notification.EXTRA_REQUEST_PROMOTED_ONGOING))
    }

    @Test
    fun `turn off action disables and cancels`() = runBlocking {
        ScheduleNotifier.setEnabled(context, container, true)
        ScheduleNotificationReceiver().handle(context, container, ScheduleNotifier.ACTION_TURN_OFF)
        assertNull(posted())
        assertFalse(container.stores.notificationPrefs.data.first().scheduleEnabled)
    }

    @Test
    fun `swiped away, not pinned - stays off`() = runBlocking {
        ScheduleNotifier.setEnabled(context, container, true)
        ScheduleNotificationReceiver().handle(context, container, ScheduleNotifier.ACTION_DISMISSED)
        assertFalse(container.stores.notificationPrefs.data.first().scheduleEnabled)
        assertNull(posted())
    }

    @Test
    fun `swiped away, pinned - comes straight back`() = runBlocking {
        container.stores.notificationPrefs.updateData { it.copy(pinned = true) }
        ScheduleNotifier.setEnabled(context, container, true)
        context.getSystemService(NotificationManager::class.java).cancel(ScheduleNotification.NOTIFICATION_ID) // the swipe
        ScheduleNotificationReceiver().handle(context, container, ScheduleNotifier.ACTION_DISMISSED)
        assertNotNull(posted())
    }

    @Test
    fun `without notification permission nothing is posted`() = runBlocking {
        shadowOf(context as Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ScheduleNotifier.setEnabled(context, container, true)
        assertNull(posted())
    }
}
```

- [ ] **Step 2: Run to verify they fail.** Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*Schedule*Test')`. Expected: compile FAIL, because the `notify` package doesn't exist.

- [ ] **Step 3: Implement**

`ScheduleNotification.kt`:

```kotlin
package com.pitwatch.app.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pitwatch.app.MainActivity
import com.pitwatch.app.R
import com.pitwatch.app.ui.TimeFormat
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.widget.ScheduleWidgetModel
import com.pitwatch.app.widget.WidgetModel
import com.pitwatch.core.model.Phase
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The opt-in schedule notification: same content as the schedule widget, as text. */
object ScheduleNotification {
    const val CHANNEL_ID = "schedule"
    const val NOTIFICATION_ID = 1002
    private const val MAX_LINES = 6

    data class Content(val title: String, val text: String?, val lines: List<String>, val summary: String?)

    fun content(model: ScheduleWidgetModel, locale: Locale = Locale.getDefault()): Content {
        if (model.state != WidgetModel.State.READY) return Content(model.message ?: "PitWatch", null, emptyList(), null)
        val times = TimeFormat(model.timeZone, model.zoneLabel)
        val summary = model.last?.let { "Last ${it.shortLabel} ${it.outcome} ${it.ourScore}–${it.theirScore}" }
        val rows = model.days.flatMap { day -> day.items.filterIsInstance<MatchListModel.Item.Upcoming>().map { it.row } }
        val next = rows.first()
        val title = "Next: ${next.shortLabel} · ${times.match(next.time, next.estimated)}"
        val text = listOfNotNull(rows.getOrNull(1)?.let { "then ${it.shortLabel} ${times.match(it.time, it.estimated)}" }, summary)
            .joinToString(" · ").ifEmpty { null }
        val dayFormat = DateTimeFormatter.ofPattern("EEE", locale)
        val firstDate = model.days.firstOrNull()?.date
        val lines = model.days.flatMap { day ->
            val prefix = day.date?.takeIf { it != firstDate }?.format(dayFormat)?.let { "$it · " } ?: ""
            day.items.mapIndexed { i, item ->
                val head = if (i == 0) prefix else ""
                when (item) {
                    is MatchListModel.Item.Upcoming -> head + item.row.shortLabel + " · " + times.match(item.row.time, item.row.estimated) +
                        (item.row.phase?.takeIf { it != Phase.PRE_QUEUE }?.let { " · ${it.stateLabel}" } ?: "")
                    is MatchListModel.Item.Break -> head + item.title + (item.end?.let { " " + times.range(item.start, it) } ?: "")
                }
            }
        }.take(MAX_LINES)
        return Content(title, text, lines, summary)
    }

    fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Match schedule", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Your upcoming matches and the last result"
                setShowBadge(false)
            },
        )
    }

    fun build(context: Context, content: Content): Notification {
        fun broadcast(code: Int, action: String) = PendingIntent.getBroadcast(
            context, code, Intent(context, ScheduleNotificationReceiver::class.java).setAction(action), PendingIntent.FLAG_IMMUTABLE,
        )
        val style = NotificationCompat.InboxStyle().also { s -> content.lines.forEach(s::addLine); content.summary?.let(s::setSummaryText) }
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_pitwatch)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(style)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setSilent(true)
            .setContentIntent(PendingIntent.getActivity(context, 10, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
            .setDeleteIntent(broadcast(11, ScheduleNotifier.ACTION_DISMISSED))
            .addAction(0, "Turn off", broadcast(12, ScheduleNotifier.ACTION_TURN_OFF))
            .build()
    }
}
```

`ScheduleNotifier.kt`:

```kotlin
package com.pitwatch.app.notify

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pitwatch.app.AppContainer
import com.pitwatch.app.PitWatchApp
import com.pitwatch.app.widget.ScheduleWidgetModels
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object ScheduleNotifier {
    const val ACTION_TURN_OFF = "com.pitwatch.app.schedule.TURN_OFF"
    const val ACTION_DISMISSED = "com.pitwatch.app.schedule.DISMISSED"

    /** Posts the current schedule when enabled (and permitted); cancels it otherwise. Runs with every widget refresh. */
    suspend fun update(context: Context, container: AppContainer) {
        val manager = NotificationManagerCompat.from(context)
        if (!container.stores.notificationPrefs.data.first().scheduleEnabled) {
            manager.cancel(ScheduleNotification.NOTIFICATION_ID)
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val model = ScheduleWidgetModels.build(container.stores.cache.data.first(), container.stores.config.data.first(), container.clock())
        manager.notify(ScheduleNotification.NOTIFICATION_ID, ScheduleNotification.build(context, ScheduleNotification.content(model)))
    }

    suspend fun setEnabled(context: Context, container: AppContainer, enabled: Boolean) {
        container.stores.notificationPrefs.updateData { it.copy(scheduleEnabled = enabled) }
        update(context, container)
    }
}

/** "Turn off", and swipes: pinned re-posts; otherwise a swipe turns the schedule notification off. */
class ScheduleNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending: PendingResult? = goAsync()
        val container = (context.applicationContext as PitWatchApp).container
        container.scope.launch {
            try {
                handle(context, container, intent.action)
            } finally {
                pending?.finish()
            }
        }
    }

    suspend fun handle(context: Context, container: AppContainer, action: String?) {
        when (action) {
            ScheduleNotifier.ACTION_TURN_OFF -> ScheduleNotifier.setEnabled(context, container, false)
            ScheduleNotifier.ACTION_DISMISSED -> {
                val prefs = container.stores.notificationPrefs.data.first()
                if (prefs.pinned && prefs.scheduleEnabled) ScheduleNotifier.update(context, container)
                else ScheduleNotifier.setEnabled(context, container, false)
            }
        }
    }
}
```

**`AppContainer.create`:** `val updateWidgets: suspend () -> Unit = { WidgetRefresh.run(appContext, container); ScheduleNotifier.update(appContext, container) }`. Update the KDoc to "Re-renders home-screen widgets and the schedule notification from the persisted cache."

**`PitWatchApp.onCreate`:** add `ScheduleNotification.ensureChannel(this)`.

**`BootReceiver`:** inside the `try`, add `ScheduleNotifier.update(context, container)`.

**Manifest:** add `<receiver android:name=".notify.ScheduleNotificationReceiver" android:exported="false" />`.

- [ ] **Step 4: Run to verify, then the full suite.** Run: `make test`. Expected: all pass.

> If the `Sat · Q` assertion doesn't match the fixture's later day label, check `model.days[1].date` in the test and assert that day's actual `EEE` abbreviation. Record that as a Ruling.

- [ ] **Step 5: Commit:** `git add android/app/src && git commit -m "feat(app): opt-in schedule notification, kept fresh with the widgets"`

---

### Task 6: Settings Notifications group and the Matches shortcut

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/SettingsScreen.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchesScreen.kt`. `MatchesContent` gains `showScheduleOffer: Boolean = false, onShowSchedule: () -> Unit = {}`.
- Test: `SettingsScreenTest.kt` and `MatchesContentTest.kt`

**Interfaces:**
- Consumes: `ScheduleNotifier.setEnabled`, `ScheduleNotifier.update`, and `Stores.notificationPrefs`.

- [ ] **Step 1: Write the failing tests**

Append to `SettingsScreenTest`:

```kotlin
    @Test
    fun `notifications group turns the schedule notification and pinning on`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        compose.setContent { SettingsScreen(container, config) }
        compose.onNode(hasText("Schedule in notifications")).performScrollTo().performClick()
        compose.onNode(hasText("Keep notifications pinned")).performScrollTo().performClick()
        compose.waitUntil(5_000) { runBlocking { container.stores.notificationPrefs.data.first().let { it.scheduleEnabled && it.pinned } } }
    }

    @Test
    fun `without notification permission the schedule switch explains instead`() {
        shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).denyPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { container.stores.notificationPrefs.updateData { it.copy(scheduleEnabled = true) } }
        compose.setContent { SettingsScreen(container, config) }
        compose.onNode(hasText("Notifications are off for PitWatch", substring = true)).performScrollTo().assertIsDisplayed()
    }
```

Append to `MatchesContentTest`:

```kotlin
    @Test
    fun `the list offers the schedule notification when it's off`() {
        var asked = 0
        compose.setContent { MatchesContent(model, SNAP_NOW, false, false, {}, {}, {}, {}, showScheduleOffer = true, onShowSchedule = { asked++ }) }
        compose.onNodeWithTag("matches").performScrollToNode(hasText("Show schedule in notifications"))
        compose.onNodeWithText("Show schedule in notifications").performClick()
        assertEquals(1, asked)
    }
```

(Imports needed: `performClick`, `ApplicationProvider`, `shadowOf`, `runBlocking`, `first`.)

- [ ] **Step 2: Run to verify they fail.** Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*SettingsScreenTest' --tests '*MatchesContentTest')`. Expected: FAIL, because the nodes don't exist and `MatchesContent` has no `showScheduleOffer`.

- [ ] **Step 3: Implement**

**`SettingsScreen`:**
- Collect `val prefs by container.stores.notificationPrefs.data.collectAsStateWithLifecycle(initialValue = NotificationPrefs())`.
- Collect a `canNotify` state, refreshed in the existing `LifecycleResumeEffect`: `ContextCompat.checkSelfPermission(context, POST_NOTIFICATIONS) == GRANTED`.
- After the "Live tracking" section, add:

```kotlin
            Section("Notifications") {
                ListItem(
                    headlineContent = { Text("Schedule in notifications") },
                    supportingContent = { Text("Your upcoming matches and the last result, kept up to date") },
                    trailingContent = {
                        Switch(prefs.scheduleEnabled, { on -> container.scope.launch { ScheduleNotifier.setEnabled(context.applicationContext, container, on) } })
                    },
                    colors = clearRow(),
                )
                if (prefs.scheduleEnabled && !canNotify) {
                    ListItem(
                        headlineContent = { Text("Notifications are off for PitWatch") },
                        supportingContent = { Text("Allow notifications to see the schedule.") },
                        trailingContent = {
                            OutlinedButton(onClick = {
                                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                            }) { Text("Settings") }
                        },
                        colors = clearRow(),
                    )
                }
                ListItem(
                    headlineContent = { Text("Keep notifications pinned") },
                    supportingContent = { Text("Swiping them away brings them back; use Stop or Turn off instead") },
                    trailingContent = {
                        Switch(prefs.pinned, { on -> container.scope.launch { container.stores.notificationPrefs.updateData { it.copy(pinned = on) } } })
                    },
                    colors = clearRow(),
                )
            }
```

**`MatchesContent`/`MatchesBody`:** thread through the new parameters. After the results items, add:

```kotlin
                if (showScheduleOffer && model.empty == null) {
                    item(key = "schedule-offer") {
                        TextButton(onClick = onShowSchedule, modifier = Modifier.fillMaxWidth()) { Text("Show schedule in notifications") }
                    }
                }
```

**`MatchesScreen`:** collect `notificationPrefs`, then pass `showScheduleOffer = !prefs.scheduleEnabled` and `onShowSchedule = { container.scope.launch { ScheduleNotifier.setEnabled(context.applicationContext, container, true) } }`.

- [ ] **Step 4: Run to verify, then the full suite.** Run: `make test`. Expected: all pass.

- [ ] **Step 5: Commit:** `git add android/app/src && git commit -m "feat(app): Settings notifications group; schedule shortcut under the match list"`

---

### Task 7: Pinned live-tracking notification

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveNotification.kt`. `Actions` gains `dismissed: PendingIntent?`, and the delete intent uses it.
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveMatchService.kt`. Adds the `ACTION_DISMISSED` handling.
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/LiveMatchServiceTest.kt`

**Interfaces:**
- Consumes: `NotificationPrefs.pinned`.
- Produces: `LiveMatchService.ACTION_DISMISSED` and `LiveNotification.Actions(content, refresh, stop, dismissed)`.

- [ ] **Step 1: Write the failing tests** (append to `LiveMatchServiceTest`):

```kotlin
    @Test
    fun `swiped away, not pinned - stops tracking as before`() {
        runBlocking { container.repository.refresh(com.pitwatch.app.SNAP_NOW) }
        val service = startService()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_DISMISSED), 0, 2)
        awaitMain { shadowOf(service).isStoppedBySelf }
    }

    @Test
    fun `swiped away, pinned - the notification comes back and tracking continues`() {
        shadowOf(context as Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { container.stores.notificationPrefs.updateData { it.copy(pinned = true) } }
        val service = startService()
        awaitMain { posted() != null }
        context.getSystemService(NotificationManager::class.java).cancel(LiveNotification.NOTIFICATION_ID) // the swipe
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_DISMISSED), 0, 2)
        awaitMain { posted() != null }
        assertTrue(LiveMatchService.tracking.value)
    }

    @Test
    fun `a stale dismissal never starts tracking`() {
        val controller = Robolectric.buildService(LiveMatchService::class.java).create()
        val service = controller.get()
        service.onStartCommand(LiveMatchService.intent(context, LiveMatchService.ACTION_DISMISSED), 0, 1)
        assertTrue(shadowOf(service).isStoppedBySelf)
        assertNull(shadowOf(service).lastForegroundNotification)
    }
```

- [ ] **Step 2: Run to verify they fail.** Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*LiveMatchServiceTest')`. Expected: compile FAIL, because `ACTION_DISMISSED` doesn't exist.

- [ ] **Step 3: Implement**

**`LiveNotification`:** change to `data class Actions(val content: PendingIntent?, val refresh: PendingIntent?, val stop: PendingIntent?, val dismissed: PendingIntent? = stop)`, and use `.setDeleteIntent(actions.dismissed) // the service decides: stop, or re-post when pinned`.

**`LiveMatchService`:**
- Add `const val ACTION_DISMISSED = "com.pitwatch.app.live.DISMISSED"`.
- In `actions()`, add `dismissed = PendingIntent.getService(this, 3, intent(this, ACTION_DISMISSED), PendingIntent.FLAG_IMMUTABLE)`.
- In `onStartCommand`'s `when`, before the foreground call:

```kotlin
            ACTION_DISMISSED -> {
                if (loop == null) { // not tracking (a stale intent): never resurrect anything
                    stopSelf(startId)
                    return START_NOT_STICKY
                }
                scope.launch {
                    if (container.stores.notificationPrefs.data.first().pinned) {
                        // Pinned: swiping only hides it for a moment; Stop is the way out.
                        val notification = lastNotification ?: LiveNotification.build(this@LiveMatchService, null, lastSuccess, container.clock(), actions())
                        NotificationManagerCompat.from(this@LiveMatchService).notify(LiveNotification.NOTIFICATION_ID, notification)
                    } else {
                        stopTracking(suppress = true)
                    }
                }
                return START_STICKY
            }
```

`NotificationManagerCompat.notify` needs the permission. Wrap it in the same permission check the service already uses before `notify`; find the existing call around line 217 and reuse its guard.

- [ ] **Step 4: Run to verify, then the full suite.** Run: `make test`. Expected: all pass, including `LiveNotificationTest`, whose `Actions(…)` calls still compile thanks to the default parameter.

- [ ] **Step 5: Commit:** `git add android/app/src && git commit -m "feat(live): pinned live-tracking notification re-posts when swiped"`

---

### Task 8: On-device check (emulator-5580 only)

- [ ] **Step 1:** Run `make fake-install DEVICE=emulator-5580`. Expected: Success, and the app launches.
- [ ] **Step 2: Widget picker previews.** Long-press the home screen → Widgets → PitWatch, and screenshot. Expected: two entries ("PitWatch" and "PitWatch schedule"), each showing a generated preview in the scoreboard style. Fall back to the XML layout only if the launcher doesn't support generated previews; record the outcome.
- [ ] **Step 3: Schedule widget.** Add "PitWatch schedule" and resize through 2×2, 2×3, 4×2 and 4×3, using the resize-handle drags from the polish pass. Screenshot each. Expected: no clipped or overlapping text.
- [ ] **Step 4: Notification.** In Settings, turn on "Schedule in notifications", then expand the shade (`adb -s emulator-5580 shell cmd statusbar expand-notifications`). Screenshot collapsed and expanded, in light and dark.
- [ ] **Step 5: Pinned.**
  - Pinned off: swipe the schedule notification (`input swipe` across it) and expect it gone and the Settings switch off.
  - Pinned on: swipe both the schedule and the live notification, and expect both back within a second.
  - Restore the defaults afterwards: schedule off, pinned off, light mode, and the user's 4×3 main widget.
- [ ] **Step 6:** Fix anything the screenshots show, with a test where possible, then run `make test` and commit.
