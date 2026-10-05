import SwiftUI
import TBAKit

struct MatchListWatchView: View {
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
            if let next = schedule.nextMatch {
                Section("Next Match") {
                    watchMatchRow(next, highlight: true)
                }
            }
            if !schedule.pastMatches.isEmpty {
                Section("Results") {
                    ForEach(schedule.pastMatches.prefix(5)) { match in
                        watchMatchRow(match, highlight: false)
                    }
                }
            }
        }
        .navigationTitle("Team \(config.teamNumber ?? 0)")
        .onAppear { eventCache = store.loadEventCache() }
    }

    @ViewBuilder
    private func watchMatchRow(_ match: Match, highlight: Bool) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                let color = match.allianceColor(for: config.teamKey ?? "")
                Circle()
                    .fill(color == "red" ? Color.red : (color == "blue" ? Color.blue : Color.gray))
                    .frame(width: 8, height: 8)
                Text(match.shortLabel).font(highlight ? .headline : .body)
                Spacer()
                if !match.isPlayed, let date = match.matchDate(useScheduled: config.useScheduledTime) {
                    Text(date, style: .time).font(.caption).foregroundStyle(.secondary)
                }
            }
            if match.isPlayed {
                HStack {
                    Text("\(match.alliances["red"]?.score ?? 0)").foregroundStyle(.red)
                    Text("–").foregroundStyle(.secondary)
                    Text("\(match.alliances["blue"]?.score ?? 0)").foregroundStyle(.blue)
                    let won = match.winningAlliance == match.allianceColor(for: config.teamKey ?? "")
                    Text(won ? "W" : "L").font(.caption2).fontWeight(.bold)
                        .foregroundStyle(won ? .green : .red)
                }.font(.caption)
            }
        }
    }
}
