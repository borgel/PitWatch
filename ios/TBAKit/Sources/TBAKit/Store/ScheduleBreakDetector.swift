import Foundation

/// A non-match interval between scheduled matches — lunch, overnight, or a
/// between-session gap (e.g. practice block ending before qualifications).
///
/// Derived from gaps in `NexusMatch.times.estimatedStartTime`; neither TBA
/// nor Nexus exposes break times as first-class data.
public struct ScheduleBreak: Sendable, Hashable {
    public enum Kind: Sendable, Hashable {
        /// A gap within a single local day that straddles the midday window.
        case lunch
        /// A gap within a single local day that does not straddle midday.
        case sessionBreak
        /// A gap that crosses a local-day boundary.
        case overnight
    }

    public let kind: Kind
    public let startsAfter: String
    public let endsBefore: String
    public let start: Date
    public let end: Date

    public var duration: TimeInterval { end.timeIntervalSince(start) }

    public init(kind: Kind, startsAfter: String, endsBefore: String, start: Date, end: Date) {
        self.kind = kind
        self.startsAfter = startsAfter
        self.endsBefore = endsBefore
        self.start = start
        self.end = end
    }
}

public enum ScheduleBreakDetector {
    /// Finds gaps in the match schedule that look like breaks.
    ///
    /// Gaps shorter than `minimumGap` are treated as normal field-turnaround
    /// cycles. `timeZone` must be the event's local zone — classification
    /// asks "did we cross midnight?" and "do we straddle local noon?", both
    /// of which are meaningless in UTC.
    ///
    /// - Parameters:
    ///   - matches: Nexus matches in any order. Matches without an
    ///     `estimatedStartTime` are ignored.
    ///   - timeZone: Event-local time zone, typically from `Event.timezone`.
    ///   - minimumGap: Smallest gap (in seconds) to report. Defaults to 20
    ///     minutes — roughly 2.5× a typical FRC cycle.
    public static func detectBreaks(
        in matches: [NexusMatch],
        timeZone: TimeZone,
        minimumGap: TimeInterval = 20 * 60
    ) -> [ScheduleBreak] {
        let stamped: [(label: String, date: Date)] = matches
            .compactMap { match in
                match.times.startDate.map { (match.label, $0) }
            }
            .sorted { $0.date < $1.date }

        guard stamped.count >= 2 else { return [] }

        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone

        return zip(stamped, stamped.dropFirst()).compactMap { prev, curr in
            let gap = curr.date.timeIntervalSince(prev.date)
            guard gap >= minimumGap else { return nil }

            let kind: ScheduleBreak.Kind
            if !calendar.isDate(prev.date, inSameDayAs: curr.date) {
                kind = .overnight
            } else if straddlesLocalLunch(from: prev.date, to: curr.date, calendar: calendar) {
                kind = .lunch
            } else {
                kind = .sessionBreak
            }

            return ScheduleBreak(
                kind: kind,
                startsAfter: prev.label,
                endsBefore: curr.label,
                start: prev.date,
                end: curr.date
            )
        }
    }

    /// True if `[start, end]` overlaps the 11:30–13:00 window on `start`'s local day.
    private static func straddlesLocalLunch(
        from start: Date,
        to end: Date,
        calendar: Calendar
    ) -> Bool {
        guard
            let lunchStart = calendar.date(bySettingHour: 11, minute: 30, second: 0, of: start),
            let lunchEnd = calendar.date(bySettingHour: 13, minute: 0, second: 0, of: start)
        else { return false }
        return start <= lunchEnd && end >= lunchStart
    }
}
