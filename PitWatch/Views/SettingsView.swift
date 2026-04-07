import SwiftUI
import TBAKit
import WidgetKit

struct SettingsView: View {
    @Binding var config: UserConfig
    let store: TBADataStore
    let onForceRefresh: () async -> Void
    let onStartLiveActivity: () -> Void

    @State private var refreshState: RefreshState

    init(config: Binding<UserConfig>, store: TBADataStore,
         onForceRefresh: @escaping () async -> Void,
         onStartLiveActivity: @escaping () -> Void) {
        self._config = config
        self.store = store
        self.onForceRefresh = onForceRefresh
        self.onStartLiveActivity = onStartLiveActivity
        self._refreshState = State(initialValue: store.loadRefreshState())
    }

    var body: some View {
        Form {
            Section("Time Display") {
                Picker("Time Source", selection: $config.useScheduledTime) {
                    Text("Predicted").tag(false)
                    Text("Scheduled").tag(true)
                }

                Picker("Queue Offset", selection: $config.queueOffsetMinutes) {
                    Text("Off").tag(0)
                    ForEach(Array(stride(from: 5, through: 60, by: 5)), id: \.self) { minutes in
                        Text("\(minutes) min").tag(minutes)
                    }
                }

                if config.queueOffsetMinutes > 0 {
                    Text("Countdown will show time to queue (\(config.queueOffsetMinutes) min before match)")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }

            Section("Live Activity") {
                Picker("Auto-Start Mode", selection: $config.liveActivityMode) {
                    Text("Near Match (2 hr)").tag(LiveActivityMode.nearMatch)
                    Text("All Day").tag(LiveActivityMode.allDay)
                }

                Button("Start Live Activity Now") {
                    onStartLiveActivity()
                }
            }

            Section("Data") {
                Button("Force Refresh") {
                    Task { await onForceRefresh() }
                }

                if let date = refreshState.lastRefreshDate {
                    LabeledContent("Last Refresh", value: date.formatted(.relative(presentation: .named)))
                }

                if let error = refreshState.lastError {
                    Label(error, systemImage: "exclamationmark.triangle")
                        .font(.caption)
                        .foregroundStyle(.red)
                }
            }

            Section("Account") {
                LabeledContent("Team", value: "\(config.teamNumber ?? 0)")
                LabeledContent("API Key") {
                    Text(maskedKey).foregroundStyle(.secondary)
                }
                Button("Clear All Data", role: .destructive) {
                    config = UserConfig()
                    store.saveConfig(config)
                }
            }
        }
        .navigationTitle("Settings")
        .onChange(of: config) { _, newConfig in
            store.saveConfig(newConfig)
        }
    }

    private var maskedKey: String {
        guard let key = config.apiKey, key.count > 8 else { return "Not set" }
        return String(key.prefix(4)) + "••••" + String(key.suffix(4))
    }
}
