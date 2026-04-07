import SwiftUI
import WidgetKit
import TBAKit

struct MatchListView: View {
    let config: UserConfig
    let store: TBADataStore
    @State private var eventCache: EventCache

    init(config: UserConfig, store: TBADataStore) {
        self.config = config
        self.store = store
        self._eventCache = State(initialValue: store.loadEventCache())
    }

    private var schedule: MatchSchedule {
        MatchSchedule(matches: eventCache.matches, teamKey: config.teamKey ?? "")
    }

    var body: some View {
        List {
            if let event = eventCache.event {
                Section {
                    VStack(alignment: .leading, spacing: 4) {
                        Text(event.name).font(.headline)
                        if let ranking = teamRanking {
                            Text("Rank #\(ranking.rank) · \(ranking.record?.display ?? "0-0-0")")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }

            if !schedule.upcomingMatches.isEmpty {
                Section("Upcoming") {
                    ForEach(schedule.upcomingMatches) { match in
                        matchLink(match)
                    }
                }
            }

            if !schedule.pastMatches.isEmpty {
                Section("Results") {
                    ForEach(schedule.pastMatches) { match in
                        matchLink(match)
                    }
                }
            }

            if schedule.teamMatches.isEmpty {
                ContentUnavailableView(
                    "No Matches",
                    systemImage: "calendar.badge.exclamationmark",
                    description: Text("No matches found for this team at this event.")
                )
            }
        }
        .refreshable {
            await forceRefresh()
        }
        .navigationTitle("Team \(config.teamNumber ?? 0)")
    }

    @ViewBuilder
    private func matchLink(_ match: Match) -> some View {
        Button {
            let url = URL(string: "https://www.thebluealliance.com/match/\(match.key)")!
            UIApplication.shared.open(url)
        } label: {
            MatchRowView(
                match: match,
                teamKey: config.teamKey ?? "",
                oprs: eventCache.oprs,
                useScheduledTime: config.useScheduledTime,
                queueOffsetMinutes: config.queueOffsetMinutes
            )
        }
        .tint(.primary)
    }

    private var teamRanking: Ranking? {
        eventCache.rankings?.rankings.first { $0.teamKey == config.teamKey }
    }

    private func forceRefresh() async {
        guard let apiKey = config.apiKey,
              let eventKey = eventCache.event?.key ?? config.eventKeyOverride else { return }

        let client = TBAClient(apiKey: apiKey)
        do {
            async let matchesResult = client.fetch([Match].self, path: Endpoints.eventMatches(key: eventKey))
            async let rankingsResult = client.fetch(EventRankings.self, path: Endpoints.eventRankings(key: eventKey))
            async let oprsResult = client.fetch(EventOPRs.self, path: Endpoints.eventOPRs(key: eventKey))

            if case .data(let matches, _) = try await matchesResult {
                eventCache.matches = matches
            }
            if case .data(let rankings, _) = try await rankingsResult {
                eventCache.rankings = rankings
            }
            if case .data(let oprs, _) = try await oprsResult {
                eventCache.oprs = oprs
            }

            store.saveEventCache(eventCache)
            WidgetCenter.shared.reloadAllTimelines()
        } catch {
            // Silently fail — data stays as-is
        }
    }
}
