# Scoreboard Restyle Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle every PitWatch Android surface in the "B · Scoreboard" direction (bold condensed type, huge countdown, phase timeline, alliance bands) on top of Material You colors.

**Architecture:** The theme layer (`ui/theme/`) gains bundled Barlow fonts, a typography, a dark-mode surface blend, and extended `StatusColors`. A new `ui/scoreboard/` package holds the shared Compose pieces (cards, pills, alliance lines and bands, phase timeline, header, hero countdown). Screens are rebuilt from those pieces with their data flow unchanged. The Glance widget and the Live Update notification get matching versions within their platform limits. One model addition: `MatchListModel.Result` carries both alliances and scores.

**Tech Stack:** Kotlin 2.4, Jetpack Compose (BOM 2026.09.00, Material 3), Glance 1.2.0, NotificationCompat ProgressStyle, Robolectric 4.17, JUnit 4.

**Spec:** `docs/superpowers/specs/2026-10-06-scoreboard-restyle-design.md`. Mockups: https://claude.ai/artifact/RZw4CQJ43Cx2tqoz4fqT3w

## Global Constraints

- **Branch:** `scoreboard-restyle`, branched off `main`. Make modular commits. When done, merge back to `main` locally. Never push.
- **Test command:** `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`. It must be green at the end of every task.
- **Devices:** use only `emulator-5580` (AVD `preset-alarm-root-api36`, visible window). NEVER install on or drive `RFGL82WFV7K` (the user's phone) or `emulator-5554`. Always pass `-s emulator-5580` to adb.
- **Running services:** keep `scripts/fake-api.py` (port 8765) and emulator-5580 running. Do not stop them.
- **Debug build against the fake API:** `./gradlew :app:assembleDebug -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3 -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1`.
- **Colors:** wallpaper-derived. Use `dynamicLight/DarkColorScheme` and never hard-code the accent; it is `MaterialTheme.colorScheme.primary`. The only fixed colors live in `StatusColors`.
- **Fonts:** Barlow Condensed 600/700/800 and Barlow 400/500/600, bundled in `res/font`, OFL licensed. Glance text uses `FontFamily("sans-serif-condensed")`. Only the widget's RemoteViews countdown uses bundled Barlow.
- **Behavior:** promoted notifications must stay standard-style. No custom content views, no `setColorized`.
- **Data:** no polling, scheduling or data-flow changes.
- **Copy:** display labels are uppercased in the string itself (not only through a style), so tests match what users see.

## Review Focus

1. **TBA-only events.** These have no Nexus phase. The hero timeline reads "Not queued yet" with no highlighted step, rows have no pill, and the widget has no phase bar. *(Task 4 test.)*
2. **Next match alone in its day.** The hero takes it out of the list, so that day's header must not be left empty. *(Task 4 test.)*
3. **Countdown edge cases.** Past the deadline it shows "0:00" and never goes negative. Beyond an hour it shows "1:05:09". *(Task 3 test.)*
4. **Long titles.** A long event name in the header truncates, and the "Choose event" button stays visible. *(Task 3 test.)*
5. **Dark blend contrast.** The darker dark-mode surface must keep `onSurface` readable: contrast ≥ 4.5. *(Task 1 test.)*

---

### Task 1: Fonts, typography, theme and status colors

**Files:**
- Create: `android/app/src/main/res/font/barlow_condensed_semibold.ttf`, `barlow_condensed_bold.ttf`, `barlow_condensed_extrabold.ttf`, `barlow_regular.ttf`, `barlow_medium.ttf`, `barlow_semibold.ttf`
- Create: `android/third_party/barlow/OFL.txt`
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/theme/Type.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/theme/Theme.kt`
- Modify: `android/app/src/main/res/layout/widget_countdown.xml`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/theme/StatusColorsTest.kt`, `android/app/src/test/kotlin/com/pitwatch/app/ui/theme/ThemeTest.kt`

**Interfaces:**
- Produces:
  - `BarlowCondensed: FontFamily` and `Barlow: FontFamily`.
  - `PitWatchTypography: Typography`.
  - `PitWatchType.countdownHero: TextStyle` and `PitWatchType.sectionLabel: TextStyle`.
  - `fun scoreboardDark(scheme: ColorScheme): ColorScheme`.
  - `StatusColors.alliance(MatchAlliance)`, now `#c62828` / `#1e5bd6`.
  - `data class StatusColors.Pill(container: Color, content: Color)` and `StatusColors.outcome(code: String): Pill?` (null for a tie).
  - `StatusColors.notificationSegments: List<Color>`.

- [ ] **Step 1: Download the fonts and license**

```bash
cd android/app/src/main/res && mkdir -p font && cd font
base=https://raw.githubusercontent.com/google/fonts/main/ofl
curl -sfL -o barlow_condensed_semibold.ttf  $base/barlowcondensed/BarlowCondensed-SemiBold.ttf
curl -sfL -o barlow_condensed_bold.ttf      $base/barlowcondensed/BarlowCondensed-Bold.ttf
curl -sfL -o barlow_condensed_extrabold.ttf $base/barlowcondensed/BarlowCondensed-ExtraBold.ttf
curl -sfL -o barlow_regular.ttf             $base/barlow/Barlow-Regular.ttf
curl -sfL -o barlow_medium.ttf              $base/barlow/Barlow-Medium.ttf
curl -sfL -o barlow_semibold.ttf            $base/barlow/Barlow-SemiBold.ttf
mkdir -p ../../../../../third_party/barlow && curl -sfL -o ../../../../../third_party/barlow/OFL.txt $base/barlow/OFL.txt
file *.ttf
```
Expected: six lines reading `TrueType Font data`, and `android/third_party/barlow/OFL.txt` exists.

- [ ] **Step 2: Write the failing tests**

Append to `StatusColorsTest`:

```kotlin
    @Test
    fun `alliance bands are the deep scoreboard colors, readable with white text`() {
        assertEquals(0xFFC62828.toInt(), StatusColors.alliance(MatchAlliance.RED).toArgb())
        assertEquals(0xFF1E5BD6.toInt(), StatusColors.alliance(MatchAlliance.BLUE).toArgb())
        for (a in MatchAlliance.entries) assertTrue(contrast(StatusColors.alliance(a), Color.White) >= 4.5, "$a")
    }

    @Test
    fun `outcome pills are readable and a tie has none`() {
        for (code in listOf("W", "L")) {
            val pill = assertNotNull(StatusColors.outcome(code))
            assertTrue(contrast(pill.container, pill.content) >= 4.5, code)
        }
        assertNull(StatusColors.outcome("T"))
    }

    @Test
    fun `notification segments are queue, on deck, on field, then the match`() {
        assertEquals(
            listOf(Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD).map { StatusColors.phase(it) } + Color(0xFF0A84FF),
            StatusColors.notificationSegments,
        )
    }
```
(Add the imports `androidx.compose.ui.graphics.Color`, `com.pitwatch.core.model.MatchAlliance`, `kotlin.test.assertNotNull` and `kotlin.test.assertNull`.)

Create `ThemeTest.kt`:

```kotlin
package com.pitwatch.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `dark mode pulls ground and surface toward scoreboard black`() {
        val base = darkColorScheme()
        val out = scoreboardDark(base)
        val expected = lerp(base.surface, Color(0xFF0F1216), 0.6f)
        assertEquals(expected, out.surface)
        assertEquals(expected, out.background)
        assertEquals(base.primary, out.primary) // the accent stays the wallpaper's
    }

    @Test
    fun `text stays readable on the darker surface`() {
        val out = scoreboardDark(darkColorScheme())
        val (hi, lo) = listOf(out.onSurface.luminance(), out.surface.luminance()).sortedDescending()
        assertTrue((hi + 0.05) / (lo + 0.05) >= 4.5)
    }

    @Test
    fun `display and titles are condensed, body is Barlow`() {
        assertEquals(BarlowCondensed, PitWatchTypography.titleLarge.fontFamily)
        assertEquals(BarlowCondensed, PitWatchTypography.labelLarge.fontFamily)
        assertEquals(Barlow, PitWatchTypography.bodyLarge.fontFamily)
        assertEquals(BarlowCondensed, PitWatchType.countdownHero.fontFamily)
    }
}
```

- [ ] **Step 3: Run them to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*StatusColorsTest' --tests '*ThemeTest')`
Expected: compile FAIL with unresolved `scoreboardDark`, `PitWatchTypography`, `StatusColors.outcome` and `notificationSegments`.

- [ ] **Step 4: Implement**

`Type.kt`:

```kotlin
package com.pitwatch.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pitwatch.app.R

/** Bundled (OFL, see android/third_party/barlow): scoreboard display type. */
val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
    Font(R.font.barlow_condensed_bold, FontWeight.Bold),
    Font(R.font.barlow_condensed_extrabold, FontWeight.ExtraBold),
)

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
    Font(R.font.barlow_semibold, FontWeight.SemiBold),
)

/** Condensed Barlow for display, headline, title and label styles; Barlow for body. */
val PitWatchTypography: Typography = Typography().let { base ->
    fun TextStyle.condensed(weight: FontWeight) = copy(fontFamily = BarlowCondensed, fontWeight = weight)
    fun TextStyle.body() = copy(fontFamily = Barlow)
    base.copy(
        displayLarge = base.displayLarge.condensed(FontWeight.ExtraBold),
        displayMedium = base.displayMedium.condensed(FontWeight.ExtraBold),
        displaySmall = base.displaySmall.condensed(FontWeight.ExtraBold),
        headlineLarge = base.headlineLarge.condensed(FontWeight.Bold),
        headlineMedium = base.headlineMedium.condensed(FontWeight.Bold),
        headlineSmall = base.headlineSmall.condensed(FontWeight.Bold),
        titleLarge = base.titleLarge.condensed(FontWeight.Bold),
        titleMedium = base.titleMedium.condensed(FontWeight.Bold),
        titleSmall = base.titleSmall.condensed(FontWeight.Bold),
        bodyLarge = base.bodyLarge.body(),
        bodyMedium = base.bodyMedium.body(),
        bodySmall = base.bodySmall.body(),
        labelLarge = base.labelLarge.condensed(FontWeight.Bold),
        labelMedium = base.labelMedium.condensed(FontWeight.Bold),
        labelSmall = base.labelSmall.condensed(FontWeight.Bold),
    )
}

object PitWatchType {
    /** The hero's big countdown; tabular figures so digits don't jitter. */
    val countdownHero = TextStyle(
        fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 128.sp,
        lineHeight = 0.95.em, letterSpacing = (-2).sp, fontFeatureSettings = "tnum",
    )

    /** "SATURDAY", "LAST": letter-spaced condensed caps. */
    val sectionLabel = TextStyle(fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold, fontSize = 16.sp, letterSpacing = 2.sp)
}
```

`Theme.kt`, replacing the file:

```kotlin
package com.pitwatch.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase

private val SCOREBOARD_BLACK = Color(0xFF0F1216)

/** Material You (wallpaper) colors in the Scoreboard type; light/dark follow the system. */
@Composable
fun PitWatchTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme = if (isSystemInDarkTheme()) scoreboardDark(dynamicDarkColorScheme(context)) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, typography = PitWatchTypography, content = content)
}

/** Dark mode reads as a scoreboard: ground and surface pulled 60 % toward near-black; the accent is untouched. */
fun scoreboardDark(scheme: ColorScheme): ColorScheme {
    val ground = lerp(scheme.surface, SCOREBOARD_BLACK, 0.6f)
    return scheme.copy(background = ground, surface = ground)
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

    /** Band colors: deep enough for white text in light and dark mode. */
    fun alliance(alliance: MatchAlliance): Color = when (alliance) {
        MatchAlliance.RED -> Color(0xFFC62828)
        MatchAlliance.BLUE -> Color(0xFF1E5BD6)
    }

    data class Pill(val container: Color, val content: Color)

    /** WIN / LOSS pill colors for an outcome code ("W", "L", "T"); null for a tie, which uses the theme's neutral surface. */
    fun outcome(code: String): Pill? = when (code) {
        "W" -> Pill(Color(0xFF30D158), Color(0xFF0B1A10))
        "L" -> Pill(Color(0xFFD32F2F), Color.White)
        else -> null
    }

    /** Live Update progress segments: queue, on deck, on field, then the match itself. */
    val notificationSegments: List<Color> =
        listOf(Phase.QUEUEING, Phase.ON_DECK, Phase.ON_FIELD).map(::phase) + Color(0xFF0A84FF)
}
```

> **Ruling (plan vs spec):** the LOSS pill is `#d32f2f`, not the spec's `#e5484d`. With white text, `#e5484d` has a contrast of only 3.9, below 4.5 for 16 sp text. If this is wrong, it's one hex value.

`widget_countdown.xml`: add `android:fontFamily="@font/barlow_condensed_bold"` and change `android:textSize` to `56sp`. Task 7 sets the size per widget height.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*StatusColorsTest' --tests '*ThemeTest')`
Expected: PASS.

- [ ] **Step 6: Check early that the launcher accepts the bundled widget font (spec §5)**

```bash
cd android && ./gradlew -q :app:assembleDebug -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3 -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1 \
  && adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk \
  && adb -s emulator-5580 shell input keyevent KEYCODE_HOME && sleep 3 \
  && adb -s emulator-5580 exec-out screencap -p > "$SCRATCH/font-check.png"
```
(`$SCRATCH` is the session scratchpad.) Look at the screenshot.
Expected: the widget countdown is drawn in Barlow Condensed (narrow, flat-sided digits) and not Roboto. If the launcher shows Roboto, change the XML to `android:fontFamily="sans-serif-condensed"` and record a Ruling in the ledger. Either way, keep going.

- [ ] **Step 7: Full suite, then commit**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`. Expected: all pass. Existing screen tests don't check fonts or alliance hex values.

```bash
git add android/app/src/main/res/font android/third_party/barlow android/app/src/main/kotlin/com/pitwatch/app/ui/theme android/app/src/main/res/layout/widget_countdown.xml android/app/src/test/kotlin/com/pitwatch/app/ui/theme
git commit -m "feat(app): Scoreboard theme — bundled Barlow, typography, dark blend, status colors"
```

---

### Task 2: Result rows carry both alliances

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchListModel.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchListModelTest.kt`

**Interfaces:**
- Produces:
  - `MatchListModel.Result(key, label, shortLabel, ourScore, theirScore, outcome, red: AllianceLine, blue: AllianceLine, redScore: Int, blueScore: Int)`.
  - `Result.outcomeLabel: String`, one of `"WIN"`, `"LOSS"` or `"TIE"`.
  - `MatchListModel.next: MatchRow?`, the row with `isNext`.

- [ ] **Step 1: Write the failing tests** (append to `MatchListModelTest`; these use the same imports as the file):

```kotlin
    private fun buildFrom(cache: com.pitwatch.core.store.EventCache) = MatchListModels.build(
        cache, UserConfig(teamNumber = 5507, apiKey = "k", nexusApiKey = "n"), RefreshState(), SNAP_NOW, java.util.Locale.US, com.pitwatch.app.LA,
    )

    @Test
    fun `results carry both alliances and their scores`() {
        val r = buildFrom(snapshotCache()).results[0] // qm22: we were blue, 403–299
        assertEquals(listOf("9400", "6418", "5104"), r.red.teams.map { it.number })
        assertEquals(listOf("5507", "2813", "8033"), r.blue.teams.map { it.number })
        assertEquals(listOf(true, false, false), r.blue.teams.map { it.isUs })
        assertEquals(299 to 403, r.redScore to r.blueScore)
        assertEquals(403 to 299, r.ourScore to r.theirScore)
        assertNull(r.red.summedOpr)
        assertEquals("WIN", r.outcomeLabel)
    }

    @Test
    fun `outcome labels`() {
        val empty = MatchListModel.AllianceLine(emptyList(), null)
        fun result(code: String) = MatchListModel.Result("k", "Qual 9", "Q9", 1, 1, code, empty, empty, 1, 1)
        assertEquals(listOf("WIN", "LOSS", "TIE"), listOf("W", "L", "T").map { result(it).outcomeLabel })
    }

    @Test
    fun `a result with a short alliance still builds`() {
        val cache = snapshotCache().let { c ->
            c.copy(matches = c.matches.map { m ->
                if (m.key != "2026cancmp_qm22") m
                else m.copy(alliances = m.alliances.mapValues { (color, a) -> if (color == "red") a.copy(teamKeys = a.teamKeys.take(2)) else a })
            })
        }
        assertEquals(listOf("9400", "6418"), buildFrom(cache).results[0].red.teams.map { it.number })
    }

    @Test
    fun `next is the hero match`() {
        val next = assertNotNull(buildFrom(snapshotCache()).next)
        assertEquals("Qual 36", next.label)
        assertTrue(next.isNext)
    }
```

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*MatchListModelTest')`
Expected: compile FAIL, because `Result` has no `red`, `outcomeLabel` or `next`.

- [ ] **Step 3: Implement**

In `MatchListModel`, replace the `Result` class and add `next`:

```kotlin
    /** The match the hero card shows; the list leaves it out. */
    val next: MatchRow? get() = days.asSequence().flatMap { it.items }.filterIsInstance<Item.Upcoming>().firstOrNull { it.row.isNext }?.row

    data class Result(
        val key: String,
        val label: String,
        val shortLabel: String,
        val ourScore: Int,
        val theirScore: Int,
        /** "W", "L" or "T", from our side. */
        val outcome: String,
        val red: AllianceLine,
        val blue: AllianceLine,
        val redScore: Int,
        val blueScore: Int,
    ) {
        val url: String get() = "https://www.thebluealliance.com/match/$key"
        val outcomeLabel: String get() = when (outcome) {
            "W" -> "WIN"
            "L" -> "LOSS"
            else -> "TIE"
        }
    }
```

In `MatchListModels.build`, replace the `results` block:

```kotlin
        val results = schedule.pastMatches.mapNotNull { match ->
            val ours = match.allianceColor(teamKey) ?: return@mapNotNull null
            val redScore = match.alliances["red"]?.score ?: return@mapNotNull null
            val blueScore = match.alliances["blue"]?.score ?: return@mapNotNull null
            val (our, their) = if (ours == "red") redScore to blueScore else blueScore to redScore
            val outcome = when {
                our > their -> "W"
                our < their -> "L"
                else -> "T"
            }
            MatchListModel.Result(
                match.key, match.label, match.shortLabel, our, their, outcome,
                line(match, "red").copy(summedOpr = null), line(match, "blue").copy(summedOpr = null), redScore, blueScore,
            )
        }
```

- [ ] **Step 4: Run to verify they pass, then the full suite**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`
Expected: all pass. Other `Result(` constructor calls fail to compile if any exist; `grep -rn "MatchListModel.Result(" android/app/src` finds them. Fix each by passing `MatchListModel.AllianceLine(emptyList(), null)` lines and the scores.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchListModel.kt android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchListModelTest.kt
git commit -m "feat(app): results carry both alliances and scores; model exposes the hero match"
```

---

### Task 3: Shared Scoreboard components

**Files:**
- Create: `android/app/src/main/kotlin/com/pitwatch/app/ui/scoreboard/Scoreboard.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/scoreboard/ScoreboardTest.kt`

**Interfaces:**
- Consumes: Task 1's `BarlowCondensed`, `PitWatchType`, `StatusColors` and `PitWatchTheme`; Task 2's `Result.outcomeLabel`.
- Produces (all in `com.pitwatch.app.ui.scoreboard`):
  - `ScoreboardCard(modifier, radius: Dp = 24.dp, onClick: (() -> Unit)? = null, content: ColumnScope.() -> Unit)`
  - `SectionLabel(text: String, modifier)`
  - `StatusPill(text, container: Color, content: Color, fontSize: TextUnit = 16.sp)`
  - `PhasePill(phase: Phase, fontSize: TextUnit = 16.sp)`
  - `OutcomePill(result: MatchListModel.Result, fontSize: TextUnit = 16.sp)`
  - `TeamChip(number, container, content, style)`
  - `AllianceLine(alliance: MatchAlliance, line: MatchListModel.AllianceLine, modifier, trailing: @Composable () -> Unit = …)`
  - `AllianceBands(red, blue, modifier)`
  - `object PhaseSteps { val steps: List<Pair<String, Phase?>>; fun current(phase: Phase?): Int?; fun description(phase: Phase?): String }`
  - `PhaseTimeline(phase: Phase?, modifier)`
  - `object HeroCountdown { fun text(deadline: Instant, now: Instant): String }`
  - `HeroCountdownText(deadline: Instant, now: Instant, modifier)`
  - `ScreenHeader(title: String, subtitle: String?, modifier, navigation: (@Composable () -> Unit)? = null, action: (@Composable () -> Unit)? = null)`
  - `HeaderIconButton(icon: ImageVector, description: String, onClick)`
  - `AccentButton(text: String, onClick, modifier, outlined: Boolean = false, enabled: Boolean = true)`
  - `fun condensed(size: TextUnit, weight: FontWeight = FontWeight.Bold, letterSpacing: TextUnit = 0.sp): TextStyle`

- [ ] **Step 1: Write the failing tests**

```kotlin
package com.pitwatch.app.ui.scoreboard

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.PitWatchTheme
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ScoreboardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun line(vararg teams: String, opr: Double? = null) =
        MatchListModel.AllianceLine(teams.map { MatchListModel.TeamChip(it, it == "5507") }, opr)

    @Test
    fun `phase steps`() {
        assertEquals(listOf(null, 1, 2, 3), Phase.entries.map { PhaseSteps.current(it) })
        assertNull(PhaseSteps.current(null))
        assertEquals("On field, step 3 of 4", PhaseSteps.description(Phase.ON_FIELD))
        assertEquals("In queue, step 1 of 4", PhaseSteps.description(Phase.QUEUEING))
        assertEquals("Not queued yet", PhaseSteps.description(Phase.PRE_QUEUE))
    }

    @Test
    fun `timeline announces the current step`() {
        compose.setContent { PitWatchTheme { PhaseTimeline(Phase.ON_FIELD) } }
        compose.onNode(hasContentDescription("On field, step 3 of 4")).assertIsDisplayed()
    }

    @Test
    fun `timeline before queueing has no current step`() {
        compose.setContent { PitWatchTheme { PhaseTimeline(null) } }
        compose.onNode(hasContentDescription("Not queued yet")).assertIsDisplayed()
    }

    @Test
    fun `alliance line marks our team and hides unknown OPR`() {
        compose.setContent { PitWatchTheme { AllianceLine(MatchAlliance.RED, line("4698", "5507", "1678")) } }
        compose.onNode(hasContentDescription("Your team, 5507")).assertIsDisplayed()
        compose.onNodeWithText("4698").assertIsDisplayed()
        compose.onAllNodes(hasText("Σ", substring = true)).assertCountEquals(0)
    }

    @Test
    fun `alliance bands show OPR when known`() {
        compose.setContent { PitWatchTheme { AllianceBands(line("4698", "5507", "1678", opr = 351.4), line("6036", "9470", "6814", opr = 539.0)) } }
        compose.onNode(hasContentDescription("Your team, 5507")).assertIsDisplayed()
        compose.onNodeWithText("Σ 351").assertIsDisplayed()
        compose.onNodeWithText("Σ 539").assertIsDisplayed()
    }

    @Test
    fun `outcome pill says win, loss or tie`() {
        val empty = MatchListModel.AllianceLine(emptyList(), null)
        compose.setContent {
            PitWatchTheme {
                androidx.compose.foundation.layout.Column {
                    for (code in listOf("W", "T")) OutcomePill(MatchListModel.Result("k$code", "Qual 1", "Q1", 1, 1, code, empty, empty, 1, 1))
                }
            }
        }
        compose.onNodeWithText("WIN").assertIsDisplayed()
        compose.onNodeWithText("TIE").assertIsDisplayed()
    }

    @Test
    fun `hero countdown text`() {
        val now = Instant.parse("2026-04-11T00:00:00Z")
        assertEquals("3:27", HeroCountdown.text(now.plusSeconds(207), now))
        assertEquals("1:05:09", HeroCountdown.text(now.plusSeconds(3909), now))
        assertEquals("0:00", HeroCountdown.text(now.minusSeconds(30), now))
    }

    @Test
    fun `long header titles truncate and keep the action`() {
        compose.setContent {
            PitWatchTheme {
                ScreenHeader("FIRST in California Northern Championship presented by a very long sponsor list", "Team 5507",
                    action = { HeaderIconButton(Icons.Filled.DateRange, "Choose event") {} })
            }
        }
        compose.onNode(hasContentDescription("Choose event")).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*ScoreboardTest')`
Expected: compile FAIL with unresolved `PhaseSteps`, `PhaseTimeline`, `AllianceLine` and the other components.

- [ ] **Step 3: Implement `Scoreboard.kt`**

```kotlin
package com.pitwatch.app.ui.scoreboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitwatch.app.ui.matches.MatchListModel
import com.pitwatch.app.ui.theme.Barlow
import com.pitwatch.app.ui.theme.BarlowCondensed
import com.pitwatch.app.ui.theme.PitWatchType
import com.pitwatch.app.ui.theme.StatusColors
import com.pitwatch.core.model.MatchAlliance
import com.pitwatch.core.model.Phase
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.delay

/** Condensed scoreboard type at [size] and [weight]. */
fun condensed(size: TextUnit, weight: FontWeight = FontWeight.Bold, letterSpacing: TextUnit = 0.sp) =
    TextStyle(fontFamily = BarlowCondensed, fontWeight = weight, fontSize = size, letterSpacing = letterSpacing)

/** Rounded surface-container card; in light mode a hairline ring stands in for elevation. */
@Composable
fun ScoreboardCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(radius)
    val light = colors.surface.luminance() > 0.5f
    Column(
        modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .then(if (light) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier, style = PitWatchType.sectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun StatusPill(text: String, container: Color, content: Color, fontSize: TextUnit = 16.sp) {
    Text(
        text.uppercase(),
        Modifier.background(container, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
        color = content,
        style = condensed(fontSize, FontWeight.ExtraBold, 1.sp),
        maxLines = 1,
    )
}

@Composable
fun PhasePill(phase: Phase, fontSize: TextUnit = 16.sp) =
    StatusPill(phase.stateLabel, StatusColors.phase(phase), StatusColors.onPhase(phase), fontSize)

@Composable
fun OutcomePill(result: MatchListModel.Result, fontSize: TextUnit = 16.sp) {
    val pill = StatusColors.outcome(result.outcome)
    val colors = MaterialTheme.colorScheme
    StatusPill(result.outcomeLabel, pill?.container ?: colors.surfaceVariant, pill?.content ?: colors.onSurfaceVariant, fontSize)
}

@Composable
fun TeamChip(number: String, container: Color, content: Color, style: TextStyle) {
    Text(
        number,
        Modifier
            .background(container, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp)
            .clearAndSetSemantics { contentDescription = "Your team, $number" },
        color = content,
        style = style,
        maxLines = 1,
    )
}

/** "4698 · [5507] · 1678": our team in a chip. */
@Composable
private fun Teams(line: MatchListModel.AllianceLine, style: TextStyle, color: Color, chipContainer: Color, chipContent: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        line.teams.forEachIndexed { i, team ->
            if (i > 0) Text(" · ", style = style, color = color)
            if (team.isUs) TeamChip(team.number, chipContainer, chipContent, style) else Text(team.number, style = style, color = color, maxLines = 1)
        }
    }
}

@Composable
private fun Opr(value: Double, color: Color) =
    Text("Σ %.0f".format(value), style = TextStyle(fontFamily = Barlow, fontWeight = FontWeight.SemiBold, fontSize = 15.sp), color = color)

/** Compact alliance row for lists: a colored edge stripe, the teams, and a trailing value (Σ OPR by default). */
@Composable
fun AllianceLine(
    alliance: MatchAlliance,
    line: MatchListModel.AllianceLine,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { line.summedOpr?.let { Opr(it, MaterialTheme.colorScheme.onSurfaceVariant) } },
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(StatusColors.alliance(alliance), RoundedCornerShape(3.dp)))
        Box(Modifier.weight(1f)) { Teams(line, condensed(20.sp, letterSpacing = 0.5.sp), colors.onSurface, colors.primary, colors.onPrimary) }
        trailing()
    }
}

/** Full-width red and blue bands for the hero. */
@Composable
fun AllianceBands(red: MatchListModel.AllianceLine, blue: MatchListModel.AllianceLine, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        for ((alliance, line) in listOf(MatchAlliance.RED to red, MatchAlliance.BLUE to blue)) {
            val band = StatusColors.alliance(alliance)
            Row(Modifier.fillMaxWidth().background(band).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { Teams(line, condensed(26.sp, letterSpacing = 1.sp), Color.White, Color.White, band) }
                line.summedOpr?.let { Opr(it, Color.White) }
            }
        }
    }
}

object PhaseSteps {
    /** Label and the phase that fills it; the last step is the match itself, which no phase reaches. */
    val steps: List<Pair<String, Phase?>> = listOf("QUEUE" to Phase.QUEUEING, "ON DECK" to Phase.ON_DECK, "ON FIELD" to Phase.ON_FIELD, "MATCH" to null)

    /** 1-based current step; null before queueing and for TBA-only rows with no phase. */
    fun current(phase: Phase?): Int? = when (phase) {
        Phase.QUEUEING -> 1
        Phase.ON_DECK -> 2
        Phase.ON_FIELD -> 3
        Phase.PRE_QUEUE, null -> null
    }

    fun description(phase: Phase?): String {
        val step = current(phase) ?: return "Not queued yet"
        return phase!!.stateLabel.lowercase().replaceFirstChar { it.uppercase() } + ", step $step of 4"
    }
}

/** Queue → on deck → on field → match: filled up to the current phase, the current step taller. */
@Composable
fun PhaseTimeline(phase: Phase?, modifier: Modifier = Modifier) {
    val current = PhaseSteps.current(phase)
    val colors = MaterialTheme.colorScheme
    val description = PhaseSteps.description(phase)
    Column(modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth().height(16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            PhaseSteps.steps.forEachIndexed { i, (_, stepPhase) ->
                val step = i + 1
                val filled = current != null && step <= current && stepPhase != null
                Box(
                    Modifier.weight(1f).height(if (step == current) 16.dp else 10.dp)
                        .background(if (filled) StatusColors.phase(stepPhase!!) else colors.surfaceContainerHighest, RoundedCornerShape(6.dp)),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PhaseSteps.steps.forEachIndexed { i, (label, stepPhase) ->
                val isCurrent = i + 1 == current
                Text(
                    if (isCurrent) "$label ◂" else label,
                    Modifier.weight(1f),
                    style = condensed(16.sp, letterSpacing = 1.sp),
                    color = if (isCurrent && stepPhase != null) StatusColors.phase(stepPhase) else colors.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        }
    }
}

object HeroCountdown {
    /** "3:27" under an hour, "1:05:09" beyond, "0:00" once passed. */
    fun text(deadline: Instant, now: Instant): String {
        val s = Duration.between(now, deadline).seconds.coerceAtLeast(0)
        return if (s < 3600) "%d:%02d".format(s / 60, s % 60) else "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60)
    }
}

/** The hero's big ticking countdown; [now] arrives every 30 s, so it ticks locally in between. */
@Composable
fun HeroCountdownText(deadline: Instant, now: Instant, modifier: Modifier = Modifier) {
    val ticking by produceState(now, deadline, now) {
        while (true) {
            delay(1_000)
            value = value.plusSeconds(1)
        }
    }
    BasicText(
        HeroCountdown.text(deadline, ticking),
        modifier.fillMaxWidth(),
        style = PitWatchType.countdownHero.copy(color = MaterialTheme.colorScheme.onSurface),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 48.sp, maxFontSize = 128.sp),
    )
}

/** Replaces the top app bar: condensed caps title, a muted subtitle, optional leading navigation and trailing action. */
@Composable
fun ScreenHeader(
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    navigation: (@Composable () -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        navigation?.let {
            it()
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title.uppercase(), style = condensed(26.sp, letterSpacing = 0.4.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        action?.invoke()
    }
}

@Composable
fun HeaderIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(44.dp)) { Icon(icon, contentDescription = description) }
}

/** Full-width 56 dp accent button in condensed caps; [outlined] for the "stop" form. */
@Composable
fun AccentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, outlined: Boolean = false, enabled: Boolean = true) {
    val shape = RoundedCornerShape(28.dp)
    val label: @Composable () -> Unit = { Text(text.uppercase(), style = condensed(22.sp, FontWeight.ExtraBold, 1.sp), textAlign = TextAlign.Center) }
    if (outlined) {
        OutlinedButton(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled, shape = shape) { label() }
    } else {
        Button(onClick, modifier.fillMaxWidth().height(56.dp), enabled = enabled, shape = shape) { label() }
    }
}
```

> **Ruling (spec gap):** the spec shows "3:27" but gives no format. The hero counts in `m:ss`, ticking each second, and switches to `h:mm:ss` past an hour. Auto-sizing keeps long values on one line. The list and widget keep their existing formats.

- [ ] **Step 4: Run to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*ScoreboardTest')`
Expected: PASS. If Robolectric can't load the bundled fonts (a font-loading exception), first check `ThemeTest`, which doesn't render. Then systematically debug the render path. Do not swap the fonts out.

- [ ] **Step 5: Full suite, then commit**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`. Expected: all pass.

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/ui/scoreboard android/app/src/test/kotlin/com/pitwatch/app/ui/scoreboard
git commit -m "feat(app): shared Scoreboard components — cards, pills, alliance lines/bands, phase timeline, hero countdown, header"
```

---

### Task 4: Matches screen

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchesScreen.kt` (everything from `MatchesBody` down; `MatchesScreen`, `MatchesContent` and `Countdowns` keep their signatures)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchesContentTest.kt`

**Interfaces:**
- Consumes: everything Task 3 produces, plus `MatchListModel.next` and `Result.red/blue/redScore/blueScore`.
- Produces: `internal fun MatchRowItem(row: MatchListModel.MatchRow, onOpenMatch: (String) -> Unit)`, same signature as today. `PhaseBadge` is deleted. Before deleting it, `grep -rn PhaseBadge android/app/src` and replace other uses with `PhasePill`.

- [ ] **Step 1: Update and add the tests**

In `MatchesContentTest`, change the expectations to match the new copy, and add tests:

```kotlin
    @Test
    fun `hero - header, queue line and next match`() {
        show()
        compose.onNodeWithText("CALIFORNIA NORTHERN").assertIsDisplayed()
        compose.onNodeWithText("Team 5507 · Rank #34 · 1-2-0").assertIsDisplayed()
        compose.onNodeWithText("START LIVE TRACKING").assertIsDisplayed()
        compose.onNodeWithText("QUAL 36").assertIsDisplayed()
        compose.onNodeWithText("ON FIELD").assertIsDisplayed()
        compose.onNode(hasContentDescription("On field, step 3 of 4")).assertIsDisplayed()
        compose.onAllNodes(hasContentDescription("Your team, 5507")).onFirst().assertIsDisplayed()
        compose.onNodeWithTag("matches").performScrollToNode(hasText("NOW QUEUING · QUALIFICATION 38"))
    }

    @Test
    fun `the hero match is not repeated in the list`() {
        show()
        compose.onAllNodes(hasText("Q36")).assertCountEquals(0)
    }

    @Test
    fun `a day holding only the hero match gets no header`() {
        val lonely = model.copy(days = listOf(MatchListModel.Day(null, "Lonely day", listOf(MatchListModel.Item.Upcoming(model.next!!)))))
        compose.setContent { MatchesContent(lonely, SNAP_NOW, false, false, {}, {}, {}, {}) }
        compose.onAllNodes(hasText("LONELY DAY")).assertCountEquals(0)
    }

    @Test
    fun `days, breaks and results are listed`() {
        show()
        val list = compose.onNodeWithTag("matches")
        for (text in listOf("SATURDAY, APR 11", "LUNCH", "END OF DAY", "LAST", "Q22")) {
            list.performScrollToNode(hasText(text, substring = true))
            compose.onAllNodes(hasText(text, substring = true)).onFirst().assertIsDisplayed()
        }
    }

    @Test
    fun `result rows show both scores and the outcome`() {
        show()
        compose.onNodeWithTag("matches").performScrollToNode(hasText("403"))
        compose.onNodeWithText("403").assertIsDisplayed()
        compose.onNodeWithText("299").assertIsDisplayed()
        compose.onAllNodes(hasText("WIN")).onFirst().assertIsDisplayed()
    }

    @Test
    fun `TBA-only events show the timeline with nothing current`() {
        val tbaOnly = MatchListModels.build(snapshotCache(), UserConfig(teamNumber = 5507, apiKey = "k"), RefreshState(), SNAP_NOW, Locale.US, com.pitwatch.app.LA)
        compose.setContent { MatchesContent(tbaOnly, SNAP_NOW, false, false, {}, {}, {}, {}) }
        compose.onNode(hasContentDescription("Not queued yet")).assertIsDisplayed()
    }
```

Also:
- Delete the old `status card, queue line and next match` test; the hero test above replaces it.
- In `tracking button reflects and toggles state`, use `"STOP LIVE TRACKING"`.
- In `tapping a result opens it on TBA`, scroll to and click `"Q22"`.
- Keep the compact-row, countdown, picker and Tokyo tests unchanged.

Add the imports `hasContentDescription` and `onFirst`.

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*MatchesContentTest')`
Expected: FAIL. There's no node "CALIFORNIA NORTHERN", no "On field, step 3 of 4", and "Q36" is still listed.

- [ ] **Step 3: Implement**

Replace `MatchesBody` and everything after it in `MatchesScreen.kt` with the code below. Remove the imports that are no longer used: `Card`, `CardDefaults`, `TopAppBar`, `HorizontalDivider`, `IconButton`, `FontFamily`, `CircleShape`. Add imports for the `ui.scoreboard` components.

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchesBody(
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
            ScreenHeader(model.title, model.status?.text, action = { HeaderIconButton(Icons.Filled.DateRange, "Choose event", onPickEvent) })
        },
    ) { padding ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().testTag("matches"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "hero") { Hero(model, now, tracking, onToggleTracking, onOpenMatch) }
                model.nowQueuing?.let { queuing ->
                    item(key = "queuing") {
                        Text("NOW QUEUING · ${queuing.uppercase()}", Modifier.padding(horizontal = 4.dp, vertical = 4.dp), style = condensed(20.sp), color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (model.nexusUnavailable) {
                    item(key = "nexus") {
                        Text("Nexus unavailable — showing TBA times", Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                model.error?.let { error -> item(key = "error") { ErrorBanner(error) } }
                when (model.empty) {
                    MatchListModel.Empty.NO_EVENT -> item(key = "empty") { EmptyState("No event yet", "Pick an event", onPickEvent) }
                    MatchListModel.Empty.NO_MATCHES -> item(key = "empty") { EmptyState("No matches scheduled yet", null, null) }
                    else -> Unit
                }
                model.days.forEachIndexed { index, day ->
                    // The hero shows the next match; a day left empty without it gets no header.
                    val items = day.items.filterNot { it is MatchListModel.Item.Upcoming && it.row.isNext }
                    if (items.isEmpty()) return@forEachIndexed
                    item(key = "day:$index") { SectionLabel(day.label, Modifier.padding(start = 4.dp, top = 10.dp)) }
                    items(items, key = { it.id }) { item ->
                        when (item) {
                            is MatchListModel.Item.Upcoming -> MatchRowItem(item.row, onOpenMatch)
                            is MatchListModel.Item.Break -> BreakRow(item)
                        }
                    }
                }
                if (model.results.isNotEmpty()) {
                    item(key = "results") { SectionLabel("Last", Modifier.padding(start = 4.dp, top = 10.dp)) }
                    items(model.results, key = { "result:${it.key}" }) { ResultRow(it, onOpenMatch) }
                }
            }
        }
    }
}

/** Next match, big countdown, phase timeline, alliance bands, and the tracking button; just the button when nothing is next. */
@Composable
private fun Hero(model: MatchListModel, now: Instant, tracking: Boolean, onToggleTracking: () -> Unit, onOpenMatch: (String) -> Unit) {
    val next = model.next
    ScoreboardCard(Modifier.fillMaxWidth()) {
        if (next != null) {
            Column(Modifier.clickable { onOpenMatch(next.url) }) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(next.label.uppercase(), Modifier.weight(1f), style = condensed(44.sp, FontWeight.ExtraBold), maxLines = 1)
                    next.phase?.let { PhasePill(it, 18.sp) }
                }
                Column(Modifier.padding(horizontal = 20.dp)) {
                    next.countdown?.let { HeroCountdownText(it.deadline, now) }
                    val start = LocalTimeFormat.current.match(next.time, next.estimated) + " start"
                    Text(
                        listOfNotNull(next.countdown?.target, start).joinToString(" · "),
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PhaseTimeline(next.phase, Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp))
                AllianceBands(next.red, next.blue, Modifier.padding(top = 18.dp))
            }
        }
        AccentButton(
            if (tracking) "Stop live tracking" else "Start live tracking",
            onToggleTracking,
            Modifier.padding(20.dp),
            outlined = tracking,
        )
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(message, Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyState(title: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title.uppercase(), style = condensed(22.sp))
        if (action != null && onAction != null) OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 12.dp)) { Text(action) }
    }
}

@Composable
internal fun MatchRowItem(row: MatchListModel.MatchRow, onOpenMatch: (String) -> Unit) {
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 16.dp, onClick = { onOpenMatch(row.url) }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                row.alliance?.let {
                    Text("●", style = condensed(22.sp), color = StatusColors.alliance(it))
                    Spacer(Modifier.width(8.dp))
                }
                Text(row.shortLabel, Modifier.weight(1f), style = condensed(28.sp, FontWeight.ExtraBold))
                // Only matches already in motion get a pill; color is for urgency, not for "later".
                row.phase?.takeIf { it != Phase.PRE_QUEUE }?.let {
                    PhasePill(it, 14.sp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(LocalTimeFormat.current.match(row.time, row.estimated), style = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AllianceLine(MatchAlliance.RED, row.red)
            AllianceLine(MatchAlliance.BLUE, row.blue)
        }
    }
}

@Composable
private fun BreakRow(item: MatchListModel.Item.Break) {
    val range = item.end?.let { " " + LocalTimeFormat.current.range(item.start, it) } ?: ""
    Text(
        "— ${(item.title + range).uppercase()} —",
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        style = condensed(17.sp, letterSpacing = 2.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun ResultRow(result: MatchListModel.Result, onOpenMatch: (String) -> Unit) {
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 16.dp, onClick = { onOpenMatch(result.url) }) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.shortLabel, Modifier.weight(1f), style = condensed(28.sp, FontWeight.ExtraBold))
                OutcomePill(result)
            }
            AllianceLine(MatchAlliance.RED, result.red) { Score(result.redScore, winner = result.redScore >= result.blueScore) }
            AllianceLine(MatchAlliance.BLUE, result.blue) { Score(result.blueScore, winner = result.blueScore >= result.redScore) }
        }
    }
}

/** The winning score at full strength, the losing one muted; a tie keeps both full. */
@Composable
private fun Score(score: Int, winner: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface
    Text("$score", style = condensed(22.sp, FontWeight.ExtraBold), color = if (winner) color else color.copy(alpha = 0.6f))
}
```

Imports this needs: `androidx.compose.ui.text.style.TextAlign`, `androidx.compose.ui.unit.sp`, `com.pitwatch.core.model.MatchAlliance`, and `com.pitwatch.app.ui.scoreboard.*` (wildcard, or each component by name).

> **Ruling (spec gap):** with no next match, the hero shows only the tracking button. The status line the spec mentions is already in the header subtitle.

- [ ] **Step 4: Run to verify they pass**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*MatchesContentTest')`
Expected: PASS. If `performScrollToNode` doesn't find an item, the LazyColumn key or the item text is wrong; debug it, and keep the assertion.

- [ ] **Step 5: Full suite, then commit**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`. Expected: all pass.

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/ui/matches/MatchesScreen.kt android/app/src/test/kotlin/com/pitwatch/app/ui/matches/MatchesContentTest.kt
git commit -m "feat(app): Scoreboard Matches screen — hero card, bold rows with alliances, results with both scores"
```

---

### Task 5: Navigation bar, Settings, event picker, setup

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/PitWatchRoot.kt` (`PitWatchScaffold` only)
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/SettingsScreen.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/events/EventPicker.kt` (`EventPickerContent`)
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/SetupScreen.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/events/EventPickerTest.kt` and `NavigationTest.kt`; existing tests must stay green. Add one test.

**Interfaces:**
- Consumes: `ScreenHeader`, `HeaderIconButton`, `SectionLabel`, `ScoreboardCard`, `AccentButton` and `condensed` from Task 3.

- [ ] **Step 1: Write the failing test** (append to `EventPickerTest`; reuse its existing setup and options helpers):

```kotlin
    @Test
    fun `picker has a scoreboard header and marks the current event`() {
        compose.setContent {
            EventPickerContent(
                EventPickerState.Loaded(listOf(EventOption("2026casf", "San Francisco", "Mar 5–8", null))), "2026casf", {}, {}, {},
            )
        }
        compose.onNodeWithText("CHOOSE EVENT").assertIsDisplayed()
        compose.onNode(hasContentDescription("Selected")).assertIsDisplayed()
    }
```
`EventOption(key, name, dates, location)` is the real constructor order (`EventPicker.kt:48`).

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*EventPickerTest')`
Expected: FAIL. There's no node "CHOOSE EVENT" (today's title is "Event").

