import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

struct LiveActivityLockScreenView: View {
    let context: ActivityViewContext<MatchActivityAttributes>

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(context.attributes.matchLabel).font(.headline)
                Spacer()
                Text(context.attributes.eventName).font(.subheadline).foregroundStyle(.secondary)
            }

            switch context.state.matchState {
            case .upcoming, .imminent:
                VStack(spacing: 2) {
                    let target = context.state.queueTime ?? context.state.matchTime
                    if let target {
                        Text(target, style: .timer)
                            .font(.system(size: 32, weight: .bold)).monospacedDigit()
                            .frame(maxWidth: .infinity, alignment: .center)
                        Text(context.state.queueTime != nil ? "to queue" : "to match")
                            .font(.caption).foregroundStyle(.secondary)
                            .frame(maxWidth: .infinity, alignment: .center)
                    }
                }
            case .inProgress:
                Text("Match in progress")
                    .font(.title3).fontWeight(.semibold)
                    .frame(maxWidth: .infinity, alignment: .center)
            case .completed:
                HStack {
                    Spacer()
                    Text("\(context.state.redScore ?? 0)")
                        .font(.system(size: 36, weight: .bold)).foregroundStyle(.red)
                    Text("--").font(.title2).foregroundStyle(.secondary)
                    Text("\(context.state.blueScore ?? 0)")
                        .font(.system(size: 36, weight: .bold)).foregroundStyle(.blue)
                    let tracked = context.attributes.trackedAllianceColor
                    let won = context.state.winningAlliance == tracked
                    Text(won ? "WIN" : "LOSS")
                        .font(.caption).fontWeight(.bold)
                        .foregroundStyle(won ? .green : .red)
                        .padding(.leading, 8)
                    Spacer()
                }
            }

            laAllianceLine(color: "red", teams: context.attributes.redTeams, opr: context.state.redAllianceOPR)
            laAllianceLine(color: "blue", teams: context.attributes.blueTeams, opr: context.state.blueAllianceOPR)

            if let rank = context.state.rank, let record = context.state.record {
                Text("Rank #\(rank) \u{00B7} \(record)").font(.caption).foregroundStyle(.secondary)
            }
        }
        .padding()
    }

    private func laAllianceLine(color: String, teams: [String], opr: Double?) -> some View {
        HStack(spacing: 4) {
            Circle().fill(color == "red" ? Color.red : Color.blue).frame(width: 8, height: 8)
            ForEach(teams, id: \.self) { team in
                if team == "\(context.attributes.teamNumber)" {
                    Text(team).font(.caption).bold()
                } else {
                    Text(team).font(.caption).foregroundStyle(.secondary)
                }
            }
            Spacer()
            if let opr {
                Text("\u{03A3} OPR \(opr, specifier: "%.1f")")
                    .font(.caption2).foregroundStyle(color == "red" ? .red : .blue)
            }
        }
    }
}
