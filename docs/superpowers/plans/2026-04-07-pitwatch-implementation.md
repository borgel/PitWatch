# PitWatch Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an iOS/watchOS app that surfaces FRC match data from The Blue Alliance API via homescreen widgets, lock screen widgets, watch complications, and Live Activities.

**Architecture:** Shared local Swift Package (`TBAKit`) containing API client, models, data store, and business logic. Platform-specific targets (iOS app, widget extension, watch app, watch complication extension) import TBAKit and provide UI. All targets share data via App Group. Xcode project generated via XcodeGen.

**Tech Stack:** Swift 6, SwiftUI, WidgetKit, ActivityKit, WatchConnectivity, BGTaskScheduler, Swift Testing, XcodeGen

**Spec:** `docs/superpowers/specs/2026-04-07-pitwatch-design.md`

---

## File Map

```
tba-ios-widget/
├── project.yml                              # XcodeGen project definition
├── TBAKit/
│   ├── Package.swift
│   ├── Sources/TBAKit/
│   │   ├── API/
│   │   │   ├── TBAClient.swift              # HTTP client with auth + If-Modified-Since
│   │   │   └── Endpoints.swift              # Typed URL path builders
│   │   ├── Models/
│   │   │   ├── Team.swift                   # Team model
│   │   │   ├── Event.swift                  # Event model
│   │   │   ├── Match.swift                  # Match + Alliance + Video models
│   │   │   ├── Ranking.swift                # Ranking + EventRankings + WLTRecord
│   │   │   └── EventOPRs.swift              # OPR/DPR/CCWM data
│   │   ├── Store/
│   │   │   ├── TBADataStore.swift           # Read/write shared App Group JSON files
│   │   │   ├── MatchSchedule.swift          # Derive next/last match, adaptive timing
│   │   │   └── ChangeDetector.swift         # Diff cached vs fetched for widget reload decisions
│   │   ├── LiveActivity/
│   │   │   └── LiveActivityManager.swift    # Start/update/end Live Activity lifecycle
│   │   └── Config/
│   │       └── UserConfig.swift             # User settings (team, API key, event, time prefs, queue offset, LA mode)
│   └── Tests/TBAKitTests/
│       ├── TBAClientTests.swift
│       ├── MatchScheduleTests.swift
│       ├── ChangeDetectorTests.swift
│       ├── UserConfigTests.swift
│       └── Fixtures/
│           ├── matches.json
│           ├── rankings.json
│           └── oprs.json
├── PitWatch/
│   ├── PitWatchApp.swift                    # App entry point, navigation, BGTask registration
│   ├── Views/
│   │   ├── SetupView.swift                  # First-launch: API key + team number
│   │   ├── EventPickerView.swift            # Event list with auto-detect highlight
│   │   ├── MatchListView.swift              # Main view: scrollable match list, pull-to-refresh
│   │   ├── MatchRowView.swift               # Single match row (reused in list)
│   │   └── SettingsView.swift               # All settings
│   └── Background/
│       └── BackgroundRefresh.swift          # BGTaskScheduler setup + handler
├── PitWatchWidgets/
│   ├── PitWatchWidgetBundle.swift           # Widget bundle entry point
│   ├── MatchTimelineProvider.swift          # TimelineProvider with adaptive refresh
│   ├── LiveActivity/
│   │   ├── MatchLiveActivityWidget.swift    # ActivityConfiguration (renders LA UI)
│   │   ├── LiveActivityLockScreenView.swift # Lock screen expanded view
│   │   └── DynamicIslandViews.swift         # Compact, minimal, expanded DI views
│   └── WidgetViews/
│       ├── SmallWidgetView.swift
│       ├── MediumWidgetView.swift
│       ├── LargeWidgetView.swift
│       ├── LockScreenWidgetView.swift
│       └── SharedWidgetComponents.swift     # Alliance line, score display, etc.
├── PitWatchWatch/
│   ├── PitWatchWatchApp.swift               # Watch app entry point
│   ├── MatchListWatchView.swift             # Watch match list
│   └── ConnectivityManager.swift            # WatchConnectivity receive handler
├── PitWatchWatchWidgets/
│   ├── PitWatchWatchWidgetBundle.swift      # Watch widget bundle
│   ├── WatchComplicationProvider.swift      # Watch TimelineProvider
│   └── ComplicationViews.swift              # Circular + rectangular complication views
├── Shared/
│   └── AppGroupConstants.swift              # App Group ID, shared container URL
└── Assets.xcassets/                         # App icon, accent color
```

---

### Task 1: Project Scaffolding — TBAKit Package + XcodeGen

**Files:**
- Create: `TBAKit/Package.swift`
- Create: `TBAKit/Sources/TBAKit/TBAKit.swift` (namespace placeholder)
- Create: `TBAKit/Tests/TBAKitTests/TBAKitTests.swift` (smoke test)
- Create: `project.yml`
- Create: `Shared/AppGroupConstants.swift`

- [ ] **Step 1: Install XcodeGen if needed**

Run: `which xcodegen || brew install xcodegen`
Expected: Path to xcodegen binary

- [ ] **Step 2: Create TBAKit Swift Package**

Create `TBAKit/Package.swift`:

```swift
// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TBAKit",
    platforms: [
        .iOS(.v18),
        .watchOS(.v11)
    ],
    products: [
        .library(name: "TBAKit", targets: ["TBAKit"]),
    ],
    targets: [
        .target(name: "TBAKit"),
        .testTarget(name: "TBAKitTests", dependencies: ["TBAKit"]),
    ]
)
```

Create `TBAKit/Sources/TBAKit/TBAKit.swift`:

```swift
// TBAKit — shared library for The Blue Alliance API
// This file intentionally minimal; public API is in submodules.
```

- [ ] **Step 3: Create TBAKit smoke test**

Create `TBAKit/Tests/TBAKitTests/TBAKitTests.swift`:

```swift
import Testing
@testable import TBAKit

@Test func tbaKitImports() {
    // Verify the package builds and imports correctly
    #expect(true)
}
```

- [ ] **Step 4: Verify TBAKit builds and tests pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test`
Expected: "Test Suite passed"

- [ ] **Step 5: Create App Group constants**

Create `Shared/AppGroupConstants.swift`:

```swift
import Foundation

enum AppGroup {
    static let identifier = "group.com.pitwatch.shared"

    static var containerURL: URL {
        FileManager.default.containerURL(
            forSecurityApplicationGroupIdentifier: identifier
        )!
    }

    static var configURL: URL { containerURL.appendingPathComponent("team_config.json") }
    static var eventCacheURL: URL { containerURL.appendingPathComponent("event_cache.json") }
    static var lastRefreshURL: URL { containerURL.appendingPathComponent("last_refresh.json") }
}
```

- [ ] **Step 6: Create XcodeGen project.yml**

Create `project.yml`:

```yaml
name: PitWatch
options:
  bundleIdPrefix: com.pitwatch
  deploymentTarget:
    iOS: "18.0"
    watchOS: "11.0"
  xcodeVersion: "16.0"
  generateEmptyDirectories: true

packages:
  TBAKit:
    path: TBAKit

settings:
  base:
    SWIFT_VERSION: "6.0"
    ENABLE_USER_SCRIPT_SANDBOXING: false

targets:
  PitWatch:
    type: application
    platform: iOS
    sources:
      - path: PitWatch
      - path: Shared
    dependencies:
      - package: TBAKit
    settings:
      base:
        INFOPLIST_KEY_UIApplicationSupportsIndirectInputEvents: true
        INFOPLIST_KEY_UILaunchScreen_Generation: true
        INFOPLIST_KEY_NSSupportsLiveActivities: true
        ASSETCATALOG_COMPILER_APPICON_NAME: AppIcon
    entitlements:
      path: PitWatch/PitWatch.entitlements
      properties:
        com.apple.security.application-groups:
          - group.com.pitwatch.shared

  PitWatchWidgets:
    type: app-extension
    platform: iOS
    sources:
      - path: PitWatchWidgets
      - path: Shared
    dependencies:
      - package: TBAKit
    settings:
      base:
        INFOPLIST_KEY_NSExtension_NSExtensionPointIdentifier: com.apple.widgetkit-extension
    entitlements:
      path: PitWatchWidgets/PitWatchWidgets.entitlements
      properties:
        com.apple.security.application-groups:
          - group.com.pitwatch.shared

  PitWatchWatch:
    type: application
    platform: watchOS
    sources:
      - path: PitWatchWatch
      - path: Shared
    dependencies:
      - package: TBAKit
      - target: PitWatchWatchWidgets
    settings:
      base:
        INFOPLIST_KEY_WKCompanionAppBundleIdentifier: com.pitwatch.PitWatch
    entitlements:
      path: PitWatchWatch/PitWatchWatch.entitlements
      properties:
        com.apple.security.application-groups:
          - group.com.pitwatch.shared

  PitWatchWatchWidgets:
    type: app-extension
    platform: watchOS
    sources:
      - path: PitWatchWatchWidgets
      - path: Shared
    dependencies:
      - package: TBAKit
    settings:
      base:
        INFOPLIST_KEY_NSExtension_NSExtensionPointIdentifier: com.apple.widgetkit-extension
    entitlements:
      path: PitWatchWatchWidgets/PitWatchWatchWidgets.entitlements
      properties:
        com.apple.security.application-groups:
          - group.com.pitwatch.shared

schemes:
  PitWatch:
    build:
      targets:
        PitWatch: all
        PitWatchWidgets: all
    run:
      config: Debug
    test:
      config: Debug

  PitWatchWatch:
    build:
      targets:
        PitWatchWatch: all
        PitWatchWatchWidgets: all
    run:
      config: Debug
```

- [ ] **Step 7: Create minimal entry points for all targets**

Create `PitWatch/PitWatchApp.swift`:

```swift
import SwiftUI

@main
struct PitWatchApp: App {
    var body: some Scene {
        WindowGroup {
            Text("PitWatch")
        }
    }
}
```

Create `PitWatch/PitWatch.entitlements` (empty entitlements plist — XcodeGen populates it from project.yml):

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict/>
</plist>
```

Create `PitWatchWidgets/PitWatchWidgetBundle.swift`:

```swift
import SwiftUI
import WidgetKit

@main
struct PitWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextMatchWidget()
    }
}

struct NextMatchWidget: Widget {
    let kind = "NextMatchWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: PlaceholderProvider()) { entry in
            Text("PitWatch")
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge,
                            .accessoryCircular, .accessoryRectangular])
    }
}

struct PlaceholderProvider: TimelineProvider {
    func placeholder(in context: Context) -> SimpleEntry { SimpleEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (SimpleEntry) -> Void) {
        completion(SimpleEntry(date: .now))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<SimpleEntry>) -> Void) {
        completion(Timeline(entries: [SimpleEntry(date: .now)], policy: .after(.now.addingTimeInterval(3600))))
    }
}

struct SimpleEntry: TimelineEntry {
    let date: Date
}
```

Create `PitWatchWidgets/PitWatchWidgets.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict/>
</plist>
```

Create `PitWatchWatch/PitWatchWatchApp.swift`:

```swift
import SwiftUI

@main
struct PitWatchWatchApp: App {
    var body: some Scene {
        WindowGroup {
            Text("PitWatch")
        }
    }
}
```

Create `PitWatchWatch/PitWatchWatch.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict/>
</plist>
```

Create `PitWatchWatchWidgets/PitWatchWatchWidgetBundle.swift`:

```swift
import SwiftUI
import WidgetKit

@main
struct PitWatchWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        WatchNextMatchWidget()
    }
}

struct WatchNextMatchWidget: Widget {
    let kind = "WatchNextMatchWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WatchPlaceholderProvider()) { entry in
            Text("PW")
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryCorner])
    }
}

struct WatchPlaceholderProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchSimpleEntry { WatchSimpleEntry(date: .now) }
    func getSnapshot(in context: Context, completion: @escaping (WatchSimpleEntry) -> Void) {
        completion(WatchSimpleEntry(date: .now))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchSimpleEntry>) -> Void) {
        completion(Timeline(entries: [WatchSimpleEntry(date: .now)], policy: .after(.now.addingTimeInterval(3600))))
    }
}

struct WatchSimpleEntry: TimelineEntry {
    let date: Date
}
```

Create `PitWatchWatchWidgets/PitWatchWatchWidgets.entitlements`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict/>
</plist>
```

- [ ] **Step 8: Generate Xcode project and verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate`
Expected: "⚙️  Generating plists... ✅  Created project PitWatch.xcodeproj"

Run: `xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 9: Add xcodeproj to gitignore and commit**

Update `.gitignore`:

```
.superpowers/
PitWatch.xcodeproj/
.build/
DerivedData/
*.xcuserdata
```

Note: The `.xcodeproj` is generated from `project.yml` — it should not be committed. Anyone cloning runs `xcodegen generate`.

Run:
```bash
git add -A && git commit -m "Scaffold project: TBAKit package, XcodeGen config, all 4 targets"
```

---

### Task 2: TBAKit Models

**Files:**
- Create: `TBAKit/Sources/TBAKit/Models/Team.swift`
- Create: `TBAKit/Sources/TBAKit/Models/Event.swift`
- Create: `TBAKit/Sources/TBAKit/Models/Match.swift`
- Create: `TBAKit/Sources/TBAKit/Models/Ranking.swift`
- Create: `TBAKit/Sources/TBAKit/Models/EventOPRs.swift`
- Create: `TBAKit/Tests/TBAKitTests/Fixtures/matches.json`
- Create: `TBAKit/Tests/TBAKitTests/Fixtures/rankings.json`
- Create: `TBAKit/Tests/TBAKitTests/Fixtures/oprs.json`
- Test: `TBAKit/Tests/TBAKitTests/ModelDecodingTests.swift`

- [ ] **Step 1: Write model decoding tests**

Create `TBAKit/Tests/TBAKitTests/Fixtures/matches.json`:

```json
[
  {
    "key": "2026miket_qm32",
    "comp_level": "qm",
    "set_number": 1,
    "match_number": 32,
    "event_key": "2026miket",
    "time": 1712000000,
    "predicted_time": 1712000600,
    "actual_time": null,
    "alliances": {
      "red": {
        "score": -1,
        "team_keys": ["frc1234", "frc5678", "frc9012"],
        "surrogate_team_keys": [],
        "dq_team_keys": []
      },
      "blue": {
        "score": -1,
        "team_keys": ["frc3456", "frc7890", "frc1111"],
        "surrogate_team_keys": [],
        "dq_team_keys": []
      }
    },
    "winning_alliance": "",
    "score_breakdown": null,
    "videos": []
  },
  {
    "key": "2026miket_qm31",
    "comp_level": "qm",
    "set_number": 1,
    "match_number": 31,
    "event_key": "2026miket",
    "time": 1711996400,
    "predicted_time": 1711996500,
    "actual_time": 1711996550,
    "alliances": {
      "red": {
        "score": 87,
        "team_keys": ["frc1234", "frc2222", "frc3333"],
        "surrogate_team_keys": [],
        "dq_team_keys": []
      },
      "blue": {
        "score": 72,
        "team_keys": ["frc4444", "frc5555", "frc6666"],
        "surrogate_team_keys": [],
        "dq_team_keys": []
      }
    },
    "winning_alliance": "red",
    "score_breakdown": null,
    "videos": []
  }
]
```

Create `TBAKit/Tests/TBAKitTests/Fixtures/rankings.json`:

```json
{
  "rankings": [
    {
      "team_key": "frc1234",
      "rank": 3,
      "record": { "wins": 5, "losses": 2, "ties": 0 },
      "qual_average": 82.5,
      "matches_played": 7,
      "dq": 0,
      "sort_orders": [2.14, 82.5]
    }
  ],
  "sort_order_info": [
    { "name": "Ranking Score", "precision": 2 },
    { "name": "Avg Match Score", "precision": 1 }
  ]
}
```

Create `TBAKit/Tests/TBAKitTests/Fixtures/oprs.json`:

```json
{
  "oprs": { "frc1234": 45.2, "frc5678": 12.1, "frc9012": 11.1 },
  "dprs": { "frc1234": 30.5, "frc5678": 18.2, "frc9012": 15.0 },
  "ccwms": { "frc1234": 14.7, "frc5678": -6.1, "frc9012": -3.9 }
}
```

Create `TBAKit/Tests/TBAKitTests/ModelDecodingTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func decodeMatches() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)
    #expect(matches.count == 2)

    let upcoming = matches[0]
    #expect(upcoming.key == "2026miket_qm32")
    #expect(upcoming.compLevel == "qm")
    #expect(upcoming.matchNumber == 32)
    #expect(upcoming.time == 1712000000)
    #expect(upcoming.predictedTime == 1712000600)
    #expect(upcoming.actualTime == nil)
    #expect(upcoming.alliances["red"]?.teamKeys == ["frc1234", "frc5678", "frc9012"])
    #expect(upcoming.alliances["red"]?.score == -1)
    #expect(upcoming.winningAlliance == "")

    let played = matches[1]
    #expect(played.actualTime == 1711996550)
    #expect(played.alliances["red"]?.score == 87)
    #expect(played.winningAlliance == "red")
}

