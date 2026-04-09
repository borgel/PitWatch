# UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace all Live Activity, Dynamic Island, and watchOS complication views with the phase-focused design from `docs/superpowers/specs/2026-04-08-ui-redesign-design.md`.

**Architecture:** New `Phase` and `MatchAlliance` enums in TBAKit provide the shared data model. A `PhaseDerivation` module computes the current phase from Nexus time estimates. New SwiftUI views implement the spec's layouts pixel-for-pixel. `LiveActivityManager` is reworked for one-activity-per-match lifecycle. Old views are deleted.

**Tech Stack:** SwiftUI, ActivityKit, WidgetKit, TBAKit (Swift Package, swift-tools-version 6.0)

---

## File Structure

### New files

| File | Responsibility |
|------|---------------|
| `TBAKit/Sources/TBAKit/Extensions/ColorExtensions.swift` | `Color(hex:)` initializer |
| `TBAKit/Sources/TBAKit/Models/Phase.swift` | `Phase` enum (preQueue/queueing/onDeck/onField) with labels and colors |
| `TBAKit/Sources/TBAKit/Models/MatchAlliance.swift` | `MatchAlliance` enum (blue/red) with badge and dot colors |
| `TBAKit/Sources/TBAKit/LiveActivity/FRCMatchAttributes.swift` | New `ActivityAttributes` with `Phase`-based `ContentState` |
| `TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift` | Derive `Phase` + deadline from `NexusMatch` times |
| `TBAKit/Tests/TBAKitTests/PhaseTests.swift` | Tests for Phase, MatchAlliance, and matches-away logic |
| `TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift` | Tests for phase derivation from Nexus data |
| `PitWatchWidgets/LiveActivity/ChevronBar.swift` | `ChevronShape`, `ChevronSegment`, `ChevronBar` views |
| `PitWatchWidgets/LiveActivity/ExpandedLiveActivityView.swift` | Lock screen Live Activity layout |
| `PitWatchWidgets/LiveActivity/DynamicIslandView.swift` | Collapsed Dynamic Island layout |

### Modified files

| File | Change |
|------|--------|
| `PitWatchWidgets/LiveActivity/MatchLiveActivityWidget.swift` | Point to new view types |
| `TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift` | Full rewrite for new attributes + lifecycle |
| `PitWatchWatchWidgets/ComplicationViews.swift` | Full rewrite for new circular + rectangular |
| `PitWatchWatchWidgets/WatchComplicationProvider.swift` | New timeline entry, derive Phase from Nexus |
| `PitWatchWatchWidgets/PitWatchWatchWidgetBundle.swift` | Drop `.accessoryCorner` |

### Deleted files

| File | Reason |
|------|--------|
| `PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift` | Replaced by `ExpandedLiveActivityView.swift` |
| `PitWatchWidgets/LiveActivity/DynamicIslandViews.swift` | Replaced by `DynamicIslandView.swift` |
| `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift` | Replaced by `FRCMatchAttributes.swift` |

---

## Task 1: Foundation Types — Color(hex:), Phase, MatchAlliance

**Files:**
- Create: `TBAKit/Sources/TBAKit/Extensions/ColorExtensions.swift`
- Create: `TBAKit/Sources/TBAKit/Models/Phase.swift`
- Create: `TBAKit/Sources/TBAKit/Models/MatchAlliance.swift`
- Test: `TBAKit/Tests/TBAKitTests/PhaseTests.swift`

- [ ] **Step 1: Write tests for Phase and MatchAlliance**

```swift
// TBAKit/Tests/TBAKitTests/PhaseTests.swift
import Testing
import SwiftUI
@testable import TBAKit

@Suite("Phase enum")
struct PhaseEnumTests {
    @Test("rawValue ordering is sequential")
    func rawValues() {
        #expect(Phase.preQueue.rawValue == 0)
        #expect(Phase.queueing.rawValue == 1)
        #expect(Phase.onDeck.rawValue == 2)
        #expect(Phase.onField.rawValue == 3)
    }

    @Test("labels match spec")
    func labels() {
        #expect(Phase.preQueue.label == "PRE")
        #expect(Phase.queueing.label == "QUEUE")
        #expect(Phase.onDeck.label == "DECK")
        #expect(Phase.onField.label == "FIELD")
    }

    @Test("sublabels match spec")
    func sublabels() {
        #expect(Phase.preQueue.sublabel == "UNTIL QUEUEING")
        #expect(Phase.queueing.sublabel == "UNTIL ON DECK")
        #expect(Phase.onDeck.sublabel == "UNTIL ON FIELD")
        #expect(Phase.onField.sublabel == "MATCH IN PROGRESS")
    }

    @Test("combinedLabel joins label and sublabel")
    func combinedLabel() {
        #expect(Phase.queueing.combinedLabel == "QUEUE · UNTIL ON DECK")
        #expect(Phase.onField.combinedLabel == "FIELD · MATCH IN PROGRESS")
    }

    @Test("CaseIterable has four phases")
    func allCases() {
        #expect(Phase.allCases.count == 4)
    }

    @Test("Codable round-trip preserves value")
    func codable() throws {
        let original = Phase.onDeck
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(Phase.self, from: data)
        #expect(decoded == original)
    }
}

@Suite("MatchAlliance enum")
struct MatchAllianceTests {
    @Test("displayName is uppercased")
    func displayName() {
        #expect(MatchAlliance.blue.displayName == "BLUE")
        #expect(MatchAlliance.red.displayName == "RED")
    }

    @Test("Codable round-trip preserves value")
    func codable() throws {
        let original = MatchAlliance.red
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(MatchAlliance.self, from: data)
        #expect(decoded == original)
    }
}

@Suite("Matches-away display")
struct MatchesAwayTests {
    @Test("gap >= 2 shows X AWAY")
    func multipleAway() {
        #expect(MatchesAwayDisplay.text(for: 5) == "5 AWAY")
        #expect(MatchesAwayDisplay.text(for: 2) == "2 AWAY")
    }

    @Test("gap == 1 shows NEXT")
    func next() {
        #expect(MatchesAwayDisplay.text(for: 1) == "NEXT")
    }

    @Test("gap == 0 shows NOW")
    func now() {
        #expect(MatchesAwayDisplay.text(for: 0) == "NOW")
    }

    @Test("negative gap shows NOW")
    func negative() {
        #expect(MatchesAwayDisplay.text(for: -1) == "NOW")
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd TBAKit && swift test --filter PhaseTests 2>&1 | tail -5`
Expected: Compilation error — types not defined yet.

