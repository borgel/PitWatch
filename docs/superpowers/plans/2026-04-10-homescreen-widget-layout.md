# Home Screen Widget Layout Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refresh the three iOS home screen widgets (`SmallWidgetView`, `MediumWidgetView`, `LargeWidgetView`) to share the Live Activity's visual vocabulary (flat layout, alliance badge header, phase color accents) and densify the large widget's UPCOMING section to show all six teams per row for as many upcoming matches as fit.

**Architecture:**
1. Foundation: add a pure `phaseFor(match:nexusEvent:)` helper to TBAKit (unit-tested via Swift Testing).
2. Shared components: add color tokens, an `AllianceBadge` capsule component, and extend `AllianceLineCompact` with a `highlighted` parameter — all in `SharedWidgetComponents.swift`.
3. Data plumbing: add `nextMatchPhase` computed property on `MatchWidgetEntry` and adjust `prefix()` values in `MatchTimelineProvider.swift`.
4. Per-widget rewrites: restyle `SmallWidgetView`, `MediumWidgetView`, and `LargeWidgetView` (with three sub-tasks for the large widget: header/NEXT, UPCOMING rework, LAST collapse). Each widget change is validated via SwiftUI previews and a build of the widget extension target.

**Tech Stack:** SwiftUI, WidgetKit, Swift Testing (`@Suite`, `@Test`, `#expect`), TBAKit Swift package (platforms: iOS 18, watchOS 11, macOS 15), xcodebuild.

**Spec:** `docs/superpowers/specs/2026-04-10-homescreen-widget-layout-design.md`

**Key build/test commands:**
- Run TBAKit unit tests: `cd TBAKit && swift test --filter <test-name-filter>`
- Build the iOS widget extension: `xcodebuild -project PitWatch.xcodeproj -scheme PitWatch -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build`
- Both commands should be run from the repo root.

---

## File Structure

**Modified / created:**

| File | Purpose |
|---|---|
| `TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift` | Add `phaseFor(match:nexusEvent:)` — pure helper composing `NexusMatchMerge.nexusInfo` + `derivePhase`. |
| `TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift` | Add 4 new tests for `phaseFor`. |
| `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift` | Add color tokens, add `AllianceBadge`, extend `AllianceLineCompact` with `highlighted` parameter. |
| `PitWatchWidgets/MatchTimelineProvider.swift` | Add `nextMatchPhase` computed property on `MatchWidgetEntry`; update `upcomingMatches` prefix from 2→8; update `pastMatches` prefix from 3→1. |
| `PitWatchWidgets/WidgetViews/SmallWidgetView.swift` | Restyle: alliance badge in header, phase-colored countdown, dim color tokens. |
| `PitWatchWidgets/WidgetViews/MediumWidgetView.swift` | Remove inner card backgrounds, add column divider, alliance badge header, phase-colored countdown, dim color tokens. |
| `PitWatchWidgets/WidgetViews/LargeWidgetView.swift` | Header + queuing + NEXT restyle (Task 8); UPCOMING rework with 3-line rows and highlight (Task 9); LAST collapse to single row (Task 10). |

**Not touched:**
- Lock screen widgets, Dynamic Island, Live Activity (`LiveActivity/` subdirectory).
- Watch widgets (`PitWatchWatchWidgets/`).
- Any TBAKit file other than `PhaseDerivation.swift` and its test file.
- `MatchTimelineProvider.swift` beyond the two prefix values and the one new computed property.

---

## Task 1: Add `phaseFor(match:nexusEvent:)` helper to `PhaseDerivation` (TDD)

**Files:**
- Modify: `TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift`
- Test: `TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift`

**Rationale:** The widget entry needs a way to derive a `Phase?` from a TBA `Match` + optional `NexusEvent`. The composition is two lines (find the Nexus match, derive the phase), but extracting it as a named helper makes it unit-testable via Swift Testing and keeps the entry's computed property trivial.

- [ ] **Step 1: Write the failing tests**

Append to `TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift` (inside the `struct PhaseDerivationTests` body, so the new tests share the existing `makeNexusMatch` helper and `@Suite` grouping):

```swift
    // MARK: - phaseFor(match:nexusEvent:) tests

    private func makeTBAMatch(matchNumber: Int,
                              redTeams: [String] = ["frc1234", "frc5678", "frc9012"],
                              blueTeams: [String] = ["frc3456", "frc7890", "frc1111"]) -> Match {
        let redJSON = "[" + redTeams.map { "\"\($0)\"" }.joined(separator: ", ") + "]"
        let blueJSON = "[" + blueTeams.map { "\"\($0)\"" }.joined(separator: ", ") + "]"
        let json = """
        {
          "key": "2026test_qm\(matchNumber)",
          "comp_level": "qm",
          "set_number": 1,
          "match_number": \(matchNumber),
          "event_key": "2026test",
          "time": 1712000000,
          "predicted_time": null,
          "actual_time": null,
          "alliances": {
            "red": {
              "score": -1,
              "team_keys": \(redJSON),
              "surrogate_team_keys": [],
              "dq_team_keys": []
            },
            "blue": {
              "score": -1,
              "team_keys": \(blueJSON),
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

    @Test("phaseFor: nil nexusEvent → nil")
    func phaseForNilEvent() {
        let tbaMatch = makeTBAMatch(matchNumber: 32)
        let result = PhaseDerivation.phaseFor(match: tbaMatch, nexusEvent: nil)
        #expect(result == nil)
    }

    @Test("phaseFor: nexusEvent present but no correlated match → nil")
    func phaseForNoCorrelation() {
        let tbaMatch = makeTBAMatch(matchNumber: 32)
        let unrelated = NexusMatch(
            label: "Qualification 99",
            status: nil,
            redTeams: ["9999", "8888", "7777"],
            blueTeams: ["6666", "5555", "4444"],
            times: NexusMatchTimes(
                estimatedQueueTime: nil, estimatedOnDeckTime: nil,
                estimatedOnFieldTime: nil, estimatedStartTime: nil,
                actualQueueTime: nil
            )
        )
        let event = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [unrelated])
        let result = PhaseDerivation.phaseFor(match: tbaMatch, nexusEvent: event)
        #expect(result == nil)
    }

    @Test("phaseFor: correlated match with status 'Now queuing' → .queueing")
    func phaseForQueueingStatus() {
        let tbaMatch = makeTBAMatch(matchNumber: 32)
        let correlated = NexusMatch(
            label: "Qualification 32",
            status: "Now queuing",
            redTeams: ["1234", "5678", "9012"],
            blueTeams: ["3456", "7890", "1111"],
            times: NexusMatchTimes(
                estimatedQueueTime: 1712000000000, estimatedOnDeckTime: 1712000300000,
                estimatedOnFieldTime: 1712000600000, estimatedStartTime: 1712000900000,
                actualQueueTime: nil
            )
        )
        let event = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [correlated])
        let result = PhaseDerivation.phaseFor(match: tbaMatch, nexusEvent: event)
        #expect(result == .queueing)
    }

    @Test("phaseFor: correlated match with status 'On field' → .onField")
    func phaseForOnFieldStatus() {
        let tbaMatch = makeTBAMatch(matchNumber: 32)
        let correlated = NexusMatch(
            label: "Qualification 32",
            status: "On field",
            redTeams: ["1234", "5678", "9012"],
            blueTeams: ["3456", "7890", "1111"],
            times: NexusMatchTimes(
                estimatedQueueTime: 1712000000000, estimatedOnDeckTime: 1712000300000,
                estimatedOnFieldTime: 1712000600000, estimatedStartTime: 1712000900000,
                actualQueueTime: nil
            )
        )
        let event = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [correlated])
        let result = PhaseDerivation.phaseFor(match: tbaMatch, nexusEvent: event)
        #expect(result == .onField)
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
cd TBAKit && swift test --filter "phaseFor"
```

