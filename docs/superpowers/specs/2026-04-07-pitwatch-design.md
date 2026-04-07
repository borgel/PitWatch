# PitWatch — TBA iOS Widget App Design Spec

## Overview

PitWatch is an iOS/watchOS app that provides homescreen widgets, lock screen widgets, and watch complications showing real-time FRC match information from The Blue Alliance (TBA) API. Users configure a team number, and every glanceable surface shows what matters most: **when is the next match, and how is the team doing?**

## Target Platforms

- iOS 18+
- watchOS (companion, matching iOS deployment target)

## Core User Story

A user (FRC team member, mentor, parent, or fan) enters their TBA API key and a team number. The app auto-detects the active or next upcoming event for that team. Widgets across all Apple surfaces show the next match countdown, recent results, rankings, and alliance strength — updated adaptively based on the match schedule.

---

## Architecture

### Approach: Shared Swift Package with Platform-Specific Shells

All TBA API client code, data models, and shared logic live in a local Swift Package (`TBAKit`). Each platform target imports it and wires up its own UI.

### Project Structure

```
tba-ios-widget/
├── TBAKit/                              # Local Swift Package
│   ├── Package.swift
│   ├── Sources/TBAKit/
│   │   ├── API/
│   │   │   ├── TBAClient.swift          # HTTP client, auth, If-Modified-Since caching
│   │   │   └── Endpoints.swift          # Typed endpoint paths
│   │   ├── Models/
│   │   │   ├── Team.swift
│   │   │   ├── Event.swift
│   │   │   ├── Match.swift
│   │   │   ├── Ranking.swift
│   │   │   ├── Alliance.swift
│   │   │   └── EventOPRs.swift
│   │   ├── Store/
│   │   │   ├── TBADataStore.swift       # Read/write App Group shared data
│   │   │   ├── MatchSchedule.swift      # Next match, last match, adaptive timing logic
│   │   │   └── ChangeDetector.swift     # Compare fetched vs cached, decide if widgets need reload
│   │   ├── LiveActivity/
│   │   │   └── LiveActivityManager.swift # Start/update/end Live Activity lifecycle
│   │   └── Config/
│   │       └── UserConfig.swift         # Team number, API key, event override, time prefs, queue offset
│   └── Tests/TBAKitTests/
├── PitWatch/                            # iOS App target
│   ├── PitWatchApp.swift
│   ├── Views/
│   │   ├── SetupView.swift              # API key + team number entry
│   │   ├── EventPickerView.swift        # Auto-detected + manual override
│   │   ├── MatchListView.swift          # Scrollable match list (pull-to-refresh)
│   │   └── SettingsView.swift           # Time source, queue offset, API key, refresh status
│   ├── LiveActivity/
│   │   ├── MatchLiveActivity.swift          # ActivityAttributes + content state definitions
│   │   ├── LiveActivityLockScreenView.swift # Expanded lock screen presentation
│   │   └── DynamicIslandViews.swift         # Compact, minimal, expanded Dynamic Island views
│   └── Background/
│       └── BackgroundRefresh.swift      # BGTaskScheduler polling
├── PitWatchWidgets/                     # iOS WidgetKit extension
│   ├── NextMatchWidget.swift            # Widget definition, all families
│   ├── MatchTimelineProvider.swift      # Adaptive timeline generation
│   └── WidgetViews/
│       ├── SmallWidgetView.swift
│       ├── MediumWidgetView.swift
│       ├── LargeWidgetView.swift
│       └── LockScreenWidgetView.swift
├── PitWatchWatch/                       # watchOS App
│   ├── PitWatchWatchApp.swift
│   ├── MatchListWatchView.swift
│   └── ConnectivityManager.swift        # WatchConnectivity fallback
├── PitWatchWatchWidgets/                # watchOS WidgetKit complications
│   ├── WatchComplicationProvider.swift
│   └── ComplicationViews.swift
└── PitWatch.xcodeproj
```

### Shared Data via App Group

All targets share an App Group container with these files:

- `team_config.json` — team number, API key, selected event key (if overridden), time source preference (scheduled vs predicted), queue offset minutes
- `event_cache.json` — current event details, all matches for the tracked team, rankings, OPRs
- `last_refresh.json` — timestamp of last successful fetch, status, and stored `Last-Modified` headers per endpoint

---

## TBA API Integration

### Base URL

`https://www.thebluealliance.com/api/v3`

### Authentication

`X-TBA-Auth-Key` header with the user's personal API key (obtained from their TBA account page).

### Endpoints Used

