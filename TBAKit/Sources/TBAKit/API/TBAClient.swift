import Foundation

public enum FetchResult<T: Decodable & Sendable>: Sendable {
    case data(T, lastModified: String?)
    case notModified
}

public final class TBAClient: Sendable {
    public static let defaultBaseURL = URL(string: "https://www.thebluealliance.com/api/v3")!

    private let apiKey: String
    private let baseURL: URL
    private let session: URLSession

    public init(apiKey: String, baseURL: URL = TBAClient.defaultBaseURL, session: URLSession = .shared) {
        self.apiKey = apiKey
        self.baseURL = baseURL
        self.session = session
    }

    public func buildRequest(path: String, lastModified: String? = nil) -> URLRequest {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.setValue(apiKey, forHTTPHeaderField: "X-TBA-Auth-Key")
        request.setValue("PitWatch", forHTTPHeaderField: "User-Agent")
        if let lm = lastModified {
            request.setValue(lm, forHTTPHeaderField: "If-Modified-Since")
        }
        return request
    }

    public func fetch<T: Decodable & Sendable>(
        _ type: T.Type,
        path: String,
        lastModified: String? = nil
    ) async throws -> FetchResult<T> {
        let request = buildRequest(path: path, lastModified: lastModified)
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw TBAError.invalidResponse
        }
        if http.statusCode == 304 { return .notModified }
        guard http.statusCode == 200 else {
            let body = String(data: data, encoding: .utf8) ?? ""
            throw TBAError.apiError(statusCode: http.statusCode, message: body)
        }
        let decoded = try JSONDecoder().decode(T.self, from: data)
        let lm = http.value(forHTTPHeaderField: "Last-Modified")
        return .data(decoded, lastModified: lm)
    }

    public func validateTeam(number: Int) async throws -> Team {
        let result = try await fetch(Team.self, path: Endpoints.team(number: number))
        switch result {
        case .data(let team, _): return team
        case .notModified: throw TBAError.unexpected("Got 304 on team validation")
        }
    }
}

public enum TBAError: Error, LocalizedError {
    case invalidResponse
    case apiError(statusCode: Int, message: String)
    case unexpected(String)

    public var errorDescription: String? {
        switch self {
        case .invalidResponse: return "Invalid server response"
        case .apiError(let code, let msg): return "API error \(code): \(msg)"
        case .unexpected(let msg): return msg
        }
    }
}