- [ ] **Step 3: Implement Color(hex:) extension**

```swift
// TBAKit/Sources/TBAKit/Extensions/ColorExtensions.swift
import SwiftUI

extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        let scanner = Scanner(string: hex)
        var rgbValue: UInt64 = 0
        scanner.scanHexInt64(&rgbValue)
        self.init(
            red: Double((rgbValue & 0xFF0000) >> 16) / 255.0,
            green: Double((rgbValue & 0x00FF00) >> 8) / 255.0,
            blue: Double(rgbValue & 0x0000FF) / 255.0
        )
    }
}
```

- [ ] **Step 4: Implement Phase enum**

```swift
// TBAKit/Sources/TBAKit/Models/Phase.swift
import SwiftUI

public enum Phase: Int, Codable, CaseIterable, Identifiable, Sendable, Hashable {
    case preQueue = 0
    case queueing = 1
    case onDeck   = 2
    case onField  = 3

    public var id: Int { rawValue }

    public var label: String {
        switch self {
        case .preQueue: return "PRE"
        case .queueing: return "QUEUE"
        case .onDeck:   return "DECK"
        case .onField:  return "FIELD"
        }
    }

    public var sublabel: String {
        switch self {
        case .preQueue: return "UNTIL QUEUEING"
        case .queueing: return "UNTIL ON DECK"
        case .onDeck:   return "UNTIL ON FIELD"
        case .onField:  return "MATCH IN PROGRESS"
        }
    }

    public var combinedLabel: String { "\(label) · \(sublabel)" }

    public var color: Color {
        switch self {
        case .preQueue: return Color(hex: "#636366")
        case .queueing: return Color(hex: "#FF9500")
        case .onDeck:   return Color(hex: "#FF6B00")
        case .onField:  return Color(hex: "#30D158")
        }
    }
}
```

- [ ] **Step 5: Implement MatchAlliance enum**

```swift
// TBAKit/Sources/TBAKit/Models/MatchAlliance.swift
import SwiftUI

public enum MatchAlliance: String, Codable, Sendable, Hashable {
    case blue, red

    public var displayName: String {
        switch self {
        case .blue: return "BLUE"
        case .red:  return "RED"
        }
    }

    public var badgeText: Color {
        switch self {
        case .blue: return Color(hex: "#4DA6FF")
        case .red:  return Color(hex: "#FF6B6B")
        }
    }

    public var badgeBackground: Color {
        switch self {
        case .blue: return Color(red: 0, green: 122/255, blue: 255/255).opacity(0.18)
        case .red:  return Color(red: 255/255, green: 59/255, blue: 48/255).opacity(0.18)
        }
    }

    public var dotColor: Color {
        switch self {
        case .blue: return Color(hex: "#1E6FFF")
        case .red:  return Color(hex: "#FF3B30")
        }
    }
}
```

- [ ] **Step 6: Implement MatchesAwayDisplay helper**

Add to the bottom of `Phase.swift`:

```swift
public enum MatchesAwayDisplay {
    public static func text(for gap: Int) -> String {
        switch gap {
        case ...0: return "NOW"
        case 1:    return "NEXT"
        default:   return "\(gap) AWAY"
        }
    }

    public static func color(for gap: Int, phase: Phase) -> Color {
        switch gap {
        case ...0: return Color(hex: "#30D158").opacity(0.65)
        case 1:    return phase.color.opacity(0.65)
        default:   return Color.white.opacity(0.50)
        }
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `cd TBAKit && swift test --filter "PhaseTests|MatchAllianceTests|MatchesAwayTests" 2>&1 | tail -10`
Expected: All tests pass.

- [ ] **Step 8: Commit**

```bash
git add TBAKit/Sources/TBAKit/Extensions/ColorExtensions.swift \
       TBAKit/Sources/TBAKit/Models/Phase.swift \
       TBAKit/Sources/TBAKit/Models/MatchAlliance.swift \
       TBAKit/Tests/TBAKitTests/PhaseTests.swift
git commit -m "feat: add Phase, MatchAlliance, Color(hex:) foundation types"
```

---

## Task 2: Phase Derivation from Nexus Data

**Files:**
- Create: `TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift`
- Test: `TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift`

- [ ] **Step 1: Write tests for phase derivation**

```swift
// TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift
import Testing
import Foundation
@testable import TBAKit

@Suite("PhaseDerivation")
struct PhaseDerivationTests {
    // Helper: create NexusMatch with times at offsets from reference date
    private func makeNexusMatch(
        queueOffset: TimeInterval? = nil,
        onDeckOffset: TimeInterval? = nil,
        onFieldOffset: TimeInterval? = nil,
        startOffset: TimeInterval? = nil,
        status: String? = nil,
        reference: Date = Date(timeIntervalSince1970: 1000)
    ) -> NexusMatch {
        func ms(_ offset: TimeInterval?) -> Int64? {
            guard let offset else { return nil }
            return Int64((reference.timeIntervalSince1970 + offset) * 1000)
        }
        return NexusMatch(
            label: "Qualification 1",
            status: status,
            redTeams: ["1", "2", "3"],
            blueTeams: ["4", "5", "6"],
            times: NexusMatchTimes(
                estimatedQueueTime: ms(queueOffset),
                estimatedOnDeckTime: ms(onDeckOffset),
                estimatedOnFieldTime: ms(onFieldOffset),
                estimatedStartTime: ms(startOffset),
                actualQueueTime: nil
            )
        )
    }

