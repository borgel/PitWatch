import Foundation
#if canImport(ActivityKit) && os(iOS)
import ActivityKit

public struct FRCMatchAttributes: ActivityAttributes, Sendable {
    public struct ContentState: Codable, Hashable, Sendable {
        public var currentPhase: Phase
        public var phaseStartDate: Date
        public var phaseDeadline: Date
        public var currentMatchOnField: Int
        public var lastUpdated: Date

        /// Per-phase deadlines for the chevron bar timers.
        public var queueDeadline: Date?
        public var onDeckDeadline: Date?
        public var onFieldDeadline: Date?
        public var matchStartDeadline: Date?
        public var matchEndDeadline: Date?

        public init(
            currentPhase: Phase,
            phaseStartDate: Date,
            phaseDeadline: Date,
            currentMatchOnField: Int,
            lastUpdated: Date,
            queueDeadline: Date? = nil,
            onDeckDeadline: Date? = nil,
            onFieldDeadline: Date? = nil,
            matchStartDeadline: Date? = nil,
            matchEndDeadline: Date? = nil
        ) {
            self.currentPhase = currentPhase
            self.phaseStartDate = phaseStartDate
            self.phaseDeadline = phaseDeadline
            self.currentMatchOnField = currentMatchOnField
            self.lastUpdated = lastUpdated
            self.queueDeadline = queueDeadline
            self.onDeckDeadline = onDeckDeadline
            self.onFieldDeadline = onFieldDeadline
            self.matchStartDeadline = matchStartDeadline
            self.matchEndDeadline = matchEndDeadline
        }

        /// Returns the deadline for a specific phase's chevron timer.
        public func deadline(for phase: Phase) -> Date? {
            switch phase {
            case .preQueue: return queueDeadline
            case .queueing: return onDeckDeadline
            case .onDeck:   return onFieldDeadline
            case .onField:  return matchEndDeadline
            }
        }

        public var phaseProgress: Double {
            let elapsed = Date().timeIntervalSince(phaseStartDate)
            let total = phaseDeadline.timeIntervalSince(phaseStartDate)
            guard total > 0 else { return 0 }
            return min(max(elapsed / total, 0), 1)
        }
    }

    public let teamNumber: Int
    public let matchNumber: Int
    public let matchLabel: String
    public let alliance: MatchAlliance

    public init(teamNumber: Int, matchNumber: Int, matchLabel: String, alliance: MatchAlliance) {
        self.teamNumber = teamNumber
        self.matchNumber = matchNumber
        self.matchLabel = matchLabel
        self.alliance = alliance
    }

    public func matchesAway(currentOnField: Int) -> Int {
        matchNumber - currentOnField
    }
}
#endif
