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

private enum SegmentState {
    case completed, active, nextPending, farPending
}

private func segmentState(phase: Phase, currentPhase: Phase) -> SegmentState {
    if phase.rawValue < currentPhase.rawValue { return .completed }
    if phase == currentPhase { return .active }
    if phase.rawValue == currentPhase.rawValue + 1 { return .nextPending }
    return .farPending
}

struct ChevronSegment: View {
    let phase: Phase
    let currentPhase: Phase
    let deadline: Date?
    let arrowDepth: CGFloat

    private var state: SegmentState { segmentState(phase: phase, currentPhase: currentPhase) }

    private var backgroundColor: Color {
        switch state {
        case .completed:   return Color(hex: "#1A3D1F")
        case .active:      return phase.color
        case .nextPending: return Color(hex: "#2A2A2A")
        case .farPending:  return Color(hex: "#222222")
        }
    }

    var body: some View {
        ZStack {
            backgroundColor

            let isFirst = phase == .preQueue
            let isLast = phase == .onField

            Group {
                switch state {
                case .completed:
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 4) {
                            Image(systemName: "checkmark.circle")
                                .font(.system(size: 10))
                            Text(phase.label)
                                .font(.system(size: 10, weight: .medium, design: .monospaced))
                                .tracking(0.5)
                        }
                    }
                    .foregroundStyle(Color(hex: "#30D158").opacity(0.85))

                case .active:
                    VStack(alignment: .leading, spacing: 1) {
                        Text(phase.label)
                            .font(.system(size: 11, weight: .bold, design: .monospaced))
                            .tracking(0.5)
                            .foregroundStyle(.black)
                        if let deadline {
                            Text(deadline, style: .timer)
                                .font(.system(size: 9, weight: .medium, design: .monospaced))
                                .foregroundStyle(.black.opacity(0.65))
                                .monospacedDigit()
                        }
                    }

                case .nextPending:
                    VStack(alignment: .leading, spacing: 1) {
                        Text(phase.label)
                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                            .tracking(0.5)
                        if let deadline {
                            Text(deadline, style: .timer)
                                .font(.system(size: 9, weight: .medium, design: .monospaced))
                                .monospacedDigit()
                                .opacity(0.7)
                        }
                    }
                    .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.45))

                case .farPending:
                    VStack(alignment: .leading, spacing: 1) {
                        Text(phase.label)
                            .font(.system(size: 10, weight: .medium, design: .monospaced))
                            .tracking(0.5)
                        if let deadline {
                            Text(deadline, style: .timer)
                                .font(.system(size: 9, weight: .medium, design: .monospaced))
                                .monospacedDigit()
                                .opacity(0.7)
                        }
                    }
                    .foregroundStyle(Color(red: 235/255, green: 235/255, blue: 245/255).opacity(0.30))
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
            .padding(.leading, (isFirst ? 10 : arrowDepth) + 6)
            .padding(.trailing, (isLast ? 0 : arrowDepth) + 6)
        }
    }
}

struct ChevronBar: View {
    let currentPhase: Phase
    let state: FRCMatchAttributes.ContentState

    var body: some View {
        GeometryReader { geo in
            let D: CGFloat = 16
            let n = CGFloat(Phase.allCases.count)
            let visibleWidth = (geo.size.width - D) / n
            let segmentWidth = visibleWidth + D

            ZStack(alignment: .topLeading) {
                ForEach(Phase.allCases) { phase in
                    ChevronSegment(
                        phase: phase,
                        currentPhase: currentPhase,
                        deadline: state.deadline(for: phase),
                        arrowDepth: D
                    )
                    .frame(width: segmentWidth, height: geo.size.height)
                    .clipShape(ChevronShape(
                        arrowDepth: D,
                        isLast: phase == .onField
                    ))
                    .offset(x: CGFloat(phase.rawValue) * visibleWidth)
                    .zIndex(Double(Phase.allCases.count - phase.rawValue))
                }
            }
        }
        .frame(height: 48)
    }
}