- [ ] **Step 3: Implement**

**`PitWatchScaffold`**: keep `NavigationBar` and `NavigationBarItem` (that keeps the selected semantics `NavigationTest` relies on). Add the colors:

```kotlin
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = tab == selected,
                        onClick = { onSelect(tab) },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label, style = MaterialTheme.typography.bodySmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onSurface,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                        ),
                    )
                }
            }
```

**`EventPickerContent`**: replace the `TopAppBar` with

```kotlin
        topBar = {
            ScreenHeader(
                "Choose event", null,
                navigation = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
            )
        },
```
Then replace the `Loaded` list's rows with `EventRow` (the "Auto" row uses the same composable with `dates = null`):

```kotlin
            is EventPickerState.Loaded -> LazyColumn(
                Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { EventRow("Auto (current or next event)", null, selectedKey == null) { onSelect(null) } }
                items(state.options, key = { it.key }) { option ->
                    EventRow(option.name, listOfNotNull(option.dates, option.location).joinToString(" · "), option.key == selectedKey) { onSelect(option.key) }
                }
            }
```

```kotlin
/** A picker row: the current event gets an accent bar on its left edge and a check. */
@Composable
private fun EventRow(name: String, detail: String?, selected: Boolean, onClick: () -> Unit) {
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 16.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent))
            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(name, style = condensed(20.sp))
                detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", Modifier.padding(end = 14.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
```

