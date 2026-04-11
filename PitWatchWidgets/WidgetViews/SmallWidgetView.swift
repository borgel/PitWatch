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
            HStack(spacing: 6) {
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                if let color = entry.nextMatchAllianceColor, let next = entry.nextMatch {
                    AllianceBadge(allianceColor: color, matchLabel: next.shortLabel)
                }
            }
            if let ranking = entry.ranking {
                Text("#\(String(ranking.rank)) · \(ranking.record?.display ?? "")")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(widgetLabelDim.opacity(0.65))
            }

            Spacer()

            if let next = entry.nextMatch {
                // Match label
                Text(next.shortLabel)
                    .font(.system(size: 28, weight: .bold, design: .monospaced))
                    .frame(maxWidth: .infinity, alignment: .center)

                // Countdown — phase-colored when Nexus provides a phase
                if let target = entry.countdownTarget {
                    Text(target, style: .relative)
                        .font(.system(size: 13, weight: .medium, design: .monospaced))
                        .monospacedDigit()
                        .foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))
                        .frame(maxWidth: .infinity, alignment: .center)
                }

                // Wall clock time
                if let time = matchTime {
                    Text(formatMatchTime(time, prefix: entry.timePrefix))
                        .font(.system(size: 10, design: .monospaced))
                        .foregroundStyle(widgetLabelDim.opacity(0.45))
                        .frame(maxWidth: .infinity, alignment: .center)
                }
            } else {
                Text("No match")
                    .font(.system(size: 12, design: .monospaced))
                    .foregroundStyle(widgetLabelDim.opacity(0.65))
                    .frame(maxWidth: .infinity, alignment: .center)
            }

            Spacer()

            if let name = entry.eventName {
                Text(name)
                    .font(.system(size: 9))
                    .foregroundStyle(widgetLabelDim.opacity(0.45))
                    .lineLimit(2)
                    .multilineTextAlignment(.trailing)
                    .frame(maxWidth: .infinity, alignment: .trailing)
            }
        }
        .containerBackground(for: .widget) {
            widgetCardBackground
        }
    }
}

#Preview("Small · Empty", as: .systemSmall) {
    NextMatchWidget()
} timeline: {
    MatchWidgetEntry.placeholder
}