Expected: compilation error — `PhaseDerivation.phaseFor` does not exist.

- [ ] **Step 3: Add the helper function**

In `TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift`, add a new static function inside the `public enum PhaseDerivation` declaration, directly below `derivePhase(from:now:)`:

```swift
    /// Find the Nexus match corresponding to a TBA match and derive its current phase.
    /// Returns nil when no Nexus event is provided or no correlated Nexus match is found.
    public static func phaseFor(match: Match, nexusEvent: NexusEvent?) -> Phase? {
        guard let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent) else {
            return nil
        }
        return derivePhase(from: nexusMatch).phase
    }
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
cd TBAKit && swift test --filter "phaseFor"
```

Expected: all 4 new tests pass. Verify output shows 4 passing tests and 0 failures.

- [ ] **Step 5: Run the full TBAKit test suite to confirm no regressions**

```bash
cd TBAKit && swift test
```

Expected: all existing tests continue to pass (including the 7 pre-existing `PhaseDerivation` tests), plus the 4 new tests.

- [ ] **Step 6: Commit**

```bash
git add TBAKit/Sources/TBAKit/LiveActivity/PhaseDerivation.swift \
        TBAKit/Tests/TBAKitTests/PhaseDerivationTests.swift
git commit -m "$(cat <<'EOF'
feat(tbakit): add PhaseDerivation.phaseFor helper

Composes NexusMatchMerge.nexusInfo and derivePhase into a single
entry point for widgets that have a TBA Match and an optional
NexusEvent. Returns nil when Nexus is unavailable or the match
can't be correlated, so widget views can fall back cleanly.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Add color tokens to `SharedWidgetComponents.swift`

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`

**Rationale:** The three widget views will all reference the same dim-label palette used by the Live Activity. Centralizing the tokens here means color changes happen in one place. No test target exists for PitWatchWidgets, so validation is "the file compiles and all widgets still build".

- [ ] **Step 1: Add the color tokens**

Open `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`. Near the top of the file (after `import TBAKit`), add:

```swift
// MARK: - Shared Color Tokens

/// Dark card background matching the Live Activity expanded view.
let widgetCardBackground = Color(hex: "#1C1C1E")

/// Dim label base color used by the Live Activity for secondary text.
/// Apply opacities 0.30 (tertiary), 0.45 (secondary-dim), 0.65 (secondary)
/// for the three levels of de-emphasis used throughout the widgets.
let widgetLabelDim = Color(red: 235/255, green: 235/255, blue: 245/255)
```

- [ ] **Step 2: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED. No warnings about the new constants (they're unused at this point but `internal` so that's fine).

- [ ] **Step 3: Commit**

```bash
git add PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift
git commit -m "$(cat <<'EOF'
feat(widgets): add shared color tokens matching Live Activity

Adds widgetCardBackground and widgetLabelDim tokens for use across
the three home screen widget views. Palette matches the Live
Activity expanded view for visual consistency.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Add `AllianceBadge` component to `SharedWidgetComponents.swift`

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`

**Rationale:** The badge is a new shared component — a colored rounded-rect capsule matching the Live Activity header's alliance/match pill. Widgets will render it only when the tracked team's alliance color is known; the component itself expects a non-nil alliance color, and call sites guard.

- [ ] **Step 1: Add the `AllianceBadge` struct**

Append to `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift` (below the existing `AllianceLineCompact` struct, above `ScoreDisplay`):

```swift
/// Colored rounded-rect pill showing alliance + match label, matching the
/// Live Activity expanded view header. Render only when alliance color is known;
/// call sites should guard on `entry.nextMatchAllianceColor` before instantiating.
struct AllianceBadge: View {
    let allianceColor: String   // "red" or "blue"
    let matchLabel: String      // e.g., "Q32"

    private var backgroundColor: Color {
        switch allianceColor {
        case "red":  return Color.red.opacity(0.25)
        case "blue": return Color.blue.opacity(0.25)
        default:     return Color.gray.opacity(0.25)
        }
    }

    private var textColor: Color {
        switch allianceColor {
        case "red":  return Color(red: 1.0, green: 0.72, blue: 0.72)
        case "blue": return Color(red: 0.72, green: 0.80, blue: 1.0)
        default:     return Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.75)
        }
    }

    private var displayName: String {
        switch allianceColor {
        case "red":  return "Red"
        case "blue": return "Blue"
        default:     return "—"
        }
    }

    var body: some View {
        Text("\(displayName) · \(matchLabel)")
            .font(.system(size: 10, weight: .medium))
            .foregroundStyle(textColor)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(
                RoundedRectangle(cornerRadius: 4)
                    .fill(backgroundColor)
            )
    }
}
```

