import SwiftUI
import TBAKit

struct ChevronShape: Shape {
    let arrowDepth: CGFloat
    let isLast: Bool

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let tipX = isLast ? rect.maxX : rect.maxX - arrowDepth
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: tipX, y: rect.minY))
        if !isLast {
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        }
        path.addLine(to: CGPoint(x: tipX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

enum ChevronMilestone: Int, CaseIterable, Identifiable {
    case inQueue = 0
    case onDeck = 1
    case onField = 2
    case matchEnd = 3

    var id: Int { rawValue }

    var label: String {
        switch self {
        case .inQueue:  return "IN QUEUE"
        case .onDeck:   return "ON DECK"
        case .onField:  return "ON FIELD"
        case .matchEnd: return "MATCH END"
        }
    }

    var color: Color {
        switch self {
        case .inQueue:  return Phase.queueing.color
        case .onDeck:   return Phase.onDeck.color
        case .onField:  return Phase.onField.color
        case .matchEnd: return Phase.onField.color
        }
    }

    /// Timer target — end of this milestone's window.
    func deadline(state: FRCMatchAttributes.ContentState) -> Date? {
        switch self {
        case .inQueue:  return state.onDeckDeadline
        case .onDeck:   return state.onFieldDeadline
        case .onField:  return state.matchStartDeadline
        case .matchEnd: return state.matchEndDeadline
        }
    }
}

private enum SegmentState {
    case completed, active, nextPending, farPending
}

private func milestoneState(
    milestone: ChevronMilestone,
    currentPhase: Phase,
    matchStartDeadline: Date?,
    matchEndDeadline: Date?,
    now: Date
) -> SegmentState {
    // While upcoming, every milestone renders as far-pending — we wait for
    // Nexus to report a real "now queuing" state before highlighting anything.
    if currentPhase == .preQueue {
        return .farPending
    }

    let matchHasStarted = matchStartDeadline.map { now >= $0 } ?? false
    let matchHasEnded = matchEndDeadline.map { now >= $0 } ?? false

    switch milestone {
    case .inQueue:
        if currentPhase == .queueing { return .active }
        if currentPhase.rawValue > Phase.queueing.rawValue { return .completed }
        return .farPending
    case .onDeck:
        if currentPhase == .onDeck { return .active }
        if currentPhase.rawValue > Phase.onDeck.rawValue { return .completed }
        if currentPhase == .queueing { return .nextPending }
        return .farPending
    case .onField:
        if currentPhase == .onField {
            return matchHasStarted ? .completed : .active
        }
        if currentPhase == .onDeck { return .nextPending }
        return .farPending
    case .matchEnd:
        if currentPhase == .onField {
            if matchHasEnded { return .completed }
            if matchHasStarted { return .active }
            return .nextPending
        }
        return .farPending
    }
}

struct ChevronSegment: View {
    let milestone: ChevronMilestone
    let currentPhase: Phase
    let matchStartDeadline: Date?
    let matchEndDeadline: Date?
    let arrowDepth: CGFloat

    private var state: SegmentState {
        milestoneState(
            milestone: milestone,
            currentPhase: currentPhase,
            matchStartDeadline: matchStartDeadline,
            matchEndDeadline: matchEndDeadline,
            now: .now
        )
    }

    let deadline: Date?

    private var backgroundColor: Color {
        switch state {
        case .completed:   return Color(hex: "#1A3D1F")
        case .active:      return milestone.color
        case .nextPending: return Color(hex: "#2A2A2A")
        case .farPending:  return Color(hex: "#222222")
        }
    }

    var body: some View {
        ZStack {
            backgroundColor

            let isFirst = milestone == .inQueue
            let isLast = milestone == .matchEnd

            content
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(.top, 12)
                .padding(.leading, (isFirst ? 10 : arrowDepth) + 6)
                .padding(.trailing, (isLast ? 0 : arrowDepth) + 6)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state {
        case .completed:
            HStack(spacing: 4) {
                Image(systemName: "checkmark.circle")
                    .font(.system(size: 10))
                Text(milestone.label)
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .tracking(0.5)
            }
            .foregroundStyle(Color(hex: "#30D158").opacity(0.85))

        case .active:
            VStack(alignment: .leading, spacing: 1) {
                Text(milestone.label)
                    .font(.system(size: 11, weight: .bold, design: .monospaced))
                    .tracking(0.5)
                    .foregroundStyle(.black)
                if let deadline {
                    PhaseCountdownText(deadline: deadline)
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                        .foregroundStyle(.black.opacity(0.65))
                        .monospacedDigit()
                }
            }

        case .nextPending:
            VStack(alignment: .leading, spacing: 1) {
                Text(milestone.label)
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .tracking(0.5)
                if let deadline {
                    PhaseCountdownText(deadline: deadline)
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                        .monospacedDigit()
                        .opacity(0.7)
                }
            }
            .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.45))

        case .farPending:
            VStack(alignment: .leading, spacing: 1) {
                Text(milestone.label)
                    .font(.system(size: 10, weight: .medium, design: .monospaced))
                    .tracking(0.5)
                if let deadline {
                    PhaseCountdownText(deadline: deadline)
                        .font(.system(size: 9, weight: .medium, design: .monospaced))
                        .monospacedDigit()
                        .opacity(0.7)
                }
            }
            .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.30))
        }
    }
}

struct ChevronBar: View {
    let currentPhase: Phase
    let state: FRCMatchAttributes.ContentState

    var body: some View {
        GeometryReader { geo in
            let D: CGFloat = 16
            let n = CGFloat(ChevronMilestone.allCases.count)
            let visibleWidth = (geo.size.width - D) / n
            let segmentWidth = visibleWidth + D

            ZStack(alignment: .topLeading) {
                ForEach(ChevronMilestone.allCases) { milestone in
                    ChevronSegment(
                        milestone: milestone,
                        currentPhase: currentPhase,
                        matchStartDeadline: state.matchStartDeadline,
                        matchEndDeadline: state.matchEndDeadline,
                        arrowDepth: D,
                        deadline: milestone.deadline(state: state)
                    )
                    .frame(width: segmentWidth, height: geo.size.height)
                    .clipShape(ChevronShape(
                        arrowDepth: D,
                        isLast: milestone == .matchEnd
                    ))
                    .offset(x: CGFloat(milestone.rawValue) * visibleWidth)
                    .zIndex(Double(ChevronMilestone.allCases.count - milestone.rawValue))
                }
            }
        }
        .frame(height: 64)
    }
}
