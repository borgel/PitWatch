# PitWatch Android — Design Spec

**Date:** 2026-10-05
**Status:** Draft — awaiting review

## Overview

An Android equivalent of the iOS PitWatch app (`ios/`): glanceable FRC match info for a tracked
team from The Blue Alliance (TBA) and FRC Nexus, with a live, lock-screen-visible match status
notification.

The headline improvement over iOS: the live match surface is driven by a **foreground service the
app controls**, so it polls Nexus on our own adaptive cadence instead of waiting for the OS to
grant a background refresh (`BGAppRefreshTask`), which is what makes the iOS Live Activity stale.

## Intent and Constraints

- **Audience / distribution:** the user's own team, **sideloaded** (APK). No Google Play policy
  review, so restricted permissions (`USE_EXACT_ALARM`, `specialUse` FGS) are acceptable.
- **Devices:** **Android 16+ only** (`minSdk 36`). Every device supports promoted Live Update
  notifications; there is no non-promoted fallback layout.
- **Success criteria:**
  - The live notification reflects a Nexus phase change (queue / on deck / on field) within ~1 min.
  - An event day of live tracking does not noticeably drain battery (adaptive polling, below).
  - Functional parity with the iOS phone app and home-screen widgets.
- **Out of scope for v1:** Wear OS app, push relay server (Nexus webhooks → FCM), Statbotics,
  precise visual design (a later pass, once the app exists).

## Approach: Pure-Kotlin Port of TBAKit

`ios/TBAKit` is ported file-by-file into a pure-Kotlin `:core` module. Kotlin Multiplatform was
rejected (would require rewriting the finished iOS core); a server-backed thin client was rejected
for v1 (hosting + outage dependency). Cross-platform consistency comes from **both test suites
asserting against the same fixture JSON**, not from shared code. A future push relay could reuse
`:core` server-side.

## Architecture

```
android/
├── settings.gradle.kts            # :core, :app
├── core/                          # Kotlin/JVM only — no Android dependencies
│   ├── model/                     Match, MatchAlliance, Event, Team, Ranking, EventOPRs,
│   │                              NexusEvent, NexusMatch, PitMap, Phase, ScheduleBreak
│   ├── api/                       TbaClient (If-Modified-Since → NotModified), NexusClient,
│   │                              Endpoints — Ktor client + kotlinx.serialization
│   ├── logic/                     MatchSchedule (incl. upcomingTimeline), PhaseDerivation,
│   │                              ScheduleBreakDetector, NexusMatchMerge, ChangeDetector
│   └── config/                    UserConfig, TimeSource, LiveActivityMode
└── app/                           # Android, Jetpack Compose, Glance
    ├── data/                      Repository, cache persistence
    ├── ui/                        Setup, MatchList, EventPicker, PitMap, Settings
    ├── live/                      LiveMatchService, LiveNotificationBuilder, PollCadence
    ├── schedule/                  RefreshWorker (WorkManager), AutoStartAlarm + receiver
    └── widget/                    Small / Medium / Large Glance widgets
```

### Porting rules for `:core`

- Field names, optionality, and JSON decoding behavior match the Swift models, including
  tolerance of `null` Nexus team slots and Unix-ms Nexus timestamps.
- Every time-dependent function takes `now` as a parameter (as in Swift) — no hidden clock reads.
- UI concerns stay out of `:core`. Swift's `Phase.color` (SwiftUI `Color`) becomes an app-layer
  mapping; `:core` keeps `stateLabel`, `targetLabel`, `glyph`, `nextPhaseProse`.
- `UserConfig.effectiveTimeSource` semantics are preserved: explicit choice, else Nexus if a Nexus
  key is configured, else TBA.

### Data flow

`Repository.refresh(force: Boolean)` is the **only** fetch path — a port of iOS
`BackgroundRefresh.performRefresh` (event auto-detect / override, matches, rankings, OPRs with
If-Modified-Since, Nexus status with silent degradation). It writes the cache and publishes it as
a `StateFlow<EventCache>`; the UI, live notification, and widgets all collect from it.

Callers:

| Caller | When |
|---|---|
| `RefreshWorker` (WorkManager one-time work, re-enqueued after each run) | Delay from `MatchSchedule.refreshInterval` (Nexus-aware), floored at 15 min; daily when no event |
| `LiveMatchService` poll loop | Adaptive cadence while live tracking (below) |
| UI | Pull-to-refresh / Settings "Force refresh" (`force = true`) |

Persistence: `UserConfig` in Jetpack DataStore; `EventCache` and `RefreshState` as JSON files in
app-private storage (same shapes as the iOS App Group files). There is one process, so no App
Group / WatchConnectivity equivalents.

Widgets are updated after every refresh whose result differs from the previous cache. Android has
no WidgetKit-style reload budget, so `ChangeDetector` is only used as a cheap "did anything
visible change" gate.

## Live Match Notification

### Service

`LiveMatchService`: foreground service, type **`specialUse`** (`dataSync` is capped at 6 h/day on
Android 15+, which "all day" mode would exceed).

