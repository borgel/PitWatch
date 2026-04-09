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
        public let matchEndDeadline: Date?
    }

    /// Derive the current phase and countdown deadline from Nexus match data.
    /// Priority: Nexus discrete status > time-based derivation.
    public static func derivePhase(
        from nexusMatch: NexusMatch,
        now: Date = .now
    ) -> Result {
        let times = nexusMatch.times

        // Compute per-phase deadlines from Nexus times (same regardless of current phase)
        let queueDL = times.queueDate
        let onDeckDL = times.onDeckDate
        let onFieldDL = times.onFieldDate
        let matchEndDL = times.startDate.map { $0.addingTimeInterval(150) }

        // Check discrete status first (takes priority per spec)
        if let status = nexusMatch.status?.lowercased() {
            if status.contains("field") || status.contains("playing") {
                return Result(
                    phase: .onField, deadline: matchEndDL,
                    phaseStartDate: times.onFieldDate ?? now,
                    queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                    onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
                )
            }
            if status.contains("deck") {
                return Result(
                    phase: .onDeck, deadline: times.onFieldDate,
                    phaseStartDate: times.onDeckDate ?? now,
                    queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                    onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
                )
            }
            if status.contains("queuing") || status.contains("queue") {
                return Result(
                    phase: .queueing, deadline: times.onDeckDate,
                    phaseStartDate: times.queueDate ?? now,
                    queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                    onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
                )
            }
        }

        // Time-based derivation: find the most advanced phase that has passed
        if let onFieldDate = times.onFieldDate, onFieldDate <= now {
            return Result(
                phase: .onField, deadline: matchEndDL, phaseStartDate: onFieldDate,
                queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
            )
        }
        if let onDeckDate = times.onDeckDate, onDeckDate <= now {
            return Result(
                phase: .onDeck, deadline: times.onFieldDate, phaseStartDate: onDeckDate,
                queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
            )
        }
        if let queueDate = times.queueDate, queueDate <= now {
            return Result(
                phase: .queueing, deadline: times.onDeckDate, phaseStartDate: queueDate,
                queueDeadline: queueDL, onDeckDeadline: onDeckDL,
                onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
            )
        }

        // Nothing has passed yet — preQueue
        return Result(
            phase: .preQueue, deadline: times.queueDate, phaseStartDate: now,
            queueDeadline: queueDL, onDeckDeadline: onDeckDL,
            onFieldDeadline: onFieldDL, matchEndDeadline: matchEndDL
        )
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
