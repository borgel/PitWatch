import Testing
import Foundation
@testable import TBAKit

@Test func endpointPaths() {
    #expect(Endpoints.team(number: 1234) == "/team/frc1234")
    #expect(Endpoints.teamEvents(number: 1234, year: 2026) == "/team/frc1234/events/2026")
    #expect(Endpoints.event(key: "2026miket") == "/event/2026miket")
    #expect(Endpoints.eventMatches(key: "2026miket") == "/event/2026miket/matches")
    #expect(Endpoints.eventRankings(key: "2026miket") == "/event/2026miket/rankings")
    #expect(Endpoints.eventOPRs(key: "2026miket") == "/event/2026miket/oprs")
    #expect(Endpoints.eventTeams(key: "2026miket") == "/event/2026miket/teams")
    #expect(Endpoints.match(key: "2026miket_qm32") == "/match/2026miket_qm32")
    #expect(Endpoints.status == "/status")
}

@Test func clientBuildsCorrectRequest() async throws {
    let client = TBAClient(apiKey: "test-key-123", baseURL: URL(string: "https://example.com/api/v3")!)
    let request = client.buildRequest(path: "/team/frc1234", lastModified: "Mon, 01 Jan 2026 00:00:00 GMT")
    #expect(request.value(forHTTPHeaderField: "X-TBA-Auth-Key") == "test-key-123")
    #expect(request.value(forHTTPHeaderField: "User-Agent") == "PitWatch")
    #expect(request.value(forHTTPHeaderField: "If-Modified-Since") == "Mon, 01 Jan 2026 00:00:00 GMT")
    #expect(request.url?.absoluteString == "https://example.com/api/v3/team/frc1234")
}
