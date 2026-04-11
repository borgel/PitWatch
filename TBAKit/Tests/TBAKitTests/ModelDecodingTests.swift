import Testing
import Foundation
@testable import TBAKit

@Test func decodeMatches() throws {
    let data = try fixtureData("matches")
    let matches = try JSONDecoder().decode([Match].self, from: data)
    #expect(matches.count == 2)

    let upcoming = matches[0]
    #expect(upcoming.key == "2026miket_qm32")
    #expect(upcoming.compLevel == "qm")
    #expect(upcoming.matchNumber == 32)
    #expect(upcoming.time == 1712000000)
    #expect(upcoming.predictedTime == 1712000600)
    #expect(upcoming.actualTime == nil)
    #expect(upcoming.alliances["red"]?.teamKeys == ["frc1234", "frc5678", "frc9012"])
    #expect(upcoming.alliances["red"]?.score == -1)
    #expect(upcoming.winningAlliance == "")

    let played = matches[1]
    #expect(played.actualTime == 1711996550)
    #expect(played.alliances["red"]?.score == 87)
    #expect(played.winningAlliance == "red")
}

@Test func decodeRankings() throws {
    let data = try fixtureData("rankings")
    let rankings = try JSONDecoder().decode(EventRankings.self, from: data)
    #expect(rankings.rankings.count == 1)
    #expect(rankings.rankings[0].rank == 3)
    #expect(rankings.rankings[0].record?.wins == 5)
    #expect(rankings.rankings[0].record?.losses == 2)
}

@Test func decodeOPRs() throws {
    let data = try fixtureData("oprs")
    let oprs = try JSONDecoder().decode(EventOPRs.self, from: data)
    #expect(oprs.oprs["frc1234"] == 45.2)
    #expect(oprs.dprs["frc1234"] == 30.5)
    #expect(oprs.ccwms["frc1234"] == 14.7)
}

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

private func fixtureData(_ name: String) throws -> Data {
    let url = Bundle.module.url(forResource: name, withExtension: "json", subdirectory: "Fixtures")!
    return try Data(contentsOf: url)
}
