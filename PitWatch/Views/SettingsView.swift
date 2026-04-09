import SwiftUI
import TBAKit
import WidgetKit
#if canImport(ActivityKit) && os(iOS)
import ActivityKit
#endif

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

                #if DEBUG
                Button("Preview Live Activity (Demo)") {
                    startDemoLiveActivity()
                }
                .foregroundStyle(.orange)
                #endif
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
                HStack {
                    Text("Team")
                    Spacer()
                    TextField("Number", text: Binding(
                        get: { config.teamNumber.map(String.init) ?? "" },
                        set: { config.teamNumber = Int($0) }
                    ))
                    .keyboardType(.numberPad)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 80)
                }

                LabeledContent("TBA API Key") {
                    Text(maskedKey(config.apiKey)).foregroundStyle(.secondary)
                }

                LabeledContent("Nexus API Key") {
                    Text(maskedKey(config.nexusApiKey)).foregroundStyle(.secondary)
                }

                Button("Clear All Data", role: .destructive) {
                    config = UserConfig()
                    store.saveConfig(config)
                }
            }

            Section {
                TextField("API Key", text: Binding(
                    get: { config.nexusApiKey ?? "" },
                    set: { config.nexusApiKey = $0.isEmpty ? nil : $0 }
                ))
                .textContentType(.password)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

                if config.isNexusConfigured {
                    if let nexusDate = store.loadRefreshState().nexusLastRefreshDate {
                        LabeledContent("Last Refresh", value: nexusDate.formatted(.relative(presentation: .named)))
                    }
                    if let nexusError = store.loadRefreshState().nexusLastError {
                        Label(nexusError, systemImage: "info.circle")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }

                Link("Get a Nexus API key", destination: URL(string: "https://frc.nexus/api")!)
                    .font(.caption)

                Text("Data provided by frc.nexus")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            } header: {
                Text("FRC Nexus")
            } footer: {
                Text("When available, Nexus provides real-time match queue times and status.")
            }

            #if DEBUG
            nexusDemoSection
            #endif
        }
        .navigationTitle("Settings")
        .onChange(of: config) { _, newConfig in
            store.saveConfig(newConfig)
        }
    }

    private func maskedKey(_ key: String?) -> String {
        guard let key, !key.isEmpty else { return "Not set" }
        guard key.count > 8 else { return String(repeating: "•", count: key.count) }
        return String(key.prefix(4)) + "••••" + String(key.suffix(4))
    }

    #if DEBUG
    @State private var nexusDemoEventKey = ""
    @State private var nexusDemoLoading = false
    @State private var nexusDemoResult: String?

    private var nexusDemoSection: some View {
        Section {
            TextField("Nexus Event Key", text: $nexusDemoEventKey)
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)

            Button {
                Task { await loadNexusDemo() }
            } label: {
                if nexusDemoLoading {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    Text("Load Nexus Demo").frame(maxWidth: .infinity)
                }
            }
            .disabled(nexusDemoEventKey.isEmpty || !config.isNexusConfigured || nexusDemoLoading)
            .foregroundStyle(.orange)

            if let result = nexusDemoResult {
                Text(result)
                    .font(.caption)
                    .foregroundStyle(result.starts(with: "Error") ? .red : .green)
            }
        } header: {
            Text("Nexus Demo Mode")
        } footer: {
            Text("Fetches live Nexus data and generates mock TBA matches so you can test the full UI.")
        }
    }

    private func loadNexusDemo() async {
        guard let nexusKey = config.nexusApiKey, !nexusKey.isEmpty else {
            nexusDemoResult = "Error: Set a Nexus API key first"
            return
        }

        nexusDemoLoading = true
        nexusDemoResult = nil

        let client = NexusClient(apiKey: nexusKey)
        guard let nexusEvent = await client.fetchEventStatus(eventKey: nexusDemoEventKey) else {
            nexusDemoResult = "Error: Could not fetch event '\(nexusDemoEventKey)' from Nexus"
            nexusDemoLoading = false
            return
        }

        // Generate mock TBA data from Nexus matches
        let teamNumber = config.teamNumber ?? 1234
        let teamKey = "frc\(teamNumber)"
        let eventKey = nexusDemoEventKey

        let mockEvent = Event.mock(key: eventKey, name: "Nexus Demo Event")
        var mockMatches: [Match] = []

        for (index, nexusMatch) in nexusEvent.matches.enumerated() {
            let parsed = parseNexusLabelForMock(nexusMatch.label)
            let compLevel = parsed.compLevel
            let setNumber = parsed.setNumber
            let matchNumber = parsed.matchNumber

            // Ensure tracked team is on an alliance for every few matches
            var redTeams = nexusMatch.redTeams.map { "frc\($0)" }
            var blueTeams = nexusMatch.blueTeams.map { "frc\($0)" }

            // Put tracked team on red for every 3rd match (so they appear in the schedule)
            if index % 3 == 0 {
                if !redTeams.contains(teamKey) && !blueTeams.contains(teamKey) {
                    if redTeams.count > 0 { redTeams[0] = teamKey }
                    else if blueTeams.count > 0 { blueTeams[0] = teamKey }
                }
            }

            let startTime: Int64? = nexusMatch.times.estimatedStartTime.map { $0 / 1000 }

            let match = Match.mock(
                key: "\(eventKey)_\(compLevel)\(matchNumber)",
                compLevel: compLevel,
                setNumber: setNumber,
                matchNumber: matchNumber,
                eventKey: eventKey,
                time: startTime,
                redTeamKeys: redTeams,
                blueTeamKeys: blueTeams
            )
            mockMatches.append(match)
        }

        // Write to cache
        var cache = store.loadEventCache()
        cache.event = mockEvent
        cache.matches = mockMatches
        cache.nexusEvent = nexusEvent
        cache.rankings = nil
        cache.oprs = nil
        store.saveEventCache(cache)

        // Also update config to point at this event
        config.eventKeyOverride = eventKey
        store.saveConfig(config)

        WidgetCenter.shared.reloadAllTimelines()

        nexusDemoResult = "Loaded \(nexusEvent.matches.count) Nexus matches, \(mockMatches.filter { m in m.alliances.values.contains { $0.teamKeys.contains(teamKey) } }.count) are your team's"
        nexusDemoLoading = false
    }

    private func parseNexusLabelForMock(_ label: String) -> (compLevel: String, setNumber: Int, matchNumber: Int) {
        let parts = label.split(separator: " ", maxSplits: 1)
        guard parts.count == 2 else { return ("qm", 1, 1) }

        let levelStr = parts[0].lowercased()
        let numberStr = String(parts[1])

        let compLevel: String
        switch levelStr {
        case "practice": compLevel = "p"
        case "qualification": compLevel = "qm"
        case "eighthfinal": compLevel = "ef"
        case "quarterfinal": compLevel = "qf"
        case "semifinal": compLevel = "sf"
        case "final": compLevel = "f"
        default: compLevel = levelStr
        }

        if numberStr.contains("-") {
            let nums = numberStr.split(separator: "-").compactMap { Int($0) }
            if nums.count == 2 {
                return (compLevel, nums[0], nums[1])
            }
        }
        return (compLevel, 1, Int(numberStr) ?? 1)
    }

    private func startDemoLiveActivity() {
        #if canImport(ActivityKit) && os(iOS)
        let teamNum = config.teamNumber ?? 1234

        let attributes = FRCMatchAttributes(
            teamNumber: teamNum,
            matchNumber: 32,
            matchLabel: "Q32",
            alliance: .red
        )

        let state = FRCMatchAttributes.ContentState(
            currentPhase: .queueing,
            phaseStartDate: .now.addingTimeInterval(-120),
            phaseDeadline: .now.addingTimeInterval(300),
            currentMatchOnField: 29,
            lastUpdated: .now.addingTimeInterval(-90),
            queueDeadline: .now.addingTimeInterval(-120),
            onDeckDeadline: .now.addingTimeInterval(300),
            onFieldDeadline: .now.addingTimeInterval(600),
            matchEndDeadline: .now.addingTimeInterval(900)
        )

        let content = ActivityContent(state: state, staleDate: .now.addingTimeInterval(3600))
        do {
            let activity = try Activity<FRCMatchAttributes>.request(
                attributes: attributes,
                content: content
            )
            print("✅ Live Activity started: \(activity.id)")
        } catch {
            print("❌ Live Activity failed: \(error)")
        }
        #endif
    }
    #endif
}