@Test func decodeRankings() throws {
    let data = try fixtureData("rankings")
    let rankings = try JSONDecoder().decode(EventRankings.self, from: data)
    #expect(rankings.rankings.count == 1)
    #expect(rankings.rankings[0].rank == 3)
    #expect(rankings.rankings[0].record?.wins == 5)
    #expect(rankings.rankings[0].record?.losses == 2)
}

@Test func decodeOPRs() throws {
    let data = try fixtureData("oprs")
    let oprs = try JSONDecoder().decode(EventOPRs.self, from: data)
    #expect(oprs.oprs["frc1234"] == 45.2)
    #expect(oprs.dprs["frc1234"] == 30.5)
    #expect(oprs.ccwms["frc1234"] == 14.7)
}

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}
```

Update `TBAKit/Package.swift` to include test fixtures as resources:

```swift
// swift-tools-version: 6.0
import PackageDescription

let package = Package(
    name: "TBAKit",
    platforms: [
        .iOS(.v18),
        .watchOS(.v11)
    ],
    products: [
        .library(name: "TBAKit", targets: ["TBAKit"]),
    ],
    targets: [
        .target(name: "TBAKit"),
        .testTarget(
            name: "TBAKitTests",
            dependencies: ["TBAKit"],
            resources: [.copy("Fixtures")]
        ),
    ]
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -20`
Expected: Compilation errors — `Match`, `EventRankings`, `EventOPRs` not defined

- [ ] **Step 3: Implement all models**

Create `TBAKit/Sources/TBAKit/Models/Team.swift`:

```swift
import Foundation

public struct Team: Codable, Sendable, Identifiable {
    public var id: String { key }
    public let key: String
    public let teamNumber: Int
    public let name: String
    public let nickname: String
    public let city: String?
    public let stateProv: String?
    public let country: String?
    public let website: String?
    public let rookieYear: Int?

    enum CodingKeys: String, CodingKey {
        case key
        case teamNumber = "team_number"
        case name, nickname, city
        case stateProv = "state_prov"
        case country, website
        case rookieYear = "rookie_year"
    }
}
```

Create `TBAKit/Sources/TBAKit/Models/Event.swift`:

```swift
import Foundation

public struct Event: Codable, Sendable, Identifiable {
    public var id: String { key }
    public let key: String
    public let name: String
    public let eventCode: String
    public let eventType: Int
    public let city: String?
    public let stateProv: String?
    public let country: String?
    public let startDate: String
    public let endDate: String
    public let year: Int
    public let shortName: String?
    public let eventTypeString: String?
    public let week: Int?
    public let locationName: String?

    enum CodingKeys: String, CodingKey {
        case key, name
        case eventCode = "event_code"
        case eventType = "event_type"
        case city
        case stateProv = "state_prov"
        case country
        case startDate = "start_date"
        case endDate = "end_date"
        case year
        case shortName = "short_name"
        case eventTypeString = "event_type_string"
        case week
        case locationName = "location_name"
    }

    /// Parse startDate string ("YYYY-MM-DD") to a Date
    public var startDateParsed: Date? {
        Self.dateFormatter.date(from: startDate)
    }

    /// Parse endDate string ("YYYY-MM-DD") to a Date
    public var endDateParsed: Date? {
        Self.dateFormatter.date(from: endDate)
    }

    /// Whether today falls within the event's date range
    public func isActive(on date: Date = .now) -> Bool {
        guard let start = startDateParsed, let end = endDateParsed else { return false }
        let calendar = Calendar.current
        let dayStart = calendar.startOfDay(for: date)
        let eventEnd = calendar.date(byAdding: .day, value: 1, to: end)!
        return dayStart >= calendar.startOfDay(for: start) && dayStart < eventEnd
    }

    private static let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "yyyy-MM-dd"
        f.timeZone = TimeZone(identifier: "UTC")
        return f
    }()
}
```

Create `TBAKit/Sources/TBAKit/Models/Match.swift`:

```swift
import Foundation

public struct Match: Codable, Sendable, Identifiable {
    public var id: String { key }
    public let key: String
    public let compLevel: String
    public let setNumber: Int
    public let matchNumber: Int
    public let eventKey: String
    public let time: Int64?
    public let predictedTime: Int64?
    public let actualTime: Int64?
    public let alliances: [String: Alliance]
    public let winningAlliance: String
    public let scoreBreakdown: [String: AnyCodable]?
    public let videos: [Video]

    enum CodingKeys: String, CodingKey {
        case key
        case compLevel = "comp_level"
        case setNumber = "set_number"
        case matchNumber = "match_number"
        case eventKey = "event_key"
        case time
        case predictedTime = "predicted_time"
        case actualTime = "actual_time"
        case alliances
        case winningAlliance = "winning_alliance"
        case scoreBreakdown = "score_breakdown"
        case videos
    }

    /// Human-readable match label, e.g., "Qual 32", "QF 2-1", "Final 1"
    public var label: String {
        switch compLevel {
        case "qm": return "Qual \(matchNumber)"
        case "qf": return "QF \(setNumber)-\(matchNumber)"
        case "sf": return "SF \(setNumber)-\(matchNumber)"
        case "f": return "Final \(matchNumber)"
        default: return "\(compLevel.uppercased()) \(matchNumber)"
        }
    }

    /// Short label for compact spaces, e.g., "Q32", "QF2-1"
    public var shortLabel: String {
        switch compLevel {
        case "qm": return "Q\(matchNumber)"
        case "qf": return "QF\(setNumber)-\(matchNumber)"
        case "sf": return "SF\(setNumber)-\(matchNumber)"
        case "f": return "F\(matchNumber)"
        default: return "\(compLevel.uppercased())\(matchNumber)"
        }
    }

    /// Whether this match has been played (has actual scores)
    public var isPlayed: Bool {
        actualTime != nil && (alliances["red"]?.score ?? -1) >= 0
    }

    /// The scheduled or predicted time as a Date
    public func matchDate(useScheduled: Bool) -> Date? {
        let timestamp = useScheduled ? time : (predictedTime ?? time)
        guard let ts = timestamp else { return nil }
        return Date(timeIntervalSince1970: TimeInterval(ts))
    }

    /// Which alliance ("red" or "blue") a team is on, or nil
    public func allianceColor(for teamKey: String) -> String? {
        if alliances["red"]?.teamKeys.contains(teamKey) == true { return "red" }
        if alliances["blue"]?.teamKeys.contains(teamKey) == true { return "blue" }
        return nil
    }

    /// Sort order value for ordering matches chronologically
    public var sortOrder: Int {
        let levelOrder: [String: Int] = ["qm": 0, "qf": 1, "sf": 2, "f": 3]
        let level = levelOrder[compLevel] ?? 4
        return level * 1000000 + setNumber * 1000 + matchNumber
    }
}

public struct Alliance: Codable, Sendable {
    public let score: Int
    public let teamKeys: [String]
    public let surrogateTeamKeys: [String]
    public let dqTeamKeys: [String]

    enum CodingKeys: String, CodingKey {
        case score
        case teamKeys = "team_keys"
        case surrogateTeamKeys = "surrogate_team_keys"
        case dqTeamKeys = "dq_team_keys"
    }
}

public struct Video: Codable, Sendable {
    public let type: String
    public let key: String
}

/// Type-erased Codable wrapper for JSON values (used for score_breakdown)
public struct AnyCodable: Codable, Sendable {
    public let value: Any

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let int = try? container.decode(Int.self) { value = int }
        else if let double = try? container.decode(Double.self) { value = double }
        else if let string = try? container.decode(String.self) { value = string }
        else if let bool = try? container.decode(Bool.self) { value = bool }
        else { value = NSNull() }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let v as Int: try container.encode(v)
        case let v as Double: try container.encode(v)
        case let v as String: try container.encode(v)
        case let v as Bool: try container.encode(v)
        default: try container.encodeNil()
        }
    }
}
```

Create `TBAKit/Sources/TBAKit/Models/Ranking.swift`:

```swift
import Foundation

public struct EventRankings: Codable, Sendable {
    public let rankings: [Ranking]
    public let sortOrderInfo: [SortOrderInfo]

    enum CodingKeys: String, CodingKey {
        case rankings
        case sortOrderInfo = "sort_order_info"
    }
}

public struct Ranking: Codable, Sendable {
    public let teamKey: String
    public let rank: Int
    public let record: WLTRecord?
    public let qualAverage: Double?
    public let matchesPlayed: Int
    public let dq: Int
    public let sortOrders: [Double]?

    enum CodingKeys: String, CodingKey {
        case teamKey = "team_key"
        case rank, record
        case qualAverage = "qual_average"
        case matchesPlayed = "matches_played"
        case dq
        case sortOrders = "sort_orders"
    }
}

public struct WLTRecord: Codable, Sendable, Equatable {
    public let wins: Int
    public let losses: Int
    public let ties: Int

    /// "5-2-0" format
    public var display: String { "\(wins)-\(losses)-\(ties)" }
}

public struct SortOrderInfo: Codable, Sendable {
    public let name: String
    public let precision: Int
}
```

Create `TBAKit/Sources/TBAKit/Models/EventOPRs.swift`:

```swift
import Foundation

public struct EventOPRs: Codable, Sendable {
    public let oprs: [String: Double]
    public let dprs: [String: Double]
    public let ccwms: [String: Double]

    /// Sum OPRs for a list of team keys. Returns nil if any team is missing.
    public func summedOPR(for teamKeys: [String]) -> Double? {
        var total = 0.0
        for key in teamKeys {
            guard let opr = oprs[key] else { return nil }
            total += opr
        }
        return total
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/Models/ TBAKit/Tests/ TBAKit/Package.swift
git commit -m "Add TBAKit models: Team, Event, Match, Ranking, EventOPRs with decoding tests"
```

---

### Task 3: TBAKit API Client

**Files:**
- Create: `TBAKit/Sources/TBAKit/API/Endpoints.swift`
- Create: `TBAKit/Sources/TBAKit/API/TBAClient.swift`
- Test: `TBAKit/Tests/TBAKitTests/TBAClientTests.swift`

- [ ] **Step 1: Write API client tests**

Create `TBAKit/Tests/TBAKitTests/TBAClientTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func endpointPaths() {
    #expect(Endpoints.team(number: 1234) == "/team/frc1234")
    #expect(Endpoints.teamEvents(number: 1234, year: 2026) == "/team/frc1234/events/2026")
    #expect(Endpoints.event(key: "2026miket") == "/event/2026miket")
    #expect(Endpoints.eventMatches(key: "2026miket") == "/event/2026miket/matches")
    #expect(Endpoints.eventRankings(key: "2026miket") == "/event/2026miket/rankings")
    #expect(Endpoints.eventOPRs(key: "2026miket") == "/event/2026miket/oprs")
    #expect(Endpoints.eventTeams(key: "2026miket") == "/event/2026miket/teams")
    #expect(Endpoints.match(key: "2026miket_qm32") == "/match/2026miket_qm32")
    #expect(Endpoints.status == "/status")
}

@Test func clientBuildsCorrectRequest() async throws {
    // Verify the client sets the right headers on a request
    let client = TBAClient(apiKey: "test-key-123", baseURL: URL(string: "https://example.com/api/v3")!)
    let request = client.buildRequest(path: "/team/frc1234", lastModified: "Mon, 01 Jan 2026 00:00:00 GMT")
    #expect(request.value(forHTTPHeaderField: "X-TBA-Auth-Key") == "test-key-123")
    #expect(request.value(forHTTPHeaderField: "User-Agent") == "PitWatch")
    #expect(request.value(forHTTPHeaderField: "If-Modified-Since") == "Mon, 01 Jan 2026 00:00:00 GMT")
    #expect(request.url?.absoluteString == "https://example.com/api/v3/team/frc1234")
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -20`
Expected: Compilation errors — `Endpoints`, `TBAClient` not defined

- [ ] **Step 3: Implement Endpoints**

Create `TBAKit/Sources/TBAKit/API/Endpoints.swift`:

```swift
import Foundation

public enum Endpoints {
    public static func team(number: Int) -> String { "/team/frc\(number)" }
    public static func teamEvents(number: Int, year: Int) -> String { "/team/frc\(number)/events/\(year)" }
    public static func event(key: String) -> String { "/event/\(key)" }
    public static func eventMatches(key: String) -> String { "/event/\(key)/matches" }
    public static func eventRankings(key: String) -> String { "/event/\(key)/rankings" }
    public static func eventOPRs(key: String) -> String { "/event/\(key)/oprs" }
    public static func eventTeams(key: String) -> String { "/event/\(key)/teams" }
    public static func match(key: String) -> String { "/match/\(key)" }
    public static let status = "/status"
}
```

- [ ] **Step 4: Implement TBAClient**

Create `TBAKit/Sources/TBAKit/API/TBAClient.swift`:

```swift
import Foundation

/// Result of an API fetch — either new data, or not modified (304)
public enum FetchResult<T: Decodable & Sendable>: Sendable {
    case data(T, lastModified: String?)
    case notModified
}

public final class TBAClient: Sendable {
    public static let defaultBaseURL = URL(string: "https://www.thebluealliance.com/api/v3")!

    private let apiKey: String
    private let baseURL: URL
    private let session: URLSession

    public init(apiKey: String, baseURL: URL = TBAClient.defaultBaseURL, session: URLSession = .shared) {
        self.apiKey = apiKey
        self.baseURL = baseURL
        self.session = session
    }

    /// Build a URLRequest with auth and conditional headers. Exposed for testing.
    public func buildRequest(path: String, lastModified: String? = nil) -> URLRequest {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "X-TBA-Auth-Key")
        request.setValue("PitWatch", forHTTPHeaderField: "User-Agent")
        if let lm = lastModified {
            request.setValue(lm, forHTTPHeaderField: "If-Modified-Since")
        }
        return request
    }

    /// Fetch and decode a resource. Supports If-Modified-Since / 304 Not Modified.
    public func fetch<T: Decodable & Sendable>(
        _ type: T.Type,
        path: String,
        lastModified: String? = nil
    ) async throws -> FetchResult<T> {
        let request = buildRequest(path: path, lastModified: lastModified)
        let (data, response) = try await session.data(for: request)

        guard let http = response as? HTTPURLResponse else {
            throw TBAError.invalidResponse
        }

        if http.statusCode == 304 {
            return .notModified
        }

        guard http.statusCode == 200 else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw TBAError.apiError(statusCode: http.statusCode, message: body)
        }

        let decoded = try JSONDecoder().decode(T.self, from: data)
        let lm = http.value(forHTTPHeaderField: "Last-Modified")
        return .data(decoded, lastModified: lm)
    }

    /// Validate that a team number exists. Returns the team if valid, throws if not.
    public func validateTeam(number: Int) async throws -> Team {
        let result = try await fetch(Team.self, path: Endpoints.team(number: number))
        switch result {
        case .data(let team, _): return team
        case .notModified: throw TBAError.unexpected("Got 304 on team validation")
        }
    }
}

public enum TBAError: Error, LocalizedError {
    case invalidResponse
    case apiError(statusCode: Int, message: String)
    case unexpected(String)

