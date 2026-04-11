# Schedule breaks in match lists — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show lunch / overnight / session-break rows inline in the main app's "Upcoming" match list, rendered between the team's upcoming matches that bracket each break in wall-clock time.

**Architecture:** A new pure composer method `MatchSchedule.upcomingTimeline(nexusEvent:timeZone:)` returns a heterogeneous `[UpcomingScheduleItem]` (cases `.match` and `.breakInterval`). Break detection already exists (`ScheduleBreakDetector`). `MatchListView` switches on the enum and renders match rows or a new muted `ScheduleBreakRow`. Timezone comes from a new decoded field on `Event`. No breaks are shown when Nexus is unavailable.

**Tech Stack:** Swift 6, SwiftUI (iOS 18), Swift Testing (`import Testing`), Swift Package Manager for `TBAKit`, XcodeGen for the app project.

**Spec reference:** `docs/superpowers/specs/2026-04-10-schedule-breaks-in-match-lists-design.md`

---

## Task 1: Decode `Event.timezone` from the TBA API

**Files:**
- Modify: `TBAKit/Sources/TBAKit/Models/Event.swift`
- Modify: `TBAKit/Tests/TBAKitTests/ModelDecodingTests.swift`

**Context:** The TBA `/event/{key}` endpoint returns `"timezone": "America/Los_Angeles"`, but the current `Event` model drops the field. Break classification needs the event-local zone, so we decode it as an optional string.

- [ ] **Step 1: Look at the existing Event decoding test**

Run: `cat TBAKit/Tests/TBAKitTests/ModelDecodingTests.swift`
Note the structure. You'll add one new `@Test` function.

- [ ] **Step 2: Add a failing test for timezone decoding**

Append to `TBAKit/Tests/TBAKitTests/ModelDecodingTests.swift` (before the final `private func fixtureData` helper at the bottom):

```swift
@Test func decodesEventTimezone() throws {
    let json = """
    {
      "key": "2026cancmp",
      "name": "Test Event",
      "event_code": "cancmp",
      "event_type": 2,
      "city": "Daly City",
      "state_prov": "CA",
      "country": "USA",
      "start_date": "2026-04-09",
      "end_date": "2026-04-12",
      "year": 2026,
      "short_name": "California Northern",
      "event_type_string": "District Championship",
      "week": 5,
      "location_name": "Cow Palace",
      "timezone": "America/Los_Angeles"
    }
    """.data(using: .utf8)!

    let event = try JSONDecoder().decode(Event.self, from: json)
    #expect(event.timezone == "America/Los_Angeles")
}

@Test func decodesEventWithoutTimezone() throws {
    // Some older event records may not include timezone — must decode as nil.
    let json = """
    {
      "key": "2020test",
      "name": "Test",
      "event_code": "test",
      "event_type": 0,
      "city": null,
      "state_prov": null,
      "country": null,
      "start_date": "2020-01-01",
      "end_date": "2020-01-02",
      "year": 2020,
      "short_name": null,
      "event_type_string": null,
      "week": null,
      "location_name": null
    }
    """.data(using: .utf8)!

    let event = try JSONDecoder().decode(Event.self, from: json)
    #expect(event.timezone == nil)
}
```

- [ ] **Step 3: Run the tests and confirm RED**

Run: `cd TBAKit && swift test --filter decodesEventTimezone`
Expected: FAIL. The errors should say `value of type 'Event' has no member 'timezone'`, confirming the field doesn't exist yet. If you see a different failure, investigate before proceeding.

- [ ] **Step 4: Add the `timezone` field to Event**

Open `TBAKit/Sources/TBAKit/Models/Event.swift`.

In the property list (after `locationName` at line 20), add:

```swift
    public let timezone: String?
```

In the `CodingKeys` enum (after `locationName` at line 36), add:

```swift
        case timezone
```

The `timezone` key name matches the JSON exactly, so no `= "..."` raw value is needed.

- [ ] **Step 5: Run the tests and confirm GREEN**

Run: `cd TBAKit && swift test --filter decodesEvent`
Expected: both `decodesEventTimezone` and `decodesEventWithoutTimezone` pass.

- [ ] **Step 6: Run the full TBAKit suite to check for regressions**

Run: `cd TBAKit && swift test`
Expected: all tests pass (69 + 2 = 71 tests). If any existing test fails, it's almost certainly a fixture that lacks `timezone` — but since the field is optional, existing tests should still pass.

- [ ] **Step 7: Commit**

