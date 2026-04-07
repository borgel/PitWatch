import Foundation

/// Offensive Power Ratings, Defensive Power Ratings, and Calculated
/// Contribution to Winning Margin for teams at an event.
public struct EventOPRs: Codable, Sendable {
    public let oprs: [String: Double]
    public let dprs: [String: Double]
    public let ccwms: [String: Double]

    /// Returns the summed OPR for a list of team keys.
    /// Returns nil if any team key is missing from the OPR data.
    public func summedOPR(for teamKeys: [String]) -> Double? {
        var total = 0.0
        for key in teamKeys {
            guard let opr = oprs[key] else { return nil }
            total += opr
        }
        return total
    }
}
