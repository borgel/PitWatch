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
