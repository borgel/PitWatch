import SwiftUI
import WidgetKit
import TBAKit

struct SmallWidgetView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 4) {
                Text("TEAM \(entry.teamNumber ?? 0)")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                if let color = entry.nextMatchAllianceColor {
                    AllianceDot(color, size: 8)
                }
            }
            if let ranking = entry.ranking {
                Text("Rank #\(ranking.rank) \u{00B7} \(ranking.record?.display ?? "")")
                    .font(.system(size: 10)).foregroundStyle(.tertiary)
            }
            Spacer()
            if let next = entry.nextMatch {
                VStack(spacing: 2) {
                    HStack(spacing: 4) {
                        Text("NEXT MATCH").font(.system(size: 10)).foregroundStyle(.secondary)
                        if let status = entry.nexusStatus {
                            Text(status.uppercased())
                                .font(.system(size: 7, weight: .bold))
                                .padding(.horizontal, 3).padding(.vertical, 1)
                                .background(nexusStatusColor(status).opacity(0.2), in: Capsule())
                                .foregroundStyle(nexusStatusColor(status))
                        }
                    }
                    Text(next.shortLabel).font(.system(size: 26, weight: .bold))
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative).font(.system(size: 12)).foregroundStyle(.secondary)
                        Text(entry.countdownLabel).font(.system(size: 9)).foregroundStyle(.tertiary)
                    }
                }.frame(maxWidth: .infinity)
            } else {
                VStack { Text("No upcoming match").font(.system(size: 11)).foregroundStyle(.secondary) }
                    .frame(maxWidth: .infinity)
            }
            Spacer()
            if let name = entry.eventName {
                Text(name).font(.system(size: 9)).foregroundStyle(.tertiary)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .containerBackground(.fill.tertiary, for: .widget)
    }
}