    public var errorDescription: String? {
        switch self {
        case .invalidResponse: return "Invalid server response"
        case .apiError(let code, let msg): return "API error \(code): \(msg)"
        case .unexpected(let msg): return msg
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add TBAKit/Sources/TBAKit/API/ TBAKit/Tests/TBAKitTests/TBAClientTests.swift
git commit -m "Add TBAKit API client with typed endpoints and If-Modified-Since support"
```

---

### Task 4: TBAKit UserConfig

**Files:**
- Create: `TBAKit/Sources/TBAKit/Config/UserConfig.swift`
- Test: `TBAKit/Tests/TBAKitTests/UserConfigTests.swift`

- [ ] **Step 1: Write UserConfig tests**

Create `TBAKit/Tests/TBAKitTests/UserConfigTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func defaultConfig() {
    let config = UserConfig()
    #expect(config.teamNumber == nil)
    #expect(config.apiKey == nil)
    #expect(config.eventKeyOverride == nil)
    #expect(config.useScheduledTime == false)
    #expect(config.queueOffsetMinutes == 0)
    #expect(config.liveActivityMode == .nearMatch)
}

@Test func configRoundTrip() throws {
    var config = UserConfig()
    config.teamNumber = 1234
    config.apiKey = "test-key"
    config.useScheduledTime = true
    config.queueOffsetMinutes = 20
    config.liveActivityMode = .allDay

    let data = try JSONEncoder().encode(config)
    let decoded = try JSONDecoder().decode(UserConfig.self, from: data)
    #expect(decoded.teamNumber == 1234)
    #expect(decoded.apiKey == "test-key")
    #expect(decoded.useScheduledTime == true)
    #expect(decoded.queueOffsetMinutes == 20)
    #expect(decoded.liveActivityMode == .allDay)
}

@Test func isConfigured() {
    var config = UserConfig()
    #expect(config.isConfigured == false)

    config.teamNumber = 1234
    #expect(config.isConfigured == false)

    config.apiKey = "key"
    #expect(config.isConfigured == true)
}

@Test func teamKey() {
    var config = UserConfig()
    #expect(config.teamKey == nil)

    config.teamNumber = 1234
    #expect(config.teamKey == "frc1234")
}

@Test func queueOffset() {
    var config = UserConfig()
    #expect(config.queueOffset == .zero)

    config.queueOffsetMinutes = 20
    #expect(config.queueOffset == .seconds(1200))
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: Compilation errors

- [ ] **Step 3: Implement UserConfig**

Create `TBAKit/Sources/TBAKit/Config/UserConfig.swift`:

```swift
import Foundation

public struct UserConfig: Codable, Sendable {
    public var teamNumber: Int?
    public var apiKey: String?
    public var eventKeyOverride: String?
    public var useScheduledTime: Bool
    public var queueOffsetMinutes: Int
    public var liveActivityMode: LiveActivityMode

    public init() {
        self.teamNumber = nil
        self.apiKey = nil
        self.eventKeyOverride = nil
        self.useScheduledTime = false
        self.queueOffsetMinutes = 0
        self.liveActivityMode = .nearMatch
    }

    public var isConfigured: Bool {
        teamNumber != nil && apiKey != nil && !apiKey!.isEmpty
    }

    public var teamKey: String? {
        guard let number = teamNumber else { return nil }
        return "frc\(number)"
    }

    public var queueOffset: TimeInterval {
        TimeInterval(queueOffsetMinutes * 60)
    }
}

public enum LiveActivityMode: String, Codable, Sendable, CaseIterable {
    case nearMatch
    case allDay
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/Config/ TBAKit/Tests/TBAKitTests/UserConfigTests.swift
git commit -m "Add UserConfig model with team, API key, time source, queue offset, LA mode"
```

---

### Task 5: TBAKit Data Store

**Files:**
- Create: `TBAKit/Sources/TBAKit/Store/TBADataStore.swift`

- [ ] **Step 1: Implement TBADataStore**

This component reads/writes JSON files in the App Group container. Since it depends on the file system and App Group (which isn't available in Swift Package unit tests), we'll implement it and verify via the Xcode build.

Create `TBAKit/Sources/TBAKit/Store/TBADataStore.swift`:

```swift
import Foundation

/// Cached event data shared across all targets via App Group
public struct EventCache: Codable, Sendable {
    public var event: Event?
    public var matches: [Match]
    public var rankings: EventRankings?
    public var oprs: EventOPRs?
    public var teams: [Team]
}

extension EventCache {
    /// Default empty cache
    public init() {
        self.event = nil
        self.matches = []
        self.rankings = nil
        self.oprs = nil
        self.teams = []
    }
}

/// Tracks last refresh timestamps and Last-Modified headers per endpoint
public struct RefreshState: Codable, Sendable {
    public var lastRefreshDate: Date?
    public var lastModifiedHeaders: [String: String]
    public var isRefreshing: Bool
    public var lastError: String?

    public init() {
        self.lastRefreshDate = nil
        self.lastModifiedHeaders = [:]
        self.isRefreshing = false
        self.lastError = nil
    }

    public func lastModified(for path: String) -> String? {
        lastModifiedHeaders[path]
    }

    public mutating func setLastModified(_ value: String?, for path: String) {
        if let value {
            lastModifiedHeaders[path] = value
        }
    }
}

/// Reads and writes shared data in the App Group container.
/// All methods are synchronous file I/O — keep off the main thread for large files.
public final class TBADataStore: Sendable {
    private let configURL: URL
    private let eventCacheURL: URL
    private let lastRefreshURL: URL

    public init(containerURL: URL) {
        self.configURL = containerURL.appendingPathComponent("team_config.json")
        self.eventCacheURL = containerURL.appendingPathComponent("event_cache.json")
        self.lastRefreshURL = containerURL.appendingPathComponent("last_refresh.json")
    }

    // MARK: - UserConfig

    public func loadConfig() -> UserConfig {
        load(UserConfig.self, from: configURL) ?? UserConfig()
    }

    public func saveConfig(_ config: UserConfig) {
        save(config, to: configURL)
    }

    // MARK: - EventCache

    public func loadEventCache() -> EventCache {
        load(EventCache.self, from: eventCacheURL) ?? EventCache()
    }

    public func saveEventCache(_ cache: EventCache) {
        save(cache, to: eventCacheURL)
    }

    // MARK: - RefreshState

    public func loadRefreshState() -> RefreshState {
        load(RefreshState.self, from: lastRefreshURL) ?? RefreshState()
    }

    public func saveRefreshState(_ state: RefreshState) {
        save(state, to: lastRefreshURL)
    }

    // MARK: - Private

    private func load<T: Decodable>(_ type: T.Type, from url: URL) -> T? {
        guard let data = try? Data(contentsOf: url) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    private func save<T: Encodable>(_ value: T, to url: URL) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: url, options: .atomic)
    }
}
```

- [ ] **Step 2: Verify TBAKit builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift build 2>&1 | tail -5`
Expected: "Build complete!"

- [ ] **Step 3: Commit**

```bash
git add TBAKit/Sources/TBAKit/Store/TBADataStore.swift
git commit -m "Add TBADataStore for App Group JSON persistence"
```

---

### Task 6: TBAKit MatchSchedule

**Files:**
- Create: `TBAKit/Sources/TBAKit/Store/MatchSchedule.swift`
- Test: `TBAKit/Tests/TBAKitTests/MatchScheduleTests.swift`

- [ ] **Step 1: Write MatchSchedule tests**

Create `TBAKit/Tests/TBAKitTests/MatchScheduleTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func nextAndLastMatch() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)
    // qm31 has actualTime (played), qm32 does not (upcoming)

    let schedule = MatchSchedule(matches: matches, teamKey: "frc1234")

    let next = schedule.nextMatch
    #expect(next?.key == "2026miket_qm32")

    let last = schedule.lastPlayedMatch
    #expect(last?.key == "2026miket_qm31")
}

@Test func teamMatches() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)
    let schedule = MatchSchedule(matches: matches, teamKey: "frc1234")

    // frc1234 is in both matches
    #expect(schedule.teamMatches.count == 2)
}

@Test func upcomingAndPastSplit() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)
    let schedule = MatchSchedule(matches: matches, teamKey: "frc1234")

    #expect(schedule.upcomingMatches.count == 1)
    #expect(schedule.pastMatches.count == 1)
    #expect(schedule.upcomingMatches[0].key == "2026miket_qm32")
    #expect(schedule.pastMatches[0].key == "2026miket_qm31")
}

@Test func adaptiveRefreshInterval() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)
    let schedule = MatchSchedule(matches: matches, teamKey: "frc1234")

    // Next match time is 1712000000 (scheduled) / 1712000600 (predicted)
    // Use scheduled time for this test
    let matchDate = Date(timeIntervalSince1970: 1712000000)

    // 3 hours before → 60 min interval
    let far = schedule.refreshInterval(
        now: matchDate.addingTimeInterval(-10800),
        useScheduledTime: true
    )
    #expect(far == 3600)

    // 90 min before → 30 min interval
    let medium = schedule.refreshInterval(
        now: matchDate.addingTimeInterval(-5400),
        useScheduledTime: true
    )
    #expect(medium == 1800)

    // 20 min before → 15 min interval
    let close = schedule.refreshInterval(
        now: matchDate.addingTimeInterval(-1200),
        useScheduledTime: true
    )
    #expect(close == 900)

    // 5 min after match time (just completed window) → 10 min interval
    let justAfter = schedule.refreshInterval(
        now: matchDate.addingTimeInterval(300),
        useScheduledTime: true
    )
    #expect(justAfter == 600)
}

@Test func noMatchesReturnsNil() {
    let schedule = MatchSchedule(matches: [], teamKey: "frc1234")
    #expect(schedule.nextMatch == nil)
    #expect(schedule.lastPlayedMatch == nil)
    #expect(schedule.teamMatches.isEmpty)
}

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: Compilation errors — `MatchSchedule` not defined

- [ ] **Step 3: Implement MatchSchedule**

Create `TBAKit/Sources/TBAKit/Store/MatchSchedule.swift`:

```swift
import Foundation

/// Derives schedule intelligence from a list of matches for a tracked team.
public struct MatchSchedule: Sendable {
    /// All matches at the event, sorted chronologically
    public let allMatches: [Match]

    /// Only matches involving the tracked team, sorted chronologically
    public let teamMatches: [Match]

    /// Upcoming matches for the tracked team (not yet played)
    public let upcomingMatches: [Match]

    /// Past matches for the tracked team (played, most recent first)
    public let pastMatches: [Match]

    /// The next unplayed match for the tracked team
    public var nextMatch: Match? { upcomingMatches.first }

    /// The most recent played match for the tracked team
    public var lastPlayedMatch: Match? { pastMatches.first }

    public init(matches: [Match], teamKey: String) {
        let sorted = matches.sorted { $0.sortOrder < $1.sortOrder }
        self.allMatches = sorted
        self.teamMatches = sorted.filter { match in
            match.alliances.values.contains { $0.teamKeys.contains(teamKey) }
        }
        self.upcomingMatches = teamMatches.filter { !$0.isPlayed }
        self.pastMatches = teamMatches.filter { $0.isPlayed }.reversed()
    }

    /// Adaptive refresh interval in seconds based on proximity to next match.
    /// See spec: Refresh Strategy > Adaptive Timeline Refresh.
    public func refreshInterval(now: Date, useScheduledTime: Bool) -> TimeInterval {
        guard let next = nextMatch,
              let matchDate = next.matchDate(useScheduled: useScheduledTime) else {
            // No upcoming match — check once per day
            return 86400
        }

        let timeUntil = matchDate.timeIntervalSince(now)

        if timeUntil < 0 && timeUntil > -900 {
            // Match time passed within 15 min — match just completed window
            return 600 // 10 minutes
        } else if timeUntil <= 1800 {
            // Within 30 minutes
            return 900 // 15 minutes
        } else if timeUntil <= 7200 {
            // Within 2 hours
            return 1800 // 30 minutes
        } else {
            // More than 2 hours away
            return 3600 // 60 minutes
        }
    }

    /// The date at which the next widget reload should be requested.
    public func nextReloadDate(now: Date, useScheduledTime: Bool) -> Date {
        now.addingTimeInterval(refreshInterval(now: now, useScheduledTime: useScheduledTime))
    }

    /// Whether a Live Activity should be auto-started given the mode and current time.
    public func shouldStartLiveActivity(
        now: Date,
        mode: LiveActivityMode,
        useScheduledTime: Bool,
        hasActiveLiveActivity: Bool
    ) -> Bool {
        guard !hasActiveLiveActivity, let next = nextMatch,
              let matchDate = next.matchDate(useScheduled: useScheduledTime) else {
            return false
        }

        let timeUntil = matchDate.timeIntervalSince(now)

        switch mode {
        case .nearMatch:
            return timeUntil > 0 && timeUntil <= 7200 // within 2 hours
        case .allDay:
            // Start if there's any match today within 2 hours or already past
            return timeUntil <= 7200
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/Store/MatchSchedule.swift TBAKit/Tests/TBAKitTests/MatchScheduleTests.swift
git commit -m "Add MatchSchedule with next/last match, adaptive refresh, LA auto-start logic"
```

