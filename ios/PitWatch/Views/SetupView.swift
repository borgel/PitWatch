import SwiftUI
import TBAKit

struct SetupView: View {
    @Binding var config: UserConfig
    var onComplete: () -> Void

    @State private var apiKeyText = ""
    @State private var nexusApiKeyText = ""
    @State private var teamNumberText = ""
    @State private var isValidating = false
    @State private var validationError: String?
    @State private var validatedTeamName: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("PitWatch needs a TBA API key to fetch match data. You can get one from your TBA account page.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    TextField("API Key", text: $apiKeyText)
                        .textContentType(.password)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Link("Get an API Key \u{2192}",
                         destination: URL(string: "https://www.thebluealliance.com/account")!)
                } header: {
                    Text("TBA API Key")
                }

                Section {
                    Text("Optional. Provides real-time match queue times at supported events.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    TextField("API Key", text: $nexusApiKeyText)
                        .textContentType(.password)
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    Link("Get an API Key \u{2192}",
                         destination: URL(string: "https://frc.nexus/api")!)
                } header: {
                    Text("FRC Nexus API Key (Optional)")
                }

                Section {
                    TextField("Team Number", text: $teamNumberText)
                        .keyboardType(.numberPad)
                    if let name = validatedTeamName {
                        Label(name, systemImage: "checkmark.circle.fill")
                            .foregroundStyle(.green)
                    }
                    if let error = validationError {
                        Label(error, systemImage: "exclamationmark.triangle.fill")
                            .foregroundStyle(.red)
                    }
                } header: {
                    Text("Your Team")
                }

                Section {
                    Button {
                        Task { await validate() }
                    } label: {
                        if isValidating {
                            ProgressView().frame(maxWidth: .infinity)
                        } else {
                            Text("Continue").frame(maxWidth: .infinity)
                        }
                    }
                    .disabled(!canValidate || isValidating)
                }
            }
            .navigationTitle("Welcome to PitWatch")
        }
    }

    private var canValidate: Bool {
        !apiKeyText.trimmingCharacters(in: .whitespaces).isEmpty && Int(teamNumberText) != nil
    }

    private func validate() async {
        guard let teamNumber = Int(teamNumberText) else { return }
        isValidating = true
        validationError = nil
        validatedTeamName = nil

        let client = TBAClient(apiKey: apiKeyText.trimmingCharacters(in: .whitespaces))
        do {
            let team = try await client.validateTeam(number: teamNumber)
            validatedTeamName = team.nickname
            config.apiKey = apiKeyText.trimmingCharacters(in: .whitespaces)
            config.teamNumber = teamNumber
            let nexusKey = nexusApiKeyText.trimmingCharacters(in: .whitespaces)
            if !nexusKey.isEmpty {
                config.nexusApiKey = nexusKey
            }
            onComplete()
        } catch {
            validationError = "Could not find team \(teamNumber). Check your API key and team number."
        }
        isValidating = false
    }
}
