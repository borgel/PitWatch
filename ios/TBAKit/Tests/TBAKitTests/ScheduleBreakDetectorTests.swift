import Testing
import Foundation
@testable import TBAKit

// MARK: - Empty / trivial inputs

@Test func detectBreaks_emptyInput_returnsEmpty() {
    let breaks = ScheduleBreakDetector.detectBreaks(in: [], timeZone: .gmt)
    #expect(breaks.isEmpty)
}

@Test func detectBreaks_singleMatch_returnsEmpty() {
    let matches = [makeNexusMatch(label: "Q1", start: 1_700_000_000_000)]
    let breaks = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: .gmt)
    #expect(breaks.isEmpty)
}

// MARK: - Gap thresholds

@Test func detectBreaks_gapBelowMinimum_isNotABreak() {
    // 10-minute gap, under the 20-minute default threshold
    let t0: Int64 = 1_700_000_000_000
    let matches = [
        makeNexusMatch(label: "Q1", start: t0),
        makeNexusMatch(label: "Q2", start: t0 + 10 * 60_000),
    ]
    #expect(ScheduleBreakDetector.detectBreaks(in: matches, timeZone: .gmt).isEmpty)
}

@Test func detectBreaks_respectsCustomMinimumGap() {
    let t0: Int64 = 1_700_000_000_000
    let matches = [
        makeNexusMatch(label: "Q1", start: t0),
        makeNexusMatch(label: "Q2", start: t0 + 15 * 60_000),
    ]
    let breaks = ScheduleBreakDetector.detectBreaks(
        in: matches, timeZone: .gmt, minimumGap: 10 * 60
    )
    #expect(breaks.count == 1)
}

// MARK: - Classification

@Test func detectBreaks_gapInAfternoon_isSessionBreak() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let start = localDate("2026-04-11T15:00:00", in: tz)
    let end = localDate("2026-04-11T15:45:00", in: tz)
    let matches = [
        makeNexusMatch(label: "Q1", start: start.unixMs),
        makeNexusMatch(label: "Q2", start: end.unixMs),
    ]
    let breaks = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: tz)
    #expect(breaks.count == 1)
    #expect(breaks.first?.kind == .sessionBreak)
    #expect(breaks.first?.startsAfter == "Q1")
    #expect(breaks.first?.endsBefore == "Q2")
    #expect(breaks.first?.start == start)
    #expect(breaks.first?.end == end)
}

@Test func detectBreaks_gapStraddlingLocalNoon_isLunch() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let start = localDate("2026-04-11T11:45:00", in: tz)
    let end = localDate("2026-04-11T12:45:00", in: tz)
    let matches = [
        makeNexusMatch(label: "Q1", start: start.unixMs),
        makeNexusMatch(label: "Q2", start: end.unixMs),
    ]
    let breaks = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: tz)
    #expect(breaks.count == 1)
    #expect(breaks.first?.kind == .lunch)
}

@Test func detectBreaks_gapCrossingLocalMidnight_isOvernight() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let start = localDate("2026-04-10T17:30:00", in: tz)
    let end = localDate("2026-04-11T09:00:00", in: tz)
    let matches = [
        makeNexusMatch(label: "Q1", start: start.unixMs),
        makeNexusMatch(label: "Q2", start: end.unixMs),
    ]
    let breaks = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: tz)
    #expect(breaks.count == 1)
    #expect(breaks.first?.kind == .overnight)
}

@Test func detectBreaks_timeZoneAffectsClassification() throws {
    // Same instants, different local zones → different classification.
    // 11:45 AM → 12:45 PM in LA is 18:45 → 19:45 in UTC.
    let la = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let start = localDate("2026-04-11T11:45:00", in: la)
    let end = localDate("2026-04-11T12:45:00", in: la)
    let matches = [
        makeNexusMatch(label: "Q1", start: start.unixMs),
        makeNexusMatch(label: "Q2", start: end.unixMs),
    ]

    let inLA = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: la)
    #expect(inLA.first?.kind == .lunch)

    let inUTC = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: .gmt)
    #expect(inUTC.first?.kind == .sessionBreak)
}

// MARK: - Input hygiene

@Test func detectBreaks_skipsMatchesWithoutEstimatedStartTime() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let t0 = localDate("2026-04-11T15:00:00", in: tz).unixMs
    let t1 = localDate("2026-04-11T15:45:00", in: tz).unixMs
    let matches = [
        makeNexusMatch(label: "Q1", start: t0),
        makeNexusMatch(label: "Q2", start: nil),
        makeNexusMatch(label: "Q3", start: t1),
    ]
    let breaks = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: tz)
    #expect(breaks.count == 1)
    #expect(breaks.first?.startsAfter == "Q1")
    #expect(breaks.first?.endsBefore == "Q3")
}

@Test func detectBreaks_sortsUnorderedInput() throws {
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))
    let t0 = localDate("2026-04-11T15:00:00", in: tz).unixMs
    let t1 = localDate("2026-04-11T15:45:00", in: tz).unixMs
    let matches = [
        makeNexusMatch(label: "Q2", start: t1),
        makeNexusMatch(label: "Q1", start: t0),
    ]
    let breaks = ScheduleBreakDetector.detectBreaks(in: matches, timeZone: tz)
    #expect(breaks.count == 1)
    #expect(breaks.first?.startsAfter == "Q1")
    #expect(breaks.first?.endsBefore == "Q2")
}

// MARK: - Real fixture (2026 California Northern State Championship)

@Test func detectBreaks_realFixture_cancmp() throws {
    let data = try fixtureData("nexus_event_2026cancmp")
    let event = try JSONDecoder().decode(NexusEvent.self, from: data)
    let tz = try #require(TimeZone(identifier: "America/Los_Angeles"))

    let breaks = ScheduleBreakDetector.detectBreaks(in: event.matches, timeZone: tz)

    // Known breaks in this snapshot:
    // Practice 11 → Qualification 1:      ~62 min, 11:30→12:32 PDT  (lunch)
    // Qualification 38 → Qualification 39: ~15 hr, overnight
    // Qualification 62 → Qualification 63: ~59 min, 12:02→13:02 PDT (lunch)
    // Qualification 96 → Qualification 97: ~15 hr, overnight
    #expect(breaks.count == 4)

    #expect(breaks[0].startsAfter == "Practice 11")
    #expect(breaks[0].endsBefore == "Qualification 1")
    #expect(breaks[0].kind == .lunch)

    #expect(breaks[1].startsAfter == "Qualification 38")
    #expect(breaks[1].endsBefore == "Qualification 39")
    #expect(breaks[1].kind == .overnight)

    #expect(breaks[2].startsAfter == "Qualification 62")
    #expect(breaks[2].endsBefore == "Qualification 63")
    #expect(breaks[2].kind == .lunch)

    #expect(breaks[3].startsAfter == "Qualification 96")
    #expect(breaks[3].endsBefore == "Qualification 97")
    #expect(breaks[3].kind == .overnight)
}

// MARK: - Helpers

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

private func localDate(_ iso: String, in timeZone: TimeZone) -> Date {
    let formatter = DateFormatter()
    formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss"
    formatter.timeZone = timeZone
    return formatter.date(from: iso)!
}

private extension Date {
    var unixMs: Int64 { Int64(timeIntervalSince1970 * 1000) }
}

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}
