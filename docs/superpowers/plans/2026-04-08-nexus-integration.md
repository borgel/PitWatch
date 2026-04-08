# FRC Nexus API Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Integrate FRC Nexus API as a priority data source for match queue times and status, with silent fallback to TBA when unavailable.

**Architecture:** Layered merge — Nexus provides timing/status, TBA provides scores/rankings/OPRs. A `NexusClient` fetches data into `NexusEvent` models stored alongside existing TBA data in `EventCache`. A `NexusMatchMerge` utility correlates Nexus matches to TBA matches at render time. UI surfaces show Nexus status badges, all four queue times with the next upcoming highlighted, and an event-level "now queuing" banner.

**Tech Stack:** Swift 6, SwiftUI, WidgetKit, ActivityKit, Swift Testing

---

### Task 1: Nexus Data Models

**Files:**
- Create: `TBAKit/Sources/TBAKit/Models/NexusEvent.swift`
- Test: `TBAKit/Tests/TBAKitTests/NexusModelDecodingTests.swift`
- Create: `TBAKit/Tests/TBAKitTests/Fixtures/nexus_event.json`

- [ ] **Step 1: Create the Nexus test fixture**

Create `TBAKit/Tests/TBAKitTests/Fixtures/nexus_event.json`:

```json
{
  "dataAsOfTime": 1712000000000,
  "nowQueuing": "Qualification 33",
  "matches": [
    {
      "label": "Qualification 32",
      "status": "On deck",
      "redTeams": ["1234", "5678", "9012"],
      "blueTeams": ["3456", "7890", "1111"],
      "times": {
        "estimatedQueueTime": 1711999200000,
        "estimatedOnDeckTime": 1711999500000,
        "estimatedOnFieldTime": 1711999800000,
        "estimatedStartTime": 1712000000000,
        "actualQueueTime": 1711999250000
      }
    },
    {
      "label": "Qualification 31",
      "status": "On field",
      "redTeams": ["1234", "2222", "3333"],
      "blueTeams": ["4444", "5555", "6666"],
      "times": {
        "estimatedQueueTime": 1711998600000,
        "estimatedOnDeckTime": 1711998900000,
        "estimatedOnFieldTime": 1711999200000,
        "estimatedStartTime": 1711999400000,
        "actualQueueTime": 1711998650000
      }
    },
    {
      "label": "Quarterfinal 2-1",
      "status": null,
      "redTeams": ["1234", "5678", "9012"],
      "blueTeams": ["3456", "7890", "1111"],
      "times": {
        "estimatedQueueTime": null,
        "estimatedOnDeckTime": null,
        "estimatedOnFieldTime": null,
        "estimatedStartTime": null,
        "actualQueueTime": null
      },
      "replayOf": null
    }
  ],
  "announcements": [],
  "partsRequests": []
}
```

- [ ] **Step 2: Write the failing decoding test**

Create `TBAKit/Tests/TBAKitTests/NexusModelDecodingTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func decodeNexusEvent() throws {
    let url = Bundle.module.url(forResource: "nexus_event", withExtension: "json", subdirectory: "Fixtures")!
    let data = try Data(contentsOf: url)
    let event = try JSONDecoder().decode(NexusEvent.self, from: data)

    #expect(event.dataAsOfTime == 1712000000000)
    #expect(event.nowQueuing == "Qualification 33")
    #expect(event.matches.count == 3)

    let match = event.matches[0]
    #expect(match.label == "Qualification 32")
    #expect(match.status == "On deck")
    #expect(match.redTeams == ["1234", "5678", "9012"])
    #expect(match.blueTeams == ["3456", "7890", "1111"])
    #expect(match.times.estimatedQueueTime == 1711999200000)
    #expect(match.times.estimatedOnDeckTime == 1711999500000)
    #expect(match.times.estimatedOnFieldTime == 1711999800000)
    #expect(match.times.estimatedStartTime == 1712000000000)
    #expect(match.times.actualQueueTime == 1711999250000)

    let noTimes = event.matches[2]
    #expect(noTimes.times.estimatedQueueTime == nil)
    #expect(noTimes.times.estimatedStartTime == nil)
    #expect(noTimes.replayOf == nil)
}

@Test func nexusMatchTimesAsDate() throws {
    let times = NexusMatchTimes(
        estimatedQueueTime: 1712000000000,
        estimatedOnDeckTime: 1712000300000,
        estimatedOnFieldTime: 1712000600000,
        estimatedStartTime: 1712000900000,
        actualQueueTime: nil
    )

    let queueDate = times.queueDate
    #expect(queueDate != nil)
    #expect(queueDate == Date(timeIntervalSince1970: 1712000000))

    let startDate = times.startDate
    #expect(startDate == Date(timeIntervalSince1970: 1712000900))
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter NexusModelDecoding 2>&1 | tail -20`
Expected: Compilation error — `NexusEvent` type not found.

- [ ] **Step 4: Create NexusEvent model**

Create `TBAKit/Sources/TBAKit/Models/NexusEvent.swift`:

```swift
import Foundation

/// Response from the FRC Nexus API `GET /event/{eventKey}`.
public struct NexusEvent: Codable, Sendable, Hashable {
    public let dataAsOfTime: Int64
    public let nowQueuing: String?
    public let matches: [NexusMatch]
}

/// A single match from the FRC Nexus API.
public struct NexusMatch: Codable, Sendable, Hashable {
    public let label: String
    public let status: String?
    public let redTeams: [String]
    public let blueTeams: [String]
    public let times: NexusMatchTimes
    public let replayOf: String?

    public init(label: String, status: String?, redTeams: [String], blueTeams: [String],
                times: NexusMatchTimes, replayOf: String? = nil) {
        self.label = label
        self.status = status
        self.redTeams = redTeams
        self.blueTeams = blueTeams
        self.times = times
        self.replayOf = replayOf
    }
}

/// Queue timing data from FRC Nexus. All timestamps are Unix milliseconds.
public struct NexusMatchTimes: Codable, Sendable, Hashable {
    public let estimatedQueueTime: Int64?
    public let estimatedOnDeckTime: Int64?
    public let estimatedOnFieldTime: Int64?
    public let estimatedStartTime: Int64?
    public let actualQueueTime: Int64?

    public init(estimatedQueueTime: Int64?, estimatedOnDeckTime: Int64?,
                estimatedOnFieldTime: Int64?, estimatedStartTime: Int64?,
                actualQueueTime: Int64?) {
        self.estimatedQueueTime = estimatedQueueTime
        self.estimatedOnDeckTime = estimatedOnDeckTime
        self.estimatedOnFieldTime = estimatedOnFieldTime
        self.estimatedStartTime = estimatedStartTime
        self.actualQueueTime = actualQueueTime
    }

    /// Convenience: estimated queue time as Date.
    public var queueDate: Date? {
        estimatedQueueTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: estimated on-deck time as Date.
    public var onDeckDate: Date? {
        estimatedOnDeckTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: estimated on-field time as Date.
    public var onFieldDate: Date? {
        estimatedOnFieldTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: estimated start time as Date.
    public var startDate: Date? {
        estimatedStartTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: actual queue time as Date.
    public var actualQueueDate: Date? {
        actualQueueTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Returns the next upcoming phase date (first non-nil date that is in the future).
    /// Order: queue → on deck → on field → start.
    public func nextPhaseDate(after now: Date = .now) -> (label: String, date: Date)? {
        let phases: [(String, Date?)] = [
            ("Queue", queueDate),
            ("On Deck", onDeckDate),
            ("On Field", onFieldDate),
            ("Start", startDate),
        ]
        return phases.compactMap { label, date in
            guard let date, date > now else { return nil }
            return (label, date)
        }.first
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter NexusModelDecoding 2>&1 | tail -20`
Expected: All tests pass.

