import Testing
import Foundation
@testable import TBAKit

@Test func nexusClientBuildsCorrectRequest() async throws {
    let client = NexusClient(
        apiKey: "nexus-test-key",
        baseURL: URL(string: "https://example.com/api/v1")!
    )
    let request = client.buildRequest(eventKey: "2026miket")
    #expect(request.value(forHTTPHeaderField: "Nexus-Api-Key") == "nexus-test-key")
    #expect(request.value(forHTTPHeaderField: "User-Agent") == "PitWatch")
    #expect(request.url?.absoluteString == "https://example.com/api/v1/event/2026miket")
}