```bash
git add TBAKit/Sources/TBAKit/Models/Event.swift TBAKit/Tests/TBAKitTests/ModelDecodingTests.swift
git commit -m "$(cat <<'EOF'
feat(tbakit): decode Event.timezone field

The TBA API returns the event's IANA timezone (e.g. "America/Los_Angeles")
but the decoder was dropping it. Needed by upcoming schedule-break classification.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 2: Implement `MatchSchedule.upcomingTimeline`

**Files:**
- Modify: `TBAKit/Sources/TBAKit/Store/MatchSchedule.swift`
- Create: `TBAKit/Tests/TBAKitTests/UpcomingTimelineTests.swift`

**Context:** This is the core composer. It walks the team's upcoming matches and inserts any schedule breaks whose wall-clock start falls between two consecutive upcoming matches. All the tests are written first, in a single file, then the implementation lands in one pass. Pattern mirrors the earlier `ScheduleBreakDetector` work.

**Implementation shape** (to give you the full picture before the per-step drill-down):

```swift
// Added to MatchSchedule.swift
public enum UpcomingScheduleItem: Sendable, Identifiable {
    case match(Match)
    case breakInterval(ScheduleBreak)

    public var id: String {
        switch self {
        case .match(let m):         return "match:\(m.key)"
        case .breakInterval(let b): return "break:\(b.startsAfter)->\(b.endsBefore)"
        }
    }
}

extension MatchSchedule {
    public func upcomingTimeline(
        nexusEvent: NexusEvent?,
        timeZone: TimeZone
    ) -> [UpcomingScheduleItem] {
        guard let nexusEvent else {
            return upcomingMatches.map(UpcomingScheduleItem.match)
        }
        let allBreaks = ScheduleBreakDetector.detectBreaks(
            in: nexusEvent.matches, timeZone: timeZone
        )
        func effectiveTime(_ match: Match) -> Date? {
            NexusMatchMerge.nexusInfo(for: match, in: nexusEvent)?.times.startDate
                ?? match.matchDate(useScheduled: true)
        }
        var result: [UpcomingScheduleItem] = []
        for (index, match) in upcomingMatches.enumerated() {
            result.append(.match(match))
            guard index < upcomingMatches.count - 1 else { continue }
            guard
                let prevTime = effectiveTime(match),
                let nextTime = effectiveTime(upcomingMatches[index + 1])
            else { continue }
            for scheduleBreak in allBreaks
                where scheduleBreak.start >= prevTime && scheduleBreak.start < nextTime {
                result.append(.breakInterval(scheduleBreak))
            }
        }
        return result
    }
}
```

- [ ] **Step 1: Create the test file with all failing tests**

Create `TBAKit/Tests/TBAKitTests/UpcomingTimelineTests.swift` with this content:

```swift
import Testing
import Foundation
@testable import TBAKit

// MARK: - Nexus absent / trivial inputs

@Test func upcomingTimeline_noNexus_returnsMatchesOnly() {
    let m1 = makeTBAMatch(number: 10, teamKey: "frc1234")
    let m2 = makeTBAMatch(number: 20, teamKey: "frc1234")
    let schedule = MatchSchedule(matches: [m1, m2], teamKey: "frc1234")

    let timeline = schedule.upcomingTimeline(nexusEvent: nil, timeZone: .gmt)

    #expect(timeline.count == 2)
    #expect(timelineMatchKeys(timeline) == [m1.key, m2.key])
    #expect(timelineBreakCount(timeline) == 0)
}

@Test func upcomingTimeline_emptyUpcoming_returnsEmpty() {
    let schedule = MatchSchedule(matches: [], teamKey: "frc1234")
    let nexus = makeNexusEvent(matches: [])

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: .gmt)

    #expect(timeline.isEmpty)
}

@Test func upcomingTimeline_singleUpcoming_returnsSingleMatch() {
    let m1 = makeTBAMatch(number: 10, teamKey: "frc1234")
    let schedule = MatchSchedule(matches: [m1], teamKey: "frc1234")
    let nexus = makeNexusEvent(matches: [
        makeNexusMatch(label: "Qualification 10", start: 1_700_000_000_000)
    ])

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: .gmt)

    #expect(timeline.count == 1)
    #expect(timelineMatchKeys(timeline) == [m1.key])
}

// MARK: - Break bracketing