    @Test("all times in future → preQueue with deadline = queue time")
    func allFuture() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: 300, onDeckOffset: 600,
            onFieldOffset: 900, startOffset: 1200, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .preQueue)
        #expect(result.deadline == nexus.times.queueDate)
    }

    @Test("queue time passed, on-deck in future → queueing")
    func queuePassed() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: -60, onDeckOffset: 300,
            onFieldOffset: 600, startOffset: 900, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .queueing)
        #expect(result.deadline == nexus.times.onDeckDate)
    }

    @Test("on-deck time passed, on-field in future → onDeck")
    func onDeckPassed() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: -300, onDeckOffset: -60,
            onFieldOffset: 300, startOffset: 600, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .onDeck)
        #expect(result.deadline == nexus.times.onFieldDate)
    }

    @Test("on-field time passed → onField with deadline = start + 150s")
    func onFieldPassed() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: -600, onDeckOffset: -300,
            onFieldOffset: -120, startOffset: -60, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .onField)
        // Deadline = startDate + 150s
        let expected = nexus.times.startDate!.addingTimeInterval(150)
        #expect(result.deadline == expected)
    }

    @Test("Nexus status 'On Field' overrides time-based derivation")
    func statusOverride() {
        let ref = Date(timeIntervalSince1970: 1000)
        // Times say preQueue but status says On Field
        let nexus = makeNexusMatch(
            queueOffset: 300, onDeckOffset: 600,
            onFieldOffset: 900, startOffset: 1200,
            status: "On Field", reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .onField)
    }

    @Test("no Nexus times → preQueue with nil deadline")
    func noTimes() {
        let nexus = NexusMatch(
            label: "Qualification 1", status: nil,
            redTeams: ["1", "2", "3"], blueTeams: ["4", "5", "6"],
            times: NexusMatchTimes(
                estimatedQueueTime: nil, estimatedOnDeckTime: nil,
                estimatedOnFieldTime: nil, estimatedStartTime: nil,
                actualQueueTime: nil
            )
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: .now)
        #expect(result.phase == .preQueue)
        #expect(result.deadline == nil)
    }

    @Test("currentMatchOnField derived from NexusEvent nowQueuing")
    func currentMatchOnField() {
        let event = NexusEvent(
            dataAsOfTime: 0,
            nowQueuing: "Qualification 45",
            matches: []
        )
        // "Qualification 45" means Q45 is queuing, so the match on field
        // is roughly 2-3 matches behind. But we need the actual on-field
        // match. Since Nexus doesn't have a direct "nowOnField" field,
        // we derive it from match statuses.
        let onFieldMatch = NexusMatch(
            label: "Qualification 42", status: "On Field",
            redTeams: [], blueTeams: [],
            times: NexusMatchTimes(
                estimatedQueueTime: nil, estimatedOnDeckTime: nil,
                estimatedOnFieldTime: nil, estimatedStartTime: nil,
                actualQueueTime: nil
            )
        )
        let result = PhaseDerivation.currentMatchOnField(
            matches: [onFieldMatch],
            fallbackMatchNumber: 1
        )
        #expect(result == 42)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd TBAKit && swift test --filter PhaseDerivation 2>&1 | tail -5`
Expected: Compilation error — `PhaseDerivation` not defined.

- [ ] **Step 3: Implement PhaseDerivation**

```swift
// TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift
import Foundation

public enum PhaseDerivation {
    public struct Result: Sendable {
        public let phase: Phase
        public let deadline: Date?
        public let phaseStartDate: Date
    }

    /// Derive the current phase and countdown deadline from Nexus match data.
    ///
    /// Priority: Nexus discrete status > time-based derivation.
    /// If Nexus provides a `status` string, it takes precedence.
    /// Otherwise, we compare Nexus estimated times against `now`.
    public static func derivePhase(
        from nexusMatch: NexusMatch,
        now: Date = .now
    ) -> Result {
        // Check discrete status first (takes priority per spec §9.2)
        if let status = nexusMatch.status?.lowercased() {
            if status.contains("field") || status.contains("playing") {
                let deadline = nexusMatch.times.startDate
                    .map { $0.addingTimeInterval(150) }
                return Result(
                    phase: .onField,
                    deadline: deadline,
                    phaseStartDate: nexusMatch.times.onFieldDate ?? now
                )
            }
            if status.contains("deck") {
                return Result(
                    phase: .onDeck,
                    deadline: nexusMatch.times.onFieldDate,
                    phaseStartDate: nexusMatch.times.onDeckDate ?? now
                )
            }
            if status.contains("queuing") || status.contains("queue") {
                return Result(
                    phase: .queueing,
                    deadline: nexusMatch.times.onDeckDate,
                    phaseStartDate: nexusMatch.times.queueDate ?? now
                )
            }
        }

        // Time-based derivation: find the most advanced phase that has passed
        let times = nexusMatch.times

        if let onFieldDate = times.onFieldDate, onFieldDate <= now {
            let deadline = times.startDate.map { $0.addingTimeInterval(150) }
            return Result(phase: .onField, deadline: deadline, phaseStartDate: onFieldDate)
        }
        if let onDeckDate = times.onDeckDate, onDeckDate <= now {
            return Result(phase: .onDeck, deadline: times.onFieldDate, phaseStartDate: onDeckDate)
        }
        if let queueDate = times.queueDate, queueDate <= now {
            return Result(phase: .queueing, deadline: times.onDeckDate, phaseStartDate: queueDate)
        }

        // Nothing has passed yet — preQueue
        return Result(phase: .preQueue, deadline: times.queueDate, phaseStartDate: now)
    }

    /// Find the match number currently on the field by scanning Nexus match statuses.
    /// Falls back to extracting from the "nowQueuing" label if no explicit on-field match.
    public static func currentMatchOnField(
        matches: [NexusMatch],
        fallbackMatchNumber: Int
    ) -> Int {
        // Find the match with "On Field" or "Playing" status
        if let onField = matches.last(where: {
            guard let status = $0.status?.lowercased() else { return false }
            return status.contains("field") || status.contains("playing")
        }) {
            return extractMatchNumber(from: onField.label)
                ?? fallbackMatchNumber
        }
        return fallbackMatchNumber
    }

    /// Extract match number from a Nexus label like "Qualification 42".
    public static func extractMatchNumber(from label: String) -> Int? {
        let parts = label.split(separator: " ")
        return parts.last.flatMap { Int($0) }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd TBAKit && swift test --filter PhaseDerivation 2>&1 | tail -10`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift \
       TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift
git commit -m "feat: add PhaseDerivation to compute phase from Nexus data"
```

---

## Task 3: FRCMatchAttributes

**Files:**
- Create: `TBAKit/Sources/TBAKit/LiveActivity/FRCMatchAttributes.swift`

- [ ] **Step 1: Create the new ActivityAttributes type**

```swift
// TBAKit/Sources/TBAKit/LiveActivity/FRCMatchAttributes.swift
import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

public struct FRCMatchAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable, Sendable {
        public var currentPhase: Phase
        public var phaseStartDate: Date
        public var phaseDeadline: Date
        public var currentMatchOnField: Int
        public var lastUpdated: Date

        public init(
            currentPhase: Phase,
            phaseStartDate: Date,
            phaseDeadline: Date,
            currentMatchOnField: Int,
            lastUpdated: Date
        ) {
            self.currentPhase = currentPhase
            self.phaseStartDate = phaseStartDate
            self.phaseDeadline = phaseDeadline
            self.currentMatchOnField = currentMatchOnField
            self.lastUpdated = lastUpdated
        }

        public var phaseProgress: Double {
            let elapsed = Date().timeIntervalSince(phaseStartDate)
            let total = phaseDeadline.timeIntervalSince(phaseStartDate)
            guard total > 0 else { return 0 }
            return min(max(elapsed / total, 0), 1)
        }
    }

    public let teamNumber: Int
    public let matchNumber: Int
    public let matchLabel: String
    public let alliance: MatchAlliance

    public init(teamNumber: Int, matchNumber: Int, matchLabel: String, alliance: MatchAlliance) {
        self.teamNumber = teamNumber
        self.matchNumber = matchNumber
        self.matchLabel = matchLabel
        self.alliance = alliance
    }

    /// Compute the matches-away gap from the dynamic content state.
    public func matchesAway(currentOnField: Int) -> Int {
        matchNumber - currentOnField
    }
}
#endif
```

- [ ] **Step 2: Verify the project builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5`
Expected: Build succeeds (or only pre-existing warnings).

