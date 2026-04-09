import Foundation

public final class NexusClient: Sendable {
    public static let defaultBaseURL = URL(string: "https://frc.nexus/api/v1")!

    private let apiKey: String
    private let baseURL: URL
    private let session: URLSession

    public init(apiKey: String, baseURL: URL = NexusClient.defaultBaseURL, session: URLSession = .shared) {
        self.apiKey = apiKey
        self.baseURL = baseURL
        self.session = session
    }

    public func buildRequest(eventKey: String) -> URLRequest {
        let url = baseURL.appendingPathComponent("event/\(eventKey)")
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "Nexus-Api-Key")
        request.setValue("PitWatch", forHTTPHeaderField: "User-Agent")
        return request
    }

    /// Fetches live event status from Nexus. Returns nil on any failure (silent degradation).
    public func fetchEventStatus(eventKey: String) async -> NexusEvent? {
        let request = buildRequest(eventKey: eventKey)
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                return nil
            }
            return try JSONDecoder().decode(NexusEvent.self, from: data)
        } catch {
            return nil
        }
    }

    /// Fetches pit map data from Nexus. Returns nil on any failure.
    public func fetchPitMap(eventKey: String) async -> PitMap? {
        let url = baseURL.appendingPathComponent("event/\(eventKey)/map")
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "Nexus-Api-Key")
        request.setValue("PitWatch", forHTTPHeaderField: "User-Agent")
        do {
            let (data, response) = try await session.data(for: request)
            guard let http = response as? HTTPURLResponse, http.statusCode == 200 else {
                return nil
            }
            return try JSONDecoder().decode(PitMap.self, from: data)
        } catch {
            return nil
        }
    }
}