@Test func upcomingTimeline_insertsBreakBetweenBracketingMatches() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    // Team plays Q10 at 11:00 and Q20 at 13:00 — lunch in between.
    let q10Time = localDate("2026-04-11T11:00:00", in: tz).unixMs
    let q20Time = localDate("2026-04-11T13:00:00", in: tz).unixMs
    let nexus = makeNexusEvent(matches: [
        makeNexusMatch(label: "Qualification 10", start: q10Time),
        // A filler match that bridges the lunch gap (>20 min default threshold)
        // must exist so the detector has something to gap-detect.
        makeNexusMatch(label: "Qualification 15", start: q10Time + 10 * 60_000),
        makeNexusMatch(label: "Qualification 16", start: q20Time - 10 * 60_000),
        makeNexusMatch(label: "Qualification 20", start: q20Time),
    ])
    let schedule = MatchSchedule(
        matches: [makeTBAMatch(number: 10, teamKey: "frc1234"),
                  makeTBAMatch(number: 20, teamKey: "frc1234")],
        teamKey: "frc1234"
    )

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    // Expect: [match(Q10), break(lunch), match(Q20)]
    #expect(timeline.count == 3)
    #expect(timelineMatchKeys(timeline) == ["2026test_qm10", "2026test_qm20"])
    #expect(timelineBreakCount(timeline) == 1)
    if case .breakInterval(let b) = timeline[1] {
        #expect(b.kind == .lunch)
        #expect(b.startsAfter == "Qualification 15")
        #expect(b.endsBefore == "Qualification 16")
    } else {
        Issue.record("Expected middle item to be a break")
    }
}

@Test func upcomingTimeline_dropsBreakBeforeFirstUpcoming() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    // Upcoming matches are Q50 and Q60, played back-to-back in the afternoon.
    // A lunch break exists earlier in the day — should NOT be shown.
    //
    // Note: Q50 and Q60 are only 10 minutes apart, so the Q50→Q60 gap is
    // below the 20-minute detector threshold and no break is produced there.
    let q45 = localDate("2026-04-11T11:00:00", in: tz).unixMs
    let q46 = localDate("2026-04-11T12:30:00", in: tz).unixMs  // 90-min lunch gap
    let q50 = localDate("2026-04-11T15:00:00", in: tz).unixMs
    let q60 = localDate("2026-04-11T15:10:00", in: tz).unixMs  // 10-min gap, no break
    let nexus = makeNexusEvent(matches: [
        makeNexusMatch(label: "Qualification 45", start: q45),
        makeNexusMatch(label: "Qualification 46", start: q46),
        makeNexusMatch(label: "Qualification 50", start: q50),
        makeNexusMatch(label: "Qualification 60", start: q60),
    ])
    let schedule = MatchSchedule(
        matches: [makeTBAMatch(number: 50, teamKey: "frc1234"),
                  makeTBAMatch(number: 60, teamKey: "frc1234")],
        teamKey: "frc1234"
    )

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    // The detector sees the morning lunch gap (Q45→Q46) AND a session break
    // between Q46 and Q50 (150 min, afternoon). Neither has a `start` inside
    // the bracket [Q50=15:00, Q60=15:10), so both are dropped.
    #expect(timeline.count == 2)
    #expect(timelineBreakCount(timeline) == 0)
}

@Test func upcomingTimeline_dropsBreakAfterLastUpcoming() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    // Team plays Q5 and Q20 (both in the morning, 30 min apart with tight fillers
    // to avoid any gaps inside the bracket). A lunch break exists AFTER Q20 —
    // between Q21 and Q22 — and must NOT be shown.
    let q5   = localDate("2026-04-11T11:00:00", in: tz).unixMs
    let q10F = localDate("2026-04-11T11:10:00", in: tz).unixMs
    let q15F = localDate("2026-04-11T11:20:00", in: tz).unixMs
    let q20  = localDate("2026-04-11T11:30:00", in: tz).unixMs
    let q21F = localDate("2026-04-11T11:40:00", in: tz).unixMs
    let q22F = localDate("2026-04-11T12:30:00", in: tz).unixMs // 50 min → lunch break after Q20
    let nexus = makeNexusEvent(matches: [
        makeNexusMatch(label: "Qualification 5",  start: q5),
        makeNexusMatch(label: "Qualification 10", start: q10F),
        makeNexusMatch(label: "Qualification 15", start: q15F),
        makeNexusMatch(label: "Qualification 20", start: q20),
        makeNexusMatch(label: "Qualification 21", start: q21F),
        makeNexusMatch(label: "Qualification 22", start: q22F),
    ])
    let schedule = MatchSchedule(
        matches: [makeTBAMatch(number: 5,  teamKey: "frc1234"),
                  makeTBAMatch(number: 20, teamKey: "frc1234")],
        teamKey: "frc1234"
    )

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    // Bracket is [Q5=11:00, Q20=11:30). The lunch break has start=11:40, which is
    // after Q20's time, so it's outside the bracket and dropped.
    #expect(timeline.count == 2)
    #expect(timelineMatchKeys(timeline) == ["2026test_qm5", "2026test_qm20"])
    #expect(timelineBreakCount(timeline) == 0)
}