- [ ] **Step 6: Commit**

```bash
git add TBAKit/Sources/TBAKit/Models/NexusEvent.swift \
       TBAKit/Tests/TBAKitTests/NexusModelDecodingTests.swift \
       TBAKit/Tests/TBAKitTests/Fixtures/nexus_event.json
git commit -m "feat: add Nexus API data models with decoding tests"
```

---

### Task 2: NexusClient Networking

**Files:**
- Create: `TBAKit/Sources/TBAKit/API/NexusClient.swift`
- Test: `TBAKit/Tests/TBAKitTests/NexusClientTests.swift`

- [ ] **Step 1: Write the failing test**

Create `TBAKit/Tests/TBAKitTests/NexusClientTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func nexusClientBuildsCorrectRequest() async throws {
    let client = NexusClient(
        apiKey: "nexus-test-key",
        baseURL: URL(string: "https://example.com/api/v1")!
    )
    let request = client.buildRequest(eventKey: "2026miket")
    #expect(request.value(forHTTPHeaderField: "Nexus-Api-Key") == "nexus-test-key")
    #expect(request.value(forHTTPHeaderField: "User-Agent") == "PitWatch")
    #expect(request.url?.absoluteString == "https://example.com/api/v1/event/2026miket")
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter NexusClient 2>&1 | tail -20`
Expected: Compilation error — `NexusClient` type not found.

- [ ] **Step 3: Create NexusClient**

Create `TBAKit/Sources/TBAKit/API/NexusClient.swift`:

```swift
import Foundation

public final class NexusClient: Sendable {
    public static let defaultBaseURL = URL(string: "https://frc.nexus/api/v1")!

    private let apiKey: String
    private let baseURL: URL
    private let session: URLSession

    public init(apiKey: String, baseURL: URL = NexusClient.defaultBaseURL, session: URLSession = .shared) {
        self.apiKey = apiKey
        self.baseURL = baseURL
        self.session = session
    }

    public func buildRequest(eventKey: String) -> URLRequest {
        let url = baseURL.appendingPathComponent("event/\(eventKey)")
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "Nexus-Api-Key")
        request.setValue("PitWatch", forHTTPHeaderField: "User-Agent")
        return request
    }

    /// Fetches live event status from Nexus. Returns nil on any failure (silent degradation).
    public func fetchEventStatus(eventKey: String) async -> NexusEvent? {
        let request = buildRequest(eventKey: eventKey)
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                return nil
            }
            return try JSONDecoder().decode(NexusEvent.self, from: data)
        } catch {
            return nil
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter NexusClient 2>&1 | tail -20`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/API/NexusClient.swift \
       TBAKit/Tests/TBAKitTests/NexusClientTests.swift
git commit -m "feat: add NexusClient for FRC Nexus API"
```

---

### Task 3: Match Matching / Merge Utility

**Files:**
- Create: `TBAKit/Sources/TBAKit/Store/NexusMatchMerge.swift`
- Test: `TBAKit/Tests/TBAKitTests/NexusMatchMergeTests.swift`

- [ ] **Step 1: Write the failing tests**

Create `TBAKit/Tests/TBAKitTests/NexusMatchMergeTests.swift`:

```swift
import Testing
import Foundation
@testable import TBAKit

