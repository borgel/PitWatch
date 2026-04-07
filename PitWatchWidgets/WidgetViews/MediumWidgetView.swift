import SwiftUI
import WidgetKit
import TBAKit

struct MediumWidgetView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("TEAM \(entry.teamNumber ?? 0)").font(.system(size: 12, weight: .semibold))
                Spacer()
                if let ranking = entry.ranking {
                    Text("Rank #\(ranking.rank) \u{00B7} \(ranking.record?.display ?? "")")
                        .font(.system(size: 11)).foregroundStyle(.secondary)
                }
            }
            HStack(spacing: 10) {
                // Next match card
                VStack(alignment: .leading, spacing: 4) {
                    Text("NEXT MATCH").font(.system(size: 9)).foregroundStyle(.secondary)
                    if let next = entry.nextMatch {
                        HStack {
                            Text(next.shortLabel).font(.system(size: 16, weight: .bold))
                            Spacer()
                            if let date = next.matchDate(useScheduled: entry.useScheduledTime) {
                                Text(formatMatchTime(date, prefix: entry.timePrefix))
                                    .font(.system(size: 10)).foregroundStyle(.secondary)
                            }
                        }
                        ForEach(["red", "blue"], id: \.self) { color in
                            let keys = next.alliances[color]?.teamKeys ?? []
                            AllianceLineCompact(allianceColor: color, teamKeys: keys,
                                trackedTeamKey: entry.teamKey, opr: entry.oprs?.summedOPR(for: keys))
                        }
                    } else {
                        Text("None scheduled").font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                }
                .padding(8).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))

                // Last match card
                VStack(alignment: .leading, spacing: 4) {
                    Text("LAST MATCH").font(.system(size: 9)).foregroundStyle(.secondary)
                    if let last = entry.lastMatch {
                        Text(last.shortLabel).font(.system(size: 16, weight: .bold))
                        HStack { Spacer(); ScoreDisplay(match: last).font(.system(size: 18)); Spacer() }
                        HStack { Spacer(); WinLossLabel(match: last, teamKey: entry.teamKey); Spacer() }
                    } else {
                        Text("No results yet").font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                }
                .padding(8).background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 8))
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}