**`SettingsScreen`**: replace the `TopAppBar` with

```kotlin
        topBar = {
            ScreenHeader(
                "Settings", RefreshStatusText.format(refreshState, container.clock()),
                navigation = onBack?.let { back -> { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } } },
            )
        },
```
Then restructure the body. Give the scroll `Column` `Modifier.padding(horizontal = 16.dp)` and `verticalArrangement = Arrangement.spacedBy(8.dp)`. Each existing section becomes `Section(title) { …existing rows… }`, and every `HorizontalDivider` is deleted.

```kotlin
/** A titled group of settings on a rounded card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    SectionLabel(title, Modifier.padding(start = 8.dp, top = 14.dp))
    ScoreboardCard(Modifier.fillMaxWidth(), radius = 20.dp) { Column(Modifier.padding(vertical = 6.dp), content = content) }
}
```
Within the groups:
- Each `ListItem` gets `colors = ListItemDefaults.colors(containerColor = Color.Transparent)` so the card shows through.
- The "Save event" and "Save keys" `Button`s become `AccentButton("Save event", …)` and `AccentButton("Save keys", …)`. Keep their `enabled`/`onClick` logic.
- The inner `Column(Modifier.padding(horizontal = 16.dp) …)` wrappers keep their padding, plus `padding(bottom = 12.dp)`.
- The "Status" section keeps its "Force refresh" `OutlinedButton`. The status text moved to the header, so delete that `Text`.
- The "About" text stays, inside its own `Section("About")`.

