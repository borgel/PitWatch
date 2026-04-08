# FRC Nexus API Integration Design

**Date:** 2026-04-08
**Status:** Approved

## Overview

Integrate the FRC Nexus API as a priority data source for match queue times and status, layered on top of the existing TBA data. When Nexus is available, its richer timing data (queue, on-deck, on-field, start) and live status strings replace TBA's predicted/scheduled times. When unavailable, TBA times are used as fallback.

## Decisions

- **Approach A (Layered Merge):** Nexus and TBA are complementary, not competing. Nexus provides timing/status; TBA provides scores, rankings, OPRs, and the match list. Each source is its own layer of non-overlapping data. This scales to future sources like Statbotics.
- **User provides their own Nexus API key** in settings, same pattern as TBA.
- **Nexus is priority** for all timing data when available.
- **Show all Nexus times** (queue, on-deck, on-field, start) with the next upcoming time highlighted and a countdown.
- **Show match status badges** ("NOW QUEUING", "ON DECK", "ON FIELD") plus countdown to next phase.
- **Show event-level "Now queuing" banner** at top of match list and in widgets.
- **Fallback indicator** shown only when degraded (Nexus unavailable but key is configured).
- **Silent degradation** — Nexus failures never block TBA data from displaying.
- **Attribution** to frc.nexus in settings/about screen only.

## Data Models

### New Models (in `TBAKit/Sources/TBAKit/Models/`)

**`NexusEvent.swift`** — top-level response from `GET /event/{eventKey}`:
- `dataAsOfTime: Int64` — Unix milliseconds timestamp
- `nowQueuing: String?` — e.g., "Qualification 24"
- `matches: [NexusMatch]`

**`NexusMatch.swift`** — per-match Nexus data:
- `label: String` — e.g., "Qualification 32"
- `status: String?` — "Now queuing", "On deck", "On field"
- `redTeams: [String]` — team numbers (bare, not "frcXXXX")
- `blueTeams: [String]`
- `times: NexusMatchTimes`
- `replayOf: String?`

**`NexusMatchTimes`** (nested struct):
- `estimatedQueueTime: Int64?` — Unix ms
- `estimatedOnDeckTime: Int64?` — Unix ms
- `estimatedOnFieldTime: Int64?` — Unix ms
- `estimatedStartTime: Int64?` — Unix ms
- `actualQueueTime: Int64?` — Unix ms

All types are `Codable & Sendable & Hashable`.

### Cache Changes

`EventCache` gains:
- `nexusEvent: NexusEvent?` — nil when Nexus unavailable

`RefreshState` gains:
- `nexusLastRefreshDate: Date?`
- `nexusLastError: String?`

## Networking Layer

### New: `NexusClient.swift` (in `TBAKit/Sources/TBAKit/API/`)

- Base URL: `https://frc.nexus/api/v1`
- Auth header: `Nexus-Api-Key`
- Method: `fetchEventStatus(eventKey: String) async throws -> NexusEvent`
- No `If-Modified-Since` caching — Nexus data is live/dynamic
- Errors are non-fatal: failures return `nil` to caller

### Unchanged: `TBAClient`

No modifications to the existing TBA networking code.

### Event Key Format

TBA event keys (e.g., `2024miket`) are used directly as Nexus event keys — same FIRST event key format.

## Refresh Orchestration

### Changes to `BackgroundRefresh.performRefresh()`

Fetch TBA and Nexus in parallel:
```swift
async let tbaResult = fetchTBAData(...)
async let nexusResult = fetchNexusData(...)  // returns NexusEvent?
```

Nexus fetch is gated on `config.nexusApiKey` being non-nil. If no key, skip entirely.

Single cache write with both TBA data and `nexusEvent`. Single `WidgetCenter.reloadAllTimelines()` call.

### Adaptive Refresh

