# Scoreboard restyle — design

**Status:** approved in conversation 2026-10-06, pending written-spec review
**Mockups:** https://claude.ai/artifact/RZw4CQJ43Cx2tqoz4fqT3w (direction "B · Scoreboard", light + dark, plus the "other surfaces" row)
**Builds on:** `2026-10-05-android-screens-widgets-design.md` (functional scope unchanged)

## Goal

Give the Android app a bold, sports-scoreboard look across every surface: the Matches, Pit map, Settings, event picker and setup screens, the Glance widget and the Live Update notification. Functionality, data flow and polling are unchanged. This is a presentation pass, plus one model addition (alliances on result rows).

## Decisions

| Topic | Decision |
|---|---|
| Direction | B · Scoreboard (chosen over A Pit Wall and C Calm Material) |
| Color source | Wallpaper colors are kept. Material You `dynamicLight/DarkColorScheme` supplies the accent and surfaces. B's yellow in the mockups stands in for `primary`. |
| Light/dark | Follows the system |
| Scope | All screens, the widget and the notification |
| List rows | Upcoming and result rows show both alliances |
| Fonts | Barlow Condensed (600/700/800) and Barlow (400/500/600) are bundled in `res/font` (OFL license, about 300 KB, no runtime download) |

## 1. Theme (`ui/theme/`)

- **`PitWatchTheme`** keeps the dynamic scheme. In dark mode, `background` and `surface` are pulled darker toward B's near-black: blend the dynamic `surface` 60 % toward `#0f1216`. Light mode uses the dynamic scheme as-is.
- **`StatusColors`** stays the single source for fixed colors and is extended:
  - Phase colors are unchanged: pre-queue `#636366`, queue `#ff9500`, on deck `#ff6b00`, on field `#30d158`.
  - Alliance colors become the deeper band colors, red `#c62828` and blue `#1e5bd6`. White text stays readable on both in light and dark mode.
  - New `outcome(win/loss/tie)` colors for pills: WIN `#30d158` with `#0b1a10` text, LOSS `#e5484d` with white text, TIE `surfaceVariant` with `onSurfaceVariant` text.
  - New `phaseTrackOff`, the color for inactive timeline segments: `surfaceContainerHighest`.
  - New `notificationSegments`, the four notification progress colors. The first three are the phase colors; the fourth keeps today's `#0a84ff` "match" segment.
- **Typography.** A `Typography` built from the two fonts:
  - Display, headline and title styles, plus label styles, use Barlow Condensed at weights 700–800.
  - Body styles use Barlow.
  - An extra `PitWatchType.countdownHero` style: Barlow Condensed 700, 128 sp, `tnum` (tabular figures), letter-spacing −2 sp, line height 0.95 em.
  - `PitWatchType.sectionLabel`: Barlow Condensed 700, 16 sp, letter-spacing 2 sp, all caps, `onSurfaceVariant`.

## 2. Shared components (`ui/scoreboard/`)

- **`ScoreboardCard`**: a 24 dp-rounded `surfaceContainer` container. In light mode it gets a 1 dp `outlineVariant` ring instead of elevation.
- **`PhaseTimeline(phase)`**: 4 equal segments in a row with 6 dp gaps and a 6 dp corner radius.
  - Segments up to and including the current phase are filled with their phase colors; later segments use `phaseTrackOff`.
  - The current segment is 16 dp tall and the others are 10 dp, all bottom-aligned.
  - The labels QUEUE / ON DECK / ON FIELD / MATCH sit underneath in condensed caps at 16 sp. The current one is shown in its phase color with a "◂" marker.
  - Screen-reader description: "<Phase>, step N of 4".
  - **Phase mapping:** PRE_QUEUE means no segment is current. QUEUEING, ON_DECK and ON_FIELD map to segments 1–3. "Match" (segment 4) is never current, because the app has no "playing" phase.
- **`AllianceBands(red, blue)`**: full-width rows, 12 dp × 20 dp padding.
  - Teams in condensed 26 sp Bold on white text. Our team is a white chip with alliance-colored text.
  - Σ OPR is right-aligned at 15 sp SemiBold and hidden when unknown.
- **`AllianceLine(color, line, trailing)`**: the compact row version.
  - A 6 dp × full-height rounded stripe in the alliance color.
  - Teams in condensed 20 sp Bold; our team is a `primary` chip with `onPrimary` text.
  - A `trailing` slot holds Σ OPR or a score.
- **`TeamChip`** semantics: "Your team, 5507".
- **`StatusPill(text, bg, fg)`**: condensed 800 weight, letter-spacing 1 sp, fully rounded. Used for the phase and for WIN/LOSS/TIE.
- **`SectionLabel(text)`**.

## 3. Screens

### Matches
- **Header** replaces the top app bar:
  - The event name in condensed 26 sp Bold caps.
  - The `Status.text` line below it in 14 sp `onSurfaceVariant`.
  - A 44 dp round tonal button that opens the event picker.
- **Hero `ScoreboardCard`** merges today's `StatusCard` and `NextMatchCard`. When there is a next match it shows, in order:
  1. The label in condensed 44 sp ExtraBold caps ("QUAL 36") and a phase `StatusPill`.
  2. `countdownHero`, then a 18 sp line with the countdown target and "~time start".
  3. `PhaseTimeline`.
  4. `AllianceBands`.
  5. A 56 dp full-width START LIVE TRACKING button: `primary`, condensed 22 sp ExtraBold. STOP is the outlined version.

  With no next match, the card shows only the status text and the tracking button.
