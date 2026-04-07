import Foundation

/// An FRC match from The Blue Alliance API v3.
public struct Match: Codable, Sendable, Identifiable {
    public var id: String { key }

    public let key: String
    public let compLevel: String
    public let setNumber: Int
    public let matchNumber: Int
    public let eventKey: String
    public let time: Int64?
    public let predictedTime: Int64?
    public let actualTime: Int64?
    public let alliances: [String: Alliance]
    public let winningAlliance: String
    public let scoreBreakdown: [String: AnyCodable]?
    public let videos: [Video]

    enum CodingKeys: String, CodingKey {
        case key
        case compLevel = "comp_level"
        case setNumber = "set_number"
        case matchNumber = "match_number"
        case eventKey = "event_key"
        case time
        case predictedTime = "predicted_time"
        case actualTime = "actual_time"
        case alliances
        case winningAlliance = "winning_alliance"
        case scoreBreakdown = "score_breakdown"
        case videos
    }

    // MARK: - Computed Properties

    /// A human-readable label like "Qual 32", "QF 2-1", "SF 1-3", "Final 1".
    public var label: String {
        switch compLevel {
        case "qm":
            return "Qual \(matchNumber)"
        case "qf":
            return "QF \(setNumber)-\(matchNumber)"
        case "sf":
            return "SF \(setNumber)-\(matchNumber)"
        case "f":
            return "Final \(matchNumber)"
        default:
            return "\(compLevel.uppercased()) \(matchNumber)"
        }
    }

    /// A short label like "Q32", "QF2-1", "SF1-3", "F1".
    public var shortLabel: String {
        switch compLevel {
        case "qm":
            return "Q\(matchNumber)"
        case "qf":
            return "QF\(setNumber)-\(matchNumber)"
        case "sf":
            return "SF\(setNumber)-\(matchNumber)"
        case "f":
            return "F\(matchNumber)"
        default:
            return "\(compLevel.uppercased())\(matchNumber)"
        }
    }

    /// Whether this match has been played (has an actual time and at least one non-negative score).
    public var isPlayed: Bool {
        guard actualTime != nil else { return false }
        return alliances.values.contains { $0.score >= 0 }
    }

    /// Returns a Date for this match, preferring actual time, then predicted, then scheduled.
    /// - Parameter useScheduled: If true, falls back to `time` when actual/predicted are nil.
    public func matchDate(useScheduled: Bool = true) -> Date? {
        if let actual = actualTime {
            return Date(timeIntervalSince1970: TimeInterval(actual))
        }
        if let predicted = predictedTime {
            return Date(timeIntervalSince1970: TimeInterval(predicted))
        }
        if useScheduled, let scheduled = time {
            return Date(timeIntervalSince1970: TimeInterval(scheduled))
        }
        return nil
    }

    /// Returns "red", "blue", or nil for a given team key.
    public func allianceColor(for teamKey: String) -> String? {
        for (color, alliance) in alliances {
            if alliance.teamKeys.contains(teamKey) {
                return color
            }
        }
        return nil
    }

    /// A sort order value for chronological ordering.
    /// Combines comp level priority with match/set numbers.
    public var sortOrder: Int {
        let levelOrder: Int
        switch compLevel {
        case "qm": levelOrder = 0
        case "ef": levelOrder = 1
        case "qf": levelOrder = 2
        case "sf": levelOrder = 3
        case "f":  levelOrder = 4
        default:   levelOrder = 5
        }
        return levelOrder * 1_000_000 + setNumber * 1_000 + matchNumber
    }
}

// MARK: - Alliance

/// An alliance within a match (red or blue side).
public struct Alliance: Codable, Sendable {
    public let score: Int
    public let teamKeys: [String]
    public let surrogateTeamKeys: [String]
    public let dqTeamKeys: [String]

    enum CodingKeys: String, CodingKey {
        case score
        case teamKeys = "team_keys"
        case surrogateTeamKeys = "surrogate_team_keys"
        case dqTeamKeys = "dq_team_keys"
    }
}

// MARK: - Video

/// A video associated with a match.
public struct Video: Codable, Sendable {
    public let type: String
    public let key: String
}

// MARK: - AnyCodable

/// A type-erased Codable wrapper, used for score_breakdown dictionaries
/// whose shape changes per game year.
public enum AnyCodable: Codable, Sendable {
    case null
    case bool(Bool)
    case int(Int)
    case double(Double)
    case string(String)
    case array([AnyCodable])
    case dictionary([String: AnyCodable])

    public init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()

        if container.decodeNil() {
            self = .null
        } else if let bool = try? container.decode(Bool.self) {
            self = .bool(bool)
        } else if let int = try? container.decode(Int.self) {
            self = .int(int)
        } else if let double = try? container.decode(Double.self) {
            self = .double(double)
        } else if let string = try? container.decode(String.self) {
            self = .string(string)
        } else if let array = try? container.decode([AnyCodable].self) {
            self = .array(array)
        } else if let dict = try? container.decode([String: AnyCodable].self) {
            self = .dictionary(dict)
        } else {
            throw DecodingError.dataCorruptedError(
                in: container,
                debugDescription: "AnyCodable cannot decode value"
            )
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()

        switch self {
        case .null:
            try container.encodeNil()
        case .bool(let value):
            try container.encode(value)
        case .int(let value):
            try container.encode(value)
        case .double(let value):
            try container.encode(value)
        case .string(let value):
            try container.encode(value)
        case .array(let value):
            try container.encode(value)
        case .dictionary(let value):
            try container.encode(value)
        }
    }
}