- [ ] **Step 3: Commit**

```bash
git add TBAKit/Sources/TBAKit/LiveActivity/FRCMatchAttributes.swift
git commit -m "feat: add FRCMatchAttributes with phase-based content state"
```

---

## Task 4: Chevron Phase Bar

**Files:**
- Create: `PitWatchWidgets/LiveActivity/ChevronBar.swift`

- [ ] **Step 1: Implement ChevronShape**

```swift
// PitWatchWidgets/LiveActivity/ChevronBar.swift
import SwiftUI
import TBAKit

struct ChevronShape: Shape {
    let arrowDepth: CGFloat
    let isLast: Bool

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let tipX = isLast ? rect.maxX : rect.maxX - arrowDepth
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: tipX, y: rect.minY))
        if !isLast {
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        }
        path.addLine(to: CGPoint(x: tipX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}
```

- [ ] **Step 2: Implement ChevronSegment with all four states**

Append to `ChevronBar.swift`:

```swift
private enum SegmentState {
    case completed, active, nextPending, farPending
}

private func segmentState(phase: Phase, currentPhase: Phase) -> SegmentState {
    if phase.rawValue < currentPhase.rawValue { return .completed }
    if phase == currentPhase { return .active }
    if phase.rawValue == currentPhase.rawValue + 1 { return .nextPending }
    return .farPending
}

struct ChevronSegment: View {
    let phase: Phase
    let currentPhase: Phase
    let deadline: Date
    let arrowDepth: CGFloat

    private var state: SegmentState { segmentState(phase: phase, currentPhase: currentPhase) }

    private var backgroundColor: Color {
        switch state {
        case .completed:   return Color(hex: "#30D158").opacity(0.22)
        case .active:      return phase.color
        case .nextPending: return Color(hex: "#2A2A2A")
        case .farPending:  return Color(hex: "#222222")
        }
    }

    var body: some View {
        ZStack {
            backgroundColor

            // Text centering: offset content to center within the visible face.
            // Segment 0: face is left-flush, so pad trailing by D.
            // Segments 1-2: face is inset by D on both sides.
            // Segment 3: face is right-flush, so pad leading by D.
            let isFirst = phase == .preQueue
            let isLast = phase == .onField

            Group {
                switch state {
                case .completed:
                    HStack(spacing: 4) {
                        Image(systemName: "checkmark.circle")
                            .font(.system(size: 12))
                        Text(phase.label)
                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                            .tracking(0.5)
                    }
                    .foregroundStyle(Color(hex: "#30D158").opacity(0.65))

                case .active:
                    VStack(spacing: 1) {
                        Text(phase.label)
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .tracking(0.5)
                            .foregroundStyle(.black)
                        Text(deadline, style: .timer)
                            .font(.system(size: 9, weight: .medium, design: .monospaced))
                            .foregroundStyle(.black.opacity(0.50))
                            .monospacedDigit()
                    }

                case .nextPending:
                    Text(phase.label)
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.22))

                case .farPending:
                    Text(phase.label)
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.12))
                }
            }
            .padding(.leading, isFirst ? 0 : arrowDepth)
            .padding(.trailing, isLast ? 0 : arrowDepth)
        }
    }
}
```

- [ ] **Step 3: Implement ChevronBar container**

Append to `ChevronBar.swift`:

```swift
struct ChevronBar: View {
    let currentPhase: Phase
    let deadline: Date

    var body: some View {
        GeometryReader { geo in
            let D: CGFloat = 16
            let n = CGFloat(Phase.allCases.count)
            let visibleWidth = (geo.size.width - D) / n
            let segmentWidth = visibleWidth + D

            ZStack(alignment: .topLeading) {
                ForEach(Phase.allCases) { phase in
                    ChevronSegment(
                        phase: phase,
                        currentPhase: currentPhase,
                        deadline: deadline,
                        arrowDepth: D
                    )
                    .frame(width: segmentWidth, height: geo.size.height)
                    .clipShape(ChevronShape(
                        arrowDepth: D,
                        isLast: phase == .onField
                    ))
                    .offset(x: CGFloat(phase.rawValue) * visibleWidth)
                    .zIndex(Double(Phase.allCases.count - phase.rawValue))
                }
            }
        }
        .frame(height: 48)
    }
}
```

- [ ] **Step 4: Verify project builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5`
Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add PitWatchWidgets/LiveActivity/ChevronBar.swift
git commit -m "feat: add ChevronBar phase progress component"
```

