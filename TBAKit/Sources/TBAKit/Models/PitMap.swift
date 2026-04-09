import Foundation

/// Pit map data from the FRC Nexus API `GET /event/{eventKey}/map`.
public struct PitMap: Codable, Sendable {
    public let size: MapSize
    public let pits: [String: Pit]
    public let areas: [String: Area]?
    public let labels: [String: MapLabel]?
    public let arrows: [String: Arrow]?
    public let walls: [String: Wall]?

    public struct MapSize: Codable, Sendable {
        public let x: Double
        public let y: Double
    }

    public struct Position: Codable, Sendable {
        public let x: Double
        public let y: Double
    }

    public struct Pit: Codable, Sendable {
        public let position: Position
        public let size: MapSize
        public let team: String?
    }

    public struct Area: Codable, Sendable {
        public let label: String
        public let position: Position
        public let size: MapSize
    }

    public struct MapLabel: Codable, Sendable {
        public let label: String
        public let position: Position
        public let size: MapSize
    }

    public struct Arrow: Codable, Sendable {
        public let position: Position
        public let size: MapSize
        public let type: String?
        public let angle: Double?
    }

    public struct Wall: Codable, Sendable {
        public let position: Position
        public let size: MapSize
    }

    /// Find the pit assigned to a given team number (e.g. "1700").
    public func pit(forTeam teamNumber: String) -> (address: String, pit: Pit)? {
        guard let entry = pits.first(where: { $0.value.team == teamNumber }) else { return nil }
        return (address: entry.key, pit: entry.value)
    }
}
