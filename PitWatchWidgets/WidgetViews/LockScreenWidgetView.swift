import SwiftUI
import WidgetKit
import TBAKit

struct CircularLockScreenView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        if let next = entry.nextMatch {
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 1) {
                    HStack(spacing: 2) {
                        AllianceDot(entry.nextMatchAllianceColor, size: 5)
                        Text(next.shortLabel).font(.system(size: 9))
                    }
                    if let target = entry.countdownTarget {
                        Text(target, style: .timer)
                            .font(.system(size: 16, weight: .bold)).monospacedDigit()
                    }
                    Text(entry.countdownLabel).font(.system(size: 7)).foregroundStyle(.secondary)
                }
            }.widgetAccentable()
        } else {
            ZStack {
                AccessoryWidgetBackground()
                VStack {
                    Text("\(entry.teamNumber ?? 0)").font(.system(size: 14, weight: .bold))
                    Text("No match").font(.system(size: 8)).foregroundStyle(.secondary)
                }
            }
        }
    }
}

struct RectangularLockScreenView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        if let next = entry.nextMatch {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(next.label).font(.system(size: 13, weight: .semibold))
                    Spacer()
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative).font(.system(size: 11)).foregroundStyle(.secondary)
                    }
                }
                ForEach(["red", "blue"], id: \.self) { color in
                    let keys = next.alliances[color]?.teamKeys ?? []
                    HStack(spacing: 2) {
                        AllianceDot(color, size: 4)
                        ForEach(keys, id: \.self) { key in
                            let num = teamNumber(from: key)
                            if key == entry.teamKey {
                                Text(num).font(.system(size: 10)).bold()
                            } else {
                                Text(num).font(.system(size: 10)).foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                if let ranking = entry.ranking {
                    Text("Rank #\(ranking.rank) \u{00B7} \(ranking.record?.display ?? "")")
                        .font(.system(size: 9)).foregroundStyle(.tertiary)
                }
            }
        } else {
            VStack(alignment: .leading) {
                Text("Team \(entry.teamNumber ?? 0)").font(.system(size: 13, weight: .semibold))
                Text("No upcoming match").font(.system(size: 11)).foregroundStyle(.secondary)
            }
        }
    }
}
