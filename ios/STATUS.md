# iOS / watchOS — Paused (2026-10-05)

Development is paused in favor of the Android app (see
`docs/superpowers/specs/2026-10-05-android-app-design.md`). This file records where things were
left so work can resume without rediscovery.

## Working state

- `TBAKit` logic and models are complete and tested: `cd ios/TBAKit && swift test` (85 tests pass).
- iPhone app, home-screen/lock-screen widgets, and Live Activity UI are implemented
  (TBA + Nexus data, schedule breaks, phase chevrons).
- Build: `cd ios && xcodegen generate`, then open `PitWatch.xcodeproj`.

## Last-verified caveats

- The full app was **not** built after the move into `ios/`: the local Xcode install failed to
  load `IDESimulatorFoundation` (fix with `xcodebuild -runFirstLaunch`). The move itself is safe —
  the regenerated `project.pbxproj` was byte-identical — and the last change
  (`BackgroundRefresh.scheduleNext` passing `nexusEvent`, commit 430a19f) was only type-checked
  against the iOS Simulator SDK. Do a real build first when resuming.

## Known gaps (vs. `docs/superpowers/specs/2026-04-07-pitwatch-design.md`)

### Watch app never receives data
- The iPhone app never activates `WCSession` (no delegate/`activate()` in `PitWatch/`), so the
  `activationState == .activated` guard in `BackgroundRefresh.performRefresh` always skips
  `transferUserInfo`.
- Only `eventCache` is sent, never `UserConfig`. The watch has its own App Group container, so
  `config.isConfigured` is always false there and the watch app shows "Set up PitWatch on your
  iPhone" forever.
- The spec's primary path — the watch fetching TBA directly — is not implemented.

### Live Activity lifecycle is incomplete
- Ends **immediately** when the match is played (`endActivity(matchLabel: last.shortLabel)`),
  rather than showing the score for ~15 min ("near match") / ~5 min then rolling ("all day").
- "All day" rolling is not wired: `LiveActivityManager.transitionToNextMatch` is unused.
- No handling of the ~8 h ActivityKit limit (end + restart).
- No stale-state UI: `staleDate` is set, but no view checks `context.isStale` / shows
  "Last updated X min ago".

### The fundamental limitation
Live Activity updates only happen when `BGAppRefreshTask` runs, and iOS decides when that is
(often far later than `earliestBeginDate`). No client-side change fixes this. The real fix is
server push: a small relay receiving **Nexus webhooks** (frc.nexus/api docs — "live event status"
and per-team "match status" webhooks) and sending **ActivityKit push notifications** via APNs
(`pushType: .token` on `Activity.request`, then push updates with the per-activity token).
The same relay could send FCM to Android, and could reuse the Android app's Kotlin `:core` module.

## If resuming, suggested order
1. Fix Xcode, do a full build + run on device.
2. Decide whether the watch app is worth keeping; if so, fix WCSession activation and sync config.
3. Build the push relay before polishing Live Activity lifecycle — it changes how updates arrive.
4. Keep `TBAKit` logic in sync with Android `:core`: both test suites read
   `ios/TBAKit/Tests/TBAKitTests/Fixtures/`, so add new fixtures there.