---

## Task 5: Expanded Live Activity View

**Files:**
- Create: `PitWatchWidgets/LiveActivity/ExpandedLiveActivityView.swift`

- [ ] **Step 1: Implement the header row**

```swift
// PitWatchWidgets/LiveActivity/ExpandedLiveActivityView.swift
import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

struct ExpandedLiveActivityView: View {
    let context: ActivityViewContext<FRCMatchAttributes>

    private var state: FRCMatchAttributes.ContentState { context.state }
    private var attrs: FRCMatchAttributes { context.attributes }
    private var isOnField: Bool { state.currentPhase == .onField }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            headerRow
                .padding(.bottom, 10)
            heroCountdownRow
                .padding(.bottom, 12)
            ChevronBar(
                currentPhase: state.currentPhase,
                deadline: state.phaseDeadline
            )
        }
        .padding(.top, 11)
        .padding(.bottom, 12)
        .padding(.horizontal, 14)
        .background(cardBackground)
    }

    // MARK: - Header Row

    private var headerRow: some View {
        HStack(spacing: 8) {
            // Team number
            Text("\(attrs.teamNumber)")
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)

            // Alliance badge
            Text("\(attrs.alliance.displayName) · Q\(attrs.matchNumber)")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(attrs.alliance.badgeText)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(attrs.alliance.badgeBackground)
                )

            Spacer()

            // Live indicator
            HStack(spacing: 4) {
                Circle()
                    .fill(Color(hex: "#FF9500"))
                    .frame(width: 5, height: 5)
                Text("Live")
                    .font(.system(size: 10.5))
                    .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.45))
            }
        }
    }

    // MARK: - Hero Countdown Row

    private var heroCountdownRow: some View {
        HStack(alignment: .top, spacing: 12) {
            // Left column: phase label + countdown
            VStack(alignment: .leading, spacing: 0) {
                Text(state.currentPhase.combinedLabel)
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .tracking(1.0)
                    .foregroundStyle(state.currentPhase.color)

                Text(state.phaseDeadline, style: .timer)
                    .font(.system(size: 50, weight: .bold, design: .monospaced))
                    .kerning(-2)
                    .foregroundStyle(.white)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }

            Spacer()

            // Right column: matches-away context
            let gap = attrs.matchesAway(currentOnField: state.currentMatchOnField)
            VStack(alignment: .trailing, spacing: 0) {
                Text("YOUR MATCH")
                    .font(.system(size: 9, weight: .regular, design: .monospaced))
                    .tracking(0.5)
                    .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.35))

                Text(MatchesAwayDisplay.text(for: gap))
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundStyle(MatchesAwayDisplay.color(for: gap, phase: state.currentPhase))
                    .padding(.top, 2)
            }
        }
    }

    // MARK: - Card Background

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 22)
            .fill(isOnField ? Color(hex: "#112214") : Color(hex: "#1C1C1E"))
            .overlay(
                RoundedRectangle(cornerRadius: 22)
                    .strokeBorder(
                        isOnField
                            ? Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20)
                            : Color.clear,
                        lineWidth: 0.5
                    )
            )
    }
}
```

- [ ] **Step 2: Verify project builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add PitWatchWidgets/LiveActivity/ExpandedLiveActivityView.swift
git commit -m "feat: add expanded Live Activity view with hero countdown and chevron bar"
```

---

## Task 6: Dynamic Island View

**Files:**
- Create: `PitWatchWidgets/LiveActivity/DynamicIslandView.swift`

- [ ] **Step 1: Implement the collapsed Dynamic Island**

```swift
// PitWatchWidgets/LiveActivity/DynamicIslandView.swift
import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

enum FRCDynamicIsland {
    static func build(for context: ActivityViewContext<FRCMatchAttributes>) -> DynamicIsland {
        DynamicIsland {
            // Expanded regions — mirror the compact layout at larger sizes
            DynamicIslandExpandedRegion(.leading) {
                phaseColumn(context: context)
            }
            DynamicIslandExpandedRegion(.trailing) {
                HStack(spacing: 12) {
                    nowColumn(context: context)
                    yourMatchColumn(context: context)
                    allianceDot(context: context)
                }
            }
            DynamicIslandExpandedRegion(.bottom) {
                EmptyView()
            }
        } compactLeading: {
            compactLeading(context: context)
        } compactTrailing: {
            compactTrailing(context: context)
        } minimal: {
            Circle()
                .fill(context.attributes.alliance.dotColor)
                .frame(width: 6, height: 6)
        }
    }

    // MARK: - Compact Leading

    @ViewBuilder
    private static func compactLeading(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        HStack(spacing: 0) {
            phaseColumn(context: context)
            divider()
            nowColumn(context: context)
        }
    }

    // MARK: - Compact Trailing

    @ViewBuilder
    private static func compactTrailing(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        HStack(spacing: 0) {
            yourMatchColumn(context: context)
            Circle()
                .fill(context.attributes.alliance.dotColor)
                .frame(width: 7, height: 7)
                .padding(.leading, 6)
        }
    }

    // MARK: - Columns