@Test func matchByQualLabel() throws {
    let tbaMatch = makeMatch(compLevel: "qm", setNumber: 1, matchNumber: 32)
    let nexusMatch = NexusMatch(
        label: "Qualification 32", status: "On deck",
        redTeams: ["1234", "5678", "9012"], blueTeams: ["3456", "7890", "1111"],
        times: makeTimes(start: 1712000000000)
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let result = NexusMatchMerge.nexusInfo(for: tbaMatch, in: nexusEvent)
    #expect(result != nil)
    #expect(result?.label == "Qualification 32")
    #expect(result?.status == "On deck")
}

@Test func matchByPlayoffLabel() throws {
    let tbaMatch = makeMatch(compLevel: "qf", setNumber: 2, matchNumber: 1)
    let nexusMatch = NexusMatch(
        label: "Quarterfinal 2-1", status: nil,
        redTeams: ["1234", "5678", "9012"], blueTeams: ["3456", "7890", "1111"],
        times: makeTimes(start: nil)
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let result = NexusMatchMerge.nexusInfo(for: tbaMatch, in: nexusEvent)
    #expect(result != nil)
    #expect(result?.label == "Quarterfinal 2-1")
}

@Test func matchByFinalLabel() throws {
    let tbaMatch = makeMatch(compLevel: "f", setNumber: 1, matchNumber: 1)
    let nexusMatch = NexusMatch(
        label: "Final 1", status: "On field",
        redTeams: ["1234", "5678", "9012"], blueTeams: ["3456", "7890", "1111"],
        times: makeTimes(start: 1712000000000)
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let result = NexusMatchMerge.nexusInfo(for: tbaMatch, in: nexusEvent)
    #expect(result != nil)
}

@Test func fallbackToTeamMatching() throws {
    let tbaMatch = makeMatch(compLevel: "qm", setNumber: 1, matchNumber: 99,
                             redTeams: ["frc1234", "frc5678", "frc9012"],
                             blueTeams: ["frc3456", "frc7890", "frc1111"])
    // Label won't match (different number), but teams will
    let nexusMatch = NexusMatch(
        label: "Qualification 100", status: "Now queuing",
        redTeams: ["1234", "5678", "9012"], blueTeams: ["3456", "7890", "1111"],
        times: makeTimes(start: 1712000000000)
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let result = NexusMatchMerge.nexusInfo(for: tbaMatch, in: nexusEvent)
    #expect(result != nil)
    #expect(result?.status == "Now queuing")
}

@Test func returnsNilWhenNoMatch() throws {
    let tbaMatch = makeMatch(compLevel: "qm", setNumber: 1, matchNumber: 50)
    let nexusMatch = NexusMatch(
        label: "Qualification 99", status: nil,
        redTeams: ["9999", "8888", "7777"], blueTeams: ["6666", "5555", "4444"],
        times: makeTimes(start: nil)
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let result = NexusMatchMerge.nexusInfo(for: tbaMatch, in: nexusEvent)
    #expect(result == nil)
}

@Test func returnsNilWhenNexusEventNil() throws {
    let tbaMatch = makeMatch(compLevel: "qm", setNumber: 1, matchNumber: 32)
    let result = NexusMatchMerge.nexusInfo(for: tbaMatch, in: nil)
    #expect(result == nil)
}

// MARK: - Helpers

private func makeMatch(
    compLevel: String, setNumber: Int, matchNumber: Int,
    redTeams: [String] = ["frc1234", "frc5678", "frc9012"],
    blueTeams: [String] = ["frc3456", "frc7890", "frc1111"]
) -> Match {
    let json = """
    {
      "key": "2026test_\(compLevel)\(matchNumber)",
      "comp_level": "\(compLevel)",
      "set_number": \(setNumber),
      "match_number": \(matchNumber),
      "event_key": "2026test",
      "time": 1712000000,
      "predicted_time": null,
      "actual_time": null,
      "alliances": {
        "red": {
          "score": -1,
          "team_keys": \(redTeams.map { "\"\($0)\"" }),
          "surrogate_team_keys": [],
          "dq_team_keys": []
        },
        "blue": {
          "score": -1,
          "team_keys": \(blueTeams.map { "\"\($0)\"" }),
          "surrogate_team_keys": [],
          "dq_team_keys": []
        }
      },
      "winning_alliance": "",
      "score_breakdown": null,
      "videos": []
    }
    """.data(using: .utf8)!
    return try! JSONDecoder().decode(Match.self, from: json)
}

private func makeTimes(start: Int64?) -> NexusMatchTimes {
    NexusMatchTimes(
        estimatedQueueTime: start.map { $0 - 800000 },
        estimatedOnDeckTime: start.map { $0 - 500000 },
        estimatedOnFieldTime: start.map { $0 - 200000 },
        estimatedStartTime: start,
        actualQueueTime: nil
    )
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter NexusMatchMerge 2>&1 | tail -20`
Expected: Compilation error — `NexusMatchMerge` type not found.

- [ ] **Step 3: Implement NexusMatchMerge**

Create `TBAKit/Sources/TBAKit/Store/NexusMatchMerge.swift`:

```swift
import Foundation

/// Correlates Nexus match data to TBA matches using label normalization and team fallback.
public enum NexusMatchMerge {
    /// Find the Nexus match corresponding to a TBA match.
    /// Returns nil if nexusEvent is nil or no match can be correlated.
    public static func nexusInfo(for match: Match, in nexusEvent: NexusEvent?) -> NexusMatch? {
        guard let nexusEvent else { return nil }

        // First pass: match by normalized label
        let tbaCanonical = canonicalLabel(
            compLevel: match.compLevel,
            setNumber: match.setNumber,
            matchNumber: match.matchNumber
        )
        if let found = nexusEvent.matches.first(where: { parseNexusLabel($0.label) == tbaCanonical }) {
            return found
        }

        // Second pass: match by team composition
        let tbaRed = Set(match.alliances["red"]?.teamKeys.map(stripFRC) ?? [])
        let tbaBlue = Set(match.alliances["blue"]?.teamKeys.map(stripFRC) ?? [])
        guard !tbaRed.isEmpty else { return nil }

        return nexusEvent.matches.first { nexus in
            let nexusRed = Set(nexus.redTeams)
            let nexusBlue = Set(nexus.blueTeams)
            return (tbaRed == nexusRed && tbaBlue == nexusBlue) ||
                   (tbaRed == nexusBlue && tbaBlue == nexusRed)
        }
    }

    // MARK: - Private

    /// Canonical form: "qm-1-32", "qf-2-1", "f-1-1"
    private static func canonicalLabel(compLevel: String, setNumber: Int, matchNumber: Int) -> String {
        "\(compLevel)-\(setNumber)-\(matchNumber)"
    }

    /// Parse a Nexus label like "Qualification 32" or "Quarterfinal 2-1" into canonical form.
    private static func parseNexusLabel(_ label: String) -> String? {
        let parts = label.split(separator: " ", maxSplits: 1)
        guard parts.count == 2 else { return nil }

        let levelStr = parts[0].lowercased()
        let numberStr = String(parts[1])

        let compLevel: String
        switch levelStr {
        case "practice":
            compLevel = "p"
        case "qualification":
            compLevel = "qm"
        case "eighthfinal":
            compLevel = "ef"
        case "quarterfinal":
            compLevel = "qf"
        case "semifinal":
            compLevel = "sf"
        case "final":
            compLevel = "f"
        default:
            compLevel = levelStr
        }

        // Handle "2-1" (set-match) vs "32" (just match number)
        if numberStr.contains("-") {
            let nums = numberStr.split(separator: "-")
            guard nums.count == 2 else { return nil }
            return "\(compLevel)-\(nums[0])-\(nums[1])"
        } else {
            return "\(compLevel)-1-\(numberStr)"
        }
    }

    private static func stripFRC(_ key: String) -> String {
        key.replacingOccurrences(of: "frc", with: "")
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter NexusMatchMerge 2>&1 | tail -20`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/Store/NexusMatchMerge.swift \
       TBAKit/Tests/TBAKitTests/NexusMatchMergeTests.swift
git commit -m "feat: add NexusMatchMerge for correlating Nexus to TBA matches"
```

---

### Task 4: UserConfig and EventCache Changes

**Files:**
- Modify: `TBAKit/Sources/TBAKit/Config/UserConfig.swift`
- Modify: `TBAKit/Sources/TBAKit/Store/TBADataStore.swift`
- Test: `TBAKit/Tests/TBAKitTests/UserConfigTests.swift` (extend)

- [ ] **Step 1: Write the failing test**

Add to `TBAKit/Tests/TBAKitTests/UserConfigTests.swift`:

```swift
@Test func nexusApiKeyConfig() {
    var config = UserConfig()
    #expect(config.nexusApiKey == nil)
    #expect(config.isNexusConfigured == false)

    config.nexusApiKey = "test-nexus-key"
    #expect(config.isNexusConfigured == true)
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter nexusApiKeyConfig 2>&1 | tail -20`
Expected: Compilation error — `nexusApiKey` property not found.

- [ ] **Step 3: Add nexusApiKey to UserConfig**

In `TBAKit/Sources/TBAKit/Config/UserConfig.swift`, add `nexusApiKey` property:

```swift
public struct UserConfig: Codable, Sendable, Equatable {
    public var teamNumber: Int?
    public var apiKey: String?
    public var nexusApiKey: String?
    public var eventKeyOverride: String?
    public var useScheduledTime: Bool
    public var queueOffsetMinutes: Int
    public var liveActivityMode: LiveActivityMode

    public init() {
        self.teamNumber = nil
        self.apiKey = nil
        self.nexusApiKey = nil
        self.eventKeyOverride = nil
        self.useScheduledTime = false
        self.queueOffsetMinutes = 0
        self.liveActivityMode = .nearMatch
    }

    public var isConfigured: Bool {
        teamNumber != nil && apiKey != nil && !apiKey!.isEmpty
    }

    public var isNexusConfigured: Bool {
        nexusApiKey != nil && !nexusApiKey!.isEmpty
    }

    public var teamKey: String? {
        guard let number = teamNumber else { return nil }
        return "frc\(number)"
    }

    public var queueOffset: TimeInterval {
        TimeInterval(queueOffsetMinutes * 60)
    }
}
```

- [ ] **Step 4: Add nexusEvent to EventCache**

In `TBAKit/Sources/TBAKit/Store/TBADataStore.swift`, add `nexusEvent` to `EventCache` and `nexusLastRefreshDate`/`nexusLastError` to `RefreshState`:

```swift
public struct EventCache: Codable, Sendable {
    public var event: Event?
    public var matches: [Match]
    public var rankings: EventRankings?
    public var oprs: EventOPRs?
    public var teams: [Team]
    public var nexusEvent: NexusEvent?
}

extension EventCache {
    public init() {
        self.event = nil
        self.matches = []
        self.rankings = nil
        self.oprs = nil
        self.teams = []
        self.nexusEvent = nil
    }
}

public struct RefreshState: Codable, Sendable {
    public var lastRefreshDate: Date?
    public var lastModifiedHeaders: [String: String]
    public var isRefreshing: Bool
    public var lastError: String?
    public var nexusLastRefreshDate: Date?
    public var nexusLastError: String?

    public init() {
        self.lastRefreshDate = nil
        self.lastModifiedHeaders = [:]
        self.isRefreshing = false
        self.lastError = nil
        self.nexusLastRefreshDate = nil
        self.nexusLastError = nil
    }

    // ... existing lastModified methods unchanged
}
```

- [ ] **Step 5: Run all tests to verify nothing breaks**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test 2>&1 | tail -20`
Expected: All tests pass. Existing JSON fixtures decode fine because new fields are optional.

- [ ] **Step 6: Commit**

```bash
git add TBAKit/Sources/TBAKit/Config/UserConfig.swift \
       TBAKit/Sources/TBAKit/Store/TBADataStore.swift \
       TBAKit/Tests/TBAKitTests/UserConfigTests.swift
git commit -m "feat: add nexusApiKey to UserConfig, nexusEvent to EventCache"
```

---

### Task 5: BackgroundRefresh Nexus Integration

**Files:**
- Modify: `PitWatch/Background/BackgroundRefresh.swift`

- [ ] **Step 1: Add Nexus fetch to performRefresh**

In `PitWatch/Background/BackgroundRefresh.swift`, modify `performRefresh()` to fetch Nexus data in parallel with TBA data. After the existing TBA fetch block (after line 134 where OPRs are fetched), add:

```swift
        // Fetch Nexus event status (non-fatal)
        if let nexusKey = config.nexusApiKey, !nexusKey.isEmpty {
            let nexusClient = NexusClient(apiKey: nexusKey)
            let nexusResult = await nexusClient.fetchEventStatus(eventKey: eventKey)
            cache.nexusEvent = nexusResult
            refreshState.nexusLastRefreshDate = .now
            refreshState.nexusLastError = nexusResult == nil ? "Nexus data unavailable" : nil
        } else {
            cache.nexusEvent = nil
        }
```

- [ ] **Step 2: Also clear nexusEvent when switching events**

In the event override block (around line 75-80), add `cache.nexusEvent = nil` alongside the other cache clears:

```swift
            if cache.event?.key != override {
                cache.event = nil
                cache.matches = []
                cache.rankings = nil
                cache.oprs = nil
                cache.nexusEvent = nil
            }
```

- [ ] **Step 3: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add PitWatch/Background/BackgroundRefresh.swift
git commit -m "feat: fetch Nexus event status during background refresh"
```

---

### Task 6: MatchSchedule Nexus-Aware Refresh Intervals

**Files:**
- Modify: `TBAKit/Sources/TBAKit/Store/MatchSchedule.swift`
- Modify: `TBAKit/Tests/TBAKitTests/MatchScheduleTests.swift`

- [ ] **Step 1: Write a failing test for Nexus-aware refresh**

Add to `TBAKit/Tests/TBAKitTests/MatchScheduleTests.swift`:

```swift
@Test func refreshIntervalTightensWithNexusTimes() {
    // Match has Nexus queue time 20 min from now — should tighten to 5 min refresh
    let matchTime = Date.now.addingTimeInterval(3600) // 1 hr out (normally 30 min refresh)
    let match = makeTestMatch(time: Int64(matchTime.timeIntervalSince1970))
    let schedule = MatchSchedule(matches: [match], teamKey: "frc1234")

    let nexusQueueTime = Date.now.addingTimeInterval(1200) // 20 min from now
    let nexusTimes = NexusMatchTimes(
        estimatedQueueTime: Int64(nexusQueueTime.timeIntervalSince1970 * 1000),
        estimatedOnDeckTime: nil, estimatedOnFieldTime: nil,
        estimatedStartTime: Int64(matchTime.timeIntervalSince1970 * 1000),
        actualQueueTime: nil
    )
    let nexusMatch = NexusMatch(
        label: "Qualification 1", status: nil,
        redTeams: ["1234", "5678", "9012"], blueTeams: ["3456", "7890", "1111"],
        times: nexusTimes
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let interval = schedule.refreshInterval(now: .now, useScheduledTime: false, nexusEvent: nexusEvent)
    #expect(interval <= 600) // Should be tighter than the 1800s TBA-only would give
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test --filter refreshIntervalTightensWithNexusTimes 2>&1 | tail -20`
Expected: Compilation error — extra argument `nexusEvent` in call.

- [ ] **Step 3: Add nexusEvent parameter to MatchSchedule refresh methods**

In `TBAKit/Sources/TBAKit/Store/MatchSchedule.swift`, add an optional `nexusEvent` parameter to `refreshInterval` and `nextReloadDate`. When Nexus data is available, use the nearest Nexus phase time to determine refresh interval:

```swift
    /// Adaptive refresh interval based on proximity to next match or Nexus phase time.
    public func refreshInterval(now: Date, useScheduledTime: Bool, nexusEvent: NexusEvent? = nil) -> TimeInterval {
        guard let next = nextMatch else {
            return 86400
        }

        // If Nexus data available, use the nearest phase time for tighter refresh
        if let nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: next, in: nexusEvent),
           let nextPhase = nexusMatch.times.nextPhaseDate(after: now) {
            let timeUntil = nextPhase.date.timeIntervalSince(now)
            if timeUntil < 0 && timeUntil > -900 {
                return 300  // Phase just passed -> 5 minutes
            } else if timeUntil <= 600 {
                return 300  // Within 10 min of phase -> 5 minutes
            } else if timeUntil <= 1800 {
                return 600  // Within 30 min -> 10 minutes
            } else {
                return 900  // More than 30 min -> 15 minutes
            }
        }

        // Fall back to TBA-based intervals
        guard let matchDate = referenceDate(for: next, useScheduledTime: useScheduledTime) else {
            return 86400
        }

        let timeUntil = matchDate.timeIntervalSince(now)

        if timeUntil < 0 && timeUntil > -900 {
            return 600
        } else if timeUntil <= 1800 {
            return 900
        } else if timeUntil <= 7200 {
            return 1800
        } else {
            return 3600
        }
    }

    public func nextReloadDate(now: Date, useScheduledTime: Bool, nexusEvent: NexusEvent? = nil) -> Date {
        now.addingTimeInterval(refreshInterval(now: now, useScheduledTime: useScheduledTime, nexusEvent: nexusEvent))
    }
```

- [ ] **Step 4: Run all tests to verify they pass**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test 2>&1 | tail -20`
Expected: All tests pass. Existing callers use the default `nil` parameter.

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/Store/MatchSchedule.swift \
       TBAKit/Tests/TBAKitTests/MatchScheduleTests.swift
git commit -m "feat: tighten refresh intervals when Nexus timing data available"
```

---

### Task 7: Pass Nexus Data Through Widget Pipeline

**Files:**
- Modify: `PitWatchWidgets/MatchTimelineProvider.swift`

- [ ] **Step 1: Add nexusEvent to MatchWidgetEntry**

In `PitWatchWidgets/MatchTimelineProvider.swift`, add `nexusEvent` to the entry and update `makeEntry`:

Add new field to `MatchWidgetEntry` after the `queueOffsetMinutes` property:

```swift
    let nexusEvent: NexusEvent?
```

Update the `countdownTarget` computed property to prefer Nexus times:

```swift
    var countdownTarget: Date? {
        guard let match = nextMatch else { return nil }
        // Prefer Nexus queue time if available
        if let nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent),
           let nextPhase = nexusMatch.times.nextPhaseDate(after: .now) {
            return nextPhase.date
        }
        // Fall back to TBA time
        guard let date = match.matchDate(useScheduled: useScheduledTime) else { return nil }
        if queueOffsetMinutes > 0 {
            return date.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        }
        return date
    }

    var countdownLabel: String {
        guard let match = nextMatch else { return "to match" }
        if let nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent),
           let nextPhase = nexusMatch.times.nextPhaseDate(after: .now) {
            return "to \(nextPhase.label.lowercased())"
        }
        return queueOffsetMinutes > 0 ? "to queue" : "to match"
    }
```

Update the `nowQueuing` convenience property:

```swift
    var nowQueuing: String? {
        nexusEvent?.nowQueuing
    }

    var nexusStatus: String? {
        guard let match = nextMatch, let nexusEvent else { return nil }
        return NexusMatchMerge.nexusInfo(for: match, in: nexusEvent)?.status
    }

    var isNexusAvailable: Bool {
        nexusEvent != nil
    }
```

Update the placeholder:

```swift
    static var placeholder: MatchWidgetEntry {
        MatchWidgetEntry(
            date: .now, teamNumber: 1234, eventName: "Regional",
            nextMatch: nil, lastMatch: nil, upcomingMatches: [], pastMatches: [],
            ranking: nil, oprs: nil, teamKey: "frc1234",
            useScheduledTime: false, queueOffsetMinutes: 0,
            nexusEvent: nil
        )
    }
```

- [ ] **Step 2: Update makeEntry to load nexusEvent**

In `makeEntry()`, pass the nexus data through:

```swift
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
            queueOffsetMinutes: config.queueOffsetMinutes,
            nexusEvent: cache.nexusEvent
        )
    }
```

- [ ] **Step 3: Update getTimeline to use nexus-aware reload**

In `getTimeline()`, pass nexusEvent to the reload date calculation:

```swift
    func getTimeline(in context: Context, completion: @escaping (Timeline<MatchWidgetEntry>) -> Void) {
        let entry = makeEntry()
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
        let reloadDate = schedule.nextReloadDate(
            now: .now, useScheduledTime: config.useScheduledTime,
            nexusEvent: cache.nexusEvent
        )
        completion(Timeline(entries: [entry], policy: .after(reloadDate)))
    }
```

- [ ] **Step 4: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add PitWatchWidgets/MatchTimelineProvider.swift
git commit -m "feat: pass Nexus data through widget timeline pipeline"
```

---

### Task 8: Live Activity Nexus Support

**Files:**
- Modify: `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift`
- Modify: `TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift`

- [ ] **Step 1: Add Nexus fields to ContentState**

In `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift`, add Nexus fields to `ContentState`:

```swift
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
        // Nexus fields
        public var nexusStatus: String?
        public var nexusQueueTime: Date?
        public var nexusOnDeckTime: Date?
        public var nexusOnFieldTime: Date?
        public var nexusStartTime: Date?

        public init(matchTime: Date?, queueTime: Date?, redScore: Int?, blueScore: Int?,
                    winningAlliance: String?, redAllianceOPR: Double?, blueAllianceOPR: Double?,
                    matchState: MatchState, rank: Int?, record: String?,
                    nexusStatus: String? = nil, nexusQueueTime: Date? = nil,
                    nexusOnDeckTime: Date? = nil, nexusOnFieldTime: Date? = nil,
                    nexusStartTime: Date? = nil) {
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
            self.nexusStatus = nexusStatus
            self.nexusQueueTime = nexusQueueTime
            self.nexusOnDeckTime = nexusOnDeckTime
            self.nexusOnFieldTime = nexusOnFieldTime
            self.nexusStartTime = nexusStartTime
        }
    }
```

- [ ] **Step 2: Update LiveActivityManager to accept and use Nexus data**

In `TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift`, add `nexusMatch` parameter to `startActivity` and `updateActivity`:

```swift
    public func startActivity(
        match: Match, teamNumber: Int, teamKey: String, eventName: String,
        useScheduledTime: Bool, queueOffsetMinutes: Int,
        ranking: Ranking?, oprs: EventOPRs?,
        nexusMatch: NexusMatch? = nil
    ) throws -> Activity<MatchActivityAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return nil }

        let allianceColor = match.allianceColor(for: teamKey) ?? "red"
        let redTeams = (match.alliances["red"]?.teamKeys ?? []).map { $0.replacingOccurrences(of: "frc", with: "") }
        let blueTeams = (match.alliances["blue"]?.teamKeys ?? []).map { $0.replacingOccurrences(of: "frc", with: "") }

        let attributes = MatchActivityAttributes(
            teamNumber: teamNumber, eventName: eventName, matchKey: match.key,
            matchLabel: match.label, compLevel: match.compLevel,
            redTeams: redTeams, blueTeams: blueTeams, trackedAllianceColor: allianceColor
        )

        let matchDate = match.matchDate(useScheduled: useScheduledTime)
        let queueDate: Date? = if queueOffsetMinutes > 0, let md = matchDate {
            md.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        } else { nil }

        let state = MatchActivityAttributes.ContentState(
            matchTime: matchDate, queueTime: queueDate,
            redScore: nil, blueScore: nil, winningAlliance: nil,
            redAllianceOPR: oprs?.summedOPR(for: match.alliances["red"]?.teamKeys ?? []),
            blueAllianceOPR: oprs?.summedOPR(for: match.alliances["blue"]?.teamKeys ?? []),
            matchState: .upcoming, rank: ranking?.rank, record: ranking?.record?.display,
            nexusStatus: nexusMatch?.status,
            nexusQueueTime: nexusMatch?.times.queueDate,
            nexusOnDeckTime: nexusMatch?.times.onDeckDate,
            nexusOnFieldTime: nexusMatch?.times.onFieldDate,
            nexusStartTime: nexusMatch?.times.startDate
        )

        let content = ActivityContent(state: state, staleDate: Date.now.addingTimeInterval(1800))
        return try Activity<MatchActivityAttributes>.request(attributes: attributes, content: content)
    }

    public func updateActivity(
        match: Match, useScheduledTime: Bool, queueOffsetMinutes: Int,
        ranking: Ranking?, oprs: EventOPRs?,
        nexusMatch: NexusMatch? = nil
    ) async {
        guard let activity = Activity<MatchActivityAttributes>.activities.first(
            where: { $0.attributes.matchKey == match.key }
        ) else { return }

        let matchDate = match.matchDate(useScheduled: useScheduledTime)
        let queueDate: Date? = if queueOffsetMinutes > 0, let md = matchDate {
            md.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        } else { nil }

        // Use Nexus status for state transitions when available
        let matchState: MatchState
        if match.isPlayed {
            matchState = .completed
        } else if let status = nexusMatch?.status?.lowercased() {
            if status.contains("field") {
                matchState = .inProgress
            } else if status.contains("deck") {
                matchState = .imminent
            } else {
                matchState = .upcoming
            }
        } else if let md = matchDate, md.timeIntervalSinceNow < 0 {
            matchState = .inProgress
        } else if let md = matchDate, md.timeIntervalSinceNow < 600 {
            matchState = .imminent
        } else {
            matchState = .upcoming
        }

        let state = MatchActivityAttributes.ContentState(
            matchTime: matchDate, queueTime: queueDate,
            redScore: match.isPlayed ? match.alliances["red"]?.score : nil,
            blueScore: match.isPlayed ? match.alliances["blue"]?.score : nil,
            winningAlliance: match.isPlayed ? match.winningAlliance : nil,
            redAllianceOPR: oprs?.summedOPR(for: match.alliances["red"]?.teamKeys ?? []),
            blueAllianceOPR: oprs?.summedOPR(for: match.alliances["blue"]?.teamKeys ?? []),
            matchState: matchState, rank: ranking?.rank, record: ranking?.record?.display,
            nexusStatus: nexusMatch?.status,
            nexusQueueTime: nexusMatch?.times.queueDate,
            nexusOnDeckTime: nexusMatch?.times.onDeckDate,
            nexusOnFieldTime: nexusMatch?.times.onFieldDate,
            nexusStartTime: nexusMatch?.times.startDate
        )

        let content = ActivityContent(state: state, staleDate: Date.now.addingTimeInterval(1800))
        await activity.update(content)
    }
```

- [ ] **Step 3: Update BackgroundRefresh to pass Nexus data to LiveActivityManager**

In `PitWatch/Background/BackgroundRefresh.swift`, update the Live Activity section (around line 162-188) to look up Nexus match data:

```swift
        #if canImport(ActivityKit) && os(iOS)
        let manager = LiveActivityManager.shared
        let schedule = MatchSchedule(matches: cache.matches, teamKey: teamKey)

        if let next = schedule.nextMatch {
            let nexusMatch = NexusMatchMerge.nexusInfo(for: next, in: cache.nexusEvent)
            if manager.hasActiveActivity {
                await manager.updateActivity(
                    match: next,
                    useScheduledTime: config.useScheduledTime,
                    queueOffsetMinutes: config.queueOffsetMinutes,
                    ranking: cache.rankings?.rankings.first { $0.teamKey == teamKey },
                    oprs: cache.oprs,
                    nexusMatch: nexusMatch
                )
            } else if schedule.shouldStartLiveActivity(
                now: .now, mode: config.liveActivityMode,
                useScheduledTime: config.useScheduledTime,
                hasActiveLiveActivity: false
            ) {
                let _ = try? manager.startActivity(
                    match: next,
                    teamNumber: config.teamNumber ?? 0,
                    teamKey: teamKey,
                    eventName: cache.event?.shortName ?? cache.event?.name ?? "",
                    useScheduledTime: config.useScheduledTime,
                    queueOffsetMinutes: config.queueOffsetMinutes,
                    ranking: cache.rankings?.rankings.first { $0.teamKey == teamKey },
                    oprs: cache.oprs,
                    nexusMatch: nexusMatch
                )
            }
        }
        #endif
```

- [ ] **Step 4: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift \
       TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift \
       PitWatch/Background/BackgroundRefresh.swift
git commit -m "feat: pass Nexus status and times through Live Activity pipeline"
```

---

### Task 9: Settings View — Nexus API Key

**Files:**
- Modify: `PitWatch/Views/SettingsView.swift`

- [ ] **Step 1: Add Nexus API key section to settings**

In `PitWatch/Views/SettingsView.swift`, add a new section after the "Account" section (around line 91):

```swift
            Section {
                SecureField("Nexus API Key", text: Binding(
                    get: { config.nexusApiKey ?? "" },
                    set: { config.nexusApiKey = $0.isEmpty ? nil : $0 }
                ))
                .textContentType(.password)
                .autocorrectionDisabled()

                if config.isNexusConfigured {
                    if let nexusDate = store.loadRefreshState().nexusLastRefreshDate {
                        LabeledContent("Last Nexus Refresh", value: nexusDate.formatted(.relative(presentation: .named)))
                    }
                    if let nexusError = store.loadRefreshState().nexusLastError {
                        Label(nexusError, systemImage: "info.circle")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Link("Get a Nexus API key", destination: URL(string: "https://frc.nexus/api")!)
                    .font(.caption)

                Text("Data provided by frc.nexus")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            } header: {
                Text("FRC Nexus")
            } footer: {
                Text("When available, Nexus provides real-time match queue times and status.")
            }
```

- [ ] **Step 2: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add PitWatch/Views/SettingsView.swift
git commit -m "feat: add Nexus API key entry and attribution to settings"
```

---

### Task 10: App Match Row — Nexus Status Badge and Times

**Files:**
- Modify: `PitWatch/Views/MatchRowView.swift`
- Modify: `PitWatch/Views/MatchListView.swift`

- [ ] **Step 1: Add nexusEvent parameter to MatchRowView**

In `PitWatch/Views/MatchRowView.swift`, add `nexusEvent` parameter and Nexus-aware display:

```swift
struct MatchRowView: View {
    let match: Match
    let teamKey: String
    let oprs: EventOPRs?
    let useScheduledTime: Bool
    let queueOffsetMinutes: Int
    let nexusEvent: NexusEvent?

    private var allianceColor: String? { match.allianceColor(for: teamKey) }
    private var nexusMatch: NexusMatch? { NexusMatchMerge.nexusInfo(for: match, in: nexusEvent) }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                allianceDot
                Text(match.label).font(.headline)
                if let status = nexusMatch?.status {
                    NexusStatusBadge(status: status)
                }
                Spacer()
                if let nexusMatch, !match.isPlayed {
                    nexusTimeDisplay(nexusMatch)
                } else if let date = match.matchDate(useScheduled: useScheduledTime) {
                    Text(timeText(date))
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }

            // Nexus times detail (only for upcoming matches with Nexus data)
            if let nexusMatch, !match.isPlayed {
                NexusTimesView(times: nexusMatch.times)
            }

            allianceLine(color: "red")
            allianceLine(color: "blue")

            if match.isPlayed {
                HStack {
                    Spacer()
                    let redScore = match.alliances["red"]?.score ?? 0
                    let blueScore = match.alliances["blue"]?.score ?? 0
                    Text("\(redScore)").foregroundStyle(.red).fontWeight(.bold)
                    Text("–").foregroundStyle(.secondary)
                    Text("\(blueScore)").foregroundStyle(.blue).fontWeight(.bold)

                    if match.winningAlliance == allianceColor {
                        Text("WIN").font(.caption).fontWeight(.bold).foregroundStyle(.green)
                    } else if !match.winningAlliance.isEmpty {
                        Text("LOSS").font(.caption).fontWeight(.bold).foregroundStyle(.red)
                    }
                    Spacer()
                }
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func nexusTimeDisplay(_ nexus: NexusMatch) -> some View {
        if let nextPhase = nexus.times.nextPhaseDate(after: .now) {
            VStack(alignment: .trailing, spacing: 1) {
                Text(nextPhase.date, style: .relative)
                    .font(.subheadline).fontWeight(.semibold)
                Text("to \(nextPhase.label.lowercased())")
                    .font(.caption2).foregroundStyle(.secondary)
            }
        } else if let startDate = nexus.times.startDate {
            Text(timeText(startDate))
                .font(.subheadline).foregroundStyle(.secondary)
        }
    }

    // ... existing allianceDot, allianceLine, timeText unchanged
```

- [ ] **Step 2: Create NexusStatusBadge and NexusTimesView**

Add these views at the bottom of `MatchRowView.swift`:

```swift
struct NexusStatusBadge: View {
    let status: String

    private var color: Color {
        switch status.lowercased() {
        case let s where s.contains("queuing"): return .orange
        case let s where s.contains("deck"): return .yellow
        case let s where s.contains("field"): return .green
        default: return .gray
        }
    }

    var body: some View {
        Text(status.uppercased())
            .font(.system(size: 9, weight: .bold))
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.2), in: Capsule())
            .foregroundStyle(color)
    }
}

struct NexusTimesView: View {
    let times: NexusMatchTimes

    private var phases: [(label: String, date: Date?, isPast: Bool)] {
        let now = Date.now
        return [
            ("Queue", times.queueDate, times.queueDate.map { $0 <= now } ?? false),
            ("On Deck", times.onDeckDate, times.onDeckDate.map { $0 <= now } ?? false),
            ("On Field", times.onFieldDate, times.onFieldDate.map { $0 <= now } ?? false),
            ("Start", times.startDate, times.startDate.map { $0 <= now } ?? false),
        ].filter { $0.date != nil }
    }

    private var nextPhaseIndex: Int? {
        phases.firstIndex { !$0.isPast }
    }

    var body: some View {
        if !phases.isEmpty {
            HStack(spacing: 12) {
                ForEach(Array(phases.enumerated()), id: \.offset) { index, phase in
                    VStack(spacing: 1) {
                        Text(phase.label)
                            .font(.system(size: 8))
                            .foregroundStyle(index == nextPhaseIndex ? .primary : .tertiary)
                        if let date = phase.date {
                            Text(formatTime(date))
                                .font(.system(size: 10, weight: index == nextPhaseIndex ? .bold : .regular))
                                .foregroundStyle(index == nextPhaseIndex ? .accentColor : (phase.isPast ? .tertiary : .secondary))
                        }
                        if index == nextPhaseIndex, let date = phase.date {
                            Text(date, style: .relative)
                                .font(.system(size: 8))
                                .foregroundStyle(.accentColor)
                        }
                    }
                }
            }
            .padding(.vertical, 2)
        }
    }

    private func formatTime(_ date: Date) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm"
        return fmt.string(from: date)
    }
}
```

- [ ] **Step 3: Update MatchListView to pass nexusEvent**

In `PitWatch/Views/MatchListView.swift`, update the `matchLink` function (around line 110-124) to pass `nexusEvent`:

```swift
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
                queueOffsetMinutes: config.queueOffsetMinutes,
                nexusEvent: eventCache.nexusEvent
            )
        }
        .tint(.primary)
    }
```

- [ ] **Step 4: Add "Now Queuing" banner to MatchListView**

In `PitWatch/Views/MatchListView.swift`, add the now-queuing banner at the top of `matchList` (inside the List, before the event header section):

```swift
    private var matchList: some View {
        List {
            if let nowQueuing = eventCache.nexusEvent?.nowQueuing {
                Section {
                    HStack {
                        Image(systemName: "arrow.right.circle.fill")
                            .foregroundStyle(.orange)
                        Text("Now queuing: \(nowQueuing)")
                            .font(.subheadline)
                            .fontWeight(.medium)
                    }
                }
            }

            // ... rest of existing sections unchanged
```

- [ ] **Step 5: Add Nexus fallback indicator**

In the error section of `matchList` (around line 65-71), add a fallback indicator after the existing error display:

```swift
            if config.isNexusConfigured && eventCache.nexusEvent == nil && eventCache.event != nil {
                Section {
                    Label("Nexus unavailable — showing TBA times", systemImage: "info.circle")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
```

- [ ] **Step 6: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 7: Commit**

```bash
git add PitWatch/Views/MatchRowView.swift PitWatch/Views/MatchListView.swift
git commit -m "feat: add Nexus status badge, phase times, and now-queuing banner to app UI"
```

---

### Task 11: Widget Views — Nexus Support

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`
- Modify: `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`
- Modify: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`
- Modify: `PitWatchWidgets/WidgetViews/LockScreenWidgetView.swift`

- [ ] **Step 1: Update SmallWidgetView**

In `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`, add Nexus status badge and update countdown:

Replace the "NEXT MATCH" section (lines 23-31) with:

```swift
            if let next = entry.nextMatch {
                VStack(spacing: 2) {
                    HStack(spacing: 4) {
                        Text("NEXT MATCH").font(.system(size: 10)).foregroundStyle(.secondary)
                        if let status = entry.nexusStatus {
                            Text(status.uppercased())
                                .font(.system(size: 7, weight: .bold))
                                .padding(.horizontal, 3).padding(.vertical, 1)
                                .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                                .foregroundStyle(nexusStatusColor(status))
                        }
                    }
                    Text(next.shortLabel).font(.system(size: 26, weight: .bold))
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative).font(.system(size: 12)).foregroundStyle(.secondary)
                        Text(entry.countdownLabel).font(.system(size: 9)).foregroundStyle(.tertiary)
                    }
                }.frame(maxWidth: .infinity)
```

Add the helper at the bottom of the file (outside the struct, or as a free function in SharedWidgetComponents):

```swift
func nexusStatusColor(_ status: String) -> Color {
    switch status.lowercased() {
    case let s where s.contains("queuing"): return .orange
    case let s where s.contains("deck"): return .yellow
    case let s where s.contains("field"): return .green
    default: return .gray
    }
}
```

- [ ] **Step 2: Update MediumWidgetView**

In `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`, update the next match card (lines 20-39) to show Nexus status and time:

Replace the match time display (lines 26-29) with:

```swift
                        HStack {
                            Text(next.shortLabel).font(.system(size: 16, weight: .bold))
                            if let status = entry.nexusStatus {
                                Text(status.uppercased())
                                    .font(.system(size: 7, weight: .bold))
                                    .padding(.horizontal, 3).padding(.vertical, 1)
                                    .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                                    .foregroundStyle(nexusStatusColor(status))
                            }
                            Spacer()
                            if let target = entry.countdownTarget {
                                Text(target, style: .relative)
                                    .font(.system(size: 10)).foregroundStyle(.secondary)
                            } else if let date = next.matchDate(useScheduled: entry.useScheduledTime) {
                                Text(formatMatchTime(date, prefix: entry.timePrefix))
                                    .font(.system(size: 10)).foregroundStyle(.secondary)
                            }
                        }
```

- [ ] **Step 3: Update LargeWidgetView**

In `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`:

Add now-queuing banner after the header (after line 20):

```swift
            if let nowQueuing = entry.nowQueuing {
                HStack(spacing: 4) {
                    Image(systemName: "arrow.right.circle.fill")
                        .font(.system(size: 9)).foregroundStyle(.orange)
                    Text("Queuing: \(nowQueuing)")
                        .font(.system(size: 10)).foregroundStyle(.orange)
                }
            }
```

Update the "UP NEXT" section (lines 24-30) to include Nexus status:

```swift
                    HStack {
                        Text("UP NEXT \u{2192}").font(.system(size: 10)).foregroundStyle(.secondary)
                        Text(next.label).font(.system(size: 14, weight: .bold))
                        if let status = entry.nexusStatus {
                            Text(status.uppercased())
                                .font(.system(size: 8, weight: .bold))
                                .padding(.horizontal, 4).padding(.vertical, 1)
                                .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                                .foregroundStyle(nexusStatusColor(status))
                        }
                        Spacer()
                        if let target = entry.countdownTarget {
                            Text(target, style: .relative).font(.system(size: 12)).foregroundStyle(.secondary)
                        }
                    }
```

- [ ] **Step 4: Update LockScreenWidgetView**

In `PitWatchWidgets/WidgetViews/LockScreenWidgetView.swift`, update `CircularLockScreenView` to show Nexus status. Replace lines 12-22:

```swift
                VStack(spacing: 1) {
                    HStack(spacing: 2) {
                        AllianceDot(entry.nextMatchAllianceColor, size: 5)
                        Text(next.shortLabel).font(.system(size: 9))
                    }
                    if let target = entry.countdownTarget {
                        Text(target, style: .timer)
                            .font(.system(size: 16, weight: .bold)).monospacedDigit()
                    }
                    Text(entry.countdownLabel).font(.system(size: 7)).foregroundStyle(.secondary)
                }
```

(This already uses `entry.countdownTarget` which now prefers Nexus times, so the lock screen gets Nexus times automatically.)

- [ ] **Step 5: Move nexusStatusColor to SharedWidgetComponents**

Add the `nexusStatusColor` function to `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift` at the bottom of the file (after line 78):

```swift
func nexusStatusColor(_ status: String) -> Color {
    switch status.lowercased() {
    case let s where s.contains("queuing"): return .orange
    case let s where s.contains("deck"): return .yellow
    case let s where s.contains("field"): return .green
    default: return .gray
    }
}
```

- [ ] **Step 6: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 7: Commit**

```bash
git add PitWatchWidgets/WidgetViews/SmallWidgetView.swift \
       PitWatchWidgets/WidgetViews/MediumWidgetView.swift \
       PitWatchWidgets/WidgetViews/LargeWidgetView.swift \
       PitWatchWidgets/WidgetViews/LockScreenWidgetView.swift \
       PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift
git commit -m "feat: add Nexus status badges and timing to all widget sizes"
```

---

### Task 12: Live Activity Views — Nexus Display

**Files:**
- Modify: `PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift`
- Modify: `PitWatchWidgets/LiveActivity/DynamicIslandViews.swift`

- [ ] **Step 1: Update LiveActivityLockScreenView**

In `PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift`, update the header to show Nexus status badge and update the upcoming/imminent display to show Nexus phase times.

Replace the header HStack (lines 11-15) with:

```swift
            HStack {
                Text(context.attributes.matchLabel).font(.headline)
                if let status = context.state.nexusStatus {
                    Text(status.uppercased())
                        .font(.system(size: 9, weight: .bold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                        .foregroundStyle(nexusStatusColor(status))
                }
                Spacer()
                Text(context.attributes.eventName).font(.subheadline).foregroundStyle(.secondary)
            }
```

Replace the upcoming/imminent case (lines 18-29) with:

```swift
            case .upcoming, .imminent:
                VStack(spacing: 4) {
                    // Nexus phase times if available
                    if context.state.nexusStartTime != nil {
                        NexusLiveActivityTimes(state: context.state)
                    }
                    // Countdown to next phase
                    let target = nextNexusPhaseDate(state: context.state)
                        ?? context.state.queueTime ?? context.state.matchTime
                    if let target {
                        Text(target, style: .timer)
                            .font(.system(size: 32, weight: .bold)).monospacedDigit()
                            .frame(maxWidth: .infinity, alignment: .center)
                        Text(nextNexusPhaseLabel(state: context.state)
                             ?? (context.state.queueTime != nil ? "to queue" : "to match"))
                            .font(.caption).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
```

Add helper views at the bottom of the file:

```swift
private struct NexusLiveActivityTimes: View {
    let state: MatchActivityAttributes.ContentState

    private var phases: [(label: String, date: Date, isPast: Bool)] {
        let now = Date.now
        var result: [(String, Date, Bool)] = []
        if let d = state.nexusQueueTime { result.append(("Queue", d, d <= now)) }
        if let d = state.nexusOnDeckTime { result.append(("Deck", d, d <= now)) }
        if let d = state.nexusOnFieldTime { result.append(("Field", d, d <= now)) }
        if let d = state.nexusStartTime { result.append(("Start", d, d <= now)) }
        return result
    }

    private var nextIndex: Int? { phases.firstIndex { !$0.isPast } }

    var body: some View {
        HStack(spacing: 10) {
            ForEach(Array(phases.enumerated()), id: \.offset) { index, phase in
                VStack(spacing: 1) {
                    Text(phase.label)
                        .font(.system(size: 8))
                        .foregroundStyle(index == nextIndex ? .primary : .tertiary)
                    Text(formatTime(phase.date))
                        .font(.system(size: 11, weight: index == nextIndex ? .bold : .regular))
                        .foregroundStyle(index == nextIndex ? .accentColor : (phase.isPast ? .tertiary : .secondary))
                }
            }
        }
    }

    private func formatTime(_ date: Date) -> String {
        let fmt = DateFormatter()
        fmt.dateFormat = "h:mm"
        return fmt.string(from: date)
    }
}

private func nextNexusPhaseDate(state: MatchActivityAttributes.ContentState) -> Date? {
    let now = Date.now
    let phases: [Date?] = [
        state.nexusQueueTime, state.nexusOnDeckTime,
        state.nexusOnFieldTime, state.nexusStartTime
    ]
    return phases.compactMap { $0 }.first { $0 > now }
}

private func nextNexusPhaseLabel(state: MatchActivityAttributes.ContentState) -> String? {
    let now = Date.now
    let phases: [(String, Date?)] = [
        ("to queue", state.nexusQueueTime),
        ("to on deck", state.nexusOnDeckTime),
        ("to on field", state.nexusOnFieldTime),
        ("to start", state.nexusStartTime),
    ]
    return phases.first { _, date in
        guard let date else { return false }
        return date > now
    }?.0
}
```

- [ ] **Step 2: Update DynamicIslandViews**

In `PitWatchWidgets/LiveActivity/DynamicIslandViews.swift`, update the compact trailing view to prefer Nexus countdown.

Replace the upcoming/imminent case in `compactTrailingView` (lines 64-75) with:

```swift
        case .upcoming, .imminent:
            let target = nextNexusPhaseDate(state: context.state)
                ?? context.state.queueTime ?? context.state.matchTime
            if let target {
                Text(target, style: .timer)
                    .font(.system(size: 12, weight: .bold))
                    .monospacedDigit()
            } else {
                Text("--")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(.secondary)
            }
```

Update the expanded trailing (lines 92-107) to show Nexus status badge and phase countdown:

```swift
        case .upcoming, .imminent:
            VStack(alignment: .trailing) {
                if let status = context.state.nexusStatus {
                    Text(status.uppercased())
                        .font(.system(size: 8, weight: .bold))
                        .padding(.horizontal, 4).padding(.vertical, 1)
                        .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                        .foregroundStyle(nexusStatusColor(status))
                }
                let target = nextNexusPhaseDate(state: context.state)
                    ?? context.state.queueTime ?? context.state.matchTime
                if let target {
                    Text(target, style: .timer)
                        .font(.system(size: 18, weight: .bold))
                        .monospacedDigit()
                    Text(nextNexusPhaseLabel(state: context.state)
                         ?? (context.state.queueTime != nil ? "to queue" : "to match"))
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }
```

Add the same helper functions at the bottom of `DynamicIslandViews.swift` (these are file-private, same as in LiveActivityLockScreenView):

```swift
private func nextNexusPhaseDate(state: MatchActivityAttributes.ContentState) -> Date? {
    let now = Date.now
    let phases: [Date?] = [
        state.nexusQueueTime, state.nexusOnDeckTime,
        state.nexusOnFieldTime, state.nexusStartTime
    ]
    return phases.compactMap { $0 }.first { $0 > now }
}

private func nextNexusPhaseLabel(state: MatchActivityAttributes.ContentState) -> String? {
    let now = Date.now
    let phases: [(String, Date?)] = [
        ("to queue", state.nexusQueueTime),
        ("to on deck", state.nexusOnDeckTime),
        ("to on field", state.nexusOnFieldTime),
        ("to start", state.nexusStartTime),
    ]
    return phases.first { _, date in
        guard let date else { return false }
        return date > now
    }?.0
}
```

- [ ] **Step 3: Build to verify compilation**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds.

- [ ] **Step 4: Commit**

```bash
git add PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift \
       PitWatchWidgets/LiveActivity/DynamicIslandViews.swift
git commit -m "feat: show Nexus status and phase times in Live Activity views"
```

---

### Task 13: Final Integration — Run Full Test Suite and Build

**Files:** None new — verification only.

- [ ] **Step 1: Run the full test suite**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && swift test 2>&1 | tail -30`
Expected: All tests pass.

- [ ] **Step 2: Build all targets**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -20`
Expected: Build succeeds with no errors.

- [ ] **Step 3: Verify no warnings**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' 2>&1 | grep -i warning | head -20`
Expected: No new warnings introduced.

- [ ] **Step 4: Commit any remaining fixes**

If any issues were found and fixed:
```bash
git add -A
git commit -m "fix: resolve build warnings and test issues from Nexus integration"
```
