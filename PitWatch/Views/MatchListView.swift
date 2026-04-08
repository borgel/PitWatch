import SwiftUI
import WidgetKit
import TBAKit

struct MatchListView: View {
    @Binding var config: UserConfig
    let store: TBADataStore
    @State private var eventCache: EventCache
    @State private var isLoading = false
    @State private var errorMessage: String?

    init(config: Binding<UserConfig>, store: TBADataStore) {
        self._config = config
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

            if let error = errorMessage {
                Section {
                    Label(error, systemImage: "exclamationmark.triangle.fill")
                        .foregroundStyle(.red)
                        .font(.caption)
                }
            }

            if isLoading && schedule.teamMatches.isEmpty {
                Section {
                    HStack {
                        Spacer()
                        ProgressView("Loading matches...")
                        Spacer()
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

            if !isLoading && schedule.teamMatches.isEmpty && eventCache.event != nil {
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
        .task {
            if eventCache.matches.isEmpty {
                await loadData()
            }
        }
        .onChange(of: config.eventKeyOverride) { _, _ in
            Task { await loadData() }
        }
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

    private func loadData() async {
        guard let apiKey = config.apiKey else { return }
        isLoading = true
        errorMessage = nil
        do {
            try await BackgroundRefresh.performRefresh(
                store: store, config: config, apiKey: apiKey, forceReload: true
            )
            eventCache = store.loadEventCache()
        } catch {
            errorMessage = "Failed to load: \(error.localizedDescription)"
        }
        isLoading = false
    }

    private func forceRefresh() async {
        guard let apiKey = config.apiKey else { return }
        errorMessage = nil
        do {
            try await BackgroundRefresh.performRefresh(
                store: store, config: config, apiKey: apiKey, forceReload: true
            )
            eventCache = store.loadEventCache()
        } catch {
            errorMessage = "Refresh failed: \(error.localizedDescription)"
        }
    }
}
