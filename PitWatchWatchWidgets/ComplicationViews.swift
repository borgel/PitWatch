import SwiftUI
import WidgetKit
import TBAKit

struct CircularComplicationView: View {
    let entry: WatchMatchEntry
    var body: some View {
        if let next = entry.nextMatch {
            ZStack {
                AccessoryWidgetBackground()
                VStack(spacing: 1) {
                    HStack(spacing: 2) {
                        Circle()
                            .fill(entry.allianceColor == "red" ? Color.red : (entry.allianceColor == "blue" ? Color.blue : Color.gray))
                            .frame(width: 5, height: 5)
                        Text(next.shortLabel).font(.system(size: 9))
                    }
                    if let target = entry.countdownTarget {
                        Text(target, style: .timer).font(.system(size: 15, weight: .bold)).monospacedDigit()
                    }
                }
            }.widgetAccentable()
        } else {
            ZStack {
                AccessoryWidgetBackground()
                Text("\(entry.teamNumber ?? 0)").font(.system(size: 14, weight: .bold))
            }
        }
    }
}

struct RectangularComplicationView: View {
    let entry: WatchMatchEntry
    var body: some View {
        if let next = entry.nextMatch {
            VStack(alignment: .leading, spacing: 2) {
                HStack {
                    Text(next.label).font(.system(size: 12, weight: .semibold))
                    Spacer()
                    if let target = entry.countdownTarget {
                        Text(target, style: .relative).font(.system(size: 10)).foregroundStyle(.secondary)
                    }
                }
                if let ranking = entry.ranking {
                    Text("#\(ranking.rank) \u{00B7} \(ranking.record?.display ?? "")")
                        .font(.system(size: 9)).foregroundStyle(.secondary)
                }
            }
        } else {
            VStack(alignment: .leading) {
                Text("Team \(entry.teamNumber ?? 0)").font(.system(size: 12, weight: .semibold))
                Text("No match").font(.system(size: 10)).foregroundStyle(.secondary)
            }
        }
    }
}
