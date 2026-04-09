import SwiftUI
import WidgetKit
import TBAKit

// MARK: - Circular Complication (70x70pt)

struct CircularComplicationView: View {
    let entry: PhaseComplicationEntry

    private var isOnField: Bool { entry.phase == .onField }

    var body: some View {
        if let phase = entry.phase, let deadline = entry.phaseDeadline {
            ZStack {
                Circle()
                    .fill(isOnField ? Color(hex: "#0F2118") : Color(hex: "#1C1C1E"))
                    .overlay(
                        Circle()
                            .strokeBorder(
                                isOnField ? Color(hex: "#30D158") : .clear,
                                lineWidth: 1
                            )
                    )

                VStack(spacing: 2) {
                    Text(phase.label)
                        .font(.system(size: 9, weight: .semibold, design: .monospaced))
                        .tracking(0.6)
                        .foregroundStyle(phase.color)

                    Text(deadline, style: .timer)
                        .font(.system(size: 22, weight: .bold, design: .monospaced))
                        .kerning(-1)
                        .foregroundStyle(.white)
                        .monospacedDigit()

                    HStack(spacing: 4) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(Color(hex: "#3A3A3C"))
                                    .frame(height: 2.5)
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(phase.color)
                                    .frame(width: geo.size.width * entry.phaseProgress, height: 2.5)
                            }
                        }
                        .frame(width: 28, height: 2.5)

                        if let alliance = entry.alliance {
                            Circle()
                                .fill(alliance.dotColor)
                                .frame(width: 5, height: 5)
                        }
                    }
                }
            }
        } else {
            ZStack {
                Circle().fill(Color(hex: "#1C1C1E"))
                Text(String(entry.teamNumber ?? 0))
                    .font(.system(size: 14, weight: .bold, design: .monospaced))
                    .foregroundStyle(.white)
            }
        }
    }
}

// MARK: - Rectangular Complication (160x68pt)

struct RectangularComplicationView: View {
    let entry: PhaseComplicationEntry

    private var isOnField: Bool { entry.phase == .onField }

    var body: some View {
        if let phase = entry.phase, let deadline = entry.phaseDeadline {
            HStack(spacing: 0) {
                VStack(spacing: 4) {
                    RoundedRectangle(cornerRadius: 5)
                        .fill(phase.color)
                        .frame(width: 20, height: 20)
                    Text("#\(String(entry.teamNumber ?? 0))")
                        .font(.system(size: 9.5, design: .monospaced))
                        .foregroundStyle(phase.color.opacity(0.50))
                }
                .frame(width: 46)

                Rectangle()
                    .fill(.white.opacity(0.15))
                    .frame(width: 0.5, height: 42)

                VStack(alignment: .leading, spacing: 2) {
                    Text(phase.label)
                        .font(.system(size: 9, weight: .semibold, design: .monospaced))
                        .tracking(0.6)
                        .foregroundStyle(phase.color)

                    Text(deadline, style: .timer)
                        .font(.system(size: 26, weight: .bold, design: .monospaced))
                        .kerning(-1)
                        .foregroundStyle(.white)
                        .monospacedDigit()

                    HStack(spacing: 4) {
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(Color(hex: "#3A3A3C"))
                                    .frame(height: 2.5)
                                RoundedRectangle(cornerRadius: 1.25)
                                    .fill(phase.color)
                                    .frame(width: geo.size.width * entry.phaseProgress, height: 2.5)
                            }
                        }
                        .frame(width: 48, height: 2.5)

                        if let alliance = entry.alliance {
                            Circle()
                                .fill(alliance.dotColor)
                                .frame(width: 5, height: 5)
                        }
                    }
                }
                .padding(.leading, 8)

                Spacer()
            }
            .padding(8)
            .background(
                RoundedRectangle(cornerRadius: 12)
                    .fill(isOnField ? Color(hex: "#0F2118") : Color(hex: "#1C1C1E"))
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .strokeBorder(
                                isOnField
                                    ? Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.40)
                                    : .clear,
                                lineWidth: 0.5
                            )
                    )
            )
        } else {
            VStack(alignment: .leading) {
                Text("Team \(String(entry.teamNumber ?? 0))")
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .foregroundStyle(.white)
                Text("No match")
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(.secondary)
            }
        }
    }
}