- [ ] **Step 2: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Commit**

```bash
git add PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift
git commit -m "$(cat <<'EOF'
feat(widgets): add AllianceBadge shared component

Colored rounded-rect pill showing alliance + match label, matching
the Live Activity expanded view header. Used by all three home
screen widgets in their header rows.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Extend `AllianceLineCompact` with `highlighted` parameter

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`

**Rationale:** The large widget's UPCOMING rows need a way to visually highlight the tracked team's alliance. Adding an optional `highlighted` parameter with a default of `false` means existing call sites (medium/large NEXT cards) remain unchanged.

> **Note on testing:** The design spec mentions unit tests for the highlight background mapping. PitWatchWidgets has no test target (see `project.yml` — only TBAKit has a `.testTarget`), and the highlight-color logic is too small to justify moving it into TBAKit just to test. Validation is therefore: (1) compile successfully, (2) existing NEXT cards in the medium and large widget previews are visually unchanged, (3) the new highlighted rows are visually confirmed in the simulator in Task 12. This omission is intentional.

- [ ] **Step 1: Add the `highlighted` parameter to the struct**

In `PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift`, modify the `AllianceLineCompact` struct. Replace the existing struct body with:

```swift
struct AllianceLineCompact: View {
    let allianceColor: String
    let teamKeys: [String]
    let trackedTeamKey: String
    let opr: Double?
    var highlighted: Bool = false

    private var highlightBackground: Color {
        guard highlighted else { return .clear }
        switch allianceColor {
        case "red":  return Color.red.opacity(0.12)
        case "blue": return Color.blue.opacity(0.12)
        default:     return .clear
        }
    }

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
        .padding(.horizontal, 4)
        .padding(.vertical, 2)
        .background(
            RoundedRectangle(cornerRadius: 3)
                .fill(highlightBackground)
        )
    }
}
```

The changes from the current struct: added `highlighted` stored property (default `false`), added `highlightBackground` computed property, wrapped the body's content in `.padding` + `.background(RoundedRectangle)`. When `highlighted` is `false`, the background is `.clear` and the rounded rectangle is effectively invisible — existing call sites see no visual change.

- [ ] **Step 2: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED. No changes to `MediumWidgetView` or `LargeWidgetView` call sites needed yet — they default `highlighted` to `false`.

- [ ] **Step 3: Open the Xcode project and verify existing NEXT previews render unchanged**

Open `PitWatch.xcodeproj` in Xcode. Open `MediumWidgetView.swift` and `LargeWidgetView.swift`. Verify their SwiftUI previews render the NEXT card identically to before — the alliance lines should look the same because `highlighted` defaults to `false`. No pixel-level verification needed; a visual check that nothing looks visibly different is sufficient.

- [ ] **Step 4: Commit**

```bash
git add PitWatchWidgets/WidgetViews/SharedWidgetComponents.swift
git commit -m "$(cat <<'EOF'
feat(widgets): add highlighted parameter to AllianceLineCompact

When highlighted is true, the row gets a subtle alliance-color
background tint. Defaults to false so existing call sites in the
medium and large widget NEXT cards are unchanged. Used by the
large widget's UPCOMING rows to highlight the tracked team's
alliance.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Update `MatchTimelineProvider` (entry property + prefix)

**Files:**
- Modify: `PitWatchWidgets/MatchTimelineProvider.swift`

**Rationale:** The widget entry needs a `nextMatchPhase` helper (so views can call a one-liner) and the upcoming/past arrays need different caps for the large widget's new layout.

- [ ] **Step 1: Add the `nextMatchPhase` computed property**

In `PitWatchWidgets/MatchTimelineProvider.swift`, locate the `struct MatchWidgetEntry: TimelineEntry` declaration. Find the existing `var nexusStatus: String?` computed property (currently at lines 53-56). Add a new computed property directly below it (before `var isNexusAvailable: Bool`):

```swift
    var nextMatchPhase: Phase? {
        guard let match = nextMatch else { return nil }
        return PhaseDerivation.phaseFor(match: match, nexusEvent: nexusEvent)
    }
```

- [ ] **Step 2: Update the `upcomingMatches` prefix**

In the same file, locate the `makeEntry()` method (currently around lines 98-119). Find this line:

```swift
            upcomingMatches: Array(schedule.upcomingMatches.dropFirst().prefix(2)),
```

Change it to:

```swift
            upcomingMatches: Array(schedule.upcomingMatches.dropFirst().prefix(8)),
```

- [ ] **Step 3: Update the `pastMatches` prefix**

In the same `makeEntry()` method, find this line:

```swift
            pastMatches: Array(schedule.pastMatches.prefix(3)),
```

Change it to:

```swift
            pastMatches: Array(schedule.pastMatches.prefix(1)),
```

- [ ] **Step 4: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 5: Commit**

```bash
git add PitWatchWidgets/MatchTimelineProvider.swift
git commit -m "$(cat <<'EOF'
feat(widgets): add nextMatchPhase and adjust entry array caps

- Adds MatchWidgetEntry.nextMatchPhase computed property using the
  new PhaseDerivation.phaseFor helper, giving widget views a
  one-liner to drive phase-colored accents.
- upcomingMatches prefix raised from 2 to 8 to give the large
  widget's new UPCOMING layout headroom for 4 visible rows.
