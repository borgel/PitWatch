import Foundation

public enum Endpoints {
    public static func team(number: Int) -> String { "/team/frc\(number)" }
    public static func teamEvents(number: Int, year: Int) -> String { "/team/frc\(number)/events/\(year)" }
    public static func event(key: String) -> String { "/event/\(key)" }
    public static func eventMatches(key: String) -> String { "/event/\(key)/matches" }
    public static func eventRankings(key: String) -> String { "/event/\(key)/rankings" }
    public static func eventOPRs(key: String) -> String { "/event/\(key)/oprs" }
    public static func eventTeams(key: String) -> String { "/event/\(key)/teams" }
    public static func match(key: String) -> String { "/match/\(key)" }
    public static let status = "/status"
}
