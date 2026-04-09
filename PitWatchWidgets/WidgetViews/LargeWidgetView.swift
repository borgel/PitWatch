import SwiftUI
import WidgetKit
import TBAKit

struct LargeWidgetView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("TEAM \(entry.teamNumber ?? 0)").font(.system(size: 12, weight: .semibold))
                if let name = entry.eventName {
                    Text("\u{00B7} \(name)").font(.system(size: 12)).foregroundStyle(.secondary)
                }
                Spacer()
                if let ranking = entry.ranking {
                    Text("Rank #\(ranking.rank) \u{00B7} \(ranking.record?.display ?? "")")
                        .font(.system(size: 11)).foregroundStyle(.secondary)
                }
            }

            if let nowQueuing = entry.nowQueuing {
                HStack(spacing: 4) {
                    Image(systemName: "arrow.right.circle.fill")
                        .font(.system(size: 9)).foregroundStyle(.orange)
                    Text("Queuing: \(nowQueuing)")
                        .font(.system(size: 10)).foregroundStyle(.orange)
                }
            }

            if let next = entry.nextMatch {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("UP NEXT \u{2192}").font(.system(size: 10)).foregroundStyle(.secondary)
                        Text(next.label).font(.system(size: 14, weight: .bold))
                        if let status = entry.nexusStatus {
                            Text(status.uppercased())
                                .font(.system(size: 8, weight: .bold))
                                .padding(.horizontal, 4).padding(.vertical, 1)
                                .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                                .foregroundStyle(nexusStatusColor(status))
                        }
                        Spacer()
                        if let target = entry.countdownTarget {
                            Text(target, style: .relative).font(.system(size: 12)).foregroundStyle(.secondary)
                        }
                    }
                    ForEach(["red", "blue"], id: \.self) { color in
                        let keys = next.alliances[color]?.teamKeys ?? []
                        AllianceLineCompact(allianceColor: color, teamKeys: keys,
                            trackedTeamKey: entry.teamKey, opr: entry.oprs?.summedOPR(for: keys))
                    }
                }
                .padding(8).background(Color.accentColor.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
            }

            if !entry.upcomingMatches.isEmpty {
                Text("UPCOMING").font(.system(size: 9)).foregroundStyle(.tertiary)
                ForEach(entry.upcomingMatches) { match in
                    HStack {
                        Text(match.shortLabel).font(.system(size: 11))
                        if let color = match.allianceColor(for: entry.teamKey) {
                            AllianceDot(color, size: 6)
                        }
                        Spacer()
                        if let date = match.matchDate(useScheduled: entry.useScheduledTime) {
                            Text(formatMatchTime(date, prefix: entry.timePrefix))
                                .font(.system(size: 11)).foregroundStyle(.secondary)
                        }
                    }
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 4))
                }
            }

            if !entry.pastMatches.isEmpty {
                Text("RECENT RESULTS").font(.system(size: 9)).foregroundStyle(.tertiary)
                ForEach(entry.pastMatches) { match in
                    HStack {
                        Text(match.shortLabel).font(.system(size: 11))
                        if let color = match.allianceColor(for: entry.teamKey) {
                            AllianceDot(color, size: 6)
                        }
                        Spacer()
                        ScoreDisplay(match: match).font(.system(size: 11))
                        WinLossLabel(match: match, teamKey: entry.teamKey)
                    }
                    .padding(.horizontal, 8).padding(.vertical, 3)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 4))
                }
            }
            Spacer(minLength: 0)
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}