- pastMatches prefix lowered from 3 to 1 since the large widget's
  LAST section now shows only the single most recent result.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 6: Restyle `SmallWidgetView`

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`

**Rationale:** Smallest structural change of the three widgets — structure preserved, only the header badge and color tokens change.

- [ ] **Step 1: Replace the `SmallWidgetView` struct body**

Open `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`. Replace the entire `body` property with:

```swift
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack(spacing: 6) {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let color = entry.nextMatchAllianceColor, let next = entry.nextMatch {
                    AllianceBadge(allianceColor: color, matchLabel: next.shortLabel)
                }
            }
            if let ranking = entry.ranking {
                Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(widgetLabelDim.opacity(0.65))
            }

            Spacer()

            if let next = entry.nextMatch {
                // Match label
                Text(next.shortLabel)
                    .font(.system(size: 28, weight: .bold, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .center)

                // Countdown — phase-colored when Nexus provides a phase
                if let target = entry.countdownTarget {
                    Text(target, style: .relative)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))
                        .frame(maxWidth: .infinity, alignment: .center)
                }

                // Wall clock time
                if let time = matchTime {
                    Text(formatMatchTime(time, prefix: entry.timePrefix))
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            } else {
                Text("No match")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(widgetLabelDim.opacity(0.65))
                    .frame(maxWidth: .infinity, alignment: .center)
            }

            Spacer()

            if let name = entry.eventName {
                Text(name)
                    .font(.system(size: 9))
                    .foregroundStyle(widgetLabelDim.opacity(0.45))
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .containerBackground(for: .widget) {
            widgetCardBackground
        }
    }
```

Changes from the current implementation:
- Removed the `AllianceDot` next to the team number; added the `AllianceBadge` call (guarded on both alliance color and next match being non-nil).
- Rank line: `.foregroundStyle(.secondary)` → `.foregroundStyle(widgetLabelDim.opacity(0.65))`.
- Countdown: `.foregroundStyle(.secondary)` → `.foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))`.
- Wall clock line: `.foregroundStyle(.tertiary)` → `.foregroundStyle(widgetLabelDim.opacity(0.45))`.
- No-match fallback text: `.foregroundStyle(.secondary)` → `.foregroundStyle(widgetLabelDim.opacity(0.65))`.
- Event name line: `.foregroundStyle(.tertiary)` → `.foregroundStyle(widgetLabelDim.opacity(0.45))`.
- Container background: `Color(hex: "#1C1C1E")` → `widgetCardBackground`.

- [ ] **Step 2: Add or verify SwiftUI previews**

Append to `SmallWidgetView.swift` if no preview block exists, or confirm an equivalent preview is already present. The preview should cover at least one `.systemSmall` case with a next match:

```swift
#Preview(as: .systemSmall) {
    MatchWidgetBundle_Placeholder()
} timeline: {
    MatchWidgetEntry.placeholder
}
```

If `MatchWidgetBundle_Placeholder` does not exist, substitute the actual widget configuration the file currently uses (check `PitWatchWidgetBundle.swift` for the widget configuration type). The goal is a visible preview; the exact incantation must match the project's preview convention.

- [ ] **Step 3: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Open Xcode and visually verify the small widget preview**

Open `PitWatch.xcodeproj` → `SmallWidgetView.swift`. Confirm:
- Header row shows team number followed by a colored `[Red · Q32]` (or equivalent) capsule.
- Countdown text appears in gray (fallback) when the placeholder entry has no Nexus data.
- Rank, wall clock, and event name text colors all look dim — similar weight to the Live Activity's `LAST UPDATED` label on the lock screen.

- [ ] **Step 5: Commit**

```bash
git add PitWatchWidgets/WidgetViews/SmallWidgetView.swift
git commit -m "$(cat <<'EOF'
feat(widgets): restyle small widget to match Live Activity language

- Replace AllianceDot with the new AllianceBadge capsule in the header
- Phase-colored countdown (falls back to dim gray when Nexus absent)
- All secondary/tertiary colors switched to widgetLabelDim tokens
- Container background uses the shared widgetCardBackground token

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 7: Restyle `MediumWidgetView`

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`

**Rationale:** Remove the inner white-opacity card backgrounds, add a thin vertical separator between the NEXT and LAST columns, add the `AllianceBadge` to the header, apply phase-colored countdown and dim color tokens. NEXT/LAST side-by-side structure is preserved.

- [ ] **Step 1: Replace the `MediumWidgetView` body**

Open `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`. Replace the entire `body` property with:

```swift
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Header
            HStack {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let color = entry.nextMatchAllianceColor, let next = entry.nextMatch {
                    AllianceBadge(allianceColor: color, matchLabel: next.shortLabel)
                }
                if let ranking = entry.ranking {
                    Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(widgetLabelDim.opacity(0.65))
                }
                Spacer()
                if let name = entry.eventName {
                    Text(name)
                        .font(.system(size: 10))
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                        .lineLimit(1)
                }
            }

            HStack(spacing: 10) {
                // Next match column (flat, no card background)
                VStack(alignment: .leading, spacing: 4) {
                    Text("NEXT")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    if let next = entry.nextMatch {
                        HStack {
                            Text(next.shortLabel)
                                .font(.system(size: 18, weight: .bold, design: .monospaced))
                            Spacer()
                            if let target = entry.countdownTarget {
                                Text(target, style: .relative)
                                    .font(.system(size: 11, design: .monospaced))
                                    .monospacedDigit()
                                    .foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))
                            }
                        }
                        // Wall clock time
                        if let time = matchTime {
                            Text(formatMatchTime(time, prefix: entry.timePrefix))
                                .font(.system(size: 10, design: .monospaced))
                                .foregroundStyle(widgetLabelDim.opacity(0.45))
                        }
                        ForEach(["red", "blue"], id: \.self) { color in
                            let keys = next.alliances[color]?.teamKeys ?? []
                            AllianceLineCompact(
                                allianceColor: color, teamKeys: keys,
                                trackedTeamKey: entry.teamKey,
                                opr: entry.oprs?.summedOPR(for: keys)
                            )
                        }
                    } else {
                        Text("None")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(widgetLabelDim.opacity(0.65))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)

                // Column separator
                Rectangle()
                    .fill(widgetLabelDim.opacity(0.15))
                    .frame(width: 0.5)

                // Last match column (flat, no card background)
                VStack(alignment: .leading, spacing: 4) {
                    Text("LAST")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    if let last = entry.lastMatch {
                        Text(last.shortLabel)
                            .font(.system(size: 18, weight: .bold, design: .monospaced))
                        HStack {
                            Spacer()
                            ScoreDisplay(match: last).font(.system(size: 18))
                            Spacer()
                        }
                        HStack {
                            Spacer()
                            WinLossLabel(match: last, teamKey: entry.teamKey)
                            Spacer()
                        }
                    } else {
                        Text("No results")
                            .font(.system(size: 12, design: .monospaced))
                            .foregroundStyle(widgetLabelDim.opacity(0.65))
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .containerBackground(for: .widget) {
            widgetCardBackground
        }
    }
```

Changes from the current implementation:
- Header: inserted `AllianceBadge` between team number and rank, and swapped `.secondary`/`.tertiary` for `widgetLabelDim` tokens on rank and event name.
- NEXT column: removed `.padding(8).background(Color.white.opacity(0.06), in: RoundedRectangle(cornerRadius: 8))`. Added `.frame(maxWidth: .infinity, alignment: .leading)` so the column expands to share the row evenly.
- NEXT section label color: `.secondary` → `widgetLabelDim.opacity(0.45)`.
- NEXT countdown: `.secondary` → `entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65)`.
- NEXT wall clock: `.tertiary` → `widgetLabelDim.opacity(0.45)`.
- NEXT no-match fallback: `.secondary` → `widgetLabelDim.opacity(0.65)`.
- Added a 0.5pt `Rectangle` column separator between NEXT and LAST.
- LAST column: removed card background. Added `.frame(maxWidth: .infinity, alignment: .leading)`.
- LAST section label color: `.secondary` → `widgetLabelDim.opacity(0.45)`.
- LAST no-results fallback: `.secondary` → `widgetLabelDim.opacity(0.65)`.
- Container background: `Color(hex: "#1C1C1E")` → `widgetCardBackground`.

- [ ] **Step 2: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Open Xcode and visually verify the medium widget preview**

In Xcode, open `MediumWidgetView.swift` and view the preview. Confirm:
- No rounded white cards inside the widget — NEXT and LAST columns sit flat on the dark background.
- A thin vertical line separates the two columns.
- The header shows the team number, the `[Red · Q32]` alliance capsule, the rank, and the event name.
- Alliance lines in the NEXT card look the same as before (unchanged — `highlighted: false` default).

- [ ] **Step 4: Commit**

```bash
git add PitWatchWidgets/WidgetViews/MediumWidgetView.swift
git commit -m "$(cat <<'EOF'
feat(widgets): restyle medium widget to match Live Activity language

