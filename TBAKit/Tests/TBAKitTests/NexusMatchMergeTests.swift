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
    let redJSON = "[" + redTeams.map { "\"\($0)\"" }.joined(separator: ", ") + "]"
    let blueJSON = "[" + blueTeams.map { "\"\($0)\"" }.joined(separator: ", ") + "]"
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

private func makeTimes(start: Int64?) -> NexusMatchTimes {
    NexusMatchTimes(
        estimatedQueueTime: start.map { $0 - 800000 },
        estimatedOnDeckTime: start.map { $0 - 500000 },
        estimatedOnFieldTime: start.map { $0 - 200000 },
        estimatedStartTime: start,
        actualQueueTime: nil
    )
}
