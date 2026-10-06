# PitWatch Android — Screens, Widgets & Visual Design (Plan 3)

**Date:** 2026-10-05
**Status:** Draft — awaiting review
**Builds on:** `2026-10-05-android-app-design.md` (functional scope) and the shipped `:core` / `:app` modules.

## Intent

Finish the Android app's user-facing surfaces — match list, event picker, pit map, home-screen widgets — and
do the visual pass that was deferred until the app existed. Success: the app and widgets answer "when do we
play next, and where are we in the queue?" at a glance in a loud, crowded venue.

## Decisions (from the user)

- **Material You native.** Material 3 components, dynamic (wallpaper) color, light/dark following the system.
  The iOS phase colors are kept **only for status** (phase badges, notification progress), never as decoration.
- **Schedule breaks:** use Nexus's explicit `breakAfter` markers when present (with their real names), and fall
  back to the existing gap inference (`ScheduleBreakDetector`) only for events with no markers.
- **Navigation:** Material 3 bottom navigation bar with three destinations — **Matches** (home) · **Pit map** ·
  **Settings**. The event picker opens from the Matches top app bar.
- Out of scope: Wear OS, iOS changes, a start-tracking button on widgets.

## Status colors

| Phase | Color |
|---|---|
| Pre-queue | `#636366` |
| Queueing | `#FF9500` |
| On deck | `#FF6B00` |
| On field | `#30D158` |

Badges use the phase color as container with white/near-black label chosen for contrast in light and dark.
Alliance identity uses red `#FF3B30` / blue `#1E6FFF` dots only.

## App screens

### Matches (home)

Top to bottom:

1. **Top app bar:** event short name (fallback: name, then "PitWatch"); actions: event picker (calendar icon).
   Pull-to-refresh forces a refresh.
2. **Status card:** "Team 5507 · Rank #3 · 5-2-0" (rank/record omitted when unknown); a primary
   **Start live tracking / Stop live tracking** button reflecting whether `LiveMatchService` is tracking;
   "Now queuing: Qualification 38" when Nexus reports it; a quiet "Nexus unavailable — showing TBA times"
   line when a Nexus key is configured but Nexus data is missing.
3. **Error banner** when `RefreshState.lastError` is set.
4. **Upcoming** (team's unplayed matches, chronological), grouped under day headers in the event's zone:
   - The **next match** as an expanded card: label, time (`~` prefix for predicted/estimated), both alliances
     (tracked team bold, Σ OPR per alliance when every team has an OPR), Nexus phase badge, and a relative
     countdown "12m to on deck" from `PhaseDerivation`.
   - Other matches as compact rows: label, time, alliance dot, phase badge when Nexus correlates.
   - **Break rows** between matches: "Lunch · 11:30 – 12:32", "End of day", "Alliance selection".
5. **Results** (played, most recent first): label, both scores, W/L/T for the tracked team.
6. Tapping a match opens `https://www.thebluealliance.com/match/{key}`.
7. Empty states: "No event yet" (with "Pick an event"), "No matches scheduled yet".

### Event picker

The team's events for the season (TBA `/team/frc{n}/events/{year}`), each with name, dates and location; an
**Auto** row first; the current choice checked. Selecting sets `eventKeyOverride` (Auto clears it) and calls
`RefreshWorker.refreshNow`. Loading and error states are shown inline.

### Pit map

The Nexus pit map (`/event/{key}/map`) drawn on a Compose `Canvas`: pits (with team numbers), areas, labels,
walls; arrows as simple lines. Pinch-zoom and pan; the tracked team's pit highlighted (primary color) and
centered on open, with its address ("Pit A1") in a top bar. States: no Nexus key → explanation + link to
Settings; map unavailable → "This event has no pit map on Nexus".

### Settings

The existing screen restyled into Material 3 list sections (Time source, Live tracking, Event, API keys,
Status, About). The "Live Updates are off" hint is shown only when the device supports promotion
(API 36.1+, i.e. `Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1`) and `canPostPromotedNotifications()` is false.

## Home-screen widgets (Jetpack Glance)

One widget with `SizeMode.Responsive` over three sizes; `GlanceTheme` (dynamic color, light/dark). Widgets
render only from the persisted cache — never network.

- **Small (2×2):** team · rank/record; next match label; time + live countdown; phase badge.
- **Medium (4×2):** next match (label, time, countdown, both alliances, Σ OPR) beside the last match
  (label, scores, W/L/T).
- **Large (4×4):** header (team, event, rank/record); next-match card; upcoming list with break rows grouped
  by day (as many rows as fit); last result.
- **Countdown:** an embedded `RemoteViews` `Chronometer` counting down to the phase deadline (or match time),
  so it ticks with no app updates.
- **States:** not configured → "Set up PitWatch"; no upcoming matches → last result + "No upcoming matches";
  off-season → "Next event: {name} · {date}" when known.
- **Updates:** after every refresh with `RefreshOutcome.changed` (worker, live service poll, manual refresh)
  and after config changes, `updateAll`. Tap opens the app on Matches.

## Shared logic (pure, JVM-tested)

- **`ScheduleBreaks.forEvent(nexusEvent, zone)`** (`:core`): breaks from `NexusMatch.breakAfter` markers when
  any match has one — each break runs from the marked match's start to the next match's start, named by the
  marker; otherwise `ScheduleBreakDetector.detectBreaks`. `ScheduleBreak` gains an optional `label`.
  `MatchSchedule.upcomingTimeline` uses it.
- **`MatchListModel.build(cache, config, refreshState, tracking, now)`**: status card, now-queuing line,
  Nexus-unavailable flag, error, upcoming items grouped by day (matches + breaks), results.
- **`WidgetModel.build(cache, config, now)`**: content for each widget size and state.
- Live-tracking state for the button: the service publishes `isTracking` via a process-wide `StateFlow`.

## Notification polish

Layout is system-defined; only: small-icon accent color from the Material theme's primary, segment colors
unchanged (phase colors), wording unchanged.

## Deferred minors folded in

From the Plan 2 review, because this plan touches the same code:
- `SetupScreen`: request notification permission and `refreshNow` before the config write; close its
  `HttpClient` in `finally`.
- `MainActivity` `launchMode="singleTop"` (notification taps don't stack activities).
- Promotion hint only on API 36.1+ (above).
- Skip the poll that `registerDefaultNetworkCallback`'s immediate `onAvailable` triggers right after start.

## Testing

- Unit tests: `ScheduleBreaks` (markers incl. all five in the cancmp snapshot, fallback, labels),
  `MatchListModel` and `WidgetModel` against the real cancmp snapshot and synthetic edge cases.
- Robolectric: widgets are updated after a changed refresh and not after an unchanged one.
- Compose UI tests (Robolectric): Matches renders the snapshot (status card, next-match card, a break row, a
  result row); event picker selection sets the override.
- On-device (separate emulator, never the user's attached devices): screenshots of Matches (light and dark),
  pit map, and the three widget sizes.