When Nexus data is available, refresh intervals tighten further — Nexus queue times are more precise than TBA predicted times, so refreshes can be scheduled closer to actual phase transitions (queue time, on-deck time, etc.).

## Match Matching Strategy

### The Problem

Nexus matches must be correlated with TBA matches to enrich the correct match card.

### Solution: Label Normalization + Team Fallback

A `NexusMatchMerge.swift` utility in `TBAKit/Sources/TBAKit/Store/`:

1. Parse comp level + numbers from both label formats (TBA: "Qual 32", Nexus: "Qualification 32")
2. Compare by `(compLevel, setNumber, matchNumber)` tuple
3. Fall back to team-list matching if labels don't align (compare sorted team numbers, stripping "frc" prefix)

### Lookup

A helper function `nexusInfo(for match: Match, in nexusEvent: NexusEvent?) -> NexusMatch?` performs the correlation at render time. No pre-merged dictionary — keeps two sources cleanly separated. Fast enough for ~100-150 matches per event.

## UI Changes

### Settings Screen

- New `nexusApiKey: String?` field in `UserConfig`
- New text field in settings for Nexus API key
- Attribution link to frc.nexus below the Nexus key field

### Event-Level "Now Queuing" Banner

- Top of match list in app when `nexusEvent?.nowQueuing` is non-nil
- In medium/large widgets when space allows
- Text: "Now queuing: Qualification 24"
- Hidden when Nexus data unavailable

### Match Cards (App + Widgets)

**When Nexus data available:**
- **Status badge** — colored pill: "NOW QUEUING", "ON DECK", "ON FIELD"
- **Four time rows** — Queue, On Deck, On Field, Start
  - Next upcoming time: bold/accent color + relative countdown ("in 12 min")
  - Past times: dimmed
- **Countdown** next to highlighted time

**When Nexus unavailable:**
- Current behavior unchanged (TBA predicted/scheduled time)
- "Nexus unavailable" note if key is configured but data is nil

### Live Activity / Dynamic Island

`MatchActivityAttributes.ContentState` gains:
- `nexusStatus: String?`
- `nexusQueueTime: Date?`
- `nexusOnDeckTime: Date?`
- `nexusOnFieldTime: Date?`
- `nexusStartTime: Date?`

- Compact view: status badge + countdown to next phase
- Expanded view: all Nexus times with highlighting
- State transitions: driven by Nexus status string when available (instead of time-based heuristics)

### Widget Sizes

- **Small:** Status badge + countdown to next phase (or TBA time fallback)
- **Medium:** Next match card with Nexus times + last result card
- **Large:** Now-queuing banner + upcoming/past matches with Nexus times
- **Lock screen:** Status badge + countdown only

## Fallback Behavior

### Time Fallback Hierarchy

1. Nexus times (if `nexusEvent` non-nil and match correlated)
2. TBA `predictedTime`
3. TBA `time` (scheduled)

### Edge Cases

- **Event not on Nexus:** `fetchEventStatus` fails → `nexusEvent = nil`, fallback indicator shown
- **Event hasn't started:** Nexus returns matches with no times → show TBA scheduled times, no badges
- **Mid-event Nexus goes down:** On refresh failure, set `nexusEvent = nil` so UI falls back to TBA rather than showing stale queue times
- **Label mismatch:** Uncorrelated Nexus matches silently skipped
- **Team number format:** Matching utility strips "frc" prefix when comparing team lists
- **Replays:** Nexus `replayOf` field — shown as additional card with its own times. TBA may not have a separate entry

## What Is NOT Changing

- Rankings, OPRs, scores — TBA-only
- Watch data transfer — `nexusEvent` field serializes via Codable automatically
- Change detection — `ChangeDetector` works on TBA data; Nexus changes trigger reload via existing `WidgetCenter.reloadAllTimelines()` path

## Future Considerations

- Statbotics integration would follow the same layered pattern (non-overlapping data)
- Nexus webhooks could replace polling for even more responsive updates (out of scope)