**`SetupScreen`**:
- The title `Text("Set up PitWatch", …)` becomes `Text("SET UP PITWATCH", style = condensed(30.sp, FontWeight.ExtraBold))`.
- The fields get `modifier = Modifier.fillMaxWidth()`.
- The `Button` becomes `AccentButton(if (busy) "Checking…" else "Continue", onClick = { …unchanged… }, enabled = !busy)`.

- [ ] **Step 4: Run to verify, then the full suite**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`
Expected: all pass, including `NavigationTest` ("Matches" is still selected) and the existing `EventPickerTest` clicks on "San Francisco" and "Auto (current or next event)". These are unchanged and not uppercased.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/ui android/app/src/test/kotlin/com/pitwatch/app/ui/events
git commit -m "feat(app): Scoreboard nav bar, Settings groups, event picker and setup"
```

---

### Task 6: Pit map

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/ui/pitmap/PitMapScreen.kt` (`PitMapContent`, plus colors and label font in `PitMapCanvas`)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/ui/pitmap/PitMapScreenTest.kt`

**Interfaces:**
- Consumes: `ScreenHeader`, `ScoreboardCard`, `SectionLabel`, `TeamChip`, `condensed` and `BarlowCondensed`.

- [ ] **Step 1: Update the test.** In `PitMapScreenTest`, replace the wait on `"Pit C1"` (line 48) with