@Test func upcomingTimeline_multipleBreaksInSamePair_chronological() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    // Team plays Q10 (Apr 10 11:00) and Q50 (Apr 11 13:05).
    // Between them we engineer exactly TWO breaks in the Nexus schedule:
    //   - lunch: Q10 at 11:00 → Q11 at 12:45 (105-min gap, straddles 11:30-13:00)
    //   - overnight: Q11 at 12:45 Apr 10 → Q12 at 12:55 Apr 11 (crosses midnight)
    // Q12 → Q50 is a 10-min gap, below the 20-min threshold, so no extra break.
    let q10Time = localDate("2026-04-10T11:00:00", in: tz).unixMs
    let q11Time = localDate("2026-04-10T12:45:00", in: tz).unixMs
    let q12Time = localDate("2026-04-11T12:55:00", in: tz).unixMs
    let q50Time = localDate("2026-04-11T13:05:00", in: tz).unixMs
    let nexus = makeNexusEvent(matches: [
        makeNexusMatch(label: "Qualification 10", start: q10Time),
        makeNexusMatch(label: "Qualification 11", start: q11Time),
        makeNexusMatch(label: "Qualification 12", start: q12Time),
        makeNexusMatch(label: "Qualification 50", start: q50Time),
    ])
    let schedule = MatchSchedule(
        matches: [makeTBAMatch(number: 10, teamKey: "frc1234"),
                  makeTBAMatch(number: 50, teamKey: "frc1234")],
        teamKey: "frc1234"
    )

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    // Expect 4 items: Q10, lunch, overnight, Q50
    #expect(timeline.count == 4)
    #expect(timelineBreakCount(timeline) == 2)
    let kinds = timeline.compactMap { item -> ScheduleBreak.Kind? in
        if case .breakInterval(let b) = item { return b.kind }
        return nil
    }
    #expect(kinds == [.lunch, .overnight])
}

// MARK: - Hygiene edge cases

@Test func upcomingTimeline_upcomingMatchMissingTime_skipsBracketing() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    // Q10 has no Nexus correlation AND no TBA time → effective time is nil → pair skipped.
    let q20 = localDate("2026-04-11T13:00:00", in: tz).unixMs
    let nexus = makeNexusEvent(matches: [
        // Note: no "Qualification 10" entry in Nexus, so correlation fails.
        makeNexusMatch(label: "Qualification 15", start: localDate("2026-04-11T11:30:00", in: tz).unixMs),
        makeNexusMatch(label: "Qualification 16", start: localDate("2026-04-11T12:45:00", in: tz).unixMs),
        makeNexusMatch(label: "Qualification 20", start: q20),
    ])
    let schedule = MatchSchedule(
        matches: [
            // Q10 has no TBA time either → missing-time case
            makeTBAMatch(number: 10, teamKey: "frc1234", time: nil),
            makeTBAMatch(number: 20, teamKey: "frc1234"),
        ],
        teamKey: "frc1234"
    )

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    // Q10 still appears, but no break is inserted around it because its bracket is unknown.
    #expect(timeline.count == 2)
    #expect(timelineMatchKeys(timeline) == ["2026test_qm10", "2026test_qm20"])
    #expect(timelineBreakCount(timeline) == 0)
}

