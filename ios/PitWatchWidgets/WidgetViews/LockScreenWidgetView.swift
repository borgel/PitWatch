import SwiftUI
import WidgetKit
import TBAKit

struct CircularLockScreenView: View {
    let entry: MatchWidgetEntry

    var body: some View {
        if let ranking = entry.ranking {
            VStack(spacing: 1) {
                Text("#\(String(ranking.rank))")
                    .font(.system(size: 20, weight: .bold, design: .monospaced))
                    .minimumScaleFactor(0.7)
                if let record = ranking.record {
                    Text(record.display)
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                        .foregroundStyle(.secondary)
                }
            }
        } else if let next = entry.nextMatch {
            VStack(spacing: 1) {
                Text(next.shortLabel)
                    .font(.system(size: 10, weight: .semibold, design: .monospaced))
                    .minimumScaleFactor(0.7)
                if let target = entry.countdownTarget {
                    Text(target, style: .timer)
                        .font(.system(size: 14, weight: .bold, design: .monospaced))
                        .monospacedDigit()
                        .minimumScaleFactor(0.7)
                }
            }
        } else {
            VStack(spacing: 1) {
                Image(systemName: "flag.checkered")
                    .font(.system(size: 14))
                    .foregroundStyle(.secondary)
                Text("No data")
                    .font(.system(size: 8))
                    .foregroundStyle(.secondary)
            }
        }
    }
}

struct RectangularLockScreenView: View {
    let entry: MatchWidgetEntry

    private var teamOPR: Double? {
        entry.oprs?.oprs[entry.teamKey]
    }

    private var totalTeamMatches: Int {
        entry.upcomingMatches.count + entry.pastMatches.count + (entry.nextMatch != nil ? 1 : 0)
    }

    private var matchesRemaining: Int {
        entry.upcomingMatches.count + (entry.nextMatch != nil ? 1 : 0)
    }

    var body: some View {
        if entry.ranking != nil || entry.nextMatch != nil {
            VStack(alignment: .leading, spacing: 3) {
                // Row 1: Rank + record
                if let ranking = entry.ranking {
                    HStack {
                        Text("Rank #\(String(ranking.rank))")
                            .font(.system(size: 13, weight: .bold))
                        if let record = ranking.record {
                            Text(record.display)
                                .font(.system(size: 12, weight: .medium, design: .monospaced))
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                    }
                }

                // Row 2: OPR + matches remaining
                HStack(spacing: 12) {
                    if let opr = teamOPR {
                        Label(String(format: "%.1f", opr), systemImage: "bolt.fill")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                    if totalTeamMatches > 0 {
                        Label("\(String(matchesRemaining)) of \(String(totalTeamMatches)) left", systemImage: "list.number")
                            .font(.system(size: 10))
                            .foregroundStyle(.secondary)
                    }
                }

                // Row 3: Next match
                if let next = entry.nextMatch {
                    HStack {
                        Text("Next: \(next.shortLabel)")
                            .font(.system(size: 10, weight: .medium))
                        Spacer()
                        if let target = entry.countdownTarget {
                            Text(target, style: .relative)
                                .font(.system(size: 10))
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        } else {
            VStack(alignment: .leading) {
                Text("PitWatch")
                    .font(.system(size: 13, weight: .semibold))
                Text("No event data")
                    .font(.system(size: 11))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
