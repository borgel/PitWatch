import WidgetKit
import TBAKit

struct MatchWidgetEntry: TimelineEntry {
    let date: Date
    let teamNumber: Int?
    let eventName: String?
    let nextMatch: Match?
    let lastMatch: Match?
    let upcomingMatches: [Match]
    let pastMatches: [Match]
    let ranking: Ranking?
    let oprs: EventOPRs?
    let teamKey: String
    let useScheduledTime: Bool
    let queueOffsetMinutes: Int
    let nexusEvent: NexusEvent?

    var nextMatchAllianceColor: String? {
        nextMatch?.allianceColor(for: teamKey)
    }

    var countdownTarget: Date? {
        guard let match = nextMatch else { return nil }
        // Prefer Nexus queue time if available
        if let nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent),
           let nextPhase = nexusMatch.times.nextPhaseDate(after: .now) {
            return nextPhase.date
        }
        // Fall back to TBA time
        guard let date = match.matchDate(useScheduled: useScheduledTime) else { return nil }
        if queueOffsetMinutes > 0 {
            return date.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        }
        return date
    }

    var countdownLabel: String {
        guard let match = nextMatch else { return "to match" }
        if let nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent),
           let nextPhase = nexusMatch.times.nextPhaseDate(after: .now) {
            return "to \(nextPhase.label.lowercased())"
        }
        return queueOffsetMinutes > 0 ? "to queue" : "to match"
    }

    var nowQueuing: String? {
        nexusEvent?.nowQueuing
    }

    var nexusStatus: String? {
        guard let match = nextMatch, let nexusEvent else { return nil }
        return NexusMatchMerge.nexusInfo(for: match, in: nexusEvent)?.status
    }

    var nextMatchPhase: Phase? {
        guard let match = nextMatch else { return nil }
        return PhaseDerivation.phaseFor(match: match, nexusEvent: nexusEvent)
    }

    var isNexusAvailable: Bool {
        nexusEvent != nil
    }

    var timePrefix: String {
        useScheduledTime ? "" : "~"
    }

    static var placeholder: MatchWidgetEntry {
        MatchWidgetEntry(
            date: .now, teamNumber: 1234, eventName: "Regional",
            nextMatch: nil, lastMatch: nil, upcomingMatches: [], pastMatches: [],
            ranking: nil, oprs: nil, teamKey: "frc1234",
            useScheduledTime: false, queueOffsetMinutes: 0,
            nexusEvent: nil
        )
    }
}

struct MatchTimelineProvider: TimelineProvider {
    func placeholder(in context: Context) -> MatchWidgetEntry { .placeholder }

    func getSnapshot(in context: Context, completion: @escaping (MatchWidgetEntry) -> Void) {
        completion(makeEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<MatchWidgetEntry>) -> Void) {
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

    private func makeEntry() -> MatchWidgetEntry {
        let store = TBADataStore(containerURL: AppGroup.containerURL)
        let config = store.loadConfig()
        let cache = store.loadEventCache()
        let schedule = MatchSchedule(matches: cache.matches, teamKey: config.teamKey ?? "")

        return MatchWidgetEntry(
            date: .now,
            teamNumber: config.teamNumber,
            eventName: cache.event?.name ?? cache.event?.shortName,
            nextMatch: schedule.nextMatch,
            lastMatch: schedule.lastPlayedMatch,
            upcomingMatches: Array(schedule.upcomingMatches.dropFirst().prefix(8)),
            pastMatches: Array(schedule.pastMatches.prefix(1)),
            ranking: cache.rankings?.rankings.first { $0.teamKey == config.teamKey },
            oprs: cache.oprs,
            teamKey: config.teamKey ?? "",
            useScheduledTime: config.useScheduledTime,
            queueOffsetMinutes: config.queueOffsetMinutes,
            nexusEvent: config.effectiveTimeSource == .nexus ? cache.nexusEvent : nil
        )
    }
}