- **Below the hero**, in order:
  1. "NOW QUEUING · QUAL 38" in condensed 20 sp Bold `primary`.
  2. The "Nexus unavailable" note, small and muted.
  3. Errors, in an `errorContainer` card.
- **Day groups.** A `SectionLabel` (the day), then upcoming rows. Each row is a 16 dp-rounded card with 14 × 16 dp padding:
  - Top line: an alliance dot plus the label in condensed 28 sp ExtraBold, and the time at 18 sp on the right.
  - Below: two `AllianceLine`s with Σ OPR.
- **Breaks** are centered "— LUNCH 12:02 – 1:02 —" lines in condensed 17 sp Bold, letter-spaced, muted.
- **Results.** A `SectionLabel("LAST")`, then result rows:
  - Top line: the label and an outcome `StatusPill`.
  - Below: two `AllianceLine`s, each with its score in condensed 22 sp ExtraBold. The winner is full-strength `onSurface` and the loser is muted (60 % alpha).
- **Bottom nav.** The same three tabs. The selected tab gets a 16 dp-rounded `secondaryContainer` pill and a `primary` icon.

### Pit map
- Header ("PIT MAP", event name and pit count).
- An "OUR PIT" card: the pit name in condensed 64 sp ExtraBold, and our team as a `primary` chip.
- The map card:
  - Our pit is filled with `primary` and labeled in `onPrimary`.
  - Other pits use `surfaceContainer` fill and an `outline` stroke.
  - Labels use the condensed font.
  - Landmarks are shown as rounded `surfaceContainerHigh` bars.
- Geometry, gestures and label layout are unchanged.

### Settings, event picker, setup
- The same header style, `SectionLabel` group titles, and groups as 20 dp-rounded `ScoreboardCard`s.
- Standard Material controls, which pick up the accent automatically. Primary buttons follow the hero button style.
- Event picker rows: the name in condensed Bold, the dates in body text, and a 4 dp `primary` bar on the left edge of the current event.

## 4. Model change

`MatchListModel.Result` gains `red: AllianceLine`, `blue: AllianceLine`, `redScore: Int` and `blueScore: Int`. The existing `ourScore`, `theirScore` and `outcome` are kept, because the widget and notification use them. `AllianceLine` for results carries `summedOpr = null`.

## 5. Widget (Glance)

- **Fonts.** Glance `Text` can't load bundled fonts, so labels use `FontFamily("sans-serif-condensed")` in Bold or ExtraBold weights.
- **Countdown.** The countdown `Chronometer` in `widget_countdown.xml` gets `android:fontFamily="@font/barlow_condensed_bold"` and 56 sp (it drops to 40 sp below 180 dp of height).
  - If the launcher doesn't apply the bundled font, it falls back to `sans-serif-condensed`.
  - Verify this on the emulator first (implementation task 1).
- **Layout**, from top to bottom:
  1. A header in condensed caps, letter-spaced.
  2. The match label in 30 sp ExtraBold and a phase pill.
  3. The countdown with its target.
  4. A 4-segment phase bar (rounded `Box`es, the current one taller, no labels).
  5. Two alliance lines with a 5 dp stripe and our team as a `primary` chip.
  6. A divider.
  7. The upcoming list, one line per match: dot, label in ExtraBold, time.
  8. On tall widgets, "LAST · Q22" with the score and an outcome pill.
- The existing `SizeMode.Exact`, the cap of 10 children per Column and `MAX_LINES` are respected. The phase bar `Row` counts as one child of its Column.

## 6. Live Update notification

A promoted notification must use a standard style: no custom content views and no colorization. Changes are therefore limited to the following:

- **Title**, in caps: "QUAL 36 · RED · ON FIELD", or "… · FINAL".
- **Text** keeps today's information ("3 AWAY · ON FIELD #29", or "W 95–80"), in caps.
  - **Deviation from the chat design:** the design showed the alliance list here. That would replace the more useful matches-away count, so the alliance list is not used.
- **Short chip** ("Q36 4m") is unchanged.
- **Segment colors** come from `StatusColors.notificationSegments`.
- **Accent** stays `system_accent1_600`.

## 7. Testing

All tests run in `(cd android && ./gradlew :core:test :app:testDebugUnitTest)`.

- **Model:** a result's alliances, which alliance is ours, scores per alliance, TIE, and incomplete team lists.
- **Compose:**
  - `PhaseTimeline`: the current step is marked, plus its screen-reader description; PRE_QUEUE has no current step.
  - `AllianceBands` and `AllianceLine`: our team's chip is present with its screen-reader label; OPR is hidden when null.
  - Result rows show the outcome pill and both scores.
  - The hero falls back when there is no next match.
  - Existing screen tests are updated where label text changes case.
- **Glance:** 4 phase segments, alliance lines on the next match, the LAST pill on tall sizes, and no Column over 10 children.
- **Notification:** the title and text are in caps, and the segment colors equal `StatusColors.notificationSegments`.
- **Manual,** on `emulator-5580` with `scripts/fake-api.py`:
  - Every screen in light and dark mode.
  - Two wallpapers.
  - The widget at small and large sizes.
  - Whether the countdown's bundled font loads.
  - Screenshots shared with the user.

## Out of scope

- Animations and transitions.
- Wear OS.
- App icon redesign.
- Changing any polling, scheduling or data behavior.
