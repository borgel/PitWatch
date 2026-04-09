import Foundation

/// Response from the FRC Nexus API `GET /event/{eventKey}`.
public struct NexusEvent: Codable, Sendable, Hashable {
    public let dataAsOfTime: Int64
    public let nowQueuing: String?
    public let matches: [NexusMatch]
}

/// A single match from the FRC Nexus API.
public struct NexusMatch: Codable, Sendable, Hashable {
    public let label: String
    public let status: String?
    public let redTeams: [String]
    public let blueTeams: [String]
    public let times: NexusMatchTimes
    public let replayOf: String?

    public init(label: String, status: String?, redTeams: [String], blueTeams: [String],
                times: NexusMatchTimes, replayOf: String? = nil) {
        self.label = label
        self.status = status
        self.redTeams = redTeams
        self.blueTeams = blueTeams
        self.times = times
        self.replayOf = replayOf
    }
}

/// Queue timing data from FRC Nexus. All timestamps are Unix milliseconds.
public struct NexusMatchTimes: Codable, Sendable, Hashable {
    public let estimatedQueueTime: Int64?
    public let estimatedOnDeckTime: Int64?
    public let estimatedOnFieldTime: Int64?
    public let estimatedStartTime: Int64?
    public let actualQueueTime: Int64?

    public init(estimatedQueueTime: Int64?, estimatedOnDeckTime: Int64?,
                estimatedOnFieldTime: Int64?, estimatedStartTime: Int64?,
                actualQueueTime: Int64?) {
        self.estimatedQueueTime = estimatedQueueTime
        self.estimatedOnDeckTime = estimatedOnDeckTime
        self.estimatedOnFieldTime = estimatedOnFieldTime
        self.estimatedStartTime = estimatedStartTime
        self.actualQueueTime = actualQueueTime
    }

    /// Convenience: estimated queue time as Date.
    public var queueDate: Date? {
        estimatedQueueTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: estimated on-deck time as Date.
    public var onDeckDate: Date? {
        estimatedOnDeckTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: estimated on-field time as Date.
    public var onFieldDate: Date? {
        estimatedOnFieldTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: estimated start time as Date.
    public var startDate: Date? {
        estimatedStartTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Convenience: actual queue time as Date.
    public var actualQueueDate: Date? {
        actualQueueTime.map { Date(timeIntervalSince1970: Double($0) / 1000.0) }
    }

    /// Returns the next upcoming phase date (first non-nil date that is in the future).
    /// Order: queue -> on deck -> on field -> start.
    public func nextPhaseDate(after now: Date = .now) -> (label: String, date: Date)? {
        let phases: [(String, Date?)] = [
            ("Queue", queueDate),
            ("On Deck", onDeckDate),
            ("On Field", onFieldDate),
            ("Start", startDate),
        ]
        return phases.compactMap { (label, date) -> (label: String, date: Date)? in
            guard let date, date > now else { return nil }
            return (label, date)
        }.first
    }
}
