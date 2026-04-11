import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

public final class LiveActivityManager: @unchecked Sendable {
    public static let shared = LiveActivityManager()
    private init() {}

    /// Start a new Live Activity for a match using the new FRCMatchAttributes.
    public func startActivity(
        match: Match,
        teamNumber: Int,
        teamKey: String,
        nexusMatch: NexusMatch?,
        nexusEvent: NexusEvent?
    ) throws -> Activity<FRCMatchAttributes>? {
        guard ActivityAuthorizationInfo().areActivitiesEnabled else { return nil }

        let allianceStr = match.allianceColor(for: teamKey) ?? "blue"
        let alliance: MatchAlliance = allianceStr == "red" ? .red : .blue

        let attributes = FRCMatchAttributes(
            teamNumber: teamNumber,
            matchNumber: match.matchNumber,
            matchLabel: match.shortLabel,
            alliance: alliance
        )

        var phase: Phase = .preQueue
        var deadline: Date = .now.addingTimeInterval(3600)
        var phaseStart: Date = .now
        var currentOnField: Int = match.matchNumber
        var queueDL: Date?
        var onDeckDL: Date?
        var onFieldDL: Date?
        var matchStartDL: Date?
        var matchEndDL: Date?

        if let nexusMatch {
            let result = PhaseDerivation.derivePhase(from: nexusMatch)
            phase = result.phase
            deadline = result.deadline ?? deadline
            phaseStart = result.phaseStartDate
            queueDL = result.queueDeadline
            onDeckDL = result.onDeckDeadline
            onFieldDL = result.onFieldDeadline
            matchStartDL = result.matchStartDeadline
            matchEndDL = result.matchEndDeadline
        }

        if let nexusEvent {
            currentOnField = PhaseDerivation.currentMatchOnField(
                matches: nexusEvent.matches,
                fallbackMatchNumber: match.matchNumber
            )
        }

        let state = FRCMatchAttributes.ContentState(
            currentPhase: phase,
            phaseStartDate: phaseStart,
            phaseDeadline: deadline,
            currentMatchOnField: currentOnField,
            lastUpdated: .now,
            queueDeadline: queueDL,
            onDeckDeadline: onDeckDL,
            onFieldDeadline: onFieldDL,
            matchStartDeadline: matchStartDL,
            matchEndDeadline: matchEndDL
        )

        let staleDate = deadline.addingTimeInterval(30)
        let content = ActivityContent(state: state, staleDate: staleDate)
        return try Activity<FRCMatchAttributes>.request(
            attributes: attributes, content: content
        )
    }

    /// Update an existing Live Activity with fresh Nexus data.
    public func updateActivity(
        match: Match,
        nexusMatch: NexusMatch?,
        nexusEvent: NexusEvent?
    ) async {
        guard let activity = Activity<FRCMatchAttributes>.activities.first(
            where: { $0.attributes.matchLabel == match.shortLabel }
        ) else { return }

        var phase: Phase = .preQueue
        var deadline: Date = .now.addingTimeInterval(3600)
        var phaseStart: Date = .now
        var currentOnField: Int = match.matchNumber
        var queueDL: Date?
        var onDeckDL: Date?
        var onFieldDL: Date?
        var matchStartDL: Date?
        var matchEndDL: Date?

        if let nexusMatch {
            let result = PhaseDerivation.derivePhase(from: nexusMatch)
            phase = result.phase
            deadline = result.deadline ?? deadline
            phaseStart = result.phaseStartDate
            queueDL = result.queueDeadline
            onDeckDL = result.onDeckDeadline
            onFieldDL = result.onFieldDeadline
            matchStartDL = result.matchStartDeadline
            matchEndDL = result.matchEndDeadline
        }

        if let nexusEvent {
            currentOnField = PhaseDerivation.currentMatchOnField(
                matches: nexusEvent.matches,
                fallbackMatchNumber: match.matchNumber
            )
        }

        let state = FRCMatchAttributes.ContentState(
            currentPhase: phase,
            phaseStartDate: phaseStart,
            phaseDeadline: deadline,
            currentMatchOnField: currentOnField,
            lastUpdated: .now,
            queueDeadline: queueDL,
            onDeckDeadline: onDeckDL,
            onFieldDeadline: onFieldDL,
            matchStartDeadline: matchStartDL,
            matchEndDeadline: matchEndDL
        )

        let staleDate = deadline.addingTimeInterval(30)
        let content = ActivityContent(state: state, staleDate: staleDate)
        await activity.update(content)
    }

    /// End the current match's activity and optionally start one for the next match.
    public func transitionToNextMatch(
        nextMatch: Match?,
        teamNumber: Int,
        teamKey: String,
        nexusMatch: NexusMatch?,
        nexusEvent: NexusEvent?
    ) async throws -> Activity<FRCMatchAttributes>? {
        await endActivity(matchLabel: nil)

        guard let nextMatch else { return nil }
        return try startActivity(
            match: nextMatch,
            teamNumber: teamNumber,
            teamKey: teamKey,
            nexusMatch: nexusMatch,
            nexusEvent: nexusEvent
        )
    }

    public func endActivity(matchLabel: String?) async {
        let target: Activity<FRCMatchAttributes>?
        if let matchLabel {
            target = Activity<FRCMatchAttributes>.activities.first(
                where: { $0.attributes.matchLabel == matchLabel }
            )
        } else {
            target = Activity<FRCMatchAttributes>.activities.first
        }
        guard let activity = target else { return }
        await activity.end(nil, dismissalPolicy: .immediate)
    }

    public func endAllActivities() async {
        for activity in Activity<FRCMatchAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }

    public var hasActiveActivity: Bool {
        !Activity<FRCMatchAttributes>.activities.isEmpty
    }
}
#endif