```kotlin
        compose.waitUntil(5_000) { compose.onAllNodes(hasText("OUR PIT")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("C1").assertIsDisplayed()
```
(Add the `onNodeWithText` and `assertIsDisplayed` imports.)

- [ ] **Step 2: Run to verify it fails**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*PitMapScreenTest')`
Expected: FAIL. `waitUntil` times out because there's no "OUR PIT".

- [ ] **Step 3: Implement**

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitMapContent(state: PitMapState, teamNumber: String?, onOpenSettings: () -> Unit) {
    val ours = (state as? PitMapState.Loaded)?.let { PitMapGeometry.focus(it.map, teamNumber) }
    Scaffold(topBar = { ScreenHeader("Pit map", null) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ours?.let { OurPit(it.address, teamNumber) }
            ScoreboardCard(Modifier.fillMaxWidth().weight(1f).padding(bottom = 12.dp)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
    }
}

@Composable
private fun OurPit(address: String, teamNumber: String?) {
    ScoreboardCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Our pit")
                Text(address, style = condensed(64.sp, FontWeight.ExtraBold).copy(lineHeight = 64.sp))
            }
            teamNumber?.let { TeamChip(it, MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary, condensed(26.sp, FontWeight.ExtraBold)) }
        }
    }
}
```
In `PitMapCanvas`:
- `layout()`'s `TextStyle` gains `fontFamily = BarlowCondensed, fontWeight = FontWeight.Bold`. Use it for both the measuring and the final `TextStyle`.
- The area fill becomes `colors.surfaceContainerHigh`.
- Non-ours pit fill becomes `colors.surfaceContainer`, stroke `colors.outline` at `Stroke(width = 1.5f)`.
- Non-ours pit label color becomes `colors.onSurface`.
- Ours stays `colors.primary` / `colors.onPrimary`.

`TeamChip` sets the semantics "Your team, 5507". The test asserts on the pit address "C1", which is plain text.

- [ ] **Step 4: Run to verify, then the full suite**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/ui/pitmap/PitMapScreen.kt android/app/src/test/kotlin/com/pitwatch/app/ui/pitmap/PitMapScreenTest.kt
git commit -m "feat(app): Scoreboard pit map — our-pit card, condensed labels, card-colored pits"
```

