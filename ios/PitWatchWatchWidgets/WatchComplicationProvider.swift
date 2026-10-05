import WidgetKit
import SwiftUI
import TBAKit

struct PhaseComplicationEntry: TimelineEntry {
    let date: Date
    let teamNumber: Int?
    let matchNumber: Int?
    let matchLabel: String?
    let alliance: MatchAlliance?
    let phase: Phase?
    let phaseDeadline: Date?
    let phaseStartDate: Date?

    static var placeholder: PhaseComplicationEntry {
        PhaseComplicationEntry(
            date: .now, teamNumber: 1234, matchNumber: 42,
            matchLabel: "Q42", alliance: .blue, phase: .queueing,
            phaseDeadline: .now.addingTimeInterval(300),
            phaseStartDate: .now.addingTimeInterval(-60)
        )
    }

    var phaseProgress: Double {
        guard let start = phaseStartDate, let end = phaseDeadline else { return 0 }
        let elapsed = Date().timeIntervalSince(start)
        let total = end.timeIntervalSince(start)
        guard total > 0 else { return 0 }
        return min(max(elapsed / total, 0), 1)
    }
}

struct WatchComplicationProvider: TimelineProvider {
    func placeholder(in context: Context) -> PhaseComplicationEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (PhaseComplicationEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<PhaseComplicationEntry>) -> Void) {
        let entry = makeEntry()
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")
        let useNexus = config.effectiveTimeSource == .nexus
        let reloadDate = schedule.nextReloadDate(
            now: .now, useScheduledTime: config.useScheduledTime,
            nexusEvent: useNexus ? cache.nexusEvent : nil
        )
        completion(Timeline(entries: [entry], policy: .after(reloadDate)))
    }

    private func makeEntry() -> PhaseComplicationEntry {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        guard let next = schedule.nextMatch else {
            return PhaseComplicationEntry(
                date: .now, teamNumber: config.teamNumber, matchNumber: nil,
                matchLabel: nil, alliance: nil, phase: nil,
                phaseDeadline: nil, phaseStartDate: nil
            )
        }

        let allianceStr = next.allianceColor(for: config.teamKey ?? "")
        let alliance: MatchAlliance? = allianceStr == "red" ? .red : (allianceStr == "blue" ? .blue : nil)

        var phase: Phase = .preQueue
        var deadline: Date? = next.matchDate(useScheduled: config.useScheduledTime)
        var phaseStart: Date = .now

        if config.effectiveTimeSource == .nexus,
           let nexusEvent = cache.nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: next, in: nexusEvent) {
            let result = PhaseDerivation.derivePhase(from: nexusMatch)
            phase = result.phase
            deadline = result.deadline ?? deadline
            phaseStart = result.phaseStartDate
        }

        return PhaseComplicationEntry(
            date: .now, teamNumber: config.teamNumber,
            matchNumber: next.matchNumber, matchLabel: next.shortLabel,
            alliance: alliance, phase: phase,
            phaseDeadline: deadline, phaseStartDate: phaseStart
        )
    }
}
