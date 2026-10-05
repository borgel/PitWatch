import Foundation

public enum PhaseDerivation {
    public struct Result: Sendable {
        public let phase: Phase
        public let deadline: Date?
        public let phaseStartDate: Date

        /// Per-phase deadlines for chevron bar timers.
        public let queueDeadline: Date?
        public let onDeckDeadline: Date?
        public let onFieldDeadline: Date?
        public let matchStartDeadline: Date?
        public let matchEndDeadline: Date?
    }

    /// Derive the current phase and countdown deadline from Nexus match data.
    /// Priority: Nexus discrete status > time-based derivation.
    /// Nexus statuses containing "soon" (e.g. "Queuing soon", "On deck soon") are rejected
    /// — those indicate the match is upcoming, not actively in that state.
    public static func derivePhase(
        from nexusMatch: NexusMatch,
        now: Date = .now
    ) -> Result {
        let times = nexusMatch.times

        let queueDL = times.queueDate
        let onDeckDL = times.onDeckDate
        let onFieldDL = times.onFieldDate
        let matchStartDL = times.startDate
        let matchEndDL = times.startDate.map { $0.addingTimeInterval(150) }

        func result(phase: Phase, deadline: Date?, phaseStartDate: Date) -> Result {
            Result(
                phase: phase, deadline: deadline, phaseStartDate: phaseStartDate,
                queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                onFieldDeadline: onFieldDL,
                matchStartDeadline: matchStartDL, matchEndDeadline: matchEndDL
            )
        }

        if let status = nexusMatch.status?.lowercased() {
            let isSoon = status.contains("soon")
            if !isSoon && (status.contains("field") || status.contains("playing")) {
                return result(phase: .onField, deadline: matchEndDL,
                              phaseStartDate: times.onFieldDate ?? now)
            }
            if !isSoon && status.contains("deck") {
                return result(phase: .onDeck, deadline: times.onFieldDate,
                              phaseStartDate: times.onDeckDate ?? now)
            }
            if !isSoon && (status.contains("queuing") || status.contains("queue")) {
                return result(phase: .queueing, deadline: times.onDeckDate,
                              phaseStartDate: times.queueDate ?? now)
            }
        }

        // Time-based derivation: find the most advanced phase that has passed
        if let onFieldDate = times.onFieldDate, onFieldDate <= now {
            return result(phase: .onField, deadline: matchEndDL, phaseStartDate: onFieldDate)
        }
        if let onDeckDate = times.onDeckDate, onDeckDate <= now {
            return result(phase: .onDeck, deadline: times.onFieldDate, phaseStartDate: onDeckDate)
        }
        if let queueDate = times.queueDate, queueDate <= now {
            return result(phase: .queueing, deadline: times.onDeckDate, phaseStartDate: queueDate)
        }

        return result(phase: .preQueue, deadline: times.queueDate, phaseStartDate: now)
    }

    /// Find the Nexus match corresponding to a TBA match and derive its current phase.
    /// Returns nil when no Nexus event is provided or no correlated Nexus match is found.
    public static func phaseFor(match: Match, nexusEvent: NexusEvent?) -> Phase? {
        guard let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent) else {
            return nil
        }
        return derivePhase(from: nexusMatch).phase
    }

    /// Find the match number currently on the field by scanning Nexus match statuses.
    public static func currentMatchOnField(
        matches: [NexusMatch],
        fallbackMatchNumber: Int
    ) -> Int {
        if let onField = matches.last(where: {
            guard let status = $0.status?.lowercased() else { return false }
            return status.contains("field") || status.contains("playing")
        }) {
            return extractMatchNumber(from: onField.label)
                ?? fallbackMatchNumber
        }
        return fallbackMatchNumber
    }

    /// Extract match number from a Nexus label like "Qualification 42".
    public static func extractMatchNumber(from label: String) -> Int? {
        let parts = label.split(separator: " ")
        return parts.last.flatMap { Int($0) }
    }
}
