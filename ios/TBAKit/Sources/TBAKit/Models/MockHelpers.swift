import Foundation

/// Helpers for generating mock TBA data from Nexus events (debug/testing use).
extension Match {
    /// Creates a mock Match by JSON-decoding, since Match uses `let` properties.
    public static func mock(
        key: String,
        compLevel: String,
        setNumber: Int,
        matchNumber: Int,
        eventKey: String,
        time: Int64?,
        redTeamKeys: [String] = ["frc1", "frc2", "frc3"],
        blueTeamKeys: [String] = ["frc4", "frc5", "frc6"],
        redScore: Int = -1,
        blueScore: Int = -1,
        winningAlliance: String = "",
        actualTime: Int64? = nil
    ) -> Match {
        let redKeysJSON = redTeamKeys.map { "\"\($0)\"" }.joined(separator: ",")
        let blueKeysJSON = blueTeamKeys.map { "\"\($0)\"" }.joined(separator: ",")
        let json = """
        {
          "key": "\(key)",
          "comp_level": "\(compLevel)",
          "set_number": \(setNumber),
          "match_number": \(matchNumber),
          "event_key": "\(eventKey)",
          "time": \(time.map(String.init) ?? "null"),
          "predicted_time": null,
          "actual_time": \(actualTime.map(String.init) ?? "null"),
          "alliances": {
            "red": {
              "score": \(redScore),
              "team_keys": [\(redKeysJSON)],
              "surrogate_team_keys": [],
              "dq_team_keys": []
            },
            "blue": {
              "score": \(blueScore),
              "team_keys": [\(blueKeysJSON)],
              "surrogate_team_keys": [],
              "dq_team_keys": []
            }
          },
          "winning_alliance": "\(winningAlliance)",
          "score_breakdown": null,
          "videos": []
        }
        """
        return try! JSONDecoder().decode(Match.self, from: json.data(using: .utf8)!)
    }
}

extension Event {
    /// Creates a mock Event for demo/testing purposes.
    public static func mock(
        key: String,
        name: String,
        shortName: String? = nil
    ) -> Event {
        let year = Calendar.current.component(.year, from: .now)
        let today = ISO8601DateFormatter().string(from: .now).prefix(10)
        let json = """
        {
          "key": "\(key)",
          "name": "\(name)",
          "event_code": "\(key)",
          "event_type": 0,
          "city": null,
          "state_prov": null,
          "country": null,
          "start_date": "\(today)",
          "end_date": "\(today)",
          "year": \(year),
          "short_name": \(shortName.map { "\"\($0)\"" } ?? "null"),
          "event_type_string": "Regional",
          "week": null,
          "location_name": null
        }
        """
        return try! JSONDecoder().decode(Event.self, from: json.data(using: .utf8)!)
    }
}
