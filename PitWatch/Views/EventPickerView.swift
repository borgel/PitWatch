import SwiftUI
import TBAKit

struct EventPickerView: View {
    let events: [Event]
    @Binding var selectedEventKey: String?
    let autoDetectedEventKey: String?

    var body: some View {
        List(events) { event in
            Button {
                if event.key == autoDetectedEventKey && selectedEventKey == nil {
                    return
                }
                selectedEventKey = event.key == autoDetectedEventKey ? nil : event.key
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
        .navigationTitle("Select Event")
    }

    private func formatLocation(_ event: Event) -> String? {
        let parts = [event.city, event.stateProv, event.country].compactMap { $0 }.filter { !$0.isEmpty }
        return parts.isEmpty ? nil : parts.joined(separator: ", ")
    }
}