---

### Task 7: Widget

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetContent.kt`
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/widget/WidgetModel.kt` (`WidgetLines.FIXED` and `ROW`; add `TeamSplit`)
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/widget/PitWatchWidget.kt` (`ChronometerCountdown` text size)
- Test: `android/app/src/test/kotlin/com/pitwatch/app/widget/WidgetContentTest.kt` and `WidgetModelTest.kt`

**Interfaces:**
- Consumes: `PhaseSteps` (Task 3), `StatusColors.outcome`/`Pill` (Task 1) and `Result.outcomeLabel` (Task 2).
- Produces: `object TeamSplit { fun of(line: MatchListModel.AllianceLine): Triple<String, String?, String> }`.

- [ ] **Step 1: Write the failing tests**

In `WidgetModelTest`:

```kotlin
    @Test
    fun `team split puts our team in the middle`() {
        fun line(vararg t: String) = MatchListModel.AllianceLine(t.map { MatchListModel.TeamChip(it, it == "5507") }, null)
        assertEquals(Triple("4698 · ", "5507", " · 1678"), TeamSplit.of(line("4698", "5507", "1678")))
        assertEquals(Triple("", "5507", " · 1678"), TeamSplit.of(line("5507", "1678")))
        assertEquals(Triple("6036 · 9470 · 6814", null, ""), TeamSplit.of(line("6036", "9470", "6814")))
    }