@Test func upcomingTimeline_preferNexusCorrelatedTimeOverTBA() throws {
    // Verify bracket comparison uses Nexus-correlated times, not TBA `time`.
    //
    // Setup:
    //   Q10 team match: TBA time 10:00, Nexus correlation time 14:00
    //   Q20 team match: TBA time 12:30, Nexus correlation time 15:40
    //   Nexus gap between 14:00 and 15:30 produces breaks.
    //
    // In TBA frame: bracket [10:00, 12:30) — the breaks (at 14:00+) are OUTSIDE this range.
    // In Nexus frame: bracket [14:00, 15:40) — the breaks ARE inside.
    //
    // Asserting ≥1 break is inserted proves the implementation used the Nexus frame.
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let tbaQ10 = localDate("2026-04-11T10:00:00", in: tz)
    let tbaQ20 = localDate("2026-04-11T12:30:00", in: tz)
    let nexusQ10 = localDate("2026-04-11T14:00:00", in: tz).unixMs
    let nexusQ15 = localDate("2026-04-11T15:00:00", in: tz).unixMs  // 60-min gap from Q10 → break
    let nexusQ16 = localDate("2026-04-11T15:30:00", in: tz).unixMs  // 30-min gap from Q15 → break
    let nexusQ20 = localDate("2026-04-11T15:40:00", in: tz).unixMs  // 10-min gap from Q16 → no break

    let q10 = makeTBAMatch(
        number: 10, teamKey: "frc1234",
        time: Int64(tbaQ10.timeIntervalSince1970)
    )
    let q20 = makeTBAMatch(
        number: 20, teamKey: "frc1234",
        time: Int64(tbaQ20.timeIntervalSince1970)
    )
    let nexus = makeNexusEvent(matches: [
        makeNexusMatch(label: "Qualification 10", start: nexusQ10),
        makeNexusMatch(label: "Qualification 15", start: nexusQ15),
        makeNexusMatch(label: "Qualification 16", start: nexusQ16),
        makeNexusMatch(label: "Qualification 20", start: nexusQ20),
    ])
    let schedule = MatchSchedule(matches: [q10, q20], teamKey: "frc1234")

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    #expect(timelineMatchKeys(timeline) == ["2026test_qm10", "2026test_qm20"])
    // 2 breaks inserted → proves Nexus-frame bracketing.
    // If TBA frame were used, the breaks (at 14:00+) would fall outside the [10:00, 12:30) bracket
    // and 0 breaks would be inserted.
    #expect(timelineBreakCount(timeline) == 2)
}

// MARK: - Real fixture

@Test func upcomingTimeline_cancmpFixture_userMatchesBracketLunch() throws {
    // Use the real cancmp Nexus fixture. Team frc1234 plays Q58 and Q70.
    // Between them sits the Q62→Q63 lunch break (~12:02 PDT, ~59 min).
    let data = try fixtureData("nexus_event_2026cancmp")
    let nexus = try JSONDecoder().decode(NexusEvent.self, from: data)
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))

    // Fabricate TBA Match objects for Q58 and Q70 with frc1234 on red.
    // Their `time` fields are ignored because the Nexus correlation kicks in.
    let schedule = MatchSchedule(
        matches: [
            makeTBAMatch(number: 58, teamKey: "frc1234", time: nil),
            makeTBAMatch(number: 70, teamKey: "frc1234", time: nil),
        ],
        teamKey: "frc1234"
    )

    let timeline = schedule.upcomingTimeline(nexusEvent: nexus, timeZone: tz)

    // Expect: Q58, lunch break, Q70
    #expect(timelineMatchKeys(timeline) == ["2026test_qm58", "2026test_qm70"])
    #expect(timelineBreakCount(timeline) == 1)
    if case .breakInterval(let b) = timeline[1] {
        #expect(b.kind == .lunch)
        #expect(b.startsAfter == "Qualification 62")
        #expect(b.endsBefore == "Qualification 63")
    } else {
        Issue.record("Expected lunch break between Q58 and Q70")
    }
}

// MARK: - Helpers

private func makeTBAMatch(number: Int, teamKey: String, time: Int64? = 1_700_000_000) -> Match {
    let timeField = time.map(String.init) ?? "null"
    let json = """
    {
      "key": "2026test_qm\(number)",
      "comp_level": "qm",
      "set_number": 1,
      "match_number": \(number),
      "event_key": "2026test",
      "time": \(timeField),
      "predicted_time": null,
      "actual_time": null,
      "alliances": {
        "red": {
          "score": -1,
          "team_keys": ["\(teamKey)", "frc5678", "frc9012"],
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
    }
    """.data(using: .utf8)!
    return try! JSONDecoder().decode(Match.self, from: json)
}

private func makeNexusMatch(label: String, start: Int64?) -> NexusMatch {
    NexusMatch(
        label: label,
        status: nil,
        redTeams: ["1", "2", "3"],
        blueTeams: ["4", "5", "6"],
        times: NexusMatchTimes(
            estimatedQueueTime: nil,
            estimatedOnDeckTime: nil,
            estimatedOnFieldTime: nil,
            estimatedStartTime: start,
            actualQueueTime: nil
        ),
        replayOf: nil
    )
}

private func makeNexusEvent(matches: [NexusMatch]) -> NexusEvent {
    NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: matches)
}

private func localDate(_ iso: String, in timeZone: TimeZone) -> Date {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    formatter.timeZone = timeZone
    return formatter.date(from: iso)!
}

