# PitWatch

Glanceable FRC match info (next match, queue status, rankings) from The Blue Alliance and FRC Nexus.

## Layout

```
ios/        iOS + watchOS app — PAUSED, see ios/STATUS.md (XcodeGen: `cd ios && xcodegen generate`)
  TBAKit/   Shared Swift logic + tests (`cd ios/TBAKit && swift test`)
android/    Android app (Gradle). :core = Kotlin port of TBAKit; :app = Matches/Pit map/Settings,
            live notification, home-screen widget (`cd android && ./gradlew :core:test :app:testDebugUnitTest`;
            manual test: docs/android-manual-test.md)
scripts/    Fixture capture + fake-api.py (local TBA/Nexus replay server)
docs/       Design specs and implementation plans
```

Curated test fixtures live in `ios/TBAKit/Tests/TBAKitTests/Fixtures/` (SwiftPM requires
resources inside the package). Other platforms should reference that directory directly
rather than copying it, so both test suites assert against the same data.

# Future
- Stats from Statbotics

## Android: deliberate differences from iOS

- **Event days are event-local.** `Event.isActive` runs from local midnight of the start date to local midnight
  after the end date in the event's own time zone (iOS used UTC days, which ended west-coast events ~5 PM local).
  Event auto-detection uses the same local-midnight boundaries, and a finished event is re-detected instead of
  sticking forever.
- **Decoding is lenient.** Fields Swift required (`videos`, `surrogate_team_keys`, `dq_team_keys`, `dq`,
  `sort_order_info`, Nexus `redTeams`/`blueTeams`/`times`) default when missing; an explicit `null` in a defaulted
  field decodes as the default. One odd record no longer drops a whole response.
- **Nexus labels tolerate extra whitespace** when matching TBA matches (iOS fell back to team matching).
- **All-day live tracking opens 2 h before the first Nexus queue time**, like near-match mode (iOS had no lead
  limit, so it re-armed for tomorrow right after the last match).
- **Breaks come from Nexus's `breakAfter` markers** (named), with gap inference only as a fallback.
- **Pit-map positions are box centers** (the real event map tiles exactly that way; iOS read them as corners).
- **Times display in the phone's zone**, labeled (e.g. "JST") when it differs from the event's.