```

In `WidgetContentTest`, replace the medium and large tests and extend the small one:

```kotlin
    @Test
    fun `small - next match, countdown and phase bar`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.SMALL)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("5507 · #34 · 1-2-0")).assertExists()
        onNode(hasText("Q36")).assertExists()
        onNode(hasText("COUNTDOWN")).assertExists()
        onAllNodes(hasTestTag("phase-step")).assertCountEquals(4)
        onNode(hasText("LAST")).assertDoesNotExist()
    }

    @Test
    fun `medium - adds alliances with our chip and the last result`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(PitWatchWidget.MEDIUM)
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("LAST")).assertExists()
        onNode(hasText("403–299")).assertExists()
        onNode(hasText("WIN")).assertExists()
        onNode(hasText("4698 · ")).assertExists()
        onNode(hasText(" · 1678")).assertExists()
    }

    @Test
    fun `large - upcoming list with breaks`() = runGlanceAppWidgetUnitTest {
        setAppWidgetSize(androidx.compose.ui.unit.DpSize(250.dp, 600.dp)) // tall enough that the 9-line cap, not the height, ends the list
        provideComposable { GlanceTheme { WidgetContent(ready) { Text("COUNTDOWN") } } }
        onNode(hasText("UPCOMING")).assertExists()
        onNode(hasText("CALIFORNIA NORTHERN")).assertExists()
        onNode(hasText("Saturday, Apr 11")).assertExists()
        onNode(hasText("LAST · Q22")).assertExists()
        onAllNodes(hasText("End of day")).assertCountEquals(1)
        onNode(hasText("Lunch")).assertExists()
        onNode(hasText("Q43")).assertExists()
    }
```
(Add the import `androidx.glance.testing.unit.hasTestTag`. In Glance's unit-test matchers, `hasText` matches substrings, which is why `"LAST"` alone works today.)

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*WidgetModelTest' --tests '*WidgetContentTest')`
Expected: compile FAIL on `TeamSplit`. With that stubbed out, the assertions fail on `phase-step` and `"LAST · Q22"`.

- [ ] **Step 3: Implement**

`WidgetModel.kt`: add `TeamSplit` and retune the budget for the bigger rows.

```kotlin
/** "4698 · ", "5507", " · 1678": the text either side of our team, so it can sit in its own chip. */
object TeamSplit {
    fun of(line: MatchListModel.AllianceLine): Triple<String, String?, String> {
        val numbers = line.teams.map { it.number }
        val i = line.teams.indexOfFirst { it.isUs }
        if (i < 0) return Triple(numbers.joinToString(" · "), null, "")
        val before = numbers.take(i).joinToString(" · ").let { if (it.isEmpty()) it else "$it · " }
        val after = numbers.drop(i + 1).joinToString(" · ").let { if (it.isEmpty()) it else " · $it" }
        return Triple(before, numbers[i], after)
    }
}
```
In `WidgetLines`, set `FIXED = 308.dp` and `ROW = 22.dp`. Update the comment: "header, title, next match with the 56 sp countdown, phase bar, alliances, divider, last result, label". Existing `budget` tests still hold: `budget(200.dp) == 0`, and `budget(300.dp) == 0 < budget(450.dp) == 6`.

`WidgetContent.kt`, replacing the composables:

```kotlin
private val CONDENSED = FontFamily("sans-serif-condensed")

@Composable
fun WidgetContent(model: WidgetModel, countdown: @Composable (Instant) -> Unit) {
    val size = LocalSize.current
    val wide = size.width >= PitWatchWidget.MEDIUM.width
    val tall = size.height >= PitWatchWidget.LARGE.height
    val muted = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val times = TimeFormat(model.timeZone, model.zoneLabel)
    // Glance renders at most 10 children per Column: this one holds at most 7.
    Column(
        GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp).clickable(actionStartActivity<MainActivity>()),
    ) {
        Text(model.header, style = muted, maxLines = 1)
        if (tall) model.eventTitle?.let { Text(it.uppercase(), style = muted, maxLines = 1) }
        val next = model.next
        if (model.state != WidgetModel.State.READY || next == null) {
            Text(model.message.orEmpty(), style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp), modifier = GlanceModifier.padding(top = 8.dp))
            model.last?.let { if (wide) LastResult(it) }
            return@Column
        }
        Row(GlanceModifier.fillMaxWidth().padding(top = 4.dp)) {
            Column(GlanceModifier.defaultWeight()) {
                NextMatch(next, times, model.countdownDeadline, tall, countdown)
                next.phase?.let { PhaseBar(it) }
                if (wide) {
                    AllianceRow(MatchAlliance.RED, next.red)
                    AllianceRow(MatchAlliance.BLUE, next.blue)
                }
            }
            if (wide && !tall) model.last?.let { LastResult(it) }
        }
        if (tall) {
            Box(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(GlanceModifier.fillMaxWidth().height(1.dp).background(GlanceTheme.colors.outline)) {}
            }
            // Last result goes above the list: a long upcoming list may only truncate itself (found on-device).
            model.last?.let { LastLine(it) }
            val lines = WidgetLines.fit(model.laterDays, WidgetLines.budget(size.height))
            if (lines.isNotEmpty()) Text("UPCOMING", style = muted, modifier = GlanceModifier.padding(top = 6.dp))
            // Own Column: Glance drops children past 10 per Column, and the parent is already busy.
            Column {
                for (line in lines) {
                    when (line) {
                        is WidgetLine.Header -> Text(
                            line.label,
                            style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED),
                            modifier = GlanceModifier.padding(top = 4.dp),
                        )
                        is WidgetLine.Entry -> when (val item = line.item) {
                            is MatchListModel.Item.Upcoming -> Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                item.row.alliance?.let { Text("● ", style = TextStyle(color = ColorProvider(StatusColors.alliance(it)), fontSize = 13.sp)) }
                                Text(item.row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED), modifier = GlanceModifier.defaultWeight())
                                Text(times.match(item.row.time, item.row.estimated), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 14.sp, fontFamily = CONDENSED))
                            }
                            is MatchListModel.Item.Break -> Text(item.title, style = muted, modifier = GlanceModifier.padding(vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NextMatch(row: MatchListModel.MatchRow, times: TimeFormat, deadline: Instant?, tall: Boolean, countdown: @Composable (Instant) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(row.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        row.phase?.let {
            Spacer(GlanceModifier.width(8.dp))
            Pill(it.stateLabel, ColorProvider(StatusColors.phase(it)), ColorProvider(StatusColors.onPhase(it)))
        }
    }
    Text(times.match(row.time, row.estimated), style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
    if (deadline != null) {
        // The platform Chronometer gets its own line: sharing a row with small text clipped it on-device.
        countdown(deadline)
        if (tall) row.countdown?.let { Text(it.target, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1) }
    }
}

/** Four rounded segments filled up to the current phase; the current one taller. */
@Composable
private fun PhaseBar(phase: Phase) {
    val current = PhaseSteps.current(phase)
    Row(GlanceModifier.fillMaxWidth().height(11.dp).padding(top = 0.dp), verticalAlignment = Alignment.Bottom) {
        PhaseSteps.steps.forEachIndexed { i, (_, stepPhase) ->
            val step = i + 1
            if (i > 0) Spacer(GlanceModifier.width(4.dp))
            val filled = current != null && step <= current && stepPhase != null
            Box(
                GlanceModifier.defaultWeight().height(if (step == current) 11.dp else 7.dp)
                    .background(if (filled) ColorProvider(StatusColors.phase(stepPhase!!)) else GlanceTheme.colors.surfaceVariant)
                    .cornerRadius(4.dp)
                    .semantics { testTag = "phase-step" },
            ) {}
        }
    }
}

@Composable
private fun AllianceRow(alliance: MatchAlliance, line: MatchListModel.AllianceLine) {
    val style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED)
    val (before, us, after) = TeamSplit.of(line)
    Row(GlanceModifier.fillMaxWidth().padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(GlanceModifier.width(5.dp).height(16.dp).background(ColorProvider(StatusColors.alliance(alliance))).cornerRadius(3.dp)) {}
        Spacer(GlanceModifier.width(8.dp))
        if (before.isNotEmpty()) Text(before, style = style, maxLines = 1)
        us?.let {
            Box(GlanceModifier.background(GlanceTheme.colors.primary).cornerRadius(5.dp).padding(horizontal = 5.dp)) {
                Text(it, style = style.copy(color = GlanceTheme.colors.onPrimary), maxLines = 1)
            }
        }
        if (after.isNotEmpty()) Text(after, style = style, maxLines = 1)
    }
}

@Composable
private fun Pill(text: String, container: ColorProvider, content: ColorProvider) {
    Box(GlanceModifier.background(container).cornerRadius(10.dp).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, style = TextStyle(color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
    }
}

@Composable
private fun OutcomePill(result: MatchListModel.Result) {
    val pill = StatusColors.outcome(result.outcome)
    Pill(
        result.outcomeLabel,
        pill?.let { ColorProvider(it.container) } ?: GlanceTheme.colors.surfaceVariant,
        pill?.let { ColorProvider(it.content) } ?: GlanceTheme.colors.onSurfaceVariant,
    )
}

/** Tall widgets: one line above the list. */
@Composable
private fun LastLine(result: MatchListModel.Result) {
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("LAST · ${result.shortLabel}", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED), modifier = GlanceModifier.defaultWeight())
        Text("${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Spacer(GlanceModifier.width(6.dp))
        OutcomePill(result)
    }
}

/** Wide, short widgets: a column beside the next match. */
@Composable
private fun LastResult(result: MatchListModel.Result) {
    Column(GlanceModifier.padding(start = 8.dp, top = 4.dp)) {
        Text("LAST", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Text(result.shortLabel, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        Text("${result.ourScore}–${result.theirScore}", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = CONDENSED))
        OutcomePill(result)
    }
}
```
These need imports: `androidx.glance.text.FontFamily`, `androidx.glance.semantics.semantics`, `androidx.glance.semantics.testTag`, `com.pitwatch.app.ui.scoreboard.PhaseSteps`, and `com.pitwatch.core.model.MatchAlliance`. Delete the old `PhaseBadge` and `AllianceText`.