### Starting

1. **Auto-start:** after each refresh, schedule an exact alarm (`USE_EXACT_ALARM`) at the moment
   `MatchSchedule.shouldStartLiveActivity` would first become true (2 h before match / Nexus
   queue time in "near match" mode; the same function's "all day" rules otherwise). The alarm receiver
   starts the service — exact alarms are a permitted background-FGS-start exemption.
2. **Force start:** button in Settings (parity with iOS).
3. **App launch:** if inside the start window and not suppressed, start the service.

### Poll loop

A coroutine in the service:

- Each tick polls **Nexus**. **TBA** (matches, rankings) is polled at most every 2 min, with
  If-Modified-Since.
- Interval (`PollCadence`, a pure function of cache + config + now):
  - **30 s** when the tracked match's next Nexus phase time is ≤ 10 min away or passed < 2 min ago.
  - **2 min** otherwise, and whenever there is no correlated Nexus data.
- Immediate poll on: screen unlock (`ACTION_USER_PRESENT`), notification tap / "Refresh"
  action, network regained (`ConnectivityManager.NetworkCallback`).
- On failure: keep last good state; back off 30 s → 60 s → 120 s (cap); reset on success.

### Notification content

Promoted Live Update: `Notification.ProgressStyle` + `requestPromotedOngoing(true)`.

| Element | Content | iOS counterpart |
|---|---|---|
| Status-bar chip (`setShortCriticalText`) | e.g. `Q32 12m` | Dynamic Island compact |
| Title | `Q32 · Red · Queuing` (match, alliance, `Phase.stateLabel`) | Lock screen header |
| Text | `3 away · on field Q29` (`matchesAway`, current match on field) | Expanded view |
| Progress | 4 segments (Queue, On deck, On field, Match) colored by phase, points at milestone deadlines | `ChevronBar` |
| Countdown | `setWhen(phase deadline)` + countdown chronometer — ticks without app updates | `Text(.timer)` |
| Subtext when stale | `Updated 6m ago` when last successful poll > 5 min | stale handling |
| Actions | Refresh, Stop | — |

Phase/deadline values come from `PhaseDerivation.derivePhase` exactly as `LiveActivityManager`
does on iOS, falling back to TBA times when Nexus data is absent.

### Stopping

- **Near match mode:** stop 15 min after the tracked match's score posts.
- **All day mode:** roll to the next match; stop after the day's last match. (No 8-hour limit on
  Android, so no end-and-restart.)
- **User Stop action or notification dismissal** (`deleteIntent`): stop, and suppress auto-start
  until the next match.
- **Promotion revoked by user:** the same notification still posts as a normal ongoing
  notification; Settings shows a hint when `canPostPromotedNotifications()` is false.

## UI (functional scope)

Visual design is deliberately deferred; v1 uses functional Material 3 with the iOS color tokens
(alliance red/blue, phase colors).

- **Setup:** TBA API key, team number (validated via `/team/frc{n}`), optional Nexus key.
- **MatchList:** next match first, inline schedule-break rows (`upcomingTimeline`), Nexus status
  badges, event-level "Now queuing" banner, pull-to-refresh, tap opens the match on TBA.
- **EventPicker:** auto-detected event + manual override from the team's season events.
- **PitMap:** port of iOS `PitMapView` (Nexus `/event/{key}/map`).
- **Settings:** time source, queue offset, Live Activity mode, force start, API keys, force
  refresh, last refresh / error status, promotion-permission hint, Nexus attribution.
- **Widgets (Glance):** small, medium, large — same content as the iOS home-screen widget specs.
  No lock-screen widgets (not available on Android phones); the Live Update covers that surface.

Permissions: `POST_NOTIFICATIONS` and Live Update promotion requested at the end of Setup.

## Error Handling

- Nexus failures never block TBA data (silent degradation, as on iOS); a fallback indicator shows
  only when a Nexus key is configured but data is unavailable.
- TBA `304 Not Modified` → no parse, no widget update.
- Refresh errors are recorded in `RefreshState.lastError` and shown in Settings.
- The live notification never blanks on error — it shows last good state plus the stale subtext.

## Testing

- **`:core`:** port all 85 Swift tests to JUnit 5. Gradle's test resources point directly at
  `ios/TBAKit/Tests/TBAKitTests/Fixtures/` — one copy of fixtures, two suites asserting against it.
- **`:app` unit tests:** `PollCadence`, auto-start alarm time, and stop decisions as pure
  functions of `(cache, config, now)`.
- **Robolectric:** build the live notification for representative states; assert progress
  segments/points, short critical text, chronometer target, stale subtext.
- **Manual on-device check:** a small local fake Nexus server replaying the two captured snapshots
  in `scripts/fixtures/2026cancmp/` to observe phase transitions end to end.

## Future

- Nexus webhook relay → FCM (Android) and APNs Live Activity push (iOS), reusing `:core`.
- Wear OS tile/complication.
- Visual design pass on notification, widgets, and screens.
