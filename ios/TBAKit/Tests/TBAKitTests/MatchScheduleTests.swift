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

@Test func refreshIntervalTightensWithNexusTimes() throws {
    // Match is 1 hr away by TBA time (normally 30 min refresh)
    // But Nexus queue time is 20 min from now — should tighten to 10 min refresh
    let now = Date.now
    let matchTime = now.addingTimeInterval(3600)
    let match = try makeTestMatch(
        key: "2026miket_qm32",
        compLevel: "qm",
        matchNumber: 32,
        time: Int64(matchTime.timeIntervalSince1970),
        teamKeys: ["frc1234", "frc5678", "frc9012"],
        opponentKeys: ["frc3456", "frc7890", "frc1111"]
    )
    let schedule = MatchSchedule(matches: [match], teamKey: "frc1234")

    let nexusQueueTime = now.addingTimeInterval(1200) // 20 min from now
    let nexusTimes = NexusMatchTimes(
        estimatedQueueTime: Int64(nexusQueueTime.timeIntervalSince1970 * 1000),
        estimatedOnDeckTime: nil, estimatedOnFieldTime: nil,
        estimatedStartTime: Int64(matchTime.timeIntervalSince1970 * 1000),
        actualQueueTime: nil
    )
    let nexusMatch = NexusMatch(
        label: "Qualification 32", status: nil,
        redTeams: ["1234", "5678", "9012"], blueTeams: ["3456", "7890", "1111"],
        times: nexusTimes
    )
    let nexusEvent = NexusEvent(dataAsOfTime: 0, nowQueuing: nil, matches: [nexusMatch])

    let interval = schedule.refreshInterval(now: now, useScheduledTime: false, nexusEvent: nexusEvent)
    // Without Nexus: 1 hr away → 1800s (30 min) refresh
    // With Nexus: queue in 20 min → should be 600s (10 min) or tighter
    #expect(interval <= 600)
}

@Test func refreshIntervalFallsBackWithoutNexus() throws {
    // Verify the existing behavior is preserved when nexusEvent is nil
    let now = Date.now
    let matchTime = now.addingTimeInterval(3600)
    let match = try makeTestMatch(
        key: "2026miket_qm32",
        compLevel: "qm",
        matchNumber: 32,
        time: Int64(matchTime.timeIntervalSince1970),
        teamKeys: ["frc1234", "frc5678", "frc9012"],
        opponentKeys: ["frc3456", "frc7890", "frc1111"]
    )
    let schedule = MatchSchedule(matches: [match], teamKey: "frc1234")

    let interval = schedule.refreshInterval(now: now, useScheduledTime: true)
    // 1 hr away → 1800s (30 min) refresh from TBA logic
    #expect(interval == 1800)
}

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}

private func makeTestMatch(
    key: String,
    compLevel: String = "qm",
    setNumber: Int = 1,
    matchNumber: Int = 1,
    time: Int64,
    teamKeys: [String] = ["frc1234", "frc5678", "frc9012"],
    opponentKeys: [String] = ["frc3456", "frc7890", "frc1111"]
) throws -> Match {
    let json: [String: Any] = [
        "key": key,
        "comp_level": compLevel,
        "set_number": setNumber,
        "match_number": matchNumber,
        "event_key": String(key.split(separator: "_").first ?? ""),
        "time": time,
        "predicted_time": NSNull(),
        "actual_time": NSNull(),
        "alliances": [
            "red": [
                "score": -1,
                "team_keys": teamKeys,
                "surrogate_team_keys": [] as [String],
                "dq_team_keys": [] as [String],
            ],
            "blue": [
                "score": -1,
                "team_keys": opponentKeys,
                "surrogate_team_keys": [] as [String],
                "dq_team_keys": [] as [String],
            ],
        ],
        "winning_alliance": "",
        "score_breakdown": NSNull(),
        "videos": [] as [[String: Any]],
    ]
    let data = try JSONSerialization.data(withJSONObject: json)
    return try JSONDecoder().decode(Match.self, from: data)
}