| Endpoint | Purpose |
|---|---|
| `/team/frc{number}` | Validate team number on setup |
| `/team/frc{number}/events/{year}` | List team's events for event auto-detection |
| `/event/{key}` | Event details (name, dates, location, type) |
| `/event/{key}/matches` | All matches — scores, alliances, times, predicted times |
| `/event/{key}/rankings` | Team rankings (W-L-T, rank, matches played) |
| `/event/{key}/oprs` | OPR/DPR/CCWM per team (used for alliance strength) |
| `/event/{key}/teams` | Teams at event (for display names) |
| `/status` | API health check |

### Key Data Fields from Match Model

- `time` (int64, unix) — originally scheduled start time
- `predicted_time` (int64, unix) — TBA's adjusted estimate accounting for schedule drift
- `actual_time` (int64, unix) — when the match actually started (post-match only)
- `comp_level` — qm (quals), qf, sf, f (playoffs)
- `alliances.red.team_keys` / `alliances.blue.team_keys` — which teams are on which alliance
- `alliances.red.score` / `alliances.blue.score` — match scores
- `winning_alliance` — "red", "blue", or "" (tie/unplayed)

### Caching Strategy

**HTTP-level:** All requests include `If-Modified-Since` with the stored `Last-Modified` value from the previous successful response. If TBA returns `304 Not Modified`, no parsing or widget update occurs.

**Data-level change detection:** When TBA returns `200` with new data, compare against the cached version before triggering widget reloads. Only reload timelines if widget-visible data changed:
- A match score was posted or changed
- The next match's `predicted_time` shifted by more than 5 minutes
- The team's ranking changed
- Alliance composition changed

This two-layer approach conserves the WidgetKit reload budget for meaningful updates.

---

## User Configuration

### Setup Flow (First Launch)

1. API key entry — text field with a link to the TBA account page for key generation
2. Team number entry — validated by hitting `/team/frc{number}` to confirm the team exists
3. Auto-fetch of the team's events for the current season

### Settings

| Setting | Options | Default |
|---|---|---|
| Time source | Scheduled / Predicted | Predicted |
| Queue offset | 0–60 min in 5-min increments | 0 (off) |
| Live Activity mode | All day / Near match (2 hr window) | Near match |
| Start Live Activity | Button (force-start immediately) | — |
| API key | View / change / clear | — |
| Force refresh | Button | — |

### Event Selection

**Auto-detect:** On each refresh, check the team's event list. Select the event whose `start_date` ≤ today ≤ `end_date`. If no event is active, select the next upcoming event (for preview). If no future events, show the most recent past event.

**Manual override:** User can tap to switch to any event from their team's event list for the current season. Override persists until the user clears it or the overridden event ends (then reverts to auto-detect).

---

## Widget Design

### Philosophy: Next Match Focused

The primary question across all surfaces is **"When do I play next?"** with secondary context of **"How are we doing?"** and **"What just happened?"**

### Time Display

- Use `predicted_time` or `time` based on user's time source setting
- When using predicted time, prefix with tilde: `~2:35 PM`
- When queue offset is 0: countdown label says "to match"
- When queue offset > 0: subtract offset from match time, countdown label says "to queue"
- Show the absolute time below the countdown

### Alliance Color Indicators

The tracked team's alliance color (red/blue) is shown on every surface:
- Circular widgets: colored border + colored dot emoji next to match number
- Small widget: colored dot next to team number in header
- Medium/large widgets: colored dots on match rows indicating which alliance the team is on
- Tracked team number is **bolded** wherever it appears in an alliance list

### Lock Screen — Circular

- Match number with alliance color dot (e.g., 🔴 Q32)
- Large countdown (e.g., 47m / 27m)
- Label: "to match" or "to queue"
- Absolute time below (~2:35 PM)

### Lock Screen — Rectangular

- Match number + countdown on first line
- Both alliance lines with team numbers (tracked team bolded)
- Rank + record on bottom line

### Home Screen — Small (2×2)

- Header: team number with alliance color dot, rank + record
- Center: "NEXT MATCH" label, match number (large), time + countdown
- Footer: event name

### Home Screen — Medium (4×2)

- Header: team number, rank + record
- Left card: Next match — match number, time, both alliance lines with team numbers, **summed alliance OPR** for each alliance
- Right card: Last match — match number, red vs blue scores, win/loss indicator

### Home Screen — Large (4×4)

- Header: team number, event name, rank + record
- Highlighted next match card: match number, time, both alliance lines with team numbers + summed alliance OPR
- Upcoming section: next 2 matches with match numbers, alliance color dots, estimated times
- Recent results section: last 3 matches with match numbers, alliance color dots, scores, win/loss