- Remove inner white-opacity card backgrounds from NEXT and LAST
- Add thin vertical separator between the two columns
- Insert AllianceBadge in the header row
- Phase-colored countdown with dim fallback
- All secondary/tertiary colors switched to widgetLabelDim tokens

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 8: Restyle `LargeWidgetView` — header, queuing indicator, NEXT section

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`

**Rationale:** First of three passes on the large widget. This task handles the parts above the UPCOMING section: header row (alliance badge insertion), the "Now Queuing" text change, and flattening the NEXT card. The UPCOMING and LAST sections are still rendered using the current (pre-refresh) code in this task — they'll be replaced in Tasks 9 and 10.

- [ ] **Step 1: Modify the header and queuing indicator sections**

Open `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`. In the `body` property, locate the existing header `HStack` (currently lines 14-29) and the queuing indicator (currently lines 31-40). Replace them with:

```swift
            // Header
            HStack {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let color = entry.nextMatchAllianceColor, let next = entry.nextMatch {
                    AllianceBadge(allianceColor: color, matchLabel: next.shortLabel)
                }
                if let name = entry.eventName {
                    Text("· \(name)")
                        .font(.system(size: 12))
                        .foregroundStyle(widgetLabelDim.opacity(0.65))
                }
                Spacer()
                if let ranking = entry.ranking {
                    Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                        .font(.system(size: 11, design: .monospaced))
                        .foregroundStyle(widgetLabelDim.opacity(0.65))
                }
            }

            if let nowQueuing = entry.nowQueuing {
                HStack(spacing: 4) {
                    Circle()
                        .fill(Phase.queueing.color)
                        .frame(width: 6, height: 6)
                    Text("Now Queuing: \(nowQueuing)")
                        .font(.system(size: 10, weight: .medium, design: .monospaced))
                        .foregroundStyle(Phase.queueing.color)
                }
            }
```

Changes from current:
- Header: inserted `AllianceBadge`, and both `.secondary` calls swapped for `widgetLabelDim.opacity(0.65)`.
- Queuing dot: `Color(hex: "#FF9500")` → `Phase.queueing.color` (same color, named reference).
- Queuing text: `"Queuing: \(nowQueuing)"` → `"Now Queuing: \(nowQueuing)"`. Foreground also migrated to `Phase.queueing.color`.

- [ ] **Step 2: Flatten the NEXT section**

In the same file, locate the existing NEXT card `VStack` wrapped in `.padding(8).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))` (currently lines 42-85). Replace the entire block with:

```swift
            // Next match section (flat, no card background)
            if let next = entry.nextMatch {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("NEXT")
                            .font(.system(size: 8, weight: .semibold, design: .monospaced))
                            .tracking(0.5)
                            .foregroundStyle(widgetLabelDim.opacity(0.45))
                        Text(next.label)
                            .font(.system(size: 14, weight: .bold, design: .monospaced))
                        if let status = entry.nexusStatus {
                            let pillColor = entry.nextMatchPhase?.color ?? nexusStatusColor(status)
                            Text(status.uppercased())
                                .font(.system(size: 8, weight: .bold, design: .monospaced))
                                .padding(.horizontal, 4).padding(.vertical, 1)
                                .background(pillColor.opacity(0.2), in: Capsule())
                                .foregroundStyle(pillColor)
                        }
                        Spacer()
                        VStack(alignment: .trailing, spacing: 1) {
                            if let target = entry.countdownTarget {
                                Text(target, style: .relative)
                                    .font(.system(size: 12, design: .monospaced))
                                    .monospacedDigit()
                                    .foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))
                            }
                            if let time = matchTime {
                                Text(formatMatchTime(time, prefix: entry.timePrefix))
                                    .font(.system(size: 10, design: .monospaced))
                                    .foregroundStyle(widgetLabelDim.opacity(0.45))
                            }
                        }
                    }
                    ForEach(["red", "blue"], id: \.self) { color in
                        let keys = next.alliances[color]?.teamKeys ?? []
                        AllianceLineCompact(
                            allianceColor: color, teamKeys: keys,
                            trackedTeamKey: entry.teamKey,
                            opr: entry.oprs?.summedOPR(for: keys)
                        )
                    }
                }
            }
