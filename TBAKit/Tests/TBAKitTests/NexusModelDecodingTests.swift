import Testing
import Foundation
@testable import TBAKit

@Test func decodeNexusEvent() throws {
    let url = Bundle.module.url(forResource: "nexus_event", withExtension: "json", subdirectory: "Fixtures")!
    let data = try Data(contentsOf: url)
    let event = try JSONDecoder().decode(NexusEvent.self, from: data)

    #expect(event.dataAsOfTime == 1712000000000)
    #expect(event.nowQueuing == "Qualification 33")
    #expect(event.matches.count == 3)

    let match = event.matches[0]
    #expect(match.label == "Qualification 32")
    #expect(match.status == "On deck")
    #expect(match.redTeams == ["1234", "5678", "9012"])
    #expect(match.blueTeams == ["3456", "7890", "1111"])
    #expect(match.times.estimatedQueueTime == 1711999200000)
    #expect(match.times.estimatedOnDeckTime == 1711999500000)
    #expect(match.times.estimatedOnFieldTime == 1711999800000)
    #expect(match.times.estimatedStartTime == 1712000000000)
    #expect(match.times.actualQueueTime == 1711999250000)

    let noTimes = event.matches[2]
    #expect(noTimes.times.estimatedQueueTime == nil)
    #expect(noTimes.times.estimatedStartTime == nil)
    #expect(noTimes.replayOf == nil)
}

@Test func decodeNexusEventTolleratesNullTeamSlot() throws {
    // Real Nexus responses can contain `null` in redTeams/blueTeams when a slot is
    // unassigned (e.g. practice matches, dropped teams). A single null historically
    // blew up the entire decode via DecodingError.valueNotFound, making `nexusEvent`
    // nil and triggering the "Nexus unavailable" banner for the whole event.
    let json = """
    {
      "dataAsOfTime": 1712000000000,
      "matches": [
        {
          "label": "Practice 1",
          "status": "Queuing soon",
          "redTeams": ["1234", null, "9012"],
          "blueTeams": ["3456", "7890", null],
          "times": {}
        }
      ]
    }
    """.data(using: .utf8)!

    let event = try JSONDecoder().decode(NexusEvent.self, from: json)

    #expect(event.matches.count == 1)
    #expect(event.matches[0].redTeams == ["1234", "9012"])
    #expect(event.matches[0].blueTeams == ["3456", "7890"])
}

@Test func nexusMatchTimesAsDate() throws {
    let times = NexusMatchTimes(
        estimatedQueueTime: 1712000000000,
        estimatedOnDeckTime: 1712000300000,
        estimatedOnFieldTime: 1712000600000,
        estimatedStartTime: 1712000900000,
        actualQueueTime: nil
    )

    let queueDate = times.queueDate
    #expect(queueDate != nil)
    #expect(queueDate == Date(timeIntervalSince1970: 1712000000))

    let startDate = times.startDate
    #expect(startDate == Date(timeIntervalSince1970: 1712000900))
}

@Test func nextPhaseDateReturnsFirstFuturePhase() {
    let times = NexusMatchTimes(
        estimatedQueueTime: 1712000000000,
        estimatedOnDeckTime: 1712000300000,
        estimatedOnFieldTime: 1712000600000,
        estimatedStartTime: 1712000900000,
        actualQueueTime: nil
    )
    let now = Date(timeIntervalSince1970: 1712000400)
    let result = times.nextPhaseDate(after: now)
    #expect(result?.label == "On Field")
    #expect(result?.date == Date(timeIntervalSince1970: 1712000600))
}

@Test func nextPhaseDateReturnsNilWhenAllPast() {
    let times = NexusMatchTimes(
        estimatedQueueTime: 1712000000000,
        estimatedOnDeckTime: 1712000300000,
        estimatedOnFieldTime: 1712000600000,
        estimatedStartTime: 1712000900000,
        actualQueueTime: nil
    )
    let now = Date(timeIntervalSince1970: 1712001000)
    #expect(times.nextPhaseDate(after: now) == nil)
}
