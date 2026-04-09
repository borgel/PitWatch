import Foundation

/// Correlates Nexus match data to TBA matches using label normalization and team fallback.
public enum NexusMatchMerge {
    /// Find the Nexus match corresponding to a TBA match.
    /// Returns nil if nexusEvent is nil or no match can be correlated.
    public static func nexusInfo(for match: Match, in nexusEvent: NexusEvent?) -> NexusMatch? {
        guard let nexusEvent else { return nil }

        // First pass: match by normalized label
        let tbaCanonical = canonicalLabel(
            compLevel: match.compLevel,
            setNumber: match.setNumber,
            matchNumber: match.matchNumber
        )
        if let found = nexusEvent.matches.first(where: { parseNexusLabel($0.label) == tbaCanonical }) {
            return found
        }

        // Second pass: match by team composition
        let tbaRed = Set(match.alliances["red"]?.teamKeys.map(stripFRC) ?? [])
        let tbaBlue = Set(match.alliances["blue"]?.teamKeys.map(stripFRC) ?? [])
        guard !tbaRed.isEmpty else { return nil }

        return nexusEvent.matches.first { nexus in
            let nexusRed = Set(nexus.redTeams)
            let nexusBlue = Set(nexus.blueTeams)
            return (tbaRed == nexusRed && tbaBlue == nexusBlue) ||
                   (tbaRed == nexusBlue && tbaBlue == nexusRed)
        }
    }

    // MARK: - Private

    /// Canonical form: "qm-1-32", "qf-2-1", "f-1-1"
    private static func canonicalLabel(compLevel: String, setNumber: Int, matchNumber: Int) -> String {
        "\(compLevel)-\(setNumber)-\(matchNumber)"
    }

    /// Parse a Nexus label like "Qualification 32" or "Quarterfinal 2-1" into canonical form.
    private static func parseNexusLabel(_ label: String) -> String? {
        let parts = label.split(separator: " ", maxSplits: 1)
        guard parts.count == 2 else { return nil }

        let levelStr = parts[0].lowercased()
        let numberStr = String(parts[1])

        let compLevel: String
        switch levelStr {
        case "practice":
            compLevel = "p"
        case "qualification":
            compLevel = "qm"
        case "eighthfinal":
            compLevel = "ef"
        case "quarterfinal":
            compLevel = "qf"
        case "semifinal":
            compLevel = "sf"
        case "final":
            compLevel = "f"
        default:
            compLevel = levelStr
        }

        // Handle "2-1" (set-match) vs "32" (just match number)
        if numberStr.contains("-") {
            let nums = numberStr.split(separator: "-")
            guard nums.count == 2 else { return nil }
            return "\(compLevel)-\(nums[0])-\(nums[1])"
        } else {
            return "\(compLevel)-1-\(numberStr)"
        }
    }

    private static func stripFRC(_ key: String) -> String {
        key.replacingOccurrences(of: "frc", with: "")
    }
}