`PitWatchWidget.ChronometerCountdown`: after `setChronometerCountDown`, add

```kotlin
        // 56 sp on tall widgets, 40 sp where the 110 dp cell would clip it.
        setTextViewTextSize(R.id.countdown, TypedValue.COMPLEX_UNIT_SP, if (LocalSize.current.height >= PitWatchWidget.LARGE.height) 56f else 40f)
```
(Imports: `android.util.TypedValue` and `androidx.glance.LocalSize`. Read `LocalSize.current` outside the `apply` block into a `val`, because it's a composable read.)

- [ ] **Step 4: Run to verify, then the full suite**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`
Expected: all pass, including `LiveWidgetTest`. If any `WidgetModelTest` budget assertion depends on the old `FIXED`, recompute it against `308.dp` and `22.dp`, and record a Ruling with the numbers.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/widget android/app/src/test/kotlin/com/pitwatch/app/widget
git commit -m "feat(app): Scoreboard widget — condensed type, phase bar, alliance chips, outcome pills, bigger countdown"
```

---

### Task 8: Live Update notification

**Files:**
- Modify: `android/app/src/main/kotlin/com/pitwatch/app/live/LiveNotification.kt`
- Test: `android/app/src/test/kotlin/com/pitwatch/app/live/LiveNotificationTest.kt`

**Interfaces:**
- Consumes: `StatusColors.notificationSegments` (Task 1).

- [ ] **Step 1: Update and add the tests**

In `requests promotion with title, text and status-bar chip`, expect `"3 AWAY · ON FIELD #29"`. Then add:

```kotlin
    @Test
    fun `segment colors come from the app's status colors`() {
        val s = style(build(snapshot()))
        assertEquals(StatusColors.notificationSegments.map { it.toArgb() }, s.progressSegments.map { it.color })
    }
```
(Imports: `androidx.compose.ui.graphics.toArgb` and `com.pitwatch.app.ui.theme.StatusColors`.)

- [ ] **Step 2: Run to verify they fail**

Run: `(cd android && ./gradlew :app:testDebugUnitTest --tests '*LiveNotificationTest')`
Expected: FAIL on the text, which is still lowercase "on field #29". The color test may already pass, because today's hex values match. It still pins the single source of truth.

- [ ] **Step 3: Implement**

- `text()`: change `"on field #$it"` to `"ON FIELD #$it"`. Update the KDoc to `/** "3 AWAY · ON FIELD #29", or "W 95–80" once scored. */`.
- Replace `SEGMENT_COLORS` with `private val SEGMENT_COLORS = StatusColors.notificationSegments.map { it.toArgb() }` and add the imports.

- [ ] **Step 4: Run to verify, then the full suite**

Run: `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`. Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/kotlin/com/pitwatch/app/live/LiveNotification.kt android/app/src/test/kotlin/com/pitwatch/app/live/LiveNotificationTest.kt
git commit -m "feat(app): Live Update text in scoreboard caps; segment colors from StatusColors"
```

---

### Task 9: On-device check and screenshots

**Files:** none committed, except a Ruling-driven fix if one is needed. Screenshots go to the session scratchpad.

- [ ] **Step 1: Build and install on the visible emulator only**

```bash
cd android && ./gradlew -q :app:assembleDebug -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3 -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1 \
  && adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
```
Expected: `Success`.

- [ ] **Step 2: Screenshot each surface in light and dark mode**

For `mode in no yes`, run `adb -s emulator-5580 shell cmd uimode night $mode`. Then for each tab, open it and run `adb -s emulator-5580 exec-out screencap -p > "$SCRATCH/<surface>-<mode>.png"`. Use `adb -s emulator-5580 shell input tap` with coordinates from `uiautomator dump` to switch tabs. Also capture:
- the home screen (widgets);
- the notification shade, after Start live tracking: `adb -s emulator-5580 shell cmd statusbar expand-notifications`.

Read every screenshot. Expected:
- nothing clipped or overlapping;
- the hero countdown fits on one line;
- the timeline labels don't wrap;
- the light-mode card ring is visible;
- the widget countdown is in Barlow (or the Task 1 fallback is recorded).

- [ ] **Step 3: Try a second wallpaper accent**

```bash
adb -s emulator-5580 shell settings put secure theme_customization_overlay_packages '{"android.theme.customization.system_palette":"1E88E5","android.theme.customization.theme_style":"TONAL_SPOT"}'
```
Reopen the app and screenshot the Matches screen. Expected: the accent (button, NOW QUEUING, chips) turns blue-ish and the alliance and phase colors are unchanged. Afterwards, restore the setting: `adb -s emulator-5580 shell settings delete secure theme_customization_overlay_packages`.

- [ ] **Step 4: Fix what the screenshots show.** Any visual bug gets a test where one is possible (layout semantics, text present), then the fix, then the full suite, then a commit. Afterwards, put `cmd uimode night` back to its original value.

---

## Self-review notes

- **Spec coverage:**

  | Spec section | Task |
  |---|---|
  | §1 Theme | Task 1 |
  | §2 Components | Task 3 |
  | §3 Matches | Task 4 |
  | §3 Pit map | Task 6 |
  | §3 Settings, picker, setup | Task 5 |
  | §4 Model | Task 2 |
  | §5 Widget | Tasks 1 (font check) and 7 |
  | §6 Notification | Task 8 |
  | §7 Manual testing | Task 9 |

- **Deviations, ruled inline:**
  - LOSS pill `#d32f2f` (contrast).
  - Hero countdown format `m:ss`.
  - Hero with no next match shows only the button.
  - Widget countdown size uses the existing `LARGE` height threshold (250 dp) instead of the spec's 180 dp, because that's the threshold where the target line and list appear.
  - `phaseTrackOff` is the theme's `surfaceContainerHighest` (Compose) or `surfaceVariant` (Glance), read in place instead of through `StatusColors`, because it's a theme color.