---

### Task 7: TBAKit ChangeDetector

**Files:**
- Create: `TBAKit/Sources/TBAKit/Store/ChangeDetector.swift`
- Test: `TBAKit/Tests/TBAKitTests/ChangeDetectorTests.swift`

- [ ] **Step 1: Write ChangeDetector tests**

Create `TBAKit/Tests/TBAKitTests/ChangeDetectorTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func noChangeDetected() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)

    let rankData = try fixtureData("rankings")
    let rankings = try JSONDecoder().decode(EventRankings.self, from: rankData)

    let old = EventCache(event: nil, matches: matches, rankings: rankings, oprs: nil, teams: [])
    let new = EventCache(event: nil, matches: matches, rankings: rankings, oprs: nil, teams: [])

    let result = ChangeDetector.detect(old: old, new: new, teamKey: "frc1234")
    #expect(result.shouldReloadWidgets == false)
}

@Test func scoreChangeDetected() throws {
    let data = try fixtureData("matches")
    var matches = try JSONDecoder().decode([Match].self, from: data)

    let old = EventCache(event: nil, matches: matches, rankings: nil, oprs: nil, teams: [])

    // Simulate qm32 getting scored — we need to decode, modify conceptually
    // Since Match fields are let, we test by having different match arrays
    let scoredMatchJSON = """
    {
        "key": "2026miket_qm32", "comp_level": "qm", "set_number": 1, "match_number": 32,
        "event_key": "2026miket", "time": 1712000000, "predicted_time": 1712000600,
        "actual_time": 1712000700,
        "alliances": {
            "red": { "score": 95, "team_keys": ["frc1234","frc5678","frc9012"], "surrogate_team_keys": [], "dq_team_keys": [] },
            "blue": { "score": 80, "team_keys": ["frc3456","frc7890","frc1111"], "surrogate_team_keys": [], "dq_team_keys": [] }
        },
        "winning_alliance": "red", "score_breakdown": null, "videos": []
    }
    """.data(using: .utf8)!
    let scoredMatch = try JSONDecoder().decode(Match.self, from: scoredMatchJSON)
    matches[0] = scoredMatch

    let new = EventCache(event: nil, matches: matches, rankings: nil, oprs: nil, teams: [])
    let result = ChangeDetector.detect(old: old, new: new, teamKey: "frc1234")
    #expect(result.shouldReloadWidgets == true)
    #expect(result.reasons.contains(.scoreChanged))
}

@Test func rankChangeDetected() throws {
    let rankData = try fixtureData("rankings")
    let rankings = try JSONDecoder().decode(EventRankings.self, from: rankData)

    let old = EventCache(event: nil, matches: [], rankings: rankings, oprs: nil, teams: [])

    let newRankJSON = """
    { "rankings": [{ "team_key": "frc1234", "rank": 5, "record": { "wins": 5, "losses": 3, "ties": 0 },
      "qual_average": 78.0, "matches_played": 8, "dq": 0, "sort_orders": [2.0, 78.0] }],
      "sort_order_info": [{ "name": "Ranking Score", "precision": 2 }, { "name": "Avg Match Score", "precision": 1 }] }
    """.data(using: .utf8)!
    let newRankings = try JSONDecoder().decode(EventRankings.self, from: newRankJSON)

    let new = EventCache(event: nil, matches: [], rankings: newRankings, oprs: nil, teams: [])
    let result = ChangeDetector.detect(old: old, new: new, teamKey: "frc1234")
    #expect(result.shouldReloadWidgets == true)
    #expect(result.reasons.contains(.rankChanged))
}

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: Compilation errors

- [ ] **Step 3: Implement ChangeDetector**

Create `TBAKit/Sources/TBAKit/Store/ChangeDetector.swift`:

```swift
import Foundation

public enum ChangeReason: Sendable {
    case scoreChanged
    case predictedTimeShifted
    case rankChanged
    case allianceChanged
}

public struct ChangeResult: Sendable {
    public let reasons: Set<ChangeReason>
    public var shouldReloadWidgets: Bool { !reasons.isEmpty }

    public init(reasons: Set<ChangeReason>) {
        self.reasons = reasons
    }
}

public enum ChangeDetector {
    /// Compare old and new event cache to determine if widget-visible data changed.
    public static func detect(old: EventCache, new: EventCache, teamKey: String) -> ChangeResult {
        var reasons = Set<ChangeReason>()

        // Check for score changes on team's matches
        let oldMatchMap = Dictionary(uniqueKeysWithValues: old.matches.map { ($0.key, $0) })
        for match in new.matches {
            guard match.alliances.values.contains(where: { $0.teamKeys.contains(teamKey) }) else {
                continue
            }
            if let oldMatch = oldMatchMap[match.key] {
                // Score posted or changed
                if match.isPlayed != oldMatch.isPlayed {
                    reasons.insert(.scoreChanged)
                } else if match.isPlayed && oldMatch.isPlayed {
                    let newRed = match.alliances["red"]?.score ?? -1
                    let oldRed = oldMatch.alliances["red"]?.score ?? -1
                    let newBlue = match.alliances["blue"]?.score ?? -1
                    let oldBlue = oldMatch.alliances["blue"]?.score ?? -1
                    if newRed != oldRed || newBlue != oldBlue {
                        reasons.insert(.scoreChanged)
                    }
                }

                // Predicted time shifted by more than 5 minutes
                if let newPT = match.predictedTime, let oldPT = oldMatch.predictedTime {
                    if abs(newPT - oldPT) > 300 {
                        reasons.insert(.predictedTimeShifted)
                    }
                }

                // Alliance composition changed
                let newTeams = Set((match.alliances["red"]?.teamKeys ?? []) + (match.alliances["blue"]?.teamKeys ?? []))
                let oldTeams = Set((oldMatch.alliances["red"]?.teamKeys ?? []) + (oldMatch.alliances["blue"]?.teamKeys ?? []))
                if newTeams != oldTeams {
                    reasons.insert(.allianceChanged)
                }
            } else {
                // New match appeared
                reasons.insert(.scoreChanged)
            }
        }

        // Check ranking changes
        if let newRank = new.rankings?.rankings.first(where: { $0.teamKey == teamKey }),
           let oldRank = old.rankings?.rankings.first(where: { $0.teamKey == teamKey }) {
            if newRank.rank != oldRank.rank ||
               newRank.record != oldRank.record ||
               newRank.matchesPlayed != oldRank.matchesPlayed {
                reasons.insert(.rankChanged)
            }
        } else if (new.rankings != nil) != (old.rankings != nil) {
            reasons.insert(.rankChanged)
        }

        return ChangeResult(reasons: reasons)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/Store/ChangeDetector.swift TBAKit/Tests/TBAKitTests/ChangeDetectorTests.swift
git commit -m "Add ChangeDetector: diff cached vs fetched to conserve widget reload budget"
```

---

### Task 8: iOS App — Setup Flow

**Files:**
- Create: `PitWatch/Views/SetupView.swift`
- Modify: `PitWatch/PitWatchApp.swift`

- [ ] **Step 1: Implement SetupView**

Create `PitWatch/Views/SetupView.swift`:

```swift
import SwiftUI
import TBAKit

struct SetupView: View {
    @Binding var config: UserConfig
    var onComplete: () -> Void

    @State private var apiKeyText = ""
    @State private var teamNumberText = ""
    @State private var isValidating = false
    @State private var validationError: String?
    @State private var validatedTeamName: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("PitWatch needs a TBA API key to fetch match data. You can get one from your TBA account page.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    TextField("API Key", text: $apiKeyText)
                        .textContentType(.password)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)

                    Link("Get an API Key →",
                         destination: URL(string: "https://www.thebluealliance.com/account")!)
                } header: {
                    Text("TBA API Key")
                }

                Section {
                    TextField("Team Number", text: $teamNumberText)
                        .keyboardType(.numberPad)

                    if let name = validatedTeamName {
                        Label(name, systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    }

                    if let error = validationError {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                    }
                } header: {
                    Text("Your Team")
                }

                Section {
                    Button {
                        Task { await validate() }
                    } label: {
                        if isValidating {
                            ProgressView()
                                .frame(maxWidth: .infinity)
                        } else {
                            Text("Continue")
                                .frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(!canValidate || isValidating)
                }
            }
            .navigationTitle("Welcome to PitWatch")
        }
    }

    private var canValidate: Bool {
        !apiKeyText.trimmingCharacters(in: .whitespaces).isEmpty &&
        Int(teamNumberText) != nil
    }

    private func validate() async {
        guard let teamNumber = Int(teamNumberText) else { return }

        isValidating = true
        validationError = nil
        validatedTeamName = nil

        let client = TBAClient(apiKey: apiKeyText.trimmingCharacters(in: .whitespaces))
        do {
            let team = try await client.validateTeam(number: teamNumber)
            validatedTeamName = team.nickname
            config.apiKey = apiKeyText.trimmingCharacters(in: .whitespaces)
            config.teamNumber = teamNumber
            onComplete()
        } catch {
            validationError = "Could not find team \(teamNumber). Check your API key and team number."
        }

        isValidating = false
    }
}
```

- [ ] **Step 2: Wire SetupView into PitWatchApp**

Replace `PitWatch/PitWatchApp.swift`:

```swift
import SwiftUI
import TBAKit

@main
struct PitWatchApp: App {
    @State private var config: UserConfig
    private let store: TBADataStore

    init() {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        self.store = store
        self._config = State(initialValue: store.loadConfig())
    }