```

Changes from current:
- Removed the outer `.padding(8).background(Color.white.opacity(0.08), in: RoundedRectangle(cornerRadius: 8))` wrapper — the section now sits flat on the container.
- NEXT label color: `.secondary` → `widgetLabelDim.opacity(0.45)`.
- Nexus status pill color: the raw `nexusStatusColor(status)` call is replaced with a local `pillColor` that prefers `entry.nextMatchPhase?.color` and falls back to `nexusStatusColor(status)` only when no phase is derivable.
- Countdown color: `.secondary` → `entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65)`.
- Wall clock color: `.tertiary` → `widgetLabelDim.opacity(0.45)`.

- [ ] **Step 3: Update the container background reference**

At the bottom of the `body` property, locate:

```swift
        .containerBackground(for: .widget) {
            Color(hex: "#1C1C1E")
        }
```

Change to:

```swift
        .containerBackground(for: .widget) {
            widgetCardBackground
        }
```

- [ ] **Step 4: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED. The UPCOMING and RESULTS sections below are still the old code and should still compile.

- [ ] **Step 5: Open Xcode and visually verify the large widget preview**

In Xcode, open `LargeWidgetView.swift` and view the preview. Confirm:
- Header shows team number, alliance badge capsule, event name, and rank.
- When the preview entry has `nowQueuing`, the indicator reads "Now Queuing: ...".
- The NEXT card no longer has a rounded white-opacity background — it sits flat.
- Countdown text takes a phase color when Nexus data is present; falls back to dim gray otherwise.
- UPCOMING and RESULTS sections below still render in their old layouts (that's expected — they're the focus of the next two tasks).

- [ ] **Step 6: Commit**

```bash
git add PitWatchWidgets/WidgetViews/LargeWidgetView.swift
git commit -m "$(cat <<'EOF'
feat(widgets): restyle large widget header, queuing, NEXT section

- Insert AllianceBadge in the header row
- Queuing indicator text: "Queuing: ..." → "Now Queuing: ..."
- Queuing dot color references Phase.queueing.color directly
- Flatten NEXT section — remove inner card background
- Nexus status pill color prefers entry.nextMatchPhase over
  the legacy nexusStatusColor hash
- Countdown phase-colored with dim fallback
- All secondary/tertiary colors switched to widgetLabelDim tokens

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 9: Rework `LargeWidgetView` UPCOMING section — 3-line rows with highlight

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`

**Rationale:** The core densification work. Each upcoming match becomes a 3-line mini-card (label+time header, red line, blue line); the tracked team's alliance row is highlighted via the `AllianceLineCompact` `highlighted: true` flag; OPR is hidden on upcoming rows (passed as `nil`); the view renders a fixed target of 4 rows (locked in via Task 11).

- [ ] **Step 1: Replace the UPCOMING section**

In `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`, locate the existing UPCOMING section (currently starts with `// Upcoming matches` around line 88-114, wrapping a `VStack` with a white-opacity card background). Replace the entire `if !entry.upcomingMatches.isEmpty { ... }` block with:

```swift
            // Upcoming matches — flat, 3-line rows with tracked alliance highlighted
            if !entry.upcomingMatches.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    Text("UPCOMING")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    ForEach(entry.upcomingMatches.prefix(upcomingRowTarget)) { match in
                        VStack(alignment: .leading, spacing: 3) {
                            HStack {
                                Text(match.shortLabel)
                                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                                Spacer()
                                if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                                    Text(formatMatchTime(date, prefix: entry.timePrefix))
                                        .font(.system(size: 11, design: .monospaced))
                                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                                }
                            }
                            let trackedAlliance = match.allianceColor(for: entry.teamKey)
                            ForEach(["red", "blue"], id: \.self) { color in
                                let keys = match.alliances[color]?.teamKeys ?? []
                                AllianceLineCompact(
                                    allianceColor: color, teamKeys: keys,
                                    trackedTeamKey: entry.teamKey,
                                    opr: nil,
                                    highlighted: color == trackedAlliance
                                )
                            }
                        }
                        .padding(.vertical, 2)
                    }
                }
            }
```

- [ ] **Step 2: Add the `upcomingRowTarget` constant to the struct**

In the same file, inside the `struct LargeWidgetView: View` declaration, directly below the `let entry: MatchWidgetEntry` line and above the existing `private var matchTime: Date?` computed property, add:

```swift
    /// Maximum number of upcoming match rows to render. Starts at 4; if preview
    /// validation (Task 11) finds that 4 rows clip on the smallest iPhone large
    /// widget, drop to 3. Locked in before implementation wraps.
    private let upcomingRowTarget: Int = 4
```

- [ ] **Step 3: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 4: Open Xcode and visually verify the large widget preview**

In Xcode, open `LargeWidgetView.swift`. The current preview (whatever exists in the file) may not have enough upcoming matches to exercise the new layout. If the preview shows only 0-2 upcoming matches, temporarily edit the preview entry or add a new preview that constructs a `MatchWidgetEntry` with at least 5 upcoming matches (using the existing `MatchWidgetEntry` initializer and fixture data from the project).

Confirm in the preview:
- UPCOMING section no longer has a rounded card background.
- Each upcoming match occupies 3 lines: a label+time header line, then two alliance lines (red, blue).
- The tracked team's alliance line has a subtle colored background tint (red tint when tracked alliance is red, blue tint when blue).
- OPR is not displayed on upcoming rows (the numbers stop at the 3 team numbers).
- Up to 4 upcoming matches are visible (or 3 if the 4th clips — this is what Task 11 locks in).

- [ ] **Step 5: Commit**

