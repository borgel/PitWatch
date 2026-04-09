import Testing
import Foundation
@testable import TBAKit

@Suite("PhaseDerivation")
struct PhaseDerivationTests {
    private func makeNexusMatch(
        queueOffset: TimeInterval? = nil,
        onDeckOffset: TimeInterval? = nil,
        onFieldOffset: TimeInterval? = nil,
        startOffset: TimeInterval? = nil,
        status: String? = nil,
        reference: Date = Date(timeIntervalSince1970: 1000)
    ) -> NexusMatch {
        func ms(_ offset: TimeInterval?) -> Int64? {
            guard let offset else { return nil }
            return Int64((reference.timeIntervalSince1970 + offset) * 1000)
        }
        return NexusMatch(
            label: "Qualification 1",
            status: status,
            redTeams: ["1", "2", "3"],
            blueTeams: ["4", "5", "6"],
            times: NexusMatchTimes(
                estimatedQueueTime: ms(queueOffset),
                estimatedOnDeckTime: ms(onDeckOffset),
                estimatedOnFieldTime: ms(onFieldOffset),
                estimatedStartTime: ms(startOffset),
                actualQueueTime: nil
            )
        )
    }

    @Test("all times in future → preQueue with deadline = queue time")
    func allFuture() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: 300, onDeckOffset: 600,
            onFieldOffset: 900, startOffset: 1200, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .preQueue)
        #expect(result.deadline == nexus.times.queueDate)
    }

    @Test("queue time passed, on-deck in future → queueing")
    func queuePassed() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: -60, onDeckOffset: 300,
            onFieldOffset: 600, startOffset: 900, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .queueing)
        #expect(result.deadline == nexus.times.onDeckDate)
    }

    @Test("on-deck time passed, on-field in future → onDeck")
    func onDeckPassed() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: -300, onDeckOffset: -60,
            onFieldOffset: 300, startOffset: 600, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .onDeck)
        #expect(result.deadline == nexus.times.onFieldDate)
    }

    @Test("on-field time passed → onField with deadline = start + 150s")
    func onFieldPassed() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: -600, onDeckOffset: -300,
            onFieldOffset: -120, startOffset: -60, reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .onField)
        let expected = nexus.times.startDate!.addingTimeInterval(150)
        #expect(result.deadline == expected)
    }

    @Test("Nexus status 'On Field' overrides time-based derivation")
    func statusOverride() {
        let ref = Date(timeIntervalSince1970: 1000)
        let nexus = makeNexusMatch(
            queueOffset: 300, onDeckOffset: 600,
            onFieldOffset: 900, startOffset: 1200,
            status: "On Field", reference: ref
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: ref)
        #expect(result.phase == .onField)
    }

    @Test("no Nexus times → preQueue with nil deadline")
    func noTimes() {
        let nexus = NexusMatch(
            label: "Qualification 1", status: nil,
            redTeams: ["1", "2", "3"], blueTeams: ["4", "5", "6"],
            times: NexusMatchTimes(
                estimatedQueueTime: nil, estimatedOnDeckTime: nil,
                estimatedOnFieldTime: nil, estimatedStartTime: nil,
                actualQueueTime: nil
            )
        )
        let result = PhaseDerivation.derivePhase(from: nexus, now: .now)
        #expect(result.phase == .preQueue)
        #expect(result.deadline == nil)
    }

    @Test("currentMatchOnField derived from match statuses")
    func currentMatchOnField() {
        let onFieldMatch = NexusMatch(
            label: "Qualification 42", status: "On Field",
            redTeams: [], blueTeams: [],
            times: NexusMatchTimes(
                estimatedQueueTime: nil, estimatedOnDeckTime: nil,
                estimatedOnFieldTime: nil, estimatedStartTime: nil,
                actualQueueTime: nil
            )
        )
        let result = PhaseDerivation.currentMatchOnField(
            matches: [onFieldMatch],
            fallbackMatchNumber: 1
        )
        #expect(result == 42)
    }
}