    private static func phaseColumn(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            HStack(spacing: 4) {
                Circle()
                    .fill(context.state.currentPhase.color)
                    .frame(width: 6, height: 6)
                Text(context.state.currentPhase.label)
                    .font(.system(size: 8.5, weight: .semibold, design: .monospaced))
                    .tracking(0.4)
                    .foregroundStyle(context.state.currentPhase.color)
            }
            Text(context.state.phaseDeadline, style: .timer)
                .font(.system(size: 15, weight: .bold, design: .monospaced))
                .kerning(-0.5)
                .foregroundStyle(.white)
                .monospacedDigit()
        }
        .padding(.trailing, 6)
    }

    private static func nowColumn(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("NOW")
                .font(.system(size: 8, weight: .regular, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(.white.opacity(0.30))
            Text("Q \(context.state.currentMatchOnField)")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundStyle(.white.opacity(0.50))
        }
        .padding(.horizontal, 6)
    }

    private static func yourMatchColumn(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text("YOUR MATCH")
                .font(.system(size: 8, weight: .regular, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(context.state.currentPhase.color)
            Text("Q \(context.attributes.matchNumber)")
                .font(.system(size: 13, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
        }
        .padding(.leading, 6)
    }

    private static func allianceDot(
        context: ActivityViewContext<FRCMatchAttributes>
    ) -> some View {
        Circle()
            .fill(context.attributes.alliance.dotColor)
            .frame(width: 7, height: 7)
    }

    private static func divider() -> some View {
        Rectangle()
            .fill(.white.opacity(0.10))
            .frame(width: 0.5, height: 30)
    }
}
```

- [ ] **Step 2: Verify project builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add PitWatchWidgets/LiveActivity/DynamicIslandView.swift
git commit -m "feat: add Dynamic Island view with three-column layout"
```

---

## Task 7: Wire Up MatchLiveActivityWidget

**Files:**
- Modify: `PitWatchWidgets/LiveActivity/MatchLiveActivityWidget.swift`

- [ ] **Step 1: Update widget to use new view types**

Replace the entire contents of `MatchLiveActivityWidget.swift`:

```swift
import SwiftUI
import WidgetKit
import ActivityKit
import TBAKit

struct MatchLiveActivityWidget: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: FRCMatchAttributes.self) { context in
            ExpandedLiveActivityView(context: context)
        } dynamicIsland: { context in
            FRCDynamicIsland.build(for: context)
        }
    }
}
```

- [ ] **Step 2: Verify project builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5`
Expected: Build succeeds.

- [ ] **Step 3: Commit**

```bash
git add PitWatchWidgets/LiveActivity/MatchLiveActivityWidget.swift
git commit -m "feat: wire MatchLiveActivityWidget to new FRCMatchAttributes views"
```

---

## Task 8: Watch Complications — Provider and Views

**Files:**
- Modify: `PitWatchWatchWidgets/WatchComplicationProvider.swift`
- Modify: `PitWatchWatchWidgets/ComplicationViews.swift`
- Modify: `PitWatchWatchWidgets/PitWatchWatchWidgetBundle.swift`

- [ ] **Step 1: Create new timeline entry and update provider**

Replace the entire contents of `WatchComplicationProvider.swift`:

```swift
import WidgetKit
import SwiftUI
import TBAKit

struct PhaseComplicationEntry: TimelineEntry {
    let date: Date
    let teamNumber: Int?
    let matchNumber: Int?
    let matchLabel: String?
    let alliance: MatchAlliance?
    let phase: Phase?
    let phaseDeadline: Date?
    let phaseStartDate: Date?

    static var placeholder: PhaseComplicationEntry {
        PhaseComplicationEntry(
            date: .now, teamNumber: 1234, matchNumber: 42,
            matchLabel: "Q42", alliance: .blue, phase: .queueing,
            phaseDeadline: .now.addingTimeInterval(300),
            phaseStartDate: .now.addingTimeInterval(-60)
        )
    }

    var phaseProgress: Double {
        guard let start = phaseStartDate, let end = phaseDeadline else { return 0 }
        let elapsed = Date().timeIntervalSince(start)
        let total = end.timeIntervalSince(start)
        guard total > 0 else { return 0 }
        return min(max(elapsed / total, 0), 1)
    }
}

struct WatchComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> PhaseComplicationEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (PhaseComplicationEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PhaseComplicationEntry>) -> Void) {
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

    private func makeEntry() -> PhaseComplicationEntry {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        guard let next = schedule.nextMatch else {
            return PhaseComplicationEntry(
                date: .now, teamNumber: config.teamNumber, matchNumber: nil,
                matchLabel: nil, alliance: nil, phase: nil,
                phaseDeadline: nil, phaseStartDate: nil
            )
        }

        let allianceStr = next.allianceColor(for: config.teamKey ?? "")
        let alliance: MatchAlliance? = allianceStr == "red" ? .red : (allianceStr == "blue" ? .blue : nil)

        // Derive phase from Nexus data if available
        var phase: Phase = .preQueue
        var deadline: Date? = next.matchDate(useScheduled: config.useScheduledTime)
        var phaseStart: Date = .now

        if let nexusEvent = cache.nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: next, in: nexusEvent) {
            let result = PhaseDerivation.derivePhase(from: nexusMatch)
            phase = result.phase
            deadline = result.deadline ?? deadline
            phaseStart = result.phaseStartDate
        }

        return PhaseComplicationEntry(
            date: .now, teamNumber: config.teamNumber,
            matchNumber: next.matchNumber, matchLabel: next.shortLabel,
            alliance: alliance, phase: phase,
            phaseDeadline: deadline, phaseStartDate: phaseStart
        )
    }
}
```

- [ ] **Step 2: Rewrite complication views**

Replace the entire contents of `ComplicationViews.swift`:

```swift
import SwiftUI
import WidgetKit
import TBAKit

// MARK: - Circular Complication (70×70pt)

struct CircularComplicationView: View {
    let entry: PhaseComplicationEntry

    private var isOnField: Bool { entry.phase == .onField }

    var body: some View {
        if let phase = entry.phase, let deadline = entry.phaseDeadline {
            ZStack {
                // Background
                Circle()
                    .fill(isOnField ? Color(hex: "#0F2118") : Color(hex: "#1C1C1E"))
                    .overlay(
                        Circle()
                            .strokeBorder(
                                isOnField ? Color(hex: "#30D158") : .clear,
                                lineWidth: 1
                            )
                    )

                VStack(spacing: 2) {
                    // Phase label
                    Text(phase.label)
                        .font(.system(size: 9, weight: .semibold, design: .monospaced))
                        .tracking(0.6)
                        .foregroundStyle(phase.color)

                    // Countdown
                    Text(deadline, style: .timer)
                        .font(.system(size: 22, weight: .bold, design: .monospaced))
                        .kerning(-1)
                        .foregroundStyle(.white)
                        .monospacedDigit()

                    // Progress bar + alliance dot
                    HStack(spacing: 4) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(Color(hex: "#3A3A3C"))
                                    .frame(height: 2.5)
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(phase.color)
                                    .frame(width: geo.size.width * entry.phaseProgress, height: 2.5)
                            }
                        }
                        .frame(width: 28, height: 2.5)

                        if let alliance = entry.alliance {
                            Circle()
                                .fill(alliance.dotColor)
                                .frame(width: 5, height: 5)
                        }
                    }
                }
            }
        } else {
            // No match — show team number
            ZStack {
                Circle().fill(Color(hex: "#1C1C1E"))
                Text("\(entry.teamNumber ?? 0)")
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(.white)
            }
        }
    }
}

