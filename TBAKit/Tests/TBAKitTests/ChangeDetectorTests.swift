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

    // Simulate qm32 getting scored
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