private extension Date {
    var unixMs: Int64 { Int64(timeIntervalSince1970 * 1000) }
}

private func timelineMatchKeys(_ items: [UpcomingScheduleItem]) -> [String] {
    items.compactMap { item in
        if case .match(let m) = item { return m.key }
        return nil
    }
}

private func timelineBreakCount(_ items: [UpcomingScheduleItem]) -> Int {
    items.filter { if case .breakInterval = $0 { return true } else { return false } }.count
}

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}
```

- [ ] **Step 2: Run the tests and confirm RED**

Run: `cd TBAKit && swift test --filter UpcomingTimelineTests 2>&1 | tail -40`
Expected: compile failures. Every `@Test` function should fail because `MatchSchedule.upcomingTimeline` and `UpcomingScheduleItem` do not exist yet. The errors will mention "cannot find 'UpcomingScheduleItem' in scope" and "value of type 'MatchSchedule' has no member 'upcomingTimeline'". Any other failure (typo, wrong import) should be fixed now.

- [ ] **Step 3: Add the enum and method to MatchSchedule**

Open `TBAKit/Sources/TBAKit/Store/MatchSchedule.swift`. At the bottom of the file (after the closing brace of `struct MatchSchedule`), append:

```swift
// MARK: - Upcoming timeline with schedule breaks

/// A single row in the upcoming timeline: either an FRC match or an
/// inferred schedule break (lunch, overnight, session break) derived from
/// gaps in the Nexus match schedule.
public enum UpcomingScheduleItem: Sendable, Identifiable {
    case match(Match)
    case breakInterval(ScheduleBreak)

    public var id: String {
        switch self {
        case .match(let m):         return "match:\(m.key)"
        case .breakInterval(let b): return "break:\(b.startsAfter)->\(b.endsBefore)"
        }
    }
}

extension MatchSchedule {
    /// Composes the team's upcoming matches with any schedule breaks that fall
    /// between consecutive upcoming matches in wall-clock time.
    ///
    /// Breaks are inferred from `nexusEvent.matches` via `ScheduleBreakDetector`.
    /// When `nexusEvent` is `nil`, the return value is the plain list of upcoming
    /// matches with no breaks. When either side of a pair has no effective time,
    /// breaks are not inserted around that pair.
    public func upcomingTimeline(
        nexusEvent: NexusEvent?,
        timeZone: TimeZone
    ) -> [UpcomingScheduleItem] {
        guard let nexusEvent else {
            return upcomingMatches.map(UpcomingScheduleItem.match)
        }

        let allBreaks = ScheduleBreakDetector.detectBreaks(
            in: nexusEvent.matches,
            timeZone: timeZone
        )

        func effectiveTime(_ match: Match) -> Date? {
            NexusMatchMerge.nexusInfo(for: match, in: nexusEvent)?.times.startDate
                ?? match.matchDate(useScheduled: true)
        }

        var result: [UpcomingScheduleItem] = []
        for (index, match) in upcomingMatches.enumerated() {
            result.append(.match(match))
            guard index < upcomingMatches.count - 1 else { continue }
            guard
                let prevTime = effectiveTime(match),
                let nextTime = effectiveTime(upcomingMatches[index + 1])
            else { continue }
            for scheduleBreak in allBreaks
                where scheduleBreak.start >= prevTime && scheduleBreak.start < nextTime {
                result.append(.breakInterval(scheduleBreak))
            }
        }
        return result
    }
}
```

- [ ] **Step 4: Run the tests and confirm GREEN**

Run: `cd TBAKit && swift test --filter UpcomingTimelineTests 2>&1 | tail -40`
Expected: all 10 `@Test` functions pass. If any fail, investigate before proceeding — do not adjust the test assertions; fix the implementation.

- [ ] **Step 5: Run the full TBAKit suite to check for regressions**

Run: `cd TBAKit && swift test 2>&1 | tail -10`
Expected: total passing count is 71 (from Task 1) + 10 new = 81 tests passing, zero failures.

- [ ] **Step 6: Commit**

```bash
git add TBAKit/Sources/TBAKit/Store/MatchSchedule.swift TBAKit/Tests/TBAKitTests/UpcomingTimelineTests.swift
git commit -m "$(cat <<'EOF'
feat(tbakit): add MatchSchedule.upcomingTimeline composer