// MARK: - Rectangular Complication (160×68pt)

struct RectangularComplicationView: View {
    let entry: PhaseComplicationEntry

    private var isOnField: Bool { entry.phase == .onField }

    var body: some View {
        if let phase = entry.phase, let deadline = entry.phaseDeadline {
            HStack(spacing: 0) {
                // Left column: phase icon + team number
                VStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 5)
                        .fill(phase.color)
                        .frame(width: 20, height: 20)
                    Text("#\(entry.teamNumber ?? 0)")
                        .font(.system(size: 9.5, design: .monospaced))
                        .foregroundStyle(phase.color.opacity(0.50))
                }
                .frame(width: 46)

                // Divider
                Rectangle()
                    .fill(.white.opacity(0.15))
                    .frame(width: 0.5, height: 42)

                // Right column: phase + countdown + progress
                VStack(alignment: .leading, spacing: 2) {
                    Text(phase.label)
                        .font(.system(size: 9, weight: .semibold, design: .monospaced))
                        .tracking(0.6)
                        .foregroundStyle(phase.color)

                    Text(deadline, style: .timer)
                        .font(.system(size: 26, weight: .bold, design: .monospaced))
                        .kerning(-1)
                        .foregroundStyle(.white)
                        .monospacedDigit()

                    HStack(spacing: 4) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(Color(hex: "#3A3A3C"))
                                    .frame(height: 2.5)
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(phase.color)
                                    .frame(width: geo.size.width * entry.phaseProgress, height: 2.5)
                            }
                        }
                        .frame(width: 48, height: 2.5)

                        if let alliance = entry.alliance {
                            Circle()
                                .fill(alliance.dotColor)
                                .frame(width: 5, height: 5)
                        }
                    }
                }
                .padding(.leading, 8)

                Spacer()
            }
            .padding(8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isOnField ? Color(hex: "#0F2118") : Color(hex: "#1C1C1E"))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(
                                isOnField
                                    ? Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.40)
                                    : .clear,
                                lineWidth: 0.5
                            )
                    )
            )
        } else {
            VStack(alignment: .leading) {
                Text("Team \(entry.teamNumber ?? 0)")
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.white)
                Text("No match")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
```

- [ ] **Step 3: Update widget bundle to drop corner complication**

Replace the entire contents of `PitWatchWatchWidgetBundle.swift`:

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
        .supportedFamilies([.accessoryCircular, .accessoryRectangular])
    }
}

struct WatchWidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: PhaseComplicationEntry
    var body: some View {
        switch family {
        case .accessoryCircular: CircularComplicationView(entry: entry)
        case .accessoryRectangular: RectangularComplicationView(entry: entry)
        default: CircularComplicationView(entry: entry)
        }
    }
}
```

- [ ] **Step 4: Verify watch target builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatchWatchWidgets -destination 'platform=watchOS Simulator,name=Apple Watch Series 10 (46mm)' -quiet 2>&1 | tail -5`
Expected: Build succeeds.

- [ ] **Step 5: Commit**

```bash
git add PitWatchWatchWidgets/WatchComplicationProvider.swift \
       PitWatchWatchWidgets/ComplicationViews.swift \
       PitWatchWatchWidgets/PitWatchWatchWidgetBundle.swift
git commit -m "feat: rewrite watch complications with phase-focused design"
```

---

## Task 9: Rewrite LiveActivityManager

**Files:**
- Modify: `TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift`

- [ ] **Step 1: Rewrite LiveActivityManager for new attributes and lifecycle**

Replace the entire contents of `LiveActivityManager.swift`:

```swift
import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

public final class LiveActivityManager: @unchecked Sendable {
    public static let shared = LiveActivityManager()
    private init() {}

    /// Start a new Live Activity for a match using the new FRCMatchAttributes.
    public func startActivity(
        match: Match,
        teamNumber: Int,
        teamKey: String,
        nexusMatch: NexusMatch?,
        nexusEvent: NexusEvent?
    ) throws -> Activity<FRCMatchAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return nil }

        let allianceStr = match.allianceColor(for: teamKey) ?? "blue"
        let alliance: MatchAlliance = allianceStr == "red" ? .red : .blue

        let attributes = FRCMatchAttributes(
            teamNumber: teamNumber,
            matchNumber: match.matchNumber,
            matchLabel: match.shortLabel,
            alliance: alliance
        )

        // Derive initial phase from Nexus data
        var phase: Phase = .preQueue
        var deadline: Date = .now.addingTimeInterval(3600) // 1hr default
        var phaseStart: Date = .now
        var currentOnField: Int = match.matchNumber

        if let nexusMatch {
            let result = PhaseDerivation.derivePhase(from: nexusMatch)
            phase = result.phase
            deadline = result.deadline ?? deadline
            phaseStart = result.phaseStartDate
        }

        if let nexusEvent {
            currentOnField = PhaseDerivation.currentMatchOnField(
                matches: nexusEvent.matches,
                fallbackMatchNumber: match.matchNumber
            )
        }

        let state = FRCMatchAttributes.ContentState(
            currentPhase: phase,
            phaseStartDate: phaseStart,
            phaseDeadline: deadline,
            currentMatchOnField: currentOnField,
            lastUpdated: .now
        )

        let staleDate = deadline.addingTimeInterval(30)
        let content = ActivityContent(state: state, staleDate: staleDate)
        return try Activity<FRCMatchAttributes>.request(
            attributes: attributes, content: content
        )
    }

    /// Update an existing Live Activity with fresh Nexus data.
    public func updateActivity(
        matchKey: String,
        match: Match,
        nexusMatch: NexusMatch?,
        nexusEvent: NexusEvent?
    ) async {
        guard let activity = Activity<FRCMatchAttributes>.activities.first(
            where: { $0.attributes.matchLabel == match.shortLabel }
        ) else { return }

        var phase: Phase = .preQueue
        var deadline: Date = .now.addingTimeInterval(3600)
        var phaseStart: Date = .now
        var currentOnField: Int = match.matchNumber

        if let nexusMatch {
            let result = PhaseDerivation.derivePhase(from: nexusMatch)
            phase = result.phase
            deadline = result.deadline ?? deadline
            phaseStart = result.phaseStartDate
        }

        if let nexusEvent {
            currentOnField = PhaseDerivation.currentMatchOnField(
                matches: nexusEvent.matches,
                fallbackMatchNumber: match.matchNumber
            )
        }

        let state = FRCMatchAttributes.ContentState(
            currentPhase: phase,
            phaseStartDate: phaseStart,
            phaseDeadline: deadline,
            currentMatchOnField: currentOnField,
            lastUpdated: .now
        )

        let staleDate = deadline.addingTimeInterval(30)
        let content = ActivityContent(state: state, staleDate: staleDate)
        await activity.update(content)
    }

    /// End the current match's activity and optionally start one for the next match.
    /// Call this when Nexus advances the next match to On Field, signaling
    /// the current match is complete.
    public func transitionToNextMatch(
        currentMatchKey: String,
        nextMatch: Match?,
        teamNumber: Int,
        teamKey: String,
        nexusMatch: NexusMatch?,
        nexusEvent: NexusEvent?
    ) async throws -> Activity<FRCMatchAttributes>? {
        // End current activity
        await endActivity(matchLabel: nil) // ends first active

        // Start new one if there's a next match
        guard let nextMatch else { return nil }
        return try startActivity(
            match: nextMatch,
            teamNumber: teamNumber,
            teamKey: teamKey,
            nexusMatch: nexusMatch,
            nexusEvent: nexusEvent
        )
    }

    public func endActivity(matchLabel: String?) async {
        let target: Activity<FRCMatchAttributes>?
        if let matchLabel {
            target = Activity<FRCMatchAttributes>.activities.first(
                where: { $0.attributes.matchLabel == matchLabel }
            )
        } else {
            target = Activity<FRCMatchAttributes>.activities.first
        }
        guard let activity = target else { return }
        await activity.end(nil, dismissalPolicy: .immediate)
    }

    public func endAllActivities() async {
        for activity in Activity<FRCMatchAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    public var hasActiveActivity: Bool {
        !Activity<FRCMatchAttributes>.activities.isEmpty
    }
}
#endif
```

- [ ] **Step 2: Verify iOS target builds**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5`
Expected: Build succeeds (the main app target imports TBAKit and references LiveActivityManager).

