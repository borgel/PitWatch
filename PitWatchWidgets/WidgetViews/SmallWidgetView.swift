import SwiftUI
import WidgetKit
import TBAKit

struct SmallWidgetView: View {
    let entry: MatchWidgetEntry

    private var matchTime: Date? {
        entry.nextMatch?.matchDate(useScheduled: true)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // Header
            HStack(spacing: 4) {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let color = entry.nextMatchAllianceColor {
                    AllianceDot(color, size: 6)
                }
            }
            if let ranking = entry.ranking {
                Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.secondary)
            }

            Spacer()

            if let next = entry.nextMatch {
                // Match label
                Text(next.shortLabel)
                    .font(.system(size: 28, weight: .bold, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .center)

                // Countdown
                if let target = entry.countdownTarget {
                    Text(target, style: .relative)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(.secondary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }

                // Wall clock time
                if let time = matchTime {
                    Text(formatMatchTime(time, prefix: entry.timePrefix))
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(.tertiary)
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            } else {
                Text("No match")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .center)
            }

            Spacer()

            if let name = entry.eventName {
                Text(name)
                    .font(.system(size: 9))
                    .foregroundStyle(.tertiary)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .containerBackground(for: .widget) {
            Color(hex: "#1C1C1E")
        }
    }
}
