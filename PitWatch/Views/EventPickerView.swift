import SwiftUI
import TBAKit

struct EventPickerView: View {
    let config: UserConfig
    let store: TBADataStore
    @Binding var selectedEventKey: String?
    let autoDetectedEventKey: String?

    @State private var events: [Event] = []
    @State private var isLoading = false

    var body: some View {
        Group {
            if isLoading && events.isEmpty {
                ProgressView("Loading events...")
            } else if events.isEmpty {
                ContentUnavailableView(
                    "No Events",
                    systemImage: "calendar.badge.exclamationmark",
                    description: Text("No events found for this team.")
                )
            } else {
                List(events) { event in
                    Button {
                        if event.key == autoDetectedEventKey && selectedEventKey == nil {
                            return
                        }
                        selectedEventKey = event.key == autoDetectedEventKey ? nil : event.key
                        store.saveConfig(config)
                    } label: {
                        HStack {
                            VStack(alignment: .leading, spacing: 4) {
                                Text(event.name)
                                    .font(.headline)
                                Text("\(event.startDate) – \(event.endDate)")
                                    .font(.subheadline)
                                    .foregroundStyle(.secondary)
                                if let location = formatLocation(event) {
                                    Text(location)
                                        .font(.caption)
                                        .foregroundStyle(.tertiary)
                                }
                            }

                            Spacer()

                            let isSelected = selectedEventKey == event.key ||
                                (selectedEventKey == nil && event.key == autoDetectedEventKey)

                            if isSelected {
                                Image(systemName: "checkmark.circle.fill")
                                    .foregroundStyle(.blue)
                            }

                            if event.key == autoDetectedEventKey && selectedEventKey == nil {
                                Text("AUTO")
                                    .font(.caption2)
                                    .fontWeight(.bold)
                                    .foregroundStyle(.blue)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(.blue.opacity(0.1), in: Capsule())
                            }
                        }
                    }
                    .tint(.primary)
                }
            }
        }
        .navigationTitle("Select Event")
        .task {
            await loadEvents()
        }
    }

    private func loadEvents() async {
        guard let apiKey = config.apiKey, let teamNumber = config.teamNumber else { return }
        isLoading = true
        let client = TBAClient(apiKey: apiKey)
        let year = Calendar.current.component(.year, from: .now)
        do {
            let result = try await client.fetch(
                [Event].self,
                path: Endpoints.teamEvents(number: teamNumber, year: year)
            )
            if case .data(let fetchedEvents, _) = result {
                events = fetchedEvents.sorted {
                    ($0.startDateParsed ?? .distantPast) > ($1.startDateParsed ?? .distantPast)
                }
            }
        } catch {
            // Events stay empty, ContentUnavailableView shows
        }
        isLoading = false
    }

    private func formatLocation(_ event: Event) -> String? {
        let parts = [event.city, event.stateProv, event.country].compactMap { $0 }.filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }
}
