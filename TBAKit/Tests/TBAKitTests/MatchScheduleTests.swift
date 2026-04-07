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

    // Next match time is 1712000000 (scheduled)
    let matchDate = Date(timeIntervalSince1970: 1712000000)

    // 3 hours before -> 60 min interval
    let far = schedule.refreshInterval(now: matchDate.addingTimeInterval(-10800), useScheduledTime: true)
    #expect(far == 3600)

    // 90 min before -> 30 min interval
    let medium = schedule.refreshInterval(now: matchDate.addingTimeInterval(-5400), useScheduledTime: true)
    #expect(medium == 1800)

    // 20 min before -> 15 min interval
    let close = schedule.refreshInterval(now: matchDate.addingTimeInterval(-1200), useScheduledTime: true)
    #expect(close == 900)

    // 5 min after match time (just completed window) -> 10 min interval
    let justAfter = schedule.refreshInterval(now: matchDate.addingTimeInterval(300), useScheduledTime: true)
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