    var body: some Scene {
        WindowGroup {
            if config.isConfigured {
                Text("Match List (coming soon)")
            } else {
                SetupView(config: $config) {
                    store.saveConfig(config)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Verify Xcode build succeeds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 4: Commit**

```bash
git add PitWatch/Views/SetupView.swift PitWatch/PitWatchApp.swift
git commit -m "Add setup flow: API key entry + team number validation"
```

---

### Task 9: iOS App — Event Picker

**Files:**
- Create: `PitWatch/Views/EventPickerView.swift`

- [ ] **Step 1: Implement EventPickerView**

Create `PitWatch/Views/EventPickerView.swift`:

```swift
import SwiftUI
import TBAKit

struct EventPickerView: View {
    let events: [Event]
    @Binding var selectedEventKey: String?
    let autoDetectedEventKey: String?

    var body: some View {
        List(events) { event in
            Button {
                if event.key == autoDetectedEventKey && selectedEventKey == nil {
                    // Already auto-selected, tapping clears override
                    return
                }
                selectedEventKey = event.key == autoDetectedEventKey ? nil : event.key
            } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(event.name)
                            .font(.headline)
                        Text(formatDateRange(event))
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                        if let location = formatLocation(event) {
                            Text(location)
                                .font(.caption)
                                .foregroundStyle(.tertiary)
                        }
                    }

                    Spacer()

                    let isSelected = selectedEventKey == event.key ||
                        (selectedEventKey == nil && event.key == autoDetectedEventKey)

                    if isSelected {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.blue)
                    }

                    if event.key == autoDetectedEventKey && selectedEventKey == nil {
                        Text("AUTO")
                            .font(.caption2)
                            .fontWeight(.bold)
                            .foregroundStyle(.blue)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(.blue.opacity(0.1), in: Capsule())
                    }
                }
            }
            .tint(.primary)
        }
        .navigationTitle("Select Event")
    }

    private func formatDateRange(_ event: Event) -> String {
        "\(event.startDate) – \(event.endDate)"
    }

    private func formatLocation(_ event: Event) -> String? {
        [event.city, event.stateProv, event.country]
            .compactMap { $0 }
            .filter { !$0.isEmpty }
            .joined(separator: ", ")
            .nilIfEmpty
    }
}

extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 3: Commit**

```bash
git add PitWatch/Views/EventPickerView.swift
git commit -m "Add EventPickerView with auto-detect highlight and manual override"
```

---

### Task 10: iOS App — Match List + Match Row

**Files:**
- Create: `PitWatch/Views/MatchRowView.swift`
- Create: `PitWatch/Views/MatchListView.swift`

- [ ] **Step 1: Implement MatchRowView**

Create `PitWatch/Views/MatchRowView.swift`:

```swift
import SwiftUI
import TBAKit

struct MatchRowView: View {
    let match: Match
    let teamKey: String
    let oprs: EventOPRs?
    let useScheduledTime: Bool
    let queueOffsetMinutes: Int

    private var allianceColor: String? { match.allianceColor(for: teamKey) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header: match label + time
            HStack {
                allianceDot
                Text(match.label)
                    .font(.headline)
                Spacer()
                if let date = match.matchDate(useScheduled: useScheduledTime) {
                    Text(timeText(date))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            // Alliance lines
            allianceLine(color: "red")
            allianceLine(color: "blue")

            // Score if played
            if match.isPlayed {
                HStack {
                    Spacer()
                    let redScore = match.alliances["red"]?.score ?? 0
                    let blueScore = match.alliances["blue"]?.score ?? 0
                    Text("\(redScore)")
                        .foregroundStyle(.red)
                        .fontWeight(.bold)
                    Text("–")
                        .foregroundStyle(.secondary)
                    Text("\(blueScore)")
                        .foregroundStyle(.blue)
                        .fontWeight(.bold)

                    if match.winningAlliance == allianceColor {
                        Text("WIN")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(.green)
                    } else if !match.winningAlliance.isEmpty {
                        Text("LOSS")
                            .font(.caption)
                            .fontWeight(.bold)
                            .foregroundStyle(.red)
                    }
                    Spacer()
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var allianceDot: some View {
        Circle()
            .fill(allianceColor == "red" ? Color.red : (allianceColor == "blue" ? Color.blue : Color.gray))
            .frame(width: 10, height: 10)
    }

    @ViewBuilder
    private func allianceLine(color: String) -> some View {
        let alliance = match.alliances[color]
        let teamKeys = alliance?.teamKeys ?? []
        let sumOPR = oprs?.summedOPR(for: teamKeys)

        HStack(spacing: 4) {
            Circle()
                .fill(color == "red" ? Color.red.opacity(0.6) : Color.blue.opacity(0.6))
                .frame(width: 6, height: 6)

            ForEach(teamKeys, id: \.self) { key in
                let number = key.replacingOccurrences(of: "frc", with: "")
                if key == teamKey {
                    Text(number).font(.caption).fontWeight(.bold)
                } else {
                    Text(number).font(.caption).foregroundStyle(.secondary)
                }
            }

            Spacer()

            if let opr = sumOPR {
                Text("Σ \(opr, specifier: "%.1f")")
                    .font(.caption2)
                    .foregroundStyle(color == "red" ? .red : .blue)
            }
        }
    }

    private func timeText(_ date: Date) -> String {
        let prefix = useScheduledTime ? "" : "~"
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm a"
        return prefix + formatter.string(from: date)
    }
}
```

- [ ] **Step 2: Implement MatchListView**

Create `PitWatch/Views/MatchListView.swift`:

```swift
import SwiftUI
import TBAKit

struct MatchListView: View {
    let config: UserConfig
    let store: TBADataStore
    @State private var eventCache: EventCache
    @State private var isRefreshing = false

    init(config: UserConfig, store: TBADataStore) {
        self.config = config
        self.store = store
        self._eventCache = State(initialValue: store.loadEventCache())
    }

    private var schedule: MatchSchedule {
        MatchSchedule(matches: eventCache.matches, teamKey: config.teamKey ?? "")
    }

    var body: some View {
        List {
            if let event = eventCache.event {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(event.name)
                            .font(.headline)
                        if let ranking = teamRanking {
                            Text("Rank #\(ranking.rank) · \(ranking.record?.display ?? "0-0-0")")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            if !schedule.upcomingMatches.isEmpty {
                Section("Upcoming") {
                    ForEach(schedule.upcomingMatches) { match in
                        matchLink(match)
                    }
                }
            }

            if !schedule.pastMatches.isEmpty {
                Section("Results") {
                    ForEach(schedule.pastMatches) { match in
                        matchLink(match)
                    }
                }
            }

            if schedule.teamMatches.isEmpty {
                ContentUnavailableView(
                    "No Matches",
                    systemImage: "calendar.badge.exclamationmark",
                    description: Text("No matches found for this team at this event.")
                )
            }
        }
        .refreshable {
            await forceRefresh()
        }
        .navigationTitle("Team \(config.teamNumber ?? 0)")
    }

    @ViewBuilder
    private func matchLink(_ match: Match) -> some View {
        Button {
            let url = URL(string: "https://www.thebluealliance.com/match/\(match.key)")!
            UIApplication.shared.open(url)
        } label: {
            MatchRowView(
                match: match,
                teamKey: config.teamKey ?? "",
                oprs: eventCache.oprs,
                useScheduledTime: config.useScheduledTime,
                queueOffsetMinutes: config.queueOffsetMinutes
            )
        }
        .tint(.primary)
    }

    private var teamRanking: Ranking? {
        eventCache.rankings?.rankings.first { $0.teamKey == config.teamKey }
    }

    private func forceRefresh() async {
        guard let apiKey = config.apiKey,
              let eventKey = eventCache.event?.key ?? config.eventKeyOverride else { return }

        isRefreshing = true
        let client = TBAClient(apiKey: apiKey)

        do {
            // Fetch all data in parallel — force refresh ignores If-Modified-Since
            async let matchesResult = client.fetch([Match].self, path: Endpoints.eventMatches(key: eventKey))
            async let rankingsResult = client.fetch(EventRankings.self, path: Endpoints.eventRankings(key: eventKey))
            async let oprsResult = client.fetch(EventOPRs.self, path: Endpoints.eventOPRs(key: eventKey))

            if case .data(let matches, _) = try await matchesResult {
                eventCache.matches = matches
            }
            if case .data(let rankings, _) = try await rankingsResult {
                eventCache.rankings = rankings
            }
            if case .data(let oprs, _) = try await oprsResult {
                eventCache.oprs = oprs
            }

            store.saveEventCache(eventCache)

            // Force widget reload
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            // Silently fail on refresh — data stays as-is
        }

        isRefreshing = false
    }
}

import WidgetKit
```

- [ ] **Step 3: Verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 4: Commit**

```bash
git add PitWatch/Views/MatchRowView.swift PitWatch/Views/MatchListView.swift
git commit -m "Add MatchListView with pull-to-refresh and TBA deep links"
```

---

### Task 11: iOS App — Settings View

**Files:**
- Create: `PitWatch/Views/SettingsView.swift`

- [ ] **Step 1: Implement SettingsView**

Create `PitWatch/Views/SettingsView.swift`:

```swift
import SwiftUI
import TBAKit
import WidgetKit

struct SettingsView: View {
    @Binding var config: UserConfig
    let store: TBADataStore
    let onForceRefresh: () async -> Void
    let onStartLiveActivity: () -> Void

    @State private var refreshState: RefreshState

    init(config: Binding<UserConfig>, store: TBADataStore,
         onForceRefresh: @escaping () async -> Void,
         onStartLiveActivity: @escaping () -> Void) {
        self._config = config
        self.store = store
        self.onForceRefresh = onForceRefresh
        self.onStartLiveActivity = onStartLiveActivity
        self._refreshState = State(initialValue: store.loadRefreshState())
    }

    var body: some View {
        Form {
            Section("Time Display") {
                Picker("Time Source", selection: $config.useScheduledTime) {
                    Text("Predicted").tag(false)
                    Text("Scheduled").tag(true)
                }

                Picker("Queue Offset", selection: $config.queueOffsetMinutes) {
                    Text("Off").tag(0)
                    ForEach(Array(stride(from: 5, through: 60, by: 5)), id: \.self) { minutes in
                        Text("\(minutes) min").tag(minutes)
                    }
                }

                if config.queueOffsetMinutes > 0 {
                    Text("Countdown will show time to queue (\(config.queueOffsetMinutes) min before match)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Live Activity") {
                Picker("Auto-Start Mode", selection: $config.liveActivityMode) {
                    Text("Near Match (2 hr)").tag(LiveActivityMode.nearMatch)
                    Text("All Day").tag(LiveActivityMode.allDay)
                }

                Button("Start Live Activity Now") {
                    onStartLiveActivity()
                }
            }

            Section("Data") {
                Button("Force Refresh") {
                    Task { await onForceRefresh() }
                }

                if let date = refreshState.lastRefreshDate {
                    LabeledContent("Last Refresh", value: date.formatted(.relative(presentation: .named)))
                }

                if let error = refreshState.lastError {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            Section("Account") {
                LabeledContent("Team", value: "\(config.teamNumber ?? 0)")

                LabeledContent("API Key") {
                    Text(maskedKey)
                        .foregroundStyle(.secondary)
                }

                Button("Clear All Data", role: .destructive) {
                    config = UserConfig()
                    store.saveConfig(config)
                }
            }
        }
        .navigationTitle("Settings")
        .onChange(of: config) { _, newConfig in
            store.saveConfig(newConfig)
        }
    }

    private var maskedKey: String {
        guard let key = config.apiKey, key.count > 8 else { return "Not set" }
        return String(key.prefix(4)) + "••••" + String(key.suffix(4))
    }
}
```

- [ ] **Step 2: Verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 3: Commit**

```bash
git add PitWatch/Views/SettingsView.swift
git commit -m "Add SettingsView: time source, queue offset, LA mode, force refresh"
```

---

### Task 12: iOS App — Background Refresh

**Files:**
- Create: `PitWatch/Background/BackgroundRefresh.swift`
- Modify: `PitWatch/PitWatchApp.swift`

- [ ] **Step 1: Implement BackgroundRefresh**

Create `PitWatch/Background/BackgroundRefresh.swift`:

```swift
import Foundation
import BackgroundTasks
import WidgetKit
import TBAKit

enum BackgroundRefresh {
    static let taskIdentifier = "com.pitwatch.refresh"

    static func register() {
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: taskIdentifier,
            using: nil
        ) { task in
            guard let refreshTask = task as? BGAppRefreshTask else { return }
            handleRefresh(refreshTask)
        }
    }

    static func scheduleNext(store: TBADataStore) {
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        let interval = schedule.refreshInterval(
            now: .now,
            useScheduledTime: config.useScheduledTime
        )

        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        request.earliestBeginDate = Date(timeIntervalSinceNow: interval)

        try? BGTaskScheduler.shared.submit(request)
    }

    private static func handleRefresh(_ task: BGAppRefreshTask) {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()

        guard config.isConfigured, let apiKey = config.apiKey else {
            task.setTaskCompleted(success: true)
            return
        }

        let refreshTask = Task {
            do {
                try await performRefresh(store: store, config: config, apiKey: apiKey)
                task.setTaskCompleted(success: true)
            } catch {
                task.setTaskCompleted(success: false)
            }
            scheduleNext(store: store)
        }

        task.expirationHandler = {
            refreshTask.cancel()
        }
    }

    static func performRefresh(
        store: TBADataStore,
        config: UserConfig,
        apiKey: String,
        forceReload: Bool = false
    ) async throws {
        let client = TBAClient(apiKey: apiKey)
        var cache = store.loadEventCache()
        var refreshState = store.loadRefreshState()

        // Determine active event
        let eventKey: String
        if let override = config.eventKeyOverride {
            eventKey = override
        } else if let active = cache.event?.key {
            eventKey = active
        } else {
            // Need to fetch team events to auto-detect
            guard let teamNumber = config.teamNumber else { return }
            let year = Calendar.current.component(.year, from: .now)
            let eventsResult = try await client.fetch(
                [Event].self,
                path: Endpoints.teamEvents(number: teamNumber, year: year)
            )
            if case .data(let events, _) = eventsResult {
                let detected = autoDetectEvent(from: events)
                if let detected {
                    cache.event = detected
                    eventKey = detected.key
                } else {
                    return // No event to track
                }
            } else {
                return
            }
        }

        let oldCache = cache

        // Fetch matches
        let matchesPath = Endpoints.eventMatches(key: eventKey)
        let matchesLM = forceReload ? nil : refreshState.lastModified(for: matchesPath)
        let matchesResult = try await client.fetch([Match].self, path: matchesPath, lastModified: matchesLM)
        if case .data(let matches, let lm) = matchesResult {
            cache.matches = matches
            refreshState.setLastModified(lm, for: matchesPath)
        }

        // Fetch rankings
        let rankingsPath = Endpoints.eventRankings(key: eventKey)
        let rankingsLM = forceReload ? nil : refreshState.lastModified(for: rankingsPath)
        let rankingsResult = try await client.fetch(EventRankings.self, path: rankingsPath, lastModified: rankingsLM)
        if case .data(let rankings, let lm) = rankingsResult {
            cache.rankings = rankings
            refreshState.setLastModified(lm, for: rankingsPath)
        }

        // Fetch OPRs
        let oprsPath = Endpoints.eventOPRs(key: eventKey)
        let oprsLM = forceReload ? nil : refreshState.lastModified(for: oprsPath)
        let oprsResult = try await client.fetch(EventOPRs.self, path: oprsPath, lastModified: oprsLM)
        if case .data(let oprs, let lm) = oprsResult {
            cache.oprs = oprs
            refreshState.setLastModified(lm, for: oprsPath)
        }

        // Save cache
        refreshState.lastRefreshDate = .now
        refreshState.lastError = nil
        store.saveEventCache(cache)
        store.saveRefreshState(refreshState)

        // Check if widget-visible data changed
        let teamKey = config.teamKey ?? ""
        let changes = ChangeDetector.detect(old: oldCache, new: cache, teamKey: teamKey)

        if forceReload || changes.shouldReloadWidgets {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    static func autoDetectEvent(from events: [Event]) -> Event? {
        let now = Date.now
        // Prefer active event
        if let active = events.first(where: { $0.isActive(on: now) }) {
            return active
        }
        // Next upcoming
        let upcoming = events
            .filter { ($0.startDateParsed ?? .distantPast) > now }
            .sorted { ($0.startDateParsed ?? .distantFuture) < ($1.startDateParsed ?? .distantFuture) }
        if let next = upcoming.first {
            return next
        }
        // Most recent past
        return events
            .sorted { ($0.endDateParsed ?? .distantPast) > ($1.endDateParsed ?? .distantPast) }
            .first
    }
}
```

- [ ] **Step 2: Wire background refresh into PitWatchApp**

Update `PitWatch/PitWatchApp.swift` to register the background task and add full navigation:

```swift
import SwiftUI
import TBAKit

@main
struct PitWatchApp: App {
    @State private var config: UserConfig
    private let store: TBADataStore

    init() {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        self.store = store
        self._config = State(initialValue: store.loadConfig())
        BackgroundRefresh.register()
        BackgroundRefresh.scheduleNext(store: store)
    }

    var body: some Scene {
        WindowGroup {
            if config.isConfigured {
                NavigationStack {
                    MatchListView(config: config, store: store)
                        .toolbar {
                            ToolbarItem(placement: .topBarTrailing) {
                                NavigationLink {
                                    SettingsView(
                                        config: $config,
                                        store: store,
                                        onForceRefresh: {
                                            guard let apiKey = config.apiKey else { return }
                                            try? await BackgroundRefresh.performRefresh(
                                                store: store, config: config,
                                                apiKey: apiKey, forceReload: true
                                            )
                                        },
                                        onStartLiveActivity: {
                                            // Wired in Task 15
                                        }
                                    )
                                } label: {
                                    Image(systemName: "gear")
                                }
                            }
                            ToolbarItem(placement: .topBarLeading) {
                                NavigationLink {
                                    EventPickerView(
                                        events: [], // Loaded dynamically in final wiring
                                        selectedEventKey: $config.eventKeyOverride,
                                        autoDetectedEventKey: store.loadEventCache().event?.key
                                    )
                                } label: {
                                    Image(systemName: "calendar")
                                }
                            }
                        }
                }
            } else {
                SetupView(config: $config) {
                    store.saveConfig(config)
                    BackgroundRefresh.scheduleNext(store: store)
                    // Trigger initial data fetch
                    Task {
                        guard let apiKey = config.apiKey else { return }
                        try? await BackgroundRefresh.performRefresh(
                            store: store, config: config,
                            apiKey: apiKey, forceReload: true
                        )
                    }
                }
            }
        }
    }
}
```

- [ ] **Step 3: Add background modes to project.yml**

Add to the PitWatch target settings in `project.yml`, under `settings.base`:

```yaml
        UIBackgroundModes:
          - fetch
```

- [ ] **Step 4: Verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 5: Commit**

```bash
git add PitWatch/Background/ PitWatch/PitWatchApp.swift project.yml
git commit -m "Add BGTaskScheduler refresh with adaptive scheduling and change detection"
```

---

### Task 13: iOS Widgets — Timeline Provider

**Files:**
- Create: `PitWatchWidgets/MatchTimelineProvider.swift`
- Modify: `PitWatchWidgets/PitWatchWidgetBundle.swift`

- [ ] **Step 1: Implement MatchTimelineProvider**

Create `PitWatchWidgets/MatchTimelineProvider.swift`:

```swift
import WidgetKit
import TBAKit

struct MatchWidgetEntry: TimelineEntry {
    let date: Date
    let teamNumber: Int?
    let eventName: String?
    let nextMatch: Match?
    let lastMatch: Match?
    let upcomingMatches: [Match]   // next 2-3 after nextMatch
    let pastMatches: [Match]       // last 2-3
    let ranking: Ranking?
    let oprs: EventOPRs?
    let teamKey: String
    let useScheduledTime: Bool
    let queueOffsetMinutes: Int

    /// The alliance color for the tracked team in the next match
    var nextMatchAllianceColor: String? {
        nextMatch?.allianceColor(for: teamKey)
    }

    /// Countdown target date (match time minus queue offset)
    var countdownTarget: Date? {
        guard let match = nextMatch,
              let date = match.matchDate(useScheduled: useScheduledTime) else { return nil }
        if queueOffsetMinutes > 0 {
            return date.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        }
        return date
    }

    /// Label for the countdown: "to match" or "to queue"
    var countdownLabel: String {
        queueOffsetMinutes > 0 ? "to queue" : "to match"
    }

    /// Time prefix: "~" for predicted, "" for scheduled
    var timePrefix: String {
        useScheduledTime ? "" : "~"
    }

    static var placeholder: MatchWidgetEntry {
        MatchWidgetEntry(
            date: .now, teamNumber: 1234, eventName: "Regional",
            nextMatch: nil, lastMatch: nil, upcomingMatches: [], pastMatches: [],
            ranking: nil, oprs: nil, teamKey: "frc1234",
            useScheduledTime: false, queueOffsetMinutes: 0
        )
    }
}

struct MatchTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> MatchWidgetEntry {
        .placeholder
    }

    func getSnapshot(in context: Context, completion: @escaping (MatchWidgetEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MatchWidgetEntry>) -> Void) {
        let entry = makeEntry()
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        let reloadDate = schedule.nextReloadDate(
            now: .now,
            useScheduledTime: config.useScheduledTime
        )

        completion(Timeline(entries: [entry], policy: .after(reloadDate)))
    }

    private func makeEntry() -> MatchWidgetEntry {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        return MatchWidgetEntry(
            date: .now,
            teamNumber: config.teamNumber,
            eventName: cache.event?.shortName ?? cache.event?.name,
            nextMatch: schedule.nextMatch,
            lastMatch: schedule.lastPlayedMatch,
            upcomingMatches: Array(schedule.upcomingMatches.dropFirst().prefix(2)),
            pastMatches: Array(schedule.pastMatches.prefix(3)),
            ranking: cache.rankings?.rankings.first { $0.teamKey == config.teamKey },
            oprs: cache.oprs,
            teamKey: config.teamKey ?? "",
            useScheduledTime: config.useScheduledTime,
            queueOffsetMinutes: config.queueOffsetMinutes
        )
    }
}
```

- [ ] **Step 2: Update widget bundle to use MatchTimelineProvider**

Replace `PitWatchWidgets/PitWatchWidgetBundle.swift`:

```swift
import SwiftUI
import WidgetKit

@main
struct PitWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextMatchWidget()
    }
}

struct NextMatchWidget: Widget {
    let kind = "NextMatchWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: MatchTimelineProvider()) { entry in
            WidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([
            .systemSmall, .systemMedium, .systemLarge,
            .accessoryCircular, .accessoryRectangular
        ])
    }
}

/// Routes to the correct view based on widget family
struct WidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: MatchWidgetEntry

    var body: some View {
        switch family {
        case .systemSmall:
            SmallWidgetView(entry: entry)
        case .systemMedium:
            MediumWidgetView(entry: entry)
        case .systemLarge:
            LargeWidgetView(entry: entry)
        case .accessoryCircular:
            CircularLockScreenView(entry: entry)
        case .accessoryRectangular:
            RectangularLockScreenView(entry: entry)
        default:
            SmallWidgetView(entry: entry)
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: Build may fail until widget views exist — that's fine, we'll add them in Task 14.

- [ ] **Step 4: Commit**

```bash
git add PitWatchWidgets/MatchTimelineProvider.swift PitWatchWidgets/PitWatchWidgetBundle.swift
git commit -m "Add MatchTimelineProvider with adaptive refresh scheduling"
```

---

### Task 14: iOS Widget Views

**Files:**
- Create: `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`
- Create: `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`
- Create: `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`
- Create: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`
- Create: `PitWatchWidgets/WidgetViews/LockScreenWidgetView.swift`

- [ ] **Step 1: Implement shared widget components**

Create `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

struct AllianceDot: View {
    let color: String?
    let size: CGFloat

    init(_ color: String?, size: CGFloat = 8) {
        self.color = color
        self.size = size
    }

    var body: some View {
        Circle()
            .fill(color == "red" ? Color.red : (color == "blue" ? Color.blue : Color.gray))
            .frame(width: size, height: size)
    }
}

struct AllianceLineCompact: View {
    let allianceColor: String
    let teamKeys: [String]
    let trackedTeamKey: String
    let opr: Double?

    var body: some View {
        HStack(spacing: 2) {
            AllianceDot(allianceColor, size: 5)
            ForEach(teamKeys, id: \.self) { key in
                let num = key.replacingOccurrences(of: "frc", with: "")
                if key == trackedTeamKey {
                    Text(num).font(.system(size: 9)).bold()
                } else {
                    Text(num).font(.system(size: 9)).foregroundStyle(.secondary)
                }
            }
            if let opr {
                Spacer()
                Text(String(format: "%.1f", opr))
                    .font(.system(size: 8))
                    .foregroundStyle(allianceColor == "red" ? .red : .blue)
            }
        }
    }
}

struct ScoreDisplay: View {
    let match: Match

    var body: some View {
        HStack(spacing: 4) {
            Text("\(match.alliances["red"]?.score ?? 0)")
                .foregroundStyle(.red)
                .fontWeight(.bold)
            Text("–").foregroundStyle(.secondary)
            Text("\(match.alliances["blue"]?.score ?? 0)")
                .foregroundStyle(.blue)
                .fontWeight(.bold)
        }
    }
}

struct WinLossLabel: View {
    let match: Match
    let teamKey: String

    var body: some View {
        let color = match.allianceColor(for: teamKey)
        if match.winningAlliance == color {
            Text("WIN").font(.caption2).bold().foregroundStyle(.green)
        } else if !match.winningAlliance.isEmpty {
            Text("LOSS").font(.caption2).bold().foregroundStyle(.red)
        }
    }
}

/// Format a date as "h:mm a" with optional tilde prefix
func formatMatchTime(_ date: Date?, prefix: String) -> String {
    guard let date else { return "--" }
    let fmt = DateFormatter()
    fmt.dateFormat = "h:mm a"
    return prefix + fmt.string(from: date)
}

/// Extract team number from a key like "frc1234" → "1234"
func teamNumber(from key: String) -> String {
    key.replacingOccurrences(of: "frc", with: "")
}
```

- [ ] **Step 2: Implement SmallWidgetView**

Create `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

struct SmallWidgetView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Text("TEAM \(entry.teamNumber ?? 0)")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                if let color = entry.nextMatchAllianceColor {
                    AllianceDot(color, size: 8)
                }
            }
            if let ranking = entry.ranking {
                Text("Rank #\(ranking.rank) · \(ranking.record?.display ?? "")")
                    .font(.system(size: 10))
                    .foregroundStyle(.tertiary)
            }

            Spacer()

            // Center: next match
            if let next = entry.nextMatch {
                VStack(spacing: 2) {
                    Text("NEXT MATCH")
                        .font(.system(size: 10))
                        .foregroundStyle(.secondary)
                    Text(next.shortLabel)
                        .font(.system(size: 26, weight: .bold))
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                        Text(entry.countdownLabel)
                            .font(.system(size: 9))
                            .foregroundStyle(.tertiary)
                    }
                }
                .frame(maxWidth: .infinity)
            } else {
                noMatchView
            }

            Spacer()

            // Footer
            if let name = entry.eventName {
                Text(name)
                    .font(.system(size: 9))
                    .foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var noMatchView: some View {
        VStack {
            Text("No upcoming match")
                .font(.system(size: 11))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
}
```

- [ ] **Step 3: Implement MediumWidgetView**

Create `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

struct MediumWidgetView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text("TEAM \(entry.teamNumber ?? 0)")
                    .font(.system(size: 12, weight: .semibold))
                Spacer()
                if let ranking = entry.ranking {
                    Text("Rank #\(ranking.rank) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            }

            HStack(spacing: 10) {
                // Next match card
                nextMatchCard

                // Last match card
                lastMatchCard
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private var nextMatchCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("NEXT MATCH")
                .font(.system(size: 9))
                .foregroundStyle(.secondary)

            if let next = entry.nextMatch {
                HStack {
                    Text(next.shortLabel)
                        .font(.system(size: 16, weight: .bold))
                    Spacer()
                    if let date = next.matchDate(useScheduled: entry.useScheduledTime) {
                        Text(formatMatchTime(date, prefix: entry.timePrefix))
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                }

                // Alliance lines with OPR
                ForEach(["red", "blue"], id: \.self) { color in
                    let keys = next.alliances[color]?.teamKeys ?? []
                    let opr = entry.oprs?.summedOPR(for: keys)
                    AllianceLineCompact(
                        allianceColor: color, teamKeys: keys,
                        trackedTeamKey: entry.teamKey, opr: opr
                    )
                }
            } else {
                Text("None scheduled")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(8)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))
    }

    private var lastMatchCard: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("LAST MATCH")
                .font(.system(size: 9))
                .foregroundStyle(.secondary)

            if let last = entry.lastMatch {
                Text(last.shortLabel)
                    .font(.system(size: 16, weight: .bold))

                HStack {
                    Spacer()
                    ScoreDisplay(match: last)
                        .font(.system(size: 18))
                    Spacer()
                }

                HStack {
                    Spacer()
                    WinLossLabel(match: last, teamKey: entry.teamKey)
                    Spacer()
                }
            } else {
                Text("No results yet")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(8)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))
    }
}
```

- [ ] **Step 4: Implement LargeWidgetView**

Create `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

struct LargeWidgetView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text("TEAM \(entry.teamNumber ?? 0)")
                    .font(.system(size: 12, weight: .semibold))
                if let name = entry.eventName {
                    Text("· \(name)")
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
                Spacer()
                if let ranking = entry.ranking {
                    Text("Rank #\(ranking.rank) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            }

            // Next match highlight
            if let next = entry.nextMatch {
                nextMatchHighlight(next)
            }

            // Upcoming
            if !entry.upcomingMatches.isEmpty {
                Text("UPCOMING")
                    .font(.system(size: 9))
                    .foregroundStyle(.tertiary)
                ForEach(entry.upcomingMatches) { match in
                    upcomingRow(match)
                }
            }

            // Recent results
            if !entry.pastMatches.isEmpty {
                Text("RECENT RESULTS")
                    .font(.system(size: 9))
                    .foregroundStyle(.tertiary)
                ForEach(entry.pastMatches) { match in
                    resultRow(match)
                }
            }

            Spacer(minLength: 0)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }

    private func nextMatchHighlight(_ match: Match) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("UP NEXT →")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
                Text(match.label)
                    .font(.system(size: 14, weight: .bold))
                Spacer()
                if let target = entry.countdownTarget {
                    Text(target, style: .relative)
                        .font(.system(size: 12))
                        .foregroundStyle(.secondary)
                }
            }

            ForEach(["red", "blue"], id: \.self) { color in
                let keys = match.alliances[color]?.teamKeys ?? []
                let opr = entry.oprs?.summedOPR(for: keys)
                AllianceLineCompact(
                    allianceColor: color, teamKeys: keys,
                    trackedTeamKey: entry.teamKey, opr: opr
                )
            }
        }
        .padding(8)
        .background(Color.accentColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
    }

    private func upcomingRow(_ match: Match) -> some View {
        HStack {
            Text(match.shortLabel)
                .font(.system(size: 11))
            if let color = match.allianceColor(for: entry.teamKey) {
                AllianceDot(color, size: 6)
            }
            Spacer()
            if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                Text(formatMatchTime(date, prefix: entry.timePrefix))
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 4))
    }

    private func resultRow(_ match: Match) -> some View {
        HStack {
            Text(match.shortLabel)
                .font(.system(size: 11))
            if let color = match.allianceColor(for: entry.teamKey) {
                AllianceDot(color, size: 6)
            }
            Spacer()
            ScoreDisplay(match: match)
                .font(.system(size: 11))
            WinLossLabel(match: match, teamKey: entry.teamKey)
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 3)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 4))
    }
}
```

- [ ] **Step 5: Implement lock screen widgets**

Create `PitWatchWidgets/WidgetViews/LockScreenWidgetView.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

struct CircularLockScreenView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        if let next = entry.nextMatch {
            ZStack {
                AccessoryWidgetBackground()

                VStack(spacing: 1) {
                    HStack(spacing: 2) {
                        AllianceDot(entry.nextMatchAllianceColor, size: 5)
                        Text(next.shortLabel)
                            .font(.system(size: 9))
                    }
                    if let target = entry.countdownTarget {
                        Text(target, style: .timer)
                            .font(.system(size: 16, weight: .bold))
                            .monospacedDigit()
                    }
                    Text(entry.countdownLabel)
                        .font(.system(size: 7))
                        .foregroundStyle(.secondary)
                }
            }
            .widgetAccentable()
        } else {
            ZStack {
                AccessoryWidgetBackground()
                VStack {
                    Text("\(entry.teamNumber ?? 0)")
                        .font(.system(size: 14, weight: .bold))
                    Text("No match")
                        .font(.system(size: 8))
                        .foregroundStyle(.secondary)
                }
            }
        }
    }
}

struct RectangularLockScreenView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        if let next = entry.nextMatch {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(next.label)
                        .font(.system(size: 13, weight: .semibold))
                    Spacer()
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative)
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }

                ForEach(["red", "blue"], id: \.self) { color in
                    let keys = next.alliances[color]?.teamKeys ?? []
                    HStack(spacing: 2) {
                        AllianceDot(color, size: 4)
                        ForEach(keys, id: \.self) { key in
                            let num = teamNumber(from: key)
                            if key == entry.teamKey {
                                Text(num).font(.system(size: 10)).bold()
                            } else {
                                Text(num).font(.system(size: 10)).foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                if let ranking = entry.ranking {
                    Text("Rank #\(ranking.rank) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 9))
                        .foregroundStyle(.tertiary)
                }
            }
        } else {
            VStack(alignment: .leading) {
                Text("Team \(entry.teamNumber ?? 0)")
                    .font(.system(size: 13, weight: .semibold))
                Text("No upcoming match")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
```

- [ ] **Step 6: Verify full widget build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 7: Commit**

```bash
git add PitWatchWidgets/WidgetViews/
git commit -m "Add all widget views: small, medium, large, lock screen circular + rectangular"
```

---

### Task 15: Live Activity — Attributes, Manager, and Views

**Files:**
- Create: `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift`
- Create: `TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift`
- Create: `PitWatchWidgets/LiveActivity/MatchLiveActivityWidget.swift`
- Create: `PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift`
- Create: `PitWatchWidgets/LiveActivity/DynamicIslandViews.swift`

**Note:** Live Activity UI renders in the widget extension process, not the main app. The `MatchActivityAttributes` live in TBAKit (shared), the `LiveActivityManager` (which calls `Activity.request()`) runs in the main app, and the views live in `PitWatchWidgets/`.

- [ ] **Step 1: Define ActivityAttributes in TBAKit**

Create `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift` (not in PitWatch — this is the shared definition):

```swift
import ActivityKit
import SwiftUI
import TBAKit

struct MatchActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var matchTime: Date?
        var queueTime: Date?
        var redScore: Int?
        var blueScore: Int?
        var winningAlliance: String?
        var redAllianceOPR: Double?
        var blueAllianceOPR: Double?
        var matchState: MatchState
        var rank: Int?
        var record: String?
    }

    var teamNumber: Int
    var eventName: String
    var matchKey: String
    var matchLabel: String
    var compLevel: String
    var redTeams: [String]
    var blueTeams: [String]
    var trackedAllianceColor: String
}

enum MatchState: String, Codable, Hashable {
    case upcoming
    case imminent
    case inProgress
    case completed
}
```

- [ ] **Step 2: Implement Live Activity lock screen view**

Create `PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift`:

```swift
import SwiftUI
import ActivityKit
import WidgetKit

struct LiveActivityLockScreenView: View {
    let context: ActivityViewContext<MatchActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text(context.attributes.matchLabel)
                    .font(.headline)
                Spacer()
                Text(context.attributes.eventName)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }

            // Status area
            switch context.state.matchState {
            case .upcoming, .imminent:
                countdownView
            case .inProgress:
                Text("Match in progress")
                    .font(.title3)
                    .fontWeight(.semibold)
                    .frame(maxWidth: .infinity, alignment: .center)
            case .completed:
                scoreView
            }

            // Alliances
            allianceLine(color: "red", teams: context.attributes.redTeams, opr: context.state.redAllianceOPR)
            allianceLine(color: "blue", teams: context.attributes.blueTeams, opr: context.state.blueAllianceOPR)

            // Footer
            if let rank = context.state.rank, let record = context.state.record {
                Text("Rank #\(rank) · \(record)")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding()
    }

    private var countdownView: some View {
        VStack(spacing: 2) {
            let target = context.state.queueTime ?? context.state.matchTime
            if let target {
                Text(target, style: .timer)
                    .font(.system(size: 32, weight: .bold))
                    .monospacedDigit()
                    .frame(maxWidth: .infinity, alignment: .center)
                Text(context.state.queueTime != nil ? "to queue" : "to match")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
            }
        }
    }

    private var scoreView: some View {
        HStack {
            Spacer()
            Text("\(context.state.redScore ?? 0)")
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(.red)
            Text("–")
                .font(.title2)
                .foregroundStyle(.secondary)
            Text("\(context.state.blueScore ?? 0)")
                .font(.system(size: 36, weight: .bold))
                .foregroundStyle(.blue)

            let tracked = context.attributes.trackedAllianceColor
            let won = context.state.winningAlliance == tracked
            Text(won ? "WIN" : "LOSS")
                .font(.caption)
                .fontWeight(.bold)
                .foregroundStyle(won ? .green : .red)
                .padding(.leading, 8)
            Spacer()
        }
    }

    private func allianceLine(color: String, teams: [String], opr: Double?) -> some View {
        HStack(spacing: 4) {
            Circle()
                .fill(color == "red" ? Color.red : Color.blue)
                .frame(width: 8, height: 8)
            ForEach(teams, id: \.self) { team in
                if team == "\(context.attributes.teamNumber)" {
                    Text(team).font(.caption).bold()
                } else {
                    Text(team).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let opr {
                Text("Σ OPR \(opr, specifier: "%.1f")")
                    .font(.caption2)
                    .foregroundStyle(color == "red" ? .red : .blue)
            }
        }
    }
}
```

- [ ] **Step 3: Implement Dynamic Island views**

Create `PitWatchWidgets/LiveActivity/DynamicIslandViews.swift`:

```swift
import SwiftUI
import ActivityKit
import WidgetKit

struct MatchDynamicIsland {
    static func build(for context: ActivityViewContext<MatchActivityAttributes>) -> DynamicIsland {
        DynamicIsland {
            // Expanded view
            DynamicIslandExpandedRegion(.leading) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.matchLabel)
                        .font(.headline)
                    if let rank = context.state.rank {
                        Text("Rank #\(rank)")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                    }
                }
            }
            DynamicIslandExpandedRegion(.trailing) {
                let target = context.state.queueTime ?? context.state.matchTime
                switch context.state.matchState {
                case .upcoming, .imminent:
                    if let target {
                        VStack(alignment: .trailing) {
                            Text(target, style: .timer)
                                .font(.system(size: 18, weight: .bold))
                                .monospacedDigit()
                            Text(context.state.queueTime != nil ? "to queue" : "to match")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                    }
                case .inProgress:
                    Text("LIVE")
                        .font(.caption)
                        .fontWeight(.bold)
                        .foregroundStyle(.green)
                case .completed:
                    HStack(spacing: 2) {
                        Text("\(context.state.redScore ?? 0)")
                            .foregroundStyle(.red).bold()
                        Text("–").foregroundStyle(.secondary)
                        Text("\(context.state.blueScore ?? 0)")
                            .foregroundStyle(.blue).bold()
                    }
                    .font(.system(size: 18))
                }
            }
            DynamicIslandExpandedRegion(.bottom) {
                VStack(spacing: 2) {
                    expandedAllianceLine(color: "red", teams: context.attributes.redTeams,
                                        opr: context.state.redAllianceOPR, context: context)
                    expandedAllianceLine(color: "blue", teams: context.attributes.blueTeams,
                                        opr: context.state.blueAllianceOPR, context: context)
                }
            }
        } compactLeading: {
            HStack(spacing: 3) {
                Circle()
                    .fill(context.attributes.trackedAllianceColor == "red" ? Color.red : Color.blue)
                    .frame(width: 6, height: 6)
                Text(shortLabel(context.attributes))
                    .font(.system(size: 12, weight: .semibold))
            }
        } compactTrailing: {
            switch context.state.matchState {
            case .upcoming, .imminent:
                let target = context.state.queueTime ?? context.state.matchTime
                if let target {
                    Text(target, style: .timer)
                        .font(.system(size: 12, weight: .bold))
                        .monospacedDigit()
                }
            case .inProgress:
                Text("LIVE")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(.green)
            case .completed:
                HStack(spacing: 1) {
                    Text("\(context.state.redScore ?? 0)").foregroundStyle(.red)
                    Text("-").foregroundStyle(.secondary)
                    Text("\(context.state.blueScore ?? 0)").foregroundStyle(.blue)
                }
                .font(.system(size: 12, weight: .bold))
            }
        } minimal: {
            HStack(spacing: 2) {
                Circle()
                    .fill(context.attributes.trackedAllianceColor == "red" ? Color.red : Color.blue)
                    .frame(width: 5, height: 5)
                switch context.state.matchState {
                case .upcoming, .imminent:
                    let target = context.state.queueTime ?? context.state.matchTime
                    if let target {
                        Text(target, style: .timer)
                            .font(.system(size: 11))
                            .monospacedDigit()
                    }
                case .inProgress:
                    Image(systemName: "circle.fill")
                        .font(.system(size: 6))
                        .foregroundStyle(.green)
                case .completed:
                    Text("✓").font(.system(size: 11))
                }
            }
        }
    }

    private static func shortLabel(_ attrs: MatchActivityAttributes) -> String {
        switch attrs.compLevel {
        case "qm": return "Q\(attrs.matchLabel.filter(\.isNumber))"
        default: return attrs.matchLabel
        }
    }

    private static func expandedAllianceLine(
        color: String, teams: [String], opr: Double?,
        context: ActivityViewContext<MatchActivityAttributes>
    ) -> some View {
        HStack(spacing: 3) {
            Circle()
                .fill(color == "red" ? Color.red : Color.blue)
                .frame(width: 6, height: 6)
            ForEach(teams, id: \.self) { team in
                if team == "\(context.attributes.teamNumber)" {
                    Text(team).font(.caption2).bold()
                } else {
                    Text(team).font(.caption2).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let opr {
                Text(String(format: "%.1f", opr))
                    .font(.caption2)
                    .foregroundStyle(color == "red" ? .red : .blue)
            }
        }
    }
}
```

- [ ] **Step 4: Implement LiveActivityManager in TBAKit**

Create `TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift`:

```swift
import Foundation
import ActivityKit

/// Manages Live Activity lifecycle. Call from iOS app target only.
/// This lives in TBAKit so it can access models, but ActivityKit is iOS-only.
#if canImport(ActivityKit)
public final class LiveActivityManager: @unchecked Sendable {
    public static let shared = LiveActivityManager()

    private init() {}

    /// Start a new Live Activity for an upcoming match.
    public func startActivity(
        match: Match,
        teamNumber: Int,
        teamKey: String,
        eventName: String,
        useScheduledTime: Bool,
        queueOffsetMinutes: Int,
        ranking: Ranking?,
        oprs: EventOPRs?
    ) throws -> Activity<MatchActivityAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return nil }

        let allianceColor = match.allianceColor(for: teamKey) ?? "red"
        let redTeams = (match.alliances["red"]?.teamKeys ?? []).map { $0.replacingOccurrences(of: "frc", with: "") }
        let blueTeams = (match.alliances["blue"]?.teamKeys ?? []).map { $0.replacingOccurrences(of: "frc", with: "") }

        let attributes = MatchActivityAttributes(
            teamNumber: teamNumber,
            eventName: eventName,
            matchKey: match.key,
            matchLabel: match.label,
            compLevel: match.compLevel,
            redTeams: redTeams,
            blueTeams: blueTeams,
            trackedAllianceColor: allianceColor
        )

        let matchDate = match.matchDate(useScheduled: useScheduledTime)
        let queueDate: Date? = if queueOffsetMinutes > 0, let md = matchDate {
            md.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        } else {
            nil
        }

        let state = MatchActivityAttributes.ContentState(
            matchTime: matchDate,
            queueTime: queueDate,
            redScore: nil,
            blueScore: nil,
            winningAlliance: nil,
            redAllianceOPR: oprs?.summedOPR(for: match.alliances["red"]?.teamKeys ?? []),
            blueAllianceOPR: oprs?.summedOPR(for: match.alliances["blue"]?.teamKeys ?? []),
            matchState: .upcoming,
            rank: ranking?.rank,
            record: ranking?.record?.display
        )

        let content = ActivityContent(state: state, staleDate: Date.now.addingTimeInterval(1800))
        return try Activity<MatchActivityAttributes>.request(
            attributes: attributes,
            content: content
        )
    }

    /// Update the current Live Activity with fresh data.
    public func updateActivity(
        match: Match,
        useScheduledTime: Bool,
        queueOffsetMinutes: Int,
        ranking: Ranking?,
        oprs: EventOPRs?
    ) async {
        guard let activity = Activity<MatchActivityAttributes>.activities.first(
            where: { $0.attributes.matchKey == match.key }
        ) else { return }

        let matchDate = match.matchDate(useScheduled: useScheduledTime)
        let queueDate: Date? = if queueOffsetMinutes > 0, let md = matchDate {
            md.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        } else {
            nil
        }

        let matchState: MatchState
        if match.isPlayed {
            matchState = .completed
        } else if let md = matchDate, md.timeIntervalSinceNow < 0 {
            matchState = .inProgress
        } else if let md = matchDate, md.timeIntervalSinceNow < 600 {
            matchState = .imminent
        } else {
            matchState = .upcoming
        }

        let redScore = match.isPlayed ? match.alliances["red"]?.score : nil
        let blueScore = match.isPlayed ? match.alliances["blue"]?.score : nil

        let state = MatchActivityAttributes.ContentState(
            matchTime: matchDate,
            queueTime: queueDate,
            redScore: redScore,
            blueScore: blueScore,
            winningAlliance: match.isPlayed ? match.winningAlliance : nil,
            redAllianceOPR: oprs?.summedOPR(for: match.alliances["red"]?.teamKeys ?? []),
            blueAllianceOPR: oprs?.summedOPR(for: match.alliances["blue"]?.teamKeys ?? []),
            matchState: matchState,
            rank: ranking?.rank,
            record: ranking?.record?.display
        )

        let content = ActivityContent(state: state, staleDate: Date.now.addingTimeInterval(1800))
        await activity.update(content)
    }

    /// End a Live Activity.
    public func endActivity(for matchKey: String) async {
        guard let activity = Activity<MatchActivityAttributes>.activities.first(
            where: { $0.attributes.matchKey == matchKey }
        ) else { return }

        await activity.end(nil, dismissalPolicy: .after(.now.addingTimeInterval(900)))
    }

    /// End all active Live Activities.
    public func endAllActivities() async {
        for activity in Activity<MatchActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    /// Whether there's currently an active Live Activity
    public var hasActiveActivity: Bool {
        !Activity<MatchActivityAttributes>.activities.isEmpty
    }
}
#endif
```

Note: `MatchActivityAttributes` and `MatchState` are defined in the iOS app target (`PitWatch/LiveActivity/MatchLiveActivity.swift`), but LiveActivityManager references them. These types need to be visible to TBAKit. 

**Important:** Move `MatchActivityAttributes` and `MatchState` into TBAKit so they're shared. Update `PitWatch/LiveActivity/MatchLiveActivity.swift` to just re-export or remove it, and create `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift` instead:

Create `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift`:

```swift
import Foundation
#if canImport(ActivityKit)
import ActivityKit

public struct MatchActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable, Sendable {
        public var matchTime: Date?
        public var queueTime: Date?
        public var redScore: Int?
        public var blueScore: Int?
        public var winningAlliance: String?
        public var redAllianceOPR: Double?
        public var blueAllianceOPR: Double?
        public var matchState: MatchState
        public var rank: Int?
        public var record: String?

        public init(matchTime: Date?, queueTime: Date?, redScore: Int?, blueScore: Int?,
                    winningAlliance: String?, redAllianceOPR: Double?, blueAllianceOPR: Double?,
                    matchState: MatchState, rank: Int?, record: String?) {
            self.matchTime = matchTime
            self.queueTime = queueTime
            self.redScore = redScore
            self.blueScore = blueScore
            self.winningAlliance = winningAlliance
            self.redAllianceOPR = redAllianceOPR
            self.blueAllianceOPR = blueAllianceOPR
            self.matchState = matchState
            self.rank = rank
            self.record = record
        }
    }

    public var teamNumber: Int
    public var eventName: String
    public var matchKey: String
    public var matchLabel: String
    public var compLevel: String
    public var redTeams: [String]
    public var blueTeams: [String]
    public var trackedAllianceColor: String

    public init(teamNumber: Int, eventName: String, matchKey: String, matchLabel: String,
                compLevel: String, redTeams: [String], blueTeams: [String], trackedAllianceColor: String) {
        self.teamNumber = teamNumber
        self.eventName = eventName
        self.matchKey = matchKey
        self.matchLabel = matchLabel
        self.compLevel = compLevel
        self.redTeams = redTeams
        self.blueTeams = blueTeams
        self.trackedAllianceColor = trackedAllianceColor
    }
}

public enum MatchState: String, Codable, Hashable, Sendable {
    case upcoming
    case imminent
    case inProgress
    case completed
}
#endif
```

- [ ] **Step 5: Create the Live Activity widget configuration in the widget extension**

Create `PitWatchWidgets/LiveActivity/MatchLiveActivityWidget.swift`:

```swift
import SwiftUI
import WidgetKit
import ActivityKit
import TBAKit

// MatchActivityAttributes and MatchState are defined in TBAKit.
// This file registers the Live Activity's UI.

struct MatchLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: MatchActivityAttributes.self) { context in
            LiveActivityLockScreenView(context: context)
        } dynamicIsland: { context in
            MatchDynamicIsland.build(for: context)
        }
    }
}
```

Add `MatchLiveActivityWidget()` to `PitWatchWidgetBundle`:

Update the widget bundle to include the Live Activity:

```swift
@main
struct PitWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextMatchWidget()
        MatchLiveActivityWidget()
    }
}
```

- [ ] **Step 6: Verify build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 7: Commit**

```bash
git add TBAKit/Sources/TBAKit/LiveActivity/ PitWatchWidgets/LiveActivity/ PitWatchWidgets/PitWatchWidgetBundle.swift
git commit -m "Add Live Activity: attributes, manager, lock screen view, Dynamic Island views"
```

---

### Task 16: watchOS App — Match List + Connectivity

**Files:**
- Create: `PitWatchWatch/MatchListWatchView.swift`
- Create: `PitWatchWatch/ConnectivityManager.swift`
- Modify: `PitWatchWatch/PitWatchWatchApp.swift`

- [ ] **Step 1: Implement ConnectivityManager**

Create `PitWatchWatch/ConnectivityManager.swift`:

```swift
import Foundation
import WatchConnectivity
import TBAKit

class ConnectivityManager: NSObject, ObservableObject, WCSessionDelegate {
    static let shared = ConnectivityManager()
    @Published var lastSyncDate: Date?

    override init() {
        super.init()
        if WCSession.isSupported() {
            WCSession.default.delegate = self
            WCSession.default.activate()
        }
    }

    func session(_ session: WCSession, activationDidCompleteWith activationState: WCSessionActivationState, error: Error?) {}

    func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        // iOS app sends serialized EventCache
        guard let data = userInfo["eventCache"] as? Data else { return }
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        if let cache = try? JSONDecoder().decode(EventCache.self, from: data) {
            store.saveEventCache(cache)
            DispatchQueue.main.async {
                self.lastSyncDate = Date()
            }
        }
    }
}
```

- [ ] **Step 2: Implement watch match list**

Create `PitWatchWatch/MatchListWatchView.swift`:

```swift
import SwiftUI
import TBAKit

struct MatchListWatchView: View {
    let config: UserConfig
    let store: TBADataStore
    @State private var eventCache: EventCache

    init(config: UserConfig, store: TBADataStore) {
        self.config = config
        self.store = store
        self._eventCache = State(initialValue: store.loadEventCache())
    }

    private var schedule: MatchSchedule {
        MatchSchedule(matches: eventCache.matches, teamKey: config.teamKey ?? "")
    }

    var body: some View {
        List {
            if let next = schedule.nextMatch {
                Section("Next Match") {
                    watchMatchRow(next, highlight: true)
                }
            }

            if !schedule.pastMatches.isEmpty {
                Section("Results") {
                    ForEach(schedule.pastMatches.prefix(5)) { match in
                        watchMatchRow(match, highlight: false)
                    }
                }
            }
        }
        .navigationTitle("Team \(config.teamNumber ?? 0)")
        .onAppear {
            eventCache = store.loadEventCache()
        }
    }

    @ViewBuilder
    private func watchMatchRow(_ match: Match, highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                let color = match.allianceColor(for: config.teamKey ?? "")
                Circle()
                    .fill(color == "red" ? Color.red : (color == "blue" ? Color.blue : Color.gray))
                    .frame(width: 8, height: 8)
                Text(match.shortLabel)
                    .font(highlight ? .headline : .body)
                Spacer()
                if !match.isPlayed, let date = match.matchDate(useScheduled: config.useScheduledTime) {
                    Text(date, style: .time)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            if match.isPlayed {
                HStack {
                    Text("\(match.alliances["red"]?.score ?? 0)")
                        .foregroundStyle(.red)
                    Text("–").foregroundStyle(.secondary)
                    Text("\(match.alliances["blue"]?.score ?? 0)")
                        .foregroundStyle(.blue)

                    let won = match.winningAlliance == match.allianceColor(for: config.teamKey ?? "")
                    Text(won ? "W" : "L")
                        .font(.caption2)
                        .fontWeight(.bold)
                        .foregroundStyle(won ? .green : .red)
                }
                .font(.caption)
            }
        }
    }
}
```

- [ ] **Step 3: Wire up PitWatchWatchApp**

Replace `PitWatchWatch/PitWatchWatchApp.swift`:

```swift
import SwiftUI
import TBAKit

@main
struct PitWatchWatchApp: App {
    @StateObject private var connectivity = ConnectivityManager.shared
    private let store = TBADataStore(containerURL: AppGroup.containerURL)

    var body: some Scene {
        WindowGroup {
            let config = store.loadConfig()
            if config.isConfigured {
                NavigationStack {
                    MatchListWatchView(config: config, store: store)
                }
            } else {
                VStack(spacing: 8) {
                    Image(systemName: "iphone.and.arrow.forward")
                        .font(.largeTitle)
                        .foregroundStyle(.secondary)
                    Text("Set up PitWatch on your iPhone")
                        .font(.caption)
                        .multilineTextAlignment(.center)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Verify watch build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatchWatch -destination 'platform=watchOS Simulator,name=Apple Watch Series 10 (46mm)' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 5: Commit**

```bash
git add PitWatchWatch/
git commit -m "Add watchOS app: match list, WatchConnectivity sync, setup prompt"
```

---

### Task 17: watchOS Complications

**Files:**
- Modify: `PitWatchWatchWidgets/PitWatchWatchWidgetBundle.swift`
- Create: `PitWatchWatchWidgets/WatchComplicationProvider.swift`
- Create: `PitWatchWatchWidgets/ComplicationViews.swift`

- [ ] **Step 1: Implement watch complication provider**

Create `PitWatchWatchWidgets/WatchComplicationProvider.swift`:

```swift
import WidgetKit
import TBAKit

struct WatchMatchEntry: TimelineEntry {
    let date: Date
    let teamNumber: Int?
    let nextMatch: Match?
    let allianceColor: String?
    let countdownTarget: Date?
    let countdownLabel: String
    let ranking: Ranking?
    let timePrefix: String

    static var placeholder: WatchMatchEntry {
        WatchMatchEntry(
            date: .now, teamNumber: 1234, nextMatch: nil,
            allianceColor: nil, countdownTarget: nil,
            countdownLabel: "to match", ranking: nil, timePrefix: ""
        )
    }
}

struct WatchComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> WatchMatchEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (WatchMatchEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WatchMatchEntry>) -> Void) {
        let entry = makeEntry()
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        let reloadDate = schedule.nextReloadDate(now: .now, useScheduledTime: config.useScheduledTime)
        completion(Timeline(entries: [entry], policy: .after(reloadDate)))
    }

    private func makeEntry() -> WatchMatchEntry {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        let next = schedule.nextMatch
        let matchDate = next?.matchDate(useScheduled: config.useScheduledTime)
        let countdownTarget: Date?
        let countdownLabel: String
        if config.queueOffsetMinutes > 0, let md = matchDate {
            countdownTarget = md.addingTimeInterval(-TimeInterval(config.queueOffsetMinutes * 60))
            countdownLabel = "to queue"
        } else {
            countdownTarget = matchDate
            countdownLabel = "to match"
        }

        return WatchMatchEntry(
            date: .now,
            teamNumber: config.teamNumber,
            nextMatch: next,
            allianceColor: next?.allianceColor(for: config.teamKey ?? ""),
            countdownTarget: countdownTarget,
            countdownLabel: countdownLabel,
            ranking: cache.rankings?.rankings.first { $0.teamKey == config.teamKey },
            timePrefix: config.useScheduledTime ? "" : "~"
        )
    }
}
```

- [ ] **Step 2: Implement complication views**

Create `PitWatchWatchWidgets/ComplicationViews.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

struct CircularComplicationView: View {
    let entry: WatchMatchEntry

    var body: some View {
        if let next = entry.nextMatch {
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 1) {
                    HStack(spacing: 2) {
                        Circle()
                            .fill(entry.allianceColor == "red" ? Color.red : (entry.allianceColor == "blue" ? Color.blue : Color.gray))
                            .frame(width: 5, height: 5)
                        Text(next.shortLabel)
                            .font(.system(size: 9))
                    }
                    if let target = entry.countdownTarget {
                        Text(target, style: .timer)
                            .font(.system(size: 15, weight: .bold))
                            .monospacedDigit()
                    }
                }
            }
            .widgetAccentable()
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Text("\(entry.teamNumber ?? 0)")
                    .font(.system(size: 14, weight: .bold))
            }
        }
    }
}

struct RectangularComplicationView: View {
    let entry: WatchMatchEntry

    var body: some View {
        if let next = entry.nextMatch {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(next.label)
                        .font(.system(size: 12, weight: .semibold))
                    Spacer()
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative)
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                }
                if let ranking = entry.ranking {
                    Text("#\(ranking.rank) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 9))
                        .foregroundStyle(.secondary)
                }
            }
        } else {
            VStack(alignment: .leading) {
                Text("Team \(entry.teamNumber ?? 0)")
                    .font(.system(size: 12, weight: .semibold))
                Text("No match")
                    .font(.system(size: 10))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
```

- [ ] **Step 3: Update watch widget bundle**

Replace `PitWatchWatchWidgets/PitWatchWatchWidgetBundle.swift`:

```swift
import SwiftUI
import WidgetKit

@main
struct PitWatchWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        WatchNextMatchWidget()
    }
}

struct WatchNextMatchWidget: Widget {
    let kind = "WatchNextMatchWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WatchComplicationProvider()) { entry in
            WatchWidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([.accessoryCircular, .accessoryRectangular, .accessoryCorner])
    }
}

struct WatchWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: WatchMatchEntry

    var body: some View {
        switch family {
        case .accessoryCircular:
            CircularComplicationView(entry: entry)
        case .accessoryRectangular:
            RectangularComplicationView(entry: entry)
        default:
            CircularComplicationView(entry: entry)
        }
    }
}
```

- [ ] **Step 4: Verify watch widget build**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate && xcodebuild -project PitWatch.xcodeproj -scheme PitWatchWatch -destination 'platform=watchOS Simulator,name=Apple Watch Series 10 (46mm)' build 2>&1 | tail -5`
Expected: "BUILD SUCCEEDED"

- [ ] **Step 5: Commit**

```bash
git add PitWatchWatchWidgets/
git commit -m "Add watchOS complications: circular countdown + rectangular match info"
```

---

### Task 18: Final Wiring + iOS Connectivity Sender

**Files:**
- Modify: `PitWatch/Background/BackgroundRefresh.swift` (add WatchConnectivity push)
- Modify: `PitWatch/PitWatchApp.swift` (wire Live Activity start button)

- [ ] **Step 1: Add WatchConnectivity sending to BackgroundRefresh**

Add to the end of `BackgroundRefresh.performRefresh`, after saving the event cache:

```swift
// Push data to watch via WatchConnectivity
if WCSession.isSupported() && WCSession.default.isReachable {
    if let data = try? JSONEncoder().encode(cache) {
        WCSession.default.transferUserInfo(["eventCache": data])
    }
}
```

Add `import WatchConnectivity` at the top of `BackgroundRefresh.swift`.

- [ ] **Step 2: Add Live Activity auto-start/update to BackgroundRefresh**

Add to `BackgroundRefresh.performRefresh`, after the change detection block:

```swift
#if canImport(ActivityKit)
// Live Activity management
let manager = LiveActivityManager.shared
let schedule = MatchSchedule(matches: cache.matches, teamKey: teamKey)

if let next = schedule.nextMatch {
    if manager.hasActiveActivity {
        // Update existing Live Activity
        await manager.updateActivity(
            match: next,
            useScheduledTime: config.useScheduledTime,
            queueOffsetMinutes: config.queueOffsetMinutes,
            ranking: cache.rankings?.rankings.first { $0.teamKey == teamKey },
            oprs: cache.oprs
        )
    } else if schedule.shouldStartLiveActivity(
        now: .now, mode: config.liveActivityMode,
        useScheduledTime: config.useScheduledTime,
        hasActiveLiveActivity: false
    ) {
        // Auto-start
        let _ = try? manager.startActivity(
            match: next,
            teamNumber: config.teamNumber ?? 0,
            teamKey: teamKey,
            eventName: cache.event?.shortName ?? cache.event?.name ?? "",
            useScheduledTime: config.useScheduledTime,
            queueOffsetMinutes: config.queueOffsetMinutes,
            ranking: cache.rankings?.rankings.first { $0.teamKey == teamKey },
            oprs: cache.oprs
        )
    }
}

// End completed match Live Activities
if let last = schedule.lastPlayedMatch {
    await manager.endActivity(for: last.key)
}
#endif
```

- [ ] **Step 3: Wire Live Activity force-start in PitWatchApp**

Update the `onStartLiveActivity` closure in `PitWatchApp.swift`:

```swift
onStartLiveActivity: {
    #if canImport(ActivityKit)
    let cache = store.loadEventCache()
    let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
    if let next = schedule.nextMatch {
        let _ = try? LiveActivityManager.shared.startActivity(
            match: next,
            teamNumber: config.teamNumber ?? 0,
            teamKey: config.teamKey ?? "",
            eventName: cache.event?.shortName ?? cache.event?.name ?? "",
            useScheduledTime: config.useScheduledTime,
            queueOffsetMinutes: config.queueOffsetMinutes,
            ranking: cache.rankings?.rankings.first { $0.teamKey == config.teamKey },
            oprs: cache.oprs
        )
    }
    #endif
}
```

- [ ] **Step 4: Verify full build (iOS + watch)**

Run:
```bash
cd /Users/borgel/working/personal/tba-ios-widget && xcodegen generate
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' build 2>&1 | tail -5
```
Expected: "BUILD SUCCEEDED"

- [ ] **Step 5: Run all TBAKit tests**

Run: `cd /Users/borgel/working/personal/tba-ios-widget/TBAKit && swift test`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add PitWatch/ 
git commit -m "Wire up Live Activity auto-start, WatchConnectivity data push, force-start button"
```

---

## Post-Implementation Verification

After all tasks are complete, verify:

1. `xcodegen generate` succeeds
2. `xcodebuild -scheme PitWatch` builds for iOS
3. `xcodebuild -scheme PitWatchWatch` builds for watchOS
4. `cd TBAKit && swift test` — all unit tests pass
5. Run in simulator: setup flow → enter API key + team → matches load → widgets render in widget gallery