Note: There will likely be build errors in the main app where it calls `LiveActivityManager.shared.startActivity(...)` and `updateActivity(...)` with the old signatures. These call sites need updating — see Task 10.

- [ ] **Step 3: Commit**

```bash
git add TBAKit/Sources/TBAKit/LiveActivity/LiveActivityManager.swift
git commit -m "feat: rewrite LiveActivityManager for phase-based lifecycle"
```

---

## Task 10: Integration — Update Call Sites and Clean Up

**Files:**
- Modify: Call sites in the main app that reference `LiveActivityManager` and old attributes
- Delete: `PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift`
- Delete: `PitWatchWidgets/LiveActivity/DynamicIslandViews.swift`
- Delete: `TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift`

- [ ] **Step 1: Find all call sites referencing old types**

Run: `cd /Users/borgel/working/personal/tba-ios-widget && grep -rn "MatchActivityAttributes\|LiveActivityManager.*startActivity\|LiveActivityManager.*updateActivity\|LiveActivityManager.*endActivity\|MatchState\." --include="*.swift" PitWatch/ PitWatchWidgets/ TBAKit/Sources/ | grep -v "FRCMatchAttributes" | grep -v "MatchActivityAttributes.swift"`

This shows every file that still references the old API. Update each call site to use the new signatures:

- `startActivity(match:teamNumber:teamKey:nexusMatch:nexusEvent:)` replaces the old signature
- `updateActivity(matchKey:match:nexusMatch:nexusEvent:)` replaces the old signature
- `endActivity(matchLabel:)` replaces `endActivity(for:)`
- Remove references to `MatchState` enum (replaced by `Phase`)

The exact changes depend on the call sites found. For each file:
1. Update the function call to match the new signature
2. Remove parameters that no longer exist (scores, OPR, rankings, etc.)
3. Pass `nexusMatch` and `nexusEvent` instead of individual Nexus time fields

- [ ] **Step 2: Delete old view files**

```bash
git rm PitWatchWidgets/LiveActivity/LiveActivityLockScreenView.swift
git rm PitWatchWidgets/LiveActivity/DynamicIslandViews.swift
```

- [ ] **Step 3: Delete old attributes file**

```bash
git rm TBAKit/Sources/TBAKit/LiveActivity/MatchActivityAttributes.swift
```

- [ ] **Step 4: Verify full project builds (all targets)**

Run each:
```bash
cd /Users/borgel/working/personal/tba-ios-widget
xcodebuild build -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5
xcodebuild build -scheme PitWatchWidgets -destination 'platform=iOS Simulator,name=iPhone 16' -quiet 2>&1 | tail -5
xcodebuild build -scheme PitWatchWatchWidgets -destination 'platform=watchOS Simulator,name=Apple Watch Series 10 (46mm)' -quiet 2>&1 | tail -5
```
Expected: All builds succeed.

- [ ] **Step 5: Run TBAKit tests**

Run: `cd TBAKit && swift test 2>&1 | tail -15`
Expected: All tests pass (existing tests should still work; new tests from Tasks 1-2 pass).

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "refactor: update call sites and remove old Live Activity views and attributes"
```

---

## Summary

| Task | Component | Type |
|------|-----------|------|
| 1 | Foundation types (Color, Phase, MatchAlliance) | TDD |
| 2 | Phase derivation from Nexus | TDD |
| 3 | FRCMatchAttributes | Implementation |
| 4 | Chevron phase bar | Implementation |
| 5 | Expanded Live Activity | Implementation |
| 6 | Dynamic Island | Implementation |
| 7 | Wire up MatchLiveActivityWidget | Integration |
| 8 | Watch complications + provider | Implementation |
| 9 | LiveActivityManager rewrite | Implementation |
| 10 | Call site updates + cleanup | Integration |