Interleaves schedule breaks (lunch/overnight/session) inline with the team's
upcoming matches. Breaks are sourced from Nexus only; no fallback when
Nexus is absent. Uses Nexus-correlated match times for the bracket
comparison to avoid clock-skew misplacement.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Build the `ScheduleBreakRow` SwiftUI view

**Files:**
- Create: `PitWatch/Views/ScheduleBreakRow.swift`

**Context:** A compact, non-interactive row. Same rough height as `MatchRowView`, muted foreground so match rows stay the visual primary. No unit tests — the view is a trivial switch on a trusted enum, and the project has no snapshot-testing infrastructure. We verify it builds and eyeball the SwiftUI preview.

- [ ] **Step 1: Create the view file**

Write `PitWatch/Views/ScheduleBreakRow.swift` with this content:

```swift
import SwiftUI
import TBAKit

struct ScheduleBreakRow: View {
    let scheduleBreak: ScheduleBreak

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: iconName)
                .foregroundStyle(.secondary)
                .frame(width: 28, alignment: .center)
            Text(title)
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Spacer()
            Text(durationText)
                .font(.caption)
                .foregroundStyle(.tertiary)
                .monospacedDigit()
        }
        .padding(.vertical, 2)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("\(title), \(durationText)")
    }

    private var iconName: String {
        switch scheduleBreak.kind {
        case .lunch:        return "fork.knife"
        case .overnight:    return "moon.stars"
        case .sessionBreak: return "pause.circle"
        }
    }

    private var title: String {
        switch scheduleBreak.kind {
        case .lunch:        return "Lunch break"
        case .overnight:    return "Overnight"
        case .sessionBreak: return "Break"
        }
    }

    private var durationText: String {
        let minutes = Int(scheduleBreak.duration / 60)
        if minutes < 60 { return "\(minutes) min" }
        let hours = minutes / 60
        let mins = minutes % 60
        return mins == 0 ? "\(hours) hr" : "\(hours) hr \(mins) min"
    }
}

#Preview("Lunch") {
    List {
        ScheduleBreakRow(scheduleBreak: ScheduleBreak(
            kind: .lunch,
            startsAfter: "Qualification 62",
            endsBefore: "Qualification 63",
            start: .now,
            end: .now.addingTimeInterval(59 * 60)
        ))
        ScheduleBreakRow(scheduleBreak: ScheduleBreak(
            kind: .overnight,
            startsAfter: "Qualification 38",
            endsBefore: "Qualification 39",
            start: .now,
            end: .now.addingTimeInterval(15 * 3600)
        ))
        ScheduleBreakRow(scheduleBreak: ScheduleBreak(
            kind: .sessionBreak,
            startsAfter: "Practice 11",
            endsBefore: "Qualification 1",
            start: .now,
            end: .now.addingTimeInterval(45 * 60)
        ))
    }
}
```

- [ ] **Step 2: Regenerate the Xcode project so the new file is included**

The project uses XcodeGen. Run: `xcodegen generate`
Expected: `Created project at PitWatch.xcodeproj` (or similar confirmation). No errors.

If `xcodegen` isn't installed, install with `brew install xcodegen` and retry.

- [ ] **Step 3: Build the iOS app target**

Run:
```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`. No warnings in the new file.

- [ ] **Step 4: Commit**

Note: `PitWatch.xcodeproj/` is gitignored (regenerated by xcodegen). Only the new source file gets committed.

```bash
git add PitWatch/Views/ScheduleBreakRow.swift
git commit -m "$(cat <<'EOF'
feat(pitwatch): add ScheduleBreakRow view

Compact, non-interactive row for rendering lunch/overnight/session-break
items inline inside the Upcoming match list. Muted foreground keeps match
rows visually primary.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: Wire the timeline into `MatchListView`

**Files:**
- Modify: `PitWatch/Views/MatchListView.swift`

**Context:** Replace the current "Upcoming" `ForEach` with a switch over the `UpcomingScheduleItem` timeline. Add a tiny computed property to resolve the event's timezone. No tests — the view is a thin dispatch over a tested enum.

- [ ] **Step 1: Add the `eventTimeZone` computed property**

Open `PitWatch/Views/MatchListView.swift`.

Locate the `schedule` computed property (lines 20–22). Immediately after it, add:

```swift
    private var eventTimeZone: TimeZone {
        eventCache.event?.timezone.flatMap(TimeZone.init(identifier:)) ?? .current
    }
```

- [ ] **Step 2: Replace the Upcoming section**

Find the block (currently lines 116–122):

```swift
            if !schedule.upcomingMatches.isEmpty {
                Section("Upcoming") {
                    ForEach(schedule.upcomingMatches) { match in
                        matchLink(match)
                    }
                }
            }