```bash
git add PitWatchWidgets/WidgetViews/LargeWidgetView.swift
git commit -m "$(cat <<'EOF'
feat(widgets): rework large widget UPCOMING as dense 3-line rows

Each upcoming match row shows match label + time + both alliance
lines (all 6 teams). The tracked team's alliance row is highlighted
with a subtle alliance-color background tint. OPR is hidden on
upcoming rows to keep them readable; OPR is still visible on the
NEXT card above. Renders up to 4 upcoming matches (constant
upcomingRowTarget), with headroom provided by the timeline
provider's prefix(8).

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 10: Rework `LargeWidgetView` LAST section — single row

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`

**Rationale:** The existing RESULTS section renders up to 3 past matches with full score and outcome. Collapse to a single row using `entry.lastMatch`.

- [ ] **Step 1: Replace the RESULTS section**

In `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`, locate the existing `if !entry.pastMatches.isEmpty { ... }` block (the RESULTS card with the white-opacity background, currently around lines 116-143). Replace the entire block with:

```swift
            // Last match — single flat row
            if let last = entry.lastMatch {
                HStack(spacing: 8) {
                    Text("LAST")
                        .font(.system(size: 8, weight: .semibold, design: .monospaced))
                        .tracking(0.5)
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                    Text(last.shortLabel)
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    Spacer()
                    ScoreDisplay(match: last).font(.system(size: 12))
                    WinLossLabel(match: last, teamKey: entry.teamKey)
                    if let date = last.matchDate(useScheduled: entry.useScheduledTime) {
                        Text(formatMatchTime(date, prefix: entry.timePrefix))
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(widgetLabelDim.opacity(0.45))
                    }
                }
            }
```

Changes:
- Reads from `entry.lastMatch` (the designated single-last-match field) instead of iterating `entry.pastMatches`.
- Collapses to a single horizontal row — no `ForEach`, no multi-row VStack.
- No card background — sits flat at the bottom of the widget.
- LAST label color uses `widgetLabelDim.opacity(0.45)`.
- Wall clock color uses `widgetLabelDim.opacity(0.45)`.
- `ScoreDisplay` and `WinLossLabel` are unchanged — they already use semantic red/blue/green.

- [ ] **Step 2: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Open Xcode and visually verify the large widget preview**

Confirm the preview shows a single horizontal row at the bottom of the widget containing: `LAST  Q29  [score]  WIN/LOSS  [time]`. No rounded card around it.

- [ ] **Step 4: Commit**

```bash
git add PitWatchWidgets/WidgetViews/LargeWidgetView.swift
git commit -m "$(cat <<'EOF'
feat(widgets): collapse large widget RESULTS to single LAST row

The full RESULTS card (up to 3 past matches with scores) is replaced
by a single-row LAST section reading from entry.lastMatch. Frees up
vertical space for the UPCOMING section's denser layout. Row is flat
— no card background.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 11: Add `.placeholder`-based previews to all three widget views

**Files:**
- Modify: `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`
- Modify: `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`
- Modify: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`

**Rationale:** The three widget view files currently have no `#Preview` blocks. Adding minimal previews (using `MatchWidgetEntry.placeholder`) lets the engineer verify in Xcode's canvas that the empty-state layout renders without crashes and the header/event-name styling looks right. This is an empty-state preview — the UPCOMING rows and phase colors require real match data and are validated in the simulator in Task 12.

The three widget view files already use direct view construction, so each preview is a one-liner: construct the view with `MatchWidgetEntry.placeholder` and wrap it.

- [ ] **Step 1: Add a preview to `SmallWidgetView.swift`**

Append to the end of `PitWatchWidgets/WidgetViews/SmallWidgetView.swift`:

```swift
#Preview("Small · Empty", as: .systemSmall) {
    NextMatchWidget()
} timeline: {
    MatchWidgetEntry.placeholder
}
```

This uses the `#Preview(as:)` family of preview macros that targets a specific widget family. `NextMatchWidget()` is the widget configuration type declared in `PitWatchWidgetBundle.swift`.

- [ ] **Step 2: Add a preview to `MediumWidgetView.swift`**

Append to the end of `PitWatchWidgets/WidgetViews/MediumWidgetView.swift`:

```swift
#Preview("Medium · Empty", as: .systemMedium) {
    NextMatchWidget()
} timeline: {
    MatchWidgetEntry.placeholder
}
```

- [ ] **Step 3: Add a preview to `LargeWidgetView.swift`**

Append to the end of `PitWatchWidgets/WidgetViews/LargeWidgetView.swift`:

```swift
#Preview("Large · Empty", as: .systemLarge) {
    NextMatchWidget()
} timeline: {
    MatchWidgetEntry.placeholder
}
```

- [ ] **Step 4: Build the widget extension target**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED. If the build fails because `#Preview(as:)` needs a different API shape, fall back to the simpler direct-instantiation form used by the Live Activity previews:

```swift
#Preview("Small · Empty") {
    SmallWidgetView(entry: MatchWidgetEntry.placeholder)
        .frame(width: 170, height: 170)
        .background(Color(hex: "#0D0D0D"))
}
```

(`170 × 170` is the baseline small widget size; medium is `364 × 170`; large is `364 × 380`. These match the iPhone 15 Pro default widget dimensions closely enough for visual check.) If the fallback form is used, apply the same pattern to medium and large.

- [ ] **Step 5: Open Xcode and verify the preview canvases render**

In Xcode, open each of the three widget view files and confirm:
- The preview canvas renders without crashing.
- Each shows the empty-state fallback: "No match" or "No results" text for the missing fields, the team number "1234" from the placeholder, and the "Regional" event name.
- No layout anomalies — no obvious overflow, no missing spacing, nothing unexpectedly cropped.

Phase colors and UPCOMING rows are NOT exercised in these previews (the placeholder has `nextMatch: nil` and empty arrays). Those are validated live in the simulator in Task 12.

- [ ] **Step 6: Commit**

