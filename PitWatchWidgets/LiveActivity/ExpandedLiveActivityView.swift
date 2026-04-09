import SwiftUI
import ActivityKit
import WidgetKit
import TBAKit

struct ExpandedLiveActivityView: View {
    nonisolated(unsafe) let context: ActivityViewContext<FRCMatchAttributes>?
    nonisolated(unsafe) private let previewState: FRCMatchAttributes.ContentState?
    nonisolated(unsafe) private let previewAttrs: FRCMatchAttributes?
    nonisolated(unsafe) private let showChevronBar: Bool

    /// Normal init from a Live Activity context.
    init(context: ActivityViewContext<FRCMatchAttributes>) {
        self.context = context
        self.previewState = nil
        self.previewAttrs = nil
        self.showChevronBar = true
    }

    /// Preview/embedded init with raw data.
    nonisolated init(state: FRCMatchAttributes.ContentState, attributes: FRCMatchAttributes, showChevronBar: Bool = true) {
        self.context = nil
        self.previewState = state
        self.previewAttrs = attributes
        self.showChevronBar = showChevronBar
    }

    private var state: FRCMatchAttributes.ContentState { previewState ?? context!.state }
    private var attrs: FRCMatchAttributes { previewAttrs ?? context!.attributes }
    private var isOnField: Bool { state.currentPhase == .onField }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            headerRow
                .padding(.bottom, 10)
                .padding(.horizontal, 14)
            heroCountdownRow
                .padding(.bottom, showChevronBar ? 12 : 0)
                .padding(.horizontal, 14)
            if showChevronBar {
                ChevronBar(
                    currentPhase: state.currentPhase,
                    state: state
                )
            }
        }
        .padding(.top, 11)
        .padding(.bottom, showChevronBar ? 0 : 11)
        .overlay(alignment: .topTrailing) {
            lastUpdatedView
                .padding(.top, 11)
                .padding(.trailing, 14)
        }
        .background(cardBackground)
        .clipShape(RoundedRectangle(cornerRadius: 22))
    }

    // MARK: - Header Row

    private var headerRow: some View {
        HStack(spacing: 8) {
            Text(String(attrs.teamNumber))
                .font(.system(size: 14, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)

            Text("\(attrs.alliance.displayName) · Q\(attrs.matchNumber)")
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(attrs.alliance.badgeText)
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(
                    RoundedRectangle(cornerRadius: 4)
                        .fill(attrs.alliance.badgeBackground)
                )

            Spacer()
        }
    }

    // MARK: - Hero Countdown Row

    private var heroCountdownRow: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 0) {
                Text(state.currentPhase.combinedLabel)
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .tracking(1.0)
                    .foregroundStyle(state.currentPhase.color)

                Text(state.phaseDeadline, style: .timer)
                    .font(.system(size: 50, weight: .bold, design: .monospaced))
                    .kerning(-2)
                    .foregroundStyle(.white)
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }

            Spacer()

            let gap = attrs.matchesAway(currentOnField: state.currentMatchOnField)
            VStack(alignment: .trailing, spacing: 0) {
                Text("YOUR MATCH")
                    .font(.system(size: 9, weight: .regular, design: .monospaced))
                    .tracking(0.5)
                    .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.35))

                Text(MatchesAwayDisplay.text(for: gap))
                    .font(.system(size: 15, weight: .bold, design: .monospaced))
                    .foregroundStyle(MatchesAwayDisplay.color(for: gap, phase: state.currentPhase))
                    .padding(.top, 2)
            }
        }
    }

    // MARK: - Last Updated

    private var lastUpdatedView: some View {
        let dimColor = Color(red: 235/255, green: 235/255, blue: 245/255)
        let formatter = DateFormatter()
        formatter.dateFormat = "h:mm:ss a"
        let timeStr = formatter.string(from: state.lastUpdated)
        return VStack(alignment: .trailing, spacing: 2) {
            Text("LAST UPDATED")
                .font(.system(size: 7.5, weight: .medium, design: .monospaced))
                .tracking(0.3)
                .foregroundStyle(dimColor.opacity(0.30))
            Text(timeStr)
                .font(.system(size: 9.5, design: .monospaced))
                .monospacedDigit()
                .foregroundStyle(dimColor.opacity(0.45))
                .lineLimit(1)
        }
    }

    // MARK: - Card Background

    private var cardBackground: some View {
        RoundedRectangle(cornerRadius: 22)
            .fill(isOnField ? Color(hex: "#112214") : Color(hex: "#1C1C1E"))
            .overlay(
                RoundedRectangle(cornerRadius: 22)
                    .strokeBorder(
                        isOnField
                            ? Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20)
                            : Color.clear,
                        lineWidth: 0.5
                    )
            )
    }
}

// MARK: - Previews

#Preview("Queueing") {
    let state = FRCMatchAttributes.ContentState(
        currentPhase: .queueing,
        phaseStartDate: .now.addingTimeInterval(-120),
        phaseDeadline: .now.addingTimeInterval(300),
        currentMatchOnField: 29,
        lastUpdated: .now.addingTimeInterval(-45),
        queueDeadline: .now.addingTimeInterval(-120),
        onDeckDeadline: .now.addingTimeInterval(300),
        onFieldDeadline: .now.addingTimeInterval(600),
        matchEndDeadline: .now.addingTimeInterval(900)
    )
    let attrs = FRCMatchAttributes(
        teamNumber: 1700,
        matchNumber: 32,
        matchLabel: "Q32",
        alliance: .red
    )
    ExpandedLiveActivityView(state: state, attributes: attrs)
        .frame(width: 360)
        .background(Color(hex: "#0D0D0D"))
}

#Preview("On Field") {
    let state = FRCMatchAttributes.ContentState(
        currentPhase: .onField,
        phaseStartDate: .now.addingTimeInterval(-30),
        phaseDeadline: .now.addingTimeInterval(120),
        currentMatchOnField: 32,
        lastUpdated: .now.addingTimeInterval(-10),
        queueDeadline: .now.addingTimeInterval(-600),
        onDeckDeadline: .now.addingTimeInterval(-300),
        onFieldDeadline: .now.addingTimeInterval(-30),
        matchEndDeadline: .now.addingTimeInterval(120)
    )
    let attrs = FRCMatchAttributes(
        teamNumber: 1700,
        matchNumber: 32,
        matchLabel: "Q32",
        alliance: .blue
    )
    ExpandedLiveActivityView(state: state, attributes: attrs)
        .frame(width: 360)
        .background(Color(hex: "#0D0D0D"))
}

#Preview("Pre-Queue") {
    let state = FRCMatchAttributes.ContentState(
        currentPhase: .preQueue,
        phaseStartDate: .now,
        phaseDeadline: .now.addingTimeInterval(2400),
        currentMatchOnField: 25,
        lastUpdated: .now.addingTimeInterval(-180),
        queueDeadline: .now.addingTimeInterval(2400),
        onDeckDeadline: .now.addingTimeInterval(2700),
        onFieldDeadline: .now.addingTimeInterval(3000),
        matchEndDeadline: .now.addingTimeInterval(3300)
    )
    let attrs = FRCMatchAttributes(
        teamNumber: 1700,
        matchNumber: 42,
        matchLabel: "Q42",
        alliance: .red
    )
    ExpandedLiveActivityView(state: state, attributes: attrs)
        .frame(width: 360)
        .background(Color(hex: "#0D0D0D"))
}
