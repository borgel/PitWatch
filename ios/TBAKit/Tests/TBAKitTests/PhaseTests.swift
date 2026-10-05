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

    @Test("state labels match spec")
    func stateLabels() {
        #expect(Phase.preQueue.stateLabel == "UPCOMING")
        #expect(Phase.queueing.stateLabel == "IN QUEUE")
        #expect(Phase.onDeck.stateLabel == "ON DECK")
        #expect(Phase.onField.stateLabel == "ON FIELD")
    }

    @Test("target labels match spec")
    func targetLabels() {
        #expect(Phase.preQueue.targetLabel == "QUEUE STARTS")
        #expect(Phase.queueing.targetLabel == "MOVE TO DECK")
        #expect(Phase.onDeck.targetLabel == "MOVE TO FIELD")
        #expect(Phase.onField.targetLabel == "MATCH ENDS")
    }

    @Test("glyphs are distinct single characters")
    func glyphs() {
        #expect(Phase.preQueue.glyph == "U")
        #expect(Phase.queueing.glyph == "Q")
        #expect(Phase.onDeck.glyph == "D")
        #expect(Phase.onField.glyph == "F")
        let all = Set(Phase.allCases.map(\.glyph))
        #expect(all.count == Phase.allCases.count)
    }

    @Test("next phase prose is nil only for onField")
    func nextPhaseProse() {
        #expect(Phase.preQueue.nextPhaseProse == "queue")
        #expect(Phase.queueing.nextPhaseProse == "on deck")
        #expect(Phase.onDeck.nextPhaseProse == "on field")
        #expect(Phase.onField.nextPhaseProse == nil)
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