### Alliance OPR Display

On medium and large widgets, show **summed OPR** for each alliance in the next match. Calculated by summing the individual OPR values from `/event/{key}/oprs` for the 3 teams on each alliance. Displayed as "Σ OPR 68.4" next to the alliance line.

**Graceful degradation:** OPR data requires several matches to be played before TBA calculates it. When OPR data is unavailable (early in an event or if the endpoint returns empty), simply omit the OPR display — don't show zeros or placeholders. The widget layout should work with or without the OPR column.

---

## Live Activity (ActivityKit)

### Overview

A Live Activity provides a persistent, frequently-updated view on the lock screen and Dynamic Island. Unlike widgets, Live Activity updates do **not** consume the WidgetKit timeline reload budget. Additionally, iOS gives the app higher background execution priority when a Live Activity is active, improving BGTaskScheduler reliability for all data fetching.

### Live Activity Mode Setting

The user chooses when Live Activities auto-start:

- **Near match (default):** Live Activity starts automatically when the next match is within 2 hours. Ends ~15 minutes after match result is posted (so the user sees the score). If the team has another match within 2 hours, a new Live Activity starts immediately.
- **All day:** Live Activity starts when the first match of the day is within 2 hours and persists throughout the event day, rolling from match to match. Ends when no more matches are scheduled for the day or the event day concludes. The Live Activity transitions between states (countdown → in progress → result → countdown to next) without the user needing to interact.
- **Force start button:** If the user dismissed a Live Activity or wants one outside the auto-start window, a button in the app immediately starts a Live Activity for the current/next match.

### ActivityAttributes

```
MatchActivityAttributes (static context):
  - teamNumber: Int
  - eventName: String
  - matchKey: String
  - matchLabel: String (e.g., "Qual 32")
  - compLevel: String
  - redTeams: [String]      (team numbers)
  - blueTeams: [String]
  - trackedAllianceColor: "red" | "blue"

ContentState (dynamic, updated via Activity.update()):
  - matchTime: Date?          (scheduled or predicted, per user setting)
  - queueTime: Date?          (matchTime minus queue offset, nil if offset is 0)
  - redScore: Int?            (nil until match is scored)
  - blueScore: Int?
  - winningAlliance: String?  (nil until match is scored)
  - redAllianceOPR: Double?   (nil if OPR data unavailable)
  - blueAllianceOPR: Double?
  - matchState: .upcoming | .imminent | .inProgress | .completed
  - rank: Int?
  - record: String?           (e.g., "5-2-0")
```

### Live Activity Lifecycle

1. **Auto-start trigger:** BGTaskScheduler or foreground app detects the next match is within the configured window (2 hours for "near match", or start-of-day for "all day"). Calls `Activity.request()` with the match's static attributes and initial content state.
2. **Countdown phase:** Each BGTask fire or foreground refresh calls `Activity.update()` with the latest predicted/scheduled time. The countdown ticks via SwiftUI's `Text(.date, style: .timer)` which updates in real-time without needing app updates.
3. **In-progress phase:** Once `matchTime` passes and no `actual_time` is posted yet, state transitions to `.inProgress`. Display shifts from countdown to "Match in progress."
4. **Completed phase:** When scores are posted (`actual_time` is set and scores are non-nil), update with final scores and win/loss. State becomes `.completed`.
5. **Transition or end:**
   - **All day mode:** After showing the result for ~5 minutes, if another match exists for the team today, seamlessly start a new Live Activity for the next match. If no more matches today, end the activity.
   - **Near match mode:** After showing the result for ~15 minutes, end the activity. A new one will auto-start when the next match enters the 2-hour window.
6. **Stale handling:** If a Live Activity has not been updated in 30 minutes (e.g., phone lost connectivity), iOS marks it as stale. The view should show a "Last updated X min ago" indicator in this state.
7. **Force start:** The app button calls `Activity.request()` immediately for the current/next match, regardless of the auto-start window.

### Dynamic Island Views

**Compact (minimal pill):**
- Leading: alliance color dot + match label (🔴 Q32)
- Trailing: countdown or score (47m / 87-72)

**Minimal (single side when sharing with another Live Activity):**
- Alliance color dot + countdown (🔴 47m)

**Expanded (long-press on Dynamic Island):**
- Match label + countdown/time
- Both alliance lines with team numbers (tracked team bolded) + summed OPR
- After completion: scores + win/loss indicator
- Rank + record at bottom

### Lock Screen Expanded View

The lock screen presentation shows more detail than the Dynamic Island:

- **Header:** Match label + event name
- **Countdown/status:** Large countdown with "to match"/"to queue" label, or "In progress", or final score
- **Alliances:** Both alliance lines with all team numbers + summed OPR (tracked team bolded, alliance color indicators)
- **Footer:** Rank + record
- After completion: prominent score display with win/loss, replacing the countdown area

### 8-Hour Limit

ActivityKit enforces a maximum ~8 hours for active Live Activities. For "all day" mode on a long event day:
- If approaching the 8-hour limit, end the current activity and immediately start a new one
- The transition is near-seamless — the user briefly sees the activity end animation before the new one appears

---

## Watch App & Complications

### Watch Complications (WidgetKit)

**Circular:** Alliance color border + dot, match number, countdown, same "to match"/"to queue" labeling as iOS.

**Rectangular:** Match number + countdown on first line, rank + record on second line.

### Watch App

Minimal match list view — same concept as iOS MatchListView but adapted for the watch form factor:
- Next match highlighted at top
- Past results listed below
- No settings on watch — all configuration done on iOS, synced via WatchConnectivity

### Watch Data Strategy

- **Primary:** Watch fetches from TBA directly when it has connectivity (Wi-Fi or cellular), using the shared `TBAKit` client
- **Fallback:** iOS app pushes latest data via WatchConnectivity `transferUserInfo` when phone is nearby
- Both paths write to the watch's App Group container for complication access

---

## iOS App Views

### MatchListView (Main Companion View)

- Scrollable list of all matches at the current event for the tracked team
- Upcoming matches at top, past results below
- Each row shows: match number, alliance color, all 6 team numbers, scores (if played), OPRs
- **Pull-to-refresh** triggers a force refresh (bypasses `If-Modified-Since`, always fetches + reloads timelines)
- **Tapping a match** opens `https://www.thebluealliance.com/match/{match_key}` in the system browser

### SettingsView

- Time source toggle (Scheduled / Predicted)
- Queue offset picker (0–60 min, 5-min increments)
- Live Activity mode toggle (All day / Near match)
- Start Live Activity button (force-start immediately for current/next match)
- API key field (change / clear)
- Force refresh button
- Last refresh timestamp + status display

---

## Refresh Strategy

### Adaptive Timeline Refresh (WidgetKit)

The `MatchTimelineProvider` builds timelines with reload policies based on proximity to the next match:

| Situation | Reload interval |
|---|---|
| No active event | Once per day |
| Event day, no match within 2 hours | Every 60 minutes |
| Match within 2 hours | Every 30 minutes |
| Match within 30 minutes | Every 15 minutes |
| Match just completed (within 15 min) | Every 10 minutes |

Timeline entries use `.after(date)` reload policy, targeting the next relevant time window.

### BGTaskScheduler (Background App Refresh)

- `BGAppRefreshTask` scheduled to fire aligned with the next match time (adjusted for queue offset)
- On fire: polls TBA with `If-Modified-Since`, runs change detection, only calls `reloadAllTimelines()` if widget-visible data changed
- Also updates the active Live Activity via `Activity.update()` if one is running (Live Activity updates are not budget-limited)
- Starts or ends Live Activities based on the user's Live Activity mode setting and match proximity
- Re-schedules itself for the next relevant window
- When no event is active, schedules once per day
- **Note:** iOS grants higher background execution priority when a Live Activity is active, improving BGTask reliability during match windows

### Force Refresh

Triggered by pull-to-refresh on MatchListView or the force refresh button in settings:
- Skips `If-Modified-Since` — always fetches fresh data
- Always calls `reloadAllTimelines()` regardless of change detection
- Updates last refresh timestamp

---

## Off-Season / No Event State

When no event is active for the tracked team:

- **Widgets:** Show team number + name, last event's final rank and record, next event name and date (if known)
- **Watch complication:** Team number + next event date
- **App MatchListView:** Shows last event's results, event picker shows upcoming events
- **Refresh rate:** Once per day (to detect if a new event has started)

---

## Future Considerations (Not In Scope)

- **Push notifications via backend server (Option C):** A lightweight server polls TBA and sends APNs pushes for near-real-time updates. This unlocks both silent pushes for widget reloads and ActivityKit push notifications for Live Activity updates — enabling near-instant score posting on the lock screen and Dynamic Island without relying on BGTaskScheduler timing.
- **Statbotics EPA integration:** Replace summed OPR with EPA from the Statbotics API for more accurate alliance strength predictions.
- **Control Center widgets:** iOS 18 supports these — could add a quick-glance complication there.
- **Multiple team tracking:** Allow users to configure multiple teams and switch between them or show side-by-side.
