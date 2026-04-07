import Foundation

/// Top-level response for event rankings from The Blue Alliance API v3.
public struct EventRankings: Codable, Sendable {
    public let rankings: [Ranking]
    public let sortOrderInfo: [SortOrderInfo]

    enum CodingKeys: String, CodingKey {
        case rankings
        case sortOrderInfo = "sort_order_info"
    }
}

/// A single team's ranking at an event.
public struct Ranking: Codable, Sendable {
    public let teamKey: String
    public let rank: Int
    public let record: WLTRecord?
    public let qualAverage: Double?
    public let matchesPlayed: Int
    public let dq: Int
    public let sortOrders: [Double]?

    enum CodingKeys: String, CodingKey {
        case teamKey = "team_key"
        case rank
        case record
        case qualAverage = "qual_average"
        case matchesPlayed = "matches_played"
        case dq
        case sortOrders = "sort_orders"
    }
}

/// A win-loss-tie record.
public struct WLTRecord: Codable, Sendable, Equatable {
    public let wins: Int
    public let losses: Int
    public let ties: Int

    /// A human-readable display string, e.g. "5-2-0".
    public var display: String {
        "\(wins)-\(losses)-\(ties)"
    }
}

/// Describes a sort order column in the rankings table.
public struct SortOrderInfo: Codable, Sendable {
    public let name: String
    public let precision: Int
}
