import SwiftUI
import WidgetKit

@main
struct PitWatchWidgetBundle: WidgetBundle {
    var body: some Widget {
        NextMatchWidget()
        MatchLiveActivityWidget()
    }
}

struct NextMatchWidget: Widget {
    let kind = "NextMatchWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: MatchTimelineProvider()) { entry in
            WidgetEntryView(entry: entry)
        }
        .configurationDisplayName("Next Match")
        .description("Track your team's next FRC match.")
        .supportedFamilies([
            .systemSmall, .systemMedium, .systemLarge,
            .accessoryCircular, .accessoryRectangular
        ])
    }
}

struct WidgetEntryView: View {
    @Environment(\.widgetFamily) var family
    let entry: MatchWidgetEntry

    var body: some View {
        switch family {
        case .systemSmall: SmallWidgetView(entry: entry)
        case .systemMedium: MediumWidgetView(entry: entry)
        case .systemLarge: LargeWidgetView(entry: entry)
        case .accessoryCircular: CircularLockScreenView(entry: entry)
        case .accessoryRectangular: RectangularLockScreenView(entry: entry)
        default: SmallWidgetView(entry: entry)
        }
    }
}
