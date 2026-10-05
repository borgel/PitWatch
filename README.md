# PitWatch

Glanceable FRC match info (next match, queue status, rankings) from The Blue Alliance and FRC Nexus.

## Layout

```
ios/        iOS + watchOS app (XcodeGen: `cd ios && xcodegen generate`)
  TBAKit/   Shared Swift logic + tests (`cd ios/TBAKit && swift test`)
android/    Android app (planned — see docs/superpowers/specs)
scripts/    Platform-agnostic tooling (fixture capture)
docs/       Design specs and implementation plans
```

Curated test fixtures live in `ios/TBAKit/Tests/TBAKitTests/Fixtures/` (SwiftPM requires
resources inside the package). Other platforms should reference that directory directly
rather than copying it, so both test suites assert against the same data.

# Future
- Stats from Statbotics
