import SwiftUI
import TBAKit

struct PitMapView: View {
    let pitMap: PitMap
    let teamNumber: Int

    @State private var zoom: CGFloat = 1.0
    @State private var lastZoom: CGFloat = 1.0
    @State private var offset: CGSize = .zero
    @State private var lastOffset: CGSize = .zero

    private let teamStr: String

    init(pitMap: PitMap, teamNumber: Int) {
        self.pitMap = pitMap
        self.teamNumber = teamNumber
        self.teamStr = String(teamNumber)
    }

    var body: some View {
        GeometryReader { geo in
            let scale = min(
                geo.size.width / pitMap.size.x,
                geo.size.height / pitMap.size.y
            )

            mapContent(scale: scale)
                .frame(
                    width: pitMap.size.x * scale,
                    height: pitMap.size.y * scale
                )
                .scaleEffect(zoom)
                .offset(offset)
                .frame(width: geo.size.width, height: geo.size.height)
                .clipped()
                .gesture(
                    MagnifyGesture()
                        .onChanged { value in
                            zoom = max(1, lastZoom * value.magnification)
                        }
                        .onEnded { value in
                            lastZoom = zoom
                        }
                )
                .simultaneousGesture(
                    DragGesture()
                        .onChanged { value in
                            offset = CGSize(
                                width: lastOffset.width + value.translation.width,
                                height: lastOffset.height + value.translation.height
                            )
                        }
                        .onEnded { _ in
                            lastOffset = offset
                        }
                )
                .onTapGesture(count: 2) {
                    withAnimation(.easeInOut(duration: 0.3)) {
                        zoom = 1.0
                        lastZoom = 1.0
                        offset = .zero
                        lastOffset = .zero
                    }
                }
        }
        .background(Color(.systemBackground))
        .navigationTitle("Pit Map")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func mapContent(scale: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            // Areas (labeled zones)
            if let areas = pitMap.areas {
                ForEach(Array(areas.keys), id: \.self) { key in
                    if let area = areas[key] {
                        Rectangle()
                            .fill(Color(.secondarySystemFill))
                            .frame(
                                width: area.size.x * scale,
                                height: area.size.y * scale
                            )
                            .overlay(
                                Text(area.label)
                                    .font(.system(size: max(8, 12 * scale), weight: .semibold))
                                    .foregroundStyle(.secondary)
                            )
                            .offset(
                                x: area.position.x * scale,
                                y: area.position.y * scale
                            )
                    }
                }
            }

            // Walls
            if let walls = pitMap.walls {
                ForEach(Array(walls.keys), id: \.self) { key in
                    if let wall = walls[key] {
                        Rectangle()
                            .fill(Color(.separator))
                            .frame(
                                width: max(1, wall.size.x * scale),
                                height: max(1, wall.size.y * scale)
                            )
                            .offset(
                                x: wall.position.x * scale,
                                y: wall.position.y * scale
                            )
                    }
                }
            }

            // All pits
            ForEach(Array(pitMap.pits.keys), id: \.self) { key in
                if let pit = pitMap.pits[key] {
                    let isMyTeam = pit.team == teamStr
                    Rectangle()
                        .fill(isMyTeam ? Color(hex: "#FF9500") : Color(.tertiarySystemFill))
                        .frame(
                            width: pit.size.x * scale,
                            height: pit.size.y * scale
                        )
                        .overlay(
                            Text(pit.team ?? "")
                                .font(.system(size: max(8, 16 * scale), weight: isMyTeam ? .bold : .medium, design: .monospaced))
                                .foregroundStyle(isMyTeam ? .black : .primary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.3)
                        )
                        .overlay(
                            Rectangle()
                                .strokeBorder(
                                    isMyTeam ? Color(hex: "#FF9500") : Color(.separator),
                                    lineWidth: isMyTeam ? 2 : 0.5
                                )
                        )
                        .offset(
                            x: pit.position.x * scale,
                            y: pit.position.y * scale
                        )
                        .zIndex(isMyTeam ? 10 : 0)
                }
            }

            // Labels
            if let labels = pitMap.labels {
                ForEach(Array(labels.keys), id: \.self) { key in
                    if let label = labels[key] {
                        Text(label.label)
                            .font(.system(size: max(8, 11 * scale), weight: .semibold))
                            .foregroundStyle(.secondary)
                            .frame(
                                width: label.size.x * scale,
                                height: label.size.y * scale
                            )
                            .offset(
                                x: label.position.x * scale,
                                y: label.position.y * scale
                            )
                    }
                }
            }
        }
    }
}
