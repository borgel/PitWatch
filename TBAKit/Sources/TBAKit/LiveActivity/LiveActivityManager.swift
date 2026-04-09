import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

public final class LiveActivityManager: @unchecked Sendable {
    public static let shared = LiveActivityManager()
    private init() {}

    public func startActivity(
        match: Match, teamNumber: Int, teamKey: String, eventName: String,
        useScheduledTime: Bool, queueOffsetMinutes: Int,
        ranking: Ranking?, oprs: EventOPRs?, nexusMatch: NexusMatch? = nil
    ) throws -> Activity<MatchActivityAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return nil }

        let allianceColor = match.allianceColor(for: teamKey) ?? "red"
        let redTeams = (match.alliances["red"]?.teamKeys ?? []).map { $0.replacingOccurrences(of: "frc", with: "") }
        let blueTeams = (match.alliances["blue"]?.teamKeys ?? []).map { $0.replacingOccurrences(of: "frc", with: "") }

        let attributes = MatchActivityAttributes(
            teamNumber: teamNumber, eventName: eventName, matchKey: match.key,
            matchLabel: match.label, compLevel: match.compLevel,
            redTeams: redTeams, blueTeams: blueTeams, trackedAllianceColor: allianceColor
        )

        let matchDate = match.matchDate(useScheduled: useScheduledTime)
        let queueDate: Date? = if queueOffsetMinutes > 0, let md = matchDate {
            md.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        } else { nil }

        let state = MatchActivityAttributes.ContentState(
            matchTime: matchDate, queueTime: queueDate,
            redScore: nil, blueScore: nil, winningAlliance: nil,
            redAllianceOPR: oprs?.summedOPR(for: match.alliances["red"]?.teamKeys ?? []),
            blueAllianceOPR: oprs?.summedOPR(for: match.alliances["blue"]?.teamKeys ?? []),
            matchState: .upcoming, rank: ranking?.rank, record: ranking?.record?.display,
            nexusStatus: nexusMatch?.status,
            nexusQueueTime: nexusMatch?.times.queueDate,
            nexusOnDeckTime: nexusMatch?.times.onDeckDate,
            nexusOnFieldTime: nexusMatch?.times.onFieldDate,
            nexusStartTime: nexusMatch?.times.startDate
        )

        let content = ActivityContent(state: state, staleDate: Date.now.addingTimeInterval(1800))
        return try Activity<MatchActivityAttributes>.request(attributes: attributes, content: content)
    }

    public func updateActivity(
        match: Match, useScheduledTime: Bool, queueOffsetMinutes: Int,
        ranking: Ranking?, oprs: EventOPRs?, nexusMatch: NexusMatch? = nil
    ) async {
        guard let activity = Activity<MatchActivityAttributes>.activities.first(
            where: { $0.attributes.matchKey == match.key }
        ) else { return }

        let matchDate = match.matchDate(useScheduled: useScheduledTime)
        let queueDate: Date? = if queueOffsetMinutes > 0, let md = matchDate {
            md.addingTimeInterval(-TimeInterval(queueOffsetMinutes * 60))
        } else { nil }

        // Use Nexus status for state transitions when available
        let matchState: MatchState
        if match.isPlayed {
            matchState = .completed
        } else if let status = nexusMatch?.status?.lowercased() {
            if status.contains("field") {
                matchState = .inProgress
            } else if status.contains("deck") {
                matchState = .imminent
            } else {
                matchState = .upcoming
            }
        } else if let md = matchDate, md.timeIntervalSinceNow < 0 {
            matchState = .inProgress
        } else if let md = matchDate, md.timeIntervalSinceNow < 600 {
            matchState = .imminent
        } else {
            matchState = .upcoming
        }

        let state = MatchActivityAttributes.ContentState(
            matchTime: matchDate, queueTime: queueDate,
            redScore: match.isPlayed ? match.alliances["red"]?.score : nil,
            blueScore: match.isPlayed ? match.alliances["blue"]?.score : nil,
            winningAlliance: match.isPlayed ? match.winningAlliance : nil,
            redAllianceOPR: oprs?.summedOPR(for: match.alliances["red"]?.teamKeys ?? []),
            blueAllianceOPR: oprs?.summedOPR(for: match.alliances["blue"]?.teamKeys ?? []),
            matchState: matchState, rank: ranking?.rank, record: ranking?.record?.display,
            nexusStatus: nexusMatch?.status,
            nexusQueueTime: nexusMatch?.times.queueDate,
            nexusOnDeckTime: nexusMatch?.times.onDeckDate,
            nexusOnFieldTime: nexusMatch?.times.onFieldDate,
            nexusStartTime: nexusMatch?.times.startDate
        )

        let content = ActivityContent(state: state, staleDate: Date.now.addingTimeInterval(1800))
        await activity.update(content)
    }

    public func endActivity(for matchKey: String) async {
        guard let activity = Activity<MatchActivityAttributes>.activities.first(
            where: { $0.attributes.matchKey == matchKey }
        ) else { return }
        await activity.end(nil, dismissalPolicy: .after(.now.addingTimeInterval(900)))
    }

    public func endAllActivities() async {
        for activity in Activity<MatchActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    public var hasActiveActivity: Bool {
        !Activity<MatchActivityAttributes>.activities.isEmpty
    }
}
#endif
