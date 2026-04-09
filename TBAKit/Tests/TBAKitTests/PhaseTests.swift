import Testing
import SwiftUI
@testable import TBAKit

@Suite("Phase enum")
struct PhaseEnumTests {
    @Test("rawValue ordering is sequential")
    func rawValues() {
        #expect(Phase.preQueue.rawValue == 0)
        #expect(Phase.queueing.rawValue == 1)
        #expect(Phase.onDeck.rawValue == 2)
        #expect(Phase.onField.rawValue == 3)
    }

    @Test("labels match spec")
    func labels() {
        #expect(Phase.preQueue.label == "PRE Q")
        #expect(Phase.queueing.label == "QUEUE IN")
        #expect(Phase.onDeck.label == "DECK IN")
        #expect(Phase.onField.label == "ON FIELD")
    }

    @Test("sublabels match spec")
    func sublabels() {
        #expect(Phase.preQueue.sublabel == "UNTIL QUEUEING")
        #expect(Phase.queueing.sublabel == "UNTIL ON DECK")
        #expect(Phase.onDeck.sublabel == "UNTIL ON FIELD")
        #expect(Phase.onField.sublabel == "MATCH IN PROGRESS")
    }

    @Test("combinedLabel joins label and sublabel")
    func combinedLabel() {
        #expect(Phase.queueing.combinedLabel == "QUEUE IN \u{00B7} UNTIL ON DECK")
        #expect(Phase.onField.combinedLabel == "ON FIELD \u{00B7} MATCH IN PROGRESS")
    }

    @Test("CaseIterable has four phases")
    func allCases() {
        #expect(Phase.allCases.count == 4)
    }

    @Test("Codable round-trip preserves value")
    func codable() throws {
        let original = Phase.onDeck
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(Phase.self, from: data)
        #expect(decoded == original)
    }
}

@Suite("MatchAlliance enum")
struct MatchAllianceTests {
    @Test("displayName is uppercased")
    func displayName() {
        #expect(MatchAlliance.blue.displayName == "BLUE")
        #expect(MatchAlliance.red.displayName == "RED")
    }

    @Test("Codable round-trip preserves value")
    func codable() throws {
        let original = MatchAlliance.red
        let data = try JSONEncoder().encode(original)
        let decoded = try JSONDecoder().decode(MatchAlliance.self, from: data)
        #expect(decoded == original)
    }
}

@Suite("Matches-away display")
struct MatchesAwayTests {
    @Test("gap >= 2 shows X AWAY")
    func multipleAway() {
        #expect(MatchesAwayDisplay.text(for: 5) == "5 AWAY")
        #expect(MatchesAwayDisplay.text(for: 2) == "2 AWAY")
    }

    @Test("gap == 1 shows NEXT")
    func next() {
        #expect(MatchesAwayDisplay.text(for: 1) == "NEXT")
    }

    @Test("gap == 0 shows NOW")
    func now() {
        #expect(MatchesAwayDisplay.text(for: 0) == "NOW")
    }

    @Test("negative gap shows NOW")
    func negative() {
        #expect(MatchesAwayDisplay.text(for: -1) == "NOW")
    }
}