```bash
git add PitWatchWidgets/WidgetViews/SmallWidgetView.swift \
        PitWatchWidgets/WidgetViews/MediumWidgetView.swift \
        PitWatchWidgets/WidgetViews/LargeWidgetView.swift
git commit -m "$(cat <<'EOF'
test(widgets): add empty-state SwiftUI previews for home widgets

Adds #Preview blocks using MatchWidgetEntry.placeholder for each of
the three home widget views. Covers empty-state fallback rendering;
phase colors and UPCOMING row validation happen in the simulator
during Task 12.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 12: Simulator validation and row count lock-in

**Files:**
- Possibly modify: `PitWatchWidgets/WidgetViews/LargeWidgetView.swift` (if the row target needs to drop from 4 to 3)

**Rationale:** The SwiftUI preview canvas in Task 11 validated the empty-state layouts, but the preview entry has no upcoming matches — so the iPhone SE row-count lock-in and the phase color rendering both need real data from the simulator. This is also the final regression check before the branch is ready for review.

- [ ] **Step 1: Run the full TBAKit test suite**

```bash
cd TBAKit && swift test
```

Expected: all tests pass, including the 4 new `phaseFor` tests from Task 1. If anything fails, investigate and fix before moving on — no regressions are allowed.

- [ ] **Step 2: Build PitWatch for the iPhone 15 Pro simulator**

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone 15 Pro' build
```

Expected: BUILD SUCCEEDED.

- [ ] **Step 3: Install PitWatch in the simulator and configure it**

Launch Simulator.app (or `open -a Simulator`). In the simulator, launch PitWatch. Configure the app:
- Set a tracked team (e.g., `frc1700`) on a currently active event that has both TBA match data and FRC Nexus data available.
- In settings, set the time source to FRC Nexus.

Wait for the first data refresh to complete (check the app shows matches and a Nexus-derived phase).

- [ ] **Step 4: Add all three widget sizes to the iPhone 15 Pro home screen**

In the simulator:
1. Long-press on the home screen.
2. Tap the `+` icon in the top-left.
3. Find PitWatch in the widget list.
4. Add one small, one medium, and one large widget.

- [ ] **Step 5: Verify the iPhone 15 Pro live rendering**

Visually confirm each widget:
- **Small:** Header shows team number + alliance badge. Countdown text has a phase color (orange/green/red per current phase).
- **Medium:** NEXT/LAST columns sit flat on dark background with a thin vertical separator. Header shows team number + alliance badge + rank + event name.
- **Large:** Header shows badge + event + rank. "Now Queuing" indicator reads `Now Queuing: <match>` if present. NEXT card is flat, phase-colored countdown. UPCOMING shows 4 full-team rows (or 3 — the count decided in Step 7) with the tracked team's alliance highlighted with a colored tint. Single flat LAST row at the bottom.

- [ ] **Step 6: Switch the simulator device to iPhone SE (3rd generation) and rebuild**

Stop the simulator (or switch devices via Simulator → File → Open Simulator → iOS 18 → iPhone SE (3rd generation)). Rebuild and install:

```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'platform=iOS Simulator,name=iPhone SE (3rd generation)' build
```

Re-add the large widget to the iPhone SE home screen.

- [ ] **Step 7: Lock in the `upcomingRowTarget` value**

On the iPhone SE large widget with real match data, count the upcoming rows visible:
- **Keep `upcomingRowTarget = 4`** if all 4 rows render fully visible with visible whitespace between the 4th row and the LAST section at the bottom.
- **Drop to `upcomingRowTarget = 3`** if the 4th upcoming row is clipped, the LAST row is pushed off the bottom, or there's no visual separation between the 4th row and LAST.

If dropping to 3: open `LargeWidgetView.swift` and change `private let upcomingRowTarget: Int = 4` to `private let upcomingRowTarget: Int = 3`. Rebuild with the xcodebuild command from Step 6 and re-verify the iPhone SE widget shows 3 rows cleanly with LAST visible.

- [ ] **Step 8: Toggle Nexus on and off in settings**

Back on iPhone 15 Pro (rebuild and reinstall for this step if needed):
1. Open the PitWatch app in the simulator.
2. In settings, switch the time source from FRC Nexus to The Blue Alliance.
3. Wait for the widget timeline to refresh (or remove + re-add the widgets to force a refresh).
4. Confirm: countdown text on all three widgets falls back to dim gray (`widgetLabelDim.opacity(0.65)`).
5. Switch back to FRC Nexus and confirm phase colors return.

- [ ] **Step 9: Commit the row count lock-in (only if it changed)**

If Step 7 required dropping `upcomingRowTarget` from 4 to 3, commit the change now:

```bash
git add PitWatchWidgets/WidgetViews/LargeWidgetView.swift
git commit -m "$(cat <<'EOF'
fix(widgets): drop large widget upcomingRowTarget to 3

Simulator validation on iPhone SE showed that 4 upcoming rows with
all six teams clipped the LAST section. Three rows render cleanly
on iPhone SE with LAST visible at the bottom.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

If 4 rows fit cleanly, no commit is needed here — leave the constant at 4.

- [ ] **Step 10: Verify the branch is ready for review**

Run:

```bash
git log --oneline main..HEAD
```

Expected: a clean sequence of commits on top of the branch's original base. Each commit should be a coherent unit of work, independently reviewable. Verify no unrelated files were modified along the way (`git diff --stat main..HEAD` helps here).

---

## Definition of Done

- [ ] All 12 tasks above have been completed with commits.
- [ ] `swift test` passes from the `TBAKit/` directory with all new and existing tests green.
- [ ] `xcodebuild ... build` succeeds for the `PitWatch` scheme with no warnings beyond the pre-existing baseline.
- [ ] All three home screen widgets render in the iOS simulator matching the layouts in the design spec.
- [ ] The large widget's UPCOMING section shows either 3 or 4 full-team rows with the tracked alliance highlighted (specific count determined by Task 11).
- [ ] The large widget's LAST section is a single flat row at the bottom.
- [ ] The large widget's queuing indicator reads `Now Queuing: <match label>`.
- [ ] Phase color accents appear on NEXT countdowns across all three widgets when Nexus is the active time source and a phase is derivable.
- [ ] Fallback dim gray color is used on all three widgets when Nexus is unavailable.
- [ ] Lock screen, Dynamic Island, Live Activity, and watch widgets are unchanged (no commits touching those files).
