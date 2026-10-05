import Foundation

/// A FIRST Robotics Competition team from The Blue Alliance API v3.
public struct Team: Codable, Sendable, Identifiable {
    public var id: String { key }

    public let key: String
    public let teamNumber: Int
    public let name: String?
    public let nickname: String?
    public let city: String?
    public let stateProv: String?
    public let country: String?
    public let website: String?
    public let rookieYear: Int?

    enum CodingKeys: String, CodingKey {
        case key
        case teamNumber = "team_number"
        case name
        case nickname
        case city
        case stateProv = "state_prov"
        case country
        case website
        case rookieYear = "rookie_year"
    }
}
