import Foundation

/// Derives schedule intelligence from a list of matches for a tracked team.
public struct MatchSchedule: Sendable {
    public let allMatches: [Match]
    public let teamMatches: [Match]
    public let upcomingMatches: [Match]
    public let pastMatches: [Match]

    public var nextMatch: Match? { upcomingMatches.first }
    public var lastPlayedMatch: Match? { pastMatches.first }

    public init(matches: [Match], teamKey: String) {
        let sorted = matches.sorted { $0.sortOrder < $1.sortOrder }
        self.allMatches = sorted
        self.teamMatches = sorted.filter { match in
            match.alliances.values.contains { $0.teamKeys.contains(teamKey) }
        }
        self.upcomingMatches = teamMatches.filter { !$0.isPlayed }
        self.pastMatches = teamMatches.filter { $0.isPlayed }.reversed()
    }

    /// Adaptive refresh interval in seconds based on proximity to next match.
    /// When `nexusEvent` is provided and the next match has correlated Nexus data,
    /// uses the nearest Nexus phase time for tighter intervals.
    public func refreshInterval(now: Date, useScheduledTime: Bool, nexusEvent: NexusEvent? = nil) -> TimeInterval {
        guard let next = nextMatch else {
            return 86400 // No upcoming match — once per day
        }

        // If Nexus data available, use the nearest phase time for tighter refresh
        if let nexusEvent,
           let nexusMatch = NexusMatchMerge.nexusInfo(for: next, in: nexusEvent),
           let nextPhase = nexusMatch.times.nextPhaseDate(after: now) {
            let timeUntil = nextPhase.date.timeIntervalSince(now)
            if timeUntil < 0 && timeUntil > -900 {
                return 300  // Phase just passed -> 5 minutes
            } else if timeUntil <= 600 {
                return 300  // Within 10 min of phase -> 5 minutes
            } else if timeUntil <= 1800 {
                return 600  // Within 30 min -> 10 minutes
            } else {
                return 900  // More than 30 min -> 15 minutes
            }
        }

        // Fall back to TBA-based intervals (existing logic)
        guard let matchDate = referenceDate(for: next, useScheduledTime: useScheduledTime) else {
            return 86400 // No upcoming match — once per day
        }

        let timeUntil = matchDate.timeIntervalSince(now)

        if timeUntil < 0 && timeUntil > -900 {
            return 600  // Match just completed (within 15 min) -> 10 minutes
        } else if timeUntil <= 1800 {
            return 900  // Within 30 minutes -> 15 minutes
        } else if timeUntil <= 7200 {
            return 1800 // Within 2 hours -> 30 minutes
        } else {
            return 3600 // More than 2 hours -> 60 minutes
        }
    }

    /// The date at which the next widget reload should be requested.
    public func nextReloadDate(now: Date, useScheduledTime: Bool, nexusEvent: NexusEvent? = nil) -> Date {
        now.addingTimeInterval(refreshInterval(now: now, useScheduledTime: useScheduledTime, nexusEvent: nexusEvent))
    }

    /// Whether a Live Activity should be auto-started given the mode and current time.
    public func shouldStartLiveActivity(
        now: Date,
        mode: LiveActivityMode,
        useScheduledTime: Bool,
        hasActiveLiveActivity: Bool
    ) -> Bool {
        guard !hasActiveLiveActivity, let next = nextMatch,
              let matchDate = referenceDate(for: next, useScheduledTime: useScheduledTime) else {
            return false
        }
        let timeUntil = matchDate.timeIntervalSince(now)
        switch mode {
        case .nearMatch:
            return timeUntil > 0 && timeUntil <= 7200
        case .allDay:
            return timeUntil <= 7200
        }
    }

    // MARK: - Private

    /// Returns the reference date for a match based on the scheduling preference.
    /// When `useScheduledTime` is true, returns the scheduled `time` directly.
    /// Otherwise, falls back to matchDate which prefers actual > predicted > scheduled.
    private func referenceDate(for match: Match, useScheduledTime: Bool) -> Date? {
        if useScheduledTime, let scheduled = match.time {
            return Date(timeIntervalSince1970: TimeInterval(scheduled))
        }
        return match.matchDate(useScheduled: false)
    }
}