```

Replace it with:

```swift
            let upcomingTimeline = schedule.upcomingTimeline(
                nexusEvent: eventCache.nexusEvent,
                timeZone: eventTimeZone
            )
            if !upcomingTimeline.isEmpty {
                Section("Upcoming") {
                    ForEach(upcomingTimeline) { item in
                        switch item {
                        case .match(let match):
                            matchLink(match)
                        case .breakInterval(let scheduleBreak):
                            ScheduleBreakRow(scheduleBreak: scheduleBreak)
                        }
                    }
                }
            }
```

- [ ] **Step 3: Build the app target**

Run:
```bash
xcodebuild -project PitWatch.xcodeproj -scheme PitWatch \
  -destination 'generic/platform=iOS Simulator' \
  -configuration Debug build 2>&1 | tail -20
```

Expected: `** BUILD SUCCEEDED **`. If the build fails on something like "cannot infer return type" in the `List` body, it's likely the mixed switch statement — SwiftUI's `@ViewBuilder` handles switches fine, so that would indicate a typo.

- [ ] **Step 4: Run the TBAKit tests again**

Run: `cd TBAKit && swift test 2>&1 | tail -10`
Expected: still 81 tests passing. This is a belt-and-suspenders check since we didn't touch TBAKit in this task.

- [ ] **Step 5: Commit**

```bash
git add PitWatch/Views/MatchListView.swift
git commit -m "$(cat <<'EOF'
feat(pitwatch): render schedule breaks inline in upcoming match list

Switches the Upcoming section over to MatchSchedule.upcomingTimeline,
which interleaves lunch/overnight/session breaks between the team's
upcoming matches.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Manual verification in the iOS Simulator

**Files:** None modified.

**Context:** The feature is a UI change that depends on real Nexus data. Automated tests verify the composer, but we need eyeballs on the result in the simulator.

- [ ] **Step 1: Launch the simulator and run the app**

From Xcode, or via the command line:
```bash
open PitWatch.xcodeproj
```
Build and run on an iPhone 16 simulator (or any iOS 18 simulator).

- [ ] **Step 2: Configure with real credentials pointing at an active event**

In the app's Settings, enter:
- Your TBA API key
- Your Nexus API key
- A team number (any team at a live or historical event with Nexus data)
- An event key override (e.g. `2026cancmp` if historical data still works, or any current event)

- [ ] **Step 3: Observe the Upcoming section**

Expected behavior:
- If the team has 2+ upcoming matches and the Nexus schedule has breaks between them → break rows appear inline between the bracketing matches with muted styling, the correct icon (fork/moon/pause), and a duration trailing ("59 min", "15 hr").
- If the team has 0 or 1 upcoming matches → no break rows, same as before.
- If Nexus isn't configured or unavailable → no break rows, "Nexus unavailable" banner still visible.

- [ ] **Step 4: Verify the visual doesn't break the existing layout**

Scroll through the list. Check that:
- Match row heights and padding look unchanged.
- Break rows are visually distinguishable but quieter than match rows.
- Dark mode looks reasonable (the row uses `.secondary` / `.tertiary` foreground which adapt automatically).

- [ ] **Step 5: Report findings**

If everything looks right, note that the manual verification passed. If not, file the issue and go back to the relevant task to fix it — don't ship a visual regression.

---

## Self-review notes

1. **Spec coverage check:** Every item in the spec's "File layout" table has a corresponding task. The spec's 10-test list is fully reflected in Task 2 (9 unit tests + 1 fixture test = 10). The spec's edge cases are all covered by individual tests.
2. **Type consistency:** `ScheduleBreak`, `ScheduleBreak.Kind`, `NexusEvent`, `NexusMatch`, `NexusMatchTimes`, `NexusMatchMerge`, and `Match` names match their real definitions — verified against existing code. `UpcomingScheduleItem` case names `.match` and `.breakInterval` are used consistently across all tasks.
3. **No placeholders:** Every step shows the exact code, path, and command. No "TBD" or "handle edge cases" language.
4. **Test data sanity:** The `insertsBreakBetweenBracketingMatches` test crafts a lunch gap that straddles local noon in LA. The detector's `straddlesLocalLunch` checks the 11:30–13:00 window; the gap from 11:10 to 12:50 falls inside that window, so classification will be `.lunch`. The `multipleBreaksInSamePair_chronological` test crafts a lunch gap and an overnight gap — both should be detected and both should fall between Q10 and Q50 in wall-clock time.
