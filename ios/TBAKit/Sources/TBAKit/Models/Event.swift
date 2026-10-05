import Foundation

/// An FRC event (competition) from The Blue Alliance API v3.
public struct Event: Codable, Sendable, Identifiable {
    public var id: String { key }

    public let key: String
    public let name: String
    public let eventCode: String
    public let eventType: Int
    public let city: String?
    public let stateProv: String?
    public let country: String?
    public let startDate: String
    public let endDate: String
    public let year: Int
    public let shortName: String?
    public let eventTypeString: String?
    public let week: Int?
    public let locationName: String?
    public let timezone: String?

    enum CodingKeys: String, CodingKey {
        case key
        case name
        case eventCode = "event_code"
        case eventType = "event_type"
        case city
        case stateProv = "state_prov"
        case country
        case startDate = "start_date"
        case endDate = "end_date"
        case year
        case shortName = "short_name"
        case eventTypeString = "event_type_string"
        case week
        case locationName = "location_name"
        case timezone
    }

    // MARK: - Computed Properties

    private static let dateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.timeZone = TimeZone(identifier: "UTC")
        return formatter
    }()

    /// The start date parsed from the "YYYY-MM-DD" string.
    public var startDateParsed: Date? {
        Self.dateFormatter.date(from: startDate)
    }

    /// The end date parsed from the "YYYY-MM-DD" string.
    public var endDateParsed: Date? {
        Self.dateFormatter.date(from: endDate)
    }

    /// Whether the given date falls within the event's date range (inclusive, full-day granularity).
    public func isActive(on date: Date) -> Bool {
        guard let start = startDateParsed, let end = endDateParsed else { return false }
        // Use the end of the end date (add one day minus one second) so
        // the entire end-date day is included.
        let calendar = Calendar.current
        guard let endOfDay = calendar.date(byAdding: .day, value: 1, to: end)?
            .addingTimeInterval(-1) else { return false }
        return date >= start && date <= endOfDay
    }
}
