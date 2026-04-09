# FRC Match Tracker — UI Redesign Specification

Design spec for the expanded Live Activity, collapsed Dynamic Island,
and watchOS complications. Implements in SwiftUI using ActivityKit (Live
Activities) and WidgetKit (complications).

This spec is the single source of truth for the UI redesign. It
incorporates the original design reference with all modifications agreed
during brainstorming. Where measurements are given (e.g. "20pt"), use
that exact value unless implementation constraints force otherwise.

---

## 1. Design Philosophy

- **Bold and readable at a glance.** These surfaces are read in noisy,
  high-stress competition environments — from across a pit, on a crowded
  stand, or on a wrist.
- **Monospace digits everywhere.** Use a monospace font for all countdowns
  and match numbers. Fixed-width digits prevent layout shift as time ticks
  down (e.g. `1:00` vs `9:59` stay the same width).
- **Color encodes urgency, not decoration.** The phase color system runs
  gray → amber → deep orange → green and is the primary communication
  channel. Don't add color for any other reason.
- **Dark surfaces throughout.** All three surfaces are dark-themed
  regardless of system appearance.
- **Phase-focused.** The Live Activity and complications exist to answer
  one question: "where is my team in the match cycle right now?" Scores,
  OPR, and rankings belong in separate widgets or the main app.

---

## 2. Surfaces Delivered

| Surface                     | Framework    | Status       |
|-----------------------------|-------------|--------------|
| Expanded Live Activity      | ActivityKit | In scope     |
| Collapsed Dynamic Island    | ActivityKit | In scope     |
| watchOS Circular complication | WidgetKit | In scope     |
| watchOS Rectangular complication | WidgetKit | In scope |
| watchOS Corner complication | WidgetKit   | Dropped      |
| Stale/Disconnected states   | —           | Deferred     |
| Scores / OPR / Rankings     | —           | Out of scope (separate widget/app) |

---

## 3. Color System

### 3.1 Phase Colors

Each phase has one canonical color used consistently across all surfaces.

| Phase     | Hex       | Mental model              |
|-----------|-----------|---------------------------|
| Pre-Queue | `#636366` | Gray — far from action    |
| Queueing  | `#FF9500` | Amber — heading in        |
| On Deck   | `#FF6B00` | Deep orange — almost time |
| On Field  | `#30D158` | Green — go                |

The "Completed" treatment for past phases uses `#30D158` at reduced
opacity (see §3.4 and §6.4 segment states).

> **Important:** Green for "On Field" is intentional. It signals *go* and
> matches the FRC community's mental model of a green light meaning active
> play. Do not substitute red or yellow for urgency here.

### 3.2 Alliance Colors

Alliance colors appear in two distinct treatments depending on surface:
a **tinted badge** on the Live Activity (text on a translucent fill),
and a **standalone dot** on the Dynamic Island and complications. These
use different hex values because text-on-tint and a small saturated dot
have different legibility requirements.

| Alliance | Badge text | Badge background           | Standalone dot |
|----------|------------|----------------------------|----------------|
| Blue     | `#4DA6FF`  | `rgba(0, 122, 255, 0.18)`  | `#1E6FFF`      |
| Red      | `#FF6B6B`  | `rgba(255, 59, 48, 0.18)`  | `#FF3B30`      |

### 3.3 Surface Colors

| Token                   | Hex       | Usage                         |
|-------------------------|-----------|-------------------------------|
| Lock screen background  | `#0D0D0D` | Outermost device frame        |
| Card surface (default)  | `#1C1C1E` | Live Activity card background |
| Card surface (On Field) | `#112214` | Green-tinted card for On Field|
| Pending chevron (next)  | `#2A2A2A` | Next-pending phase segment fill|
| Pending chevron (far)   | `#222222` | Further-pending phase segment fill|
| Complication surface    | `#1C1C1E` | Watch complication background |
| Complication (On Field) | `#0F2118` | Green-tinted complication bg  |

### 3.4 Text Colors

| Usage                        | Value                        |
|------------------------------|------------------------------|
| Primary white                | `#FFFFFF`                    |
| Live indicator label         | `rgba(235, 235, 245, 0.45)` |
| Now-context label            | `rgba(235, 235, 245, 0.35)` |
| Now-context value (dim)      | `rgba(255, 255, 255, 0.50)` |
| Pending phase label (next)   | `rgba(235, 235, 245, 0.22)` |
| Pending phase label (far)    | `rgba(235, 235, 245, 0.12)` |
| Completed phase label / icon | `rgba(48, 209, 88, 0.65)`   |
| Active phase text on color   | `#000000`                    |
| Active phase secondary line  | `rgba(0, 0, 0, 0.50)`       |
| Matches-away "NEXT"          | Phase color at 65% opacity   |
| Matches-away "NOW"           | `rgba(48, 209, 88, 0.65)`   |

---

## 4. Typography

All countdown numerals and match numbers use a monospaced font. SwiftUI
default is `.system(..., design: .monospaced)`. Body labels use SF Pro.

| Role                              | Font   | Size   | Weight   | Tracking      |
|-----------------------------------|--------|--------|----------|---------------|
| Live Activity countdown           | mono   | 50pt   | Bold     | −2px kerning  |
| Live Activity team number         | mono   | 14pt   | Bold     | default       |
| Live Activity phase + sublabel    | mono   | 10pt   | Medium   | +0.1em        |
| Live Activity now-context label   | mono   | 9pt    | Regular  | +0.05em       |
| Live Activity now-context value   | mono   | 15pt   | Bold     | default       |
| Live Activity alliance badge text | SF Pro | 10pt   | Medium   | default       |
| Live Activity Live indicator      | SF Pro | 10.5pt | Regular  | default       |
| Chevron active label              | mono   | 11pt   | Bold     | +0.05em       |
| Chevron active secondary line     | mono   | 9pt    | Medium   | default       |
| Chevron pending label             | mono   | 10pt   | Medium   | +0.05em       |
| Dynamic Island countdown          | mono   | 15pt   | Bold     | −0.5px        |
| Dynamic Island phase label        | mono   | 8.5pt  | Semibold | +0.05em       |
| Dynamic Island column label       | mono   | 8pt    | Regular  | +0.05em       |
| Dynamic Island match number       | mono   | 13pt   | Bold     | default       |
| Watch rect countdown              | mono   | 26pt   | Bold     | −1px          |
| Watch circular countdown          | mono   | 22pt   | Bold     | −1px          |
| Watch complication phase label    | mono   | 9pt    | Semibold | +0.07em       |

---

## 5. Data Model

The app tracks one active match per Live Activity. The model is split
between **static attributes** (set when the activity starts) and
**content state** (updated on each push/poll).

```swift
import ActivityKit
import SwiftUI

enum Alliance: String, Codable {
    case blue, red

    var displayName: String {
        switch self {
        case .blue: return "BLUE"
        case .red:  return "RED"
        }
    }

    /// Text color for the alliance badge on the Live Activity.
    var badgeText: Color {
        switch self {
        case .blue: return Color(hex: "#4DA6FF")
        case .red:  return Color(hex: "#FF6B6B")
        }
    }

    /// Background fill for the alliance badge on the Live Activity.
    var badgeBackground: Color {
        switch self {
        case .blue: return Color(red: 0,   green: 122/255, blue: 255/255).opacity(0.18)
        case .red:  return Color(red: 255/255, green: 59/255,  blue: 48/255).opacity(0.18)
        }
    }

    /// Standalone dot color used on Dynamic Island and complications.
    var dotColor: Color {
        switch self {
        case .blue: return Color(hex: "#1E6FFF")
        case .red:  return Color(hex: "#FF3B30")
        }
    }
}

enum Phase: Int, Codable, CaseIterable, Identifiable {
    case preQueue = 0
    case queueing = 1
    case onDeck   = 2
    case onField  = 3

    var id: Int { rawValue }

    /// Canonical short label, used on every surface.
    var label: String {
        switch self {
        case .preQueue: return "PRE"
        case .queueing: return "QUEUE"
        case .onDeck:   return "DECK"
        case .onField:  return "FIELD"
        }
    }

    /// Sublabel shown on the Live Activity hero.
    var sublabel: String {
        switch self {
        case .preQueue: return "UNTIL QUEUEING"
        case .queueing: return "UNTIL ON DECK"
        case .onDeck:   return "UNTIL ON FIELD"
        case .onField:  return "MATCH IN PROGRESS"
        }
    }

    var combinedLabel: String { "\(label) · \(sublabel)" }

    var color: Color {
        switch self {
        case .preQueue: return Color(hex: "#636366")
        case .queueing: return Color(hex: "#FF9500")
        case .onDeck:   return Color(hex: "#FF6B00")
        case .onField:  return Color(hex: "#30D158")
        }
    }
}

struct FRCMatchAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        var currentPhase: Phase
        /// Start of the current phase (used by complication progress bar).
        var phaseStartDate: Date
        /// Countdown target for the current phase. When `currentPhase` is
        /// `.onField`, this is the estimated time until Nexus advances
        /// the next match to On Field (i.e. this match is done).
        var phaseDeadline: Date
        /// Match number currently playing on the field, sourced from the
        /// layered Nexus/TBA merge. Used to compute "matches away."
        var currentMatchOnField: Int
        /// Time of the most recent successful data fetch. Retained for
        /// future staleness UI; unused in current implementation.
        var lastUpdated: Date
    }

    // Static attributes — set when the activity starts.
    let teamNumber: Int       // e.g. 1234
    let matchNumber: Int      // e.g. 42  → displayed as "Q42"
    let alliance: Alliance
}
```

### Derived helpers

```swift
extension FRCMatchAttributes.ContentState {
    /// 0…1 progress through the current phase. Drives the complication
    /// progress bar (§8.3).
    var phaseProgress: Double {
        let elapsed = Date().timeIntervalSince(phaseStartDate)
        let total   = phaseDeadline.timeIntervalSince(phaseStartDate)
        guard total > 0 else { return 0 }
        return min(max(elapsed / total, 0), 1)
    }
}

extension FRCMatchAttributes {
    /// How many matches until this team plays. Derived from static
    /// matchNumber and dynamic currentMatchOnField.
    func matchesAway(currentOnField: Int) -> Int {
        matchNumber - currentOnField
    }
}
```

### Matches-away display rules

The right column of the Live Activity hero row shows how far away the
team's match is from the current field action.

| Gap (`matchNumber - currentMatchOnField`) | Display text | Color |
|-------------------------------------------|-------------|-------|
| ≥ 2                                       | `"X AWAY"`  | `rgba(255, 255, 255, 0.50)` |
| 1                                         | `"NEXT"`    | `phase.color` at 65% opacity |
| 0 (team is On Field)                      | `"NOW"`     | `rgba(48, 209, 88, 0.65)` |

---

## 6. Expanded Live Activity

Displayed on the iOS Lock Screen and as a banner. This is the primary
information surface.

### 6.1 Layout Structure

```
┌─────────────────────────────────────────────┐
│ 1234 [BLUE·Q42]                    ● Live   │  ← header row
│                                             │
│ QUEUE · UNTIL ON DECK          YOUR MATCH   │  ← label row
│ 4:23                            2 AWAY      │  ← number row
│                                             │
│ ┌──────────┬──────────┬──────────┬─────────┐│  ← chevron bar
│ │  ✓ PRE  ▶│  QUEUE  ▶│  DECK   ▶│  FIELD  ││
│ └──────────┴──────────┴──────────┴─────────┘│
└─────────────────────────────────────────────┘
```

Card padding: `11pt` top, `12pt` bottom, `14pt` horizontal. Card corner
radius: `22pt`. Card width on a 390pt iPhone is approximately `360pt`
after the 12pt outer margins; the chevron bar's internal coordinate
system uses `W = 332pt`.

### 6.2 Header Row

A single row containing identity and live status.

- **Team number:** 14pt monospaced bold white, left-aligned.
- **Alliance badge:** pill with `Alliance.badgeBackground` fill and
  `Alliance.badgeText` text color. 4pt corner radius. Text format:
  `"\(alliance.displayName) · Q\(matchNumber)"`, e.g. `BLUE · Q42`.
  10pt SF Pro medium. 8pt gap from team number.
  **No alliance dot on this surface** — the badge is sufficient.
- **Spacer** absorbs remaining horizontal space.
- **Live indicator** (right-aligned): 5pt amber `#FF9500` dot + "Live"
  label at 10.5pt, `rgba(235,235,245,0.45)`. 4pt gap between dot and
  label.

Bottom margin: 10pt.

### 6.3 Hero Countdown Row

A two-column grid (`1fr auto`) where the **label row** and **number row**
each share a baseline across both columns. Column gap: 12pt.

**Left column (primary):**
- Label: phase color, 10pt monospaced medium, tracking +0.1em.
  Text is `currentPhase.combinedLabel`, e.g. `QUEUE · UNTIL ON DECK`.
  Bottom-aligned within the label row.
- Countdown number: 50pt monospaced bold white, letter-spacing −2px,
  line-height 0.95. Top-aligned within the number row.

**Right column (matches-away context, right-aligned):**
- Label: 9pt monospaced regular, `rgba(235,235,245,0.35)`,
  tracking +0.05em. Text: `"YOUR MATCH"`. Bottom-aligned within the
  label row (shares a baseline with the left-column label).
- Value: 15pt monospaced bold, colored per the matches-away display
  rules in §5. Text: `"X AWAY"`, `"NEXT"`, or `"NOW"`.
  Top-aligned within the number row, with 2pt of top padding so its
  cap-height roughly aligns with the top of the large countdown digits.

**On Field exception.** When `currentPhase == .onField`, the value
displays `"NOW"` in `rgba(48, 209, 88, 0.65)` to reinforce that the
team's match is the one playing.

Bottom margin: 12pt.

### 6.4 Chevron Phase Bar

The most distinctive element. A continuous rectangular band divided by
right-pointing arrow tips, where each segment represents one phase.

**Shape rules:**
- Overall height: **48pt**.
- Arrow tip depth (`D`): **16pt**.
- All four segments share the same height, forming one unbroken rectangle.
- Each segment has a **flat left edge** and a **pointed right edge**.
  The last segment (On Field) has a flat right edge to terminate cleanly.
- **Z-ordering: earlier phases sit on top.** The tip of each segment
  overlaps the face of the segment to its right. Implement with
  `zIndex` decreasing left to right (preQueue=4, queueing=3, onDeck=2,
  onField=1).

**Width math.** For a bar of total width `W` with `n = 4` segments and
tip depth `D`:

```
visibleWidth = (W - D) / n      // width of each segment's visible "face"
segmentWidth = visibleWidth + D  // actual frame width (face + its own tip)
```

Each segment is `segmentWidth` wide. Place segment `i` at
`x = i * visibleWidth`. The last segment is the same width but has a
flat right edge, so it terminates exactly at `W`.

With `W = 332` and `D = 16`: `visibleWidth = 79`, `segmentWidth = 95`,
and the four segments sit at `x = 0, 79, 158, 237`, the last one ending
at `332`.

**Segment states.** A segment's state is determined by comparing its
phase to `currentPhase`:

| Relationship                             | State        |
|------------------------------------------|--------------|
| `phase.rawValue < currentPhase.rawValue` | Completed    |
| `phase == currentPhase`                  | Active       |
| `phase.rawValue == currentPhase + 1`     | Next pending |
| `phase.rawValue >  currentPhase + 1`     | Far pending  |

**Segment styling by state:**

| State        | Background fill        | Label                                           |
|--------------|------------------------|-------------------------------------------------|
| Completed    | `rgba(48,209,88,0.22)` | Checkmark + label, `rgba(48,209,88,0.65)`       |
| Active       | `phase.color` (full)   | Label in `#000000`, bold; secondary line below   |
| Next pending | `#2A2A2A`              | Label in `rgba(235,235,245,0.22)`                |
| Far pending  | `#222222`              | Label in `rgba(235,235,245,0.12)`                |

The active segment has a two-line stack centered in its visible face:
- Line 1: `phase.label` at 11pt mono bold, `#000000`, tracking +0.05em
- Line 2: secondary "X:XX left" at 9pt mono medium, `rgba(0,0,0,0.50)`,
  using the live countdown value

The completed segment renders, from left to right within its face:
a 12pt circle-check icon, then a 4pt gap, then the phase label.

**Padding for text centering inside clipped segments.** Each segment's
visible face is offset from its frame's left edge by `0` (first segment)
or `D` (all others, because the previous segment's tip overlaps them).
Center text within the visible face, not the frame:

- Segment 0 (preQueue): `padding(.trailing, D)` — face is left-flush
- Segments 1–2:         `padding(.leading, D).padding(.trailing, D)`
- Segment 3 (onField):  `padding(.leading, D)` — face is right-flush

**SwiftUI implementation:**

```swift
struct ChevronShape: Shape {
    let arrowDepth: CGFloat
    let isLast: Bool

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let tipX = isLast ? rect.maxX : rect.maxX - arrowDepth
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: tipX, y: rect.minY))
        if !isLast {
            path.addLine(to: CGPoint(x: rect.maxX, y: rect.midY))
        }
        path.addLine(to: CGPoint(x: tipX, y: rect.maxY))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

struct ChevronBar: View {
    let state: FRCMatchAttributes.ContentState

    var body: some View {
        GeometryReader { geo in
            let D: CGFloat = 16
            let n = CGFloat(Phase.allCases.count)
            let visibleWidth = (geo.size.width - D) / n
            let segmentWidth = visibleWidth + D

            ZStack(alignment: .topLeading) {
                ForEach(Phase.allCases) { phase in
                    ChevronSegment(phase: phase, currentPhase: state.currentPhase)
                        .frame(width: segmentWidth, height: geo.size.height)
                        .clipShape(ChevronShape(
                            arrowDepth: D,
                            isLast: phase == .onField
                        ))
                        .offset(x: CGFloat(phase.rawValue) * visibleWidth)
                        .zIndex(Double(Phase.allCases.count - phase.rawValue))
                }
            }
        }
        .frame(height: 48)
    }
}
```

### 6.5 On Field Card Background

When `currentPhase == .onField`, swap the card background from `#1C1C1E`
to `#112214` and add a `0.5pt` border in `rgba(48, 209, 88, 0.20)`. This
makes the On Field state unmistakable at a peripheral glance.

```swift
private var cardBackground: some View {
    let isOnField = state.currentPhase == .onField

    return RoundedRectangle(cornerRadius: 22)
        .fill(isOnField ? Color(hex: "#112214") : Color(hex: "#1C1C1E"))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .strokeBorder(
                    isOnField ? Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20)
                              : Color.clear,
                    lineWidth: 0.5
                )
        )
}
```

---

## 7. Dynamic Island (Collapsed)

Displayed in the Dynamic Island pill when the app has an active Live
Activity and another app is in the foreground.

### 7.1 Layout

```
╭──────────────────────────────────────────────────╮
│ ● QUEUE      │  NOW      │  YOUR MATCH    ●blue  │
│   4:23       │  Q 40     │  Q 42                 │
╰──────────────────────────────────────────────────╯
```

Pill background: `#000000`, never tinted. Approximate pill width:
250–280pt on current hardware.

Three content columns plus a trailing alliance dot, separated by thin
vertical dividers (`0.5pt`, `rgba(255, 255, 255, 0.10)`, 30pt tall,
centered vertically):

1. **Phase + countdown column.** Two-line stack:
   - Line 1: 6pt phase-color dot + 8.5pt mono semibold phase label
     (`phase.label`, e.g. `QUEUE`) in phase color, 4pt gap between dot
     and label.
   - Line 2: 15pt mono bold countdown in white, letter-spacing −0.5px.

2. **Now-on-field column.** Two-line stack:
   - Line 1: `NOW` at 8pt mono regular, `rgba(255, 255, 255, 0.30)`.
   - Line 2: `Q \(currentMatchOnField)` at 13pt mono bold,
     `rgba(255, 255, 255, 0.50)`.

3. **Your-match column.** Two-line stack:
   - Line 1: `YOUR MATCH` at 8pt mono regular, in phase color.
   - Line 2: `Q \(matchNumber)` at 13pt mono bold, `#FFFFFF`.

4. **Alliance dot** (no divider): 7pt circle in `alliance.dotColor`,
   flush right end of pill.

When `currentPhase == .onField`, the "Now" and "Your match" numbers will
both show the team's match number. This convergence is intentional and
signals the match has started — leave both visible.

### 7.2 Phase color application

Across all phases, the dot and the phase label in column 1, and the
`YOUR MATCH` label in column 3, all use `phase.color`. Everything else
in the pill is white or dimmed white.

---

## 8. watchOS Complications

Two complication families. All use dark backgrounds. The alliance dot
sits immediately to the right of the phase progress bar in all variants.

### 8.1 Circular Complication

**Size:** 70×70pt circular.

**Layout (centered, stacked vertically):**
1. Phase label (`phase.label`) — 9pt mono semibold, phase color,
   tracking +0.07em.
2. Countdown — 22pt mono bold white, letter-spacing −1px.
3. Progress bar + alliance dot — inline row:
   - Bar: 28pt × 2.5pt, background `#3A3A3C`, fill `phase.color`,
     fill width = `phaseProgress × 28`.
   - Alliance dot: 5pt circle in `alliance.dotColor`, 4pt gap after bar.

**On Field variant:** Background `#0F2118`, border `1pt solid #30D158`.

### 8.2 Rectangular Complication

**Size:** 160×68pt, 12pt corner radius.

**Layout** (two columns separated by a 0.5pt vertical divider, 42pt
tall, centered vertically):

Left column (fixed width ~46pt, centered):
- Icon: 20×20pt rounded square filled with `phase.color`, 5pt radius.
  Solid color, no glyph.
- Team number: `#1234` format, 9.5pt mono, phase color at 50%.

Right column (remaining width):
- Phase label (`phase.label`): 9pt mono semibold, phase color,
  tracking +0.07em.
- Countdown: 26pt mono bold white, letter-spacing −1px.
- Progress bar + alliance dot: same construction as circular,
  bar 48pt wide.

**On Field variant:** Background `#0F2118`, border
`0.5pt solid rgba(48, 209, 88, 0.40)`.

### 8.3 Phase Progress Bar

The thin progress bar is an ambient indicator of how far through the
*current phase* the team is — not total event progress. Use the
`phaseProgress` helper from §5.

On Field state: the bar fills left to right during the match duration,
so at the 30-second-remaining mark it should be roughly 80% full.

---

## 9. Match Lifecycle

### 9.1 One Activity Per Match

Each match gets its own `Activity<FRCMatchAttributes>` instance. There
is no "complete" phase — completion is the absence of an activity.

### 9.2 Phase Transitions

Phases are derived from Nexus time estimates via polling (BGTaskScheduler).
The app compares Nexus fields (`estimatedQueueTime`,
`estimatedOnDeckTime`, `estimatedOnFieldTime`, `estimatedStartTime`)
against the current time to determine `currentPhase`. When Nexus
provides discrete status changes, those take priority over time-based
derivation.

On each state change:
1. Update `currentPhase` to the new phase.
2. Update `phaseStartDate` to now.
3. Update `phaseDeadline` to the new countdown target.
4. Update `currentMatchOnField` from the layered Nexus/TBA merge.
5. Update `lastUpdated` to now.

### 9.3 On Field Deadline

When `currentPhase == .onField`, `phaseDeadline` represents the
estimated time until this match ends. This is derived from when Nexus
moves the *next* scheduled match to On Field status — that event signals
the current match is complete.

If Nexus provides an estimated start time for the next match, use that
as the deadline. Otherwise, use the current match's
`estimatedStartTime + 150 seconds` (standard 2:30 FRC match duration)
as a reasonable approximation.

### 9.4 Match Completion

When the app detects that the current match is complete (Nexus advances
the next match to On Field, or the phaseDeadline passes):

1. End the current Live Activity via
   `activity.end(dismissalPolicy: .immediate)`.
2. If another match is scheduled for the tracked team, immediately start
   a new `Activity<FRCMatchAttributes>` with that match's static
   attributes and an initial Pre-Queue content state.
3. If no more matches are scheduled (last match of the day), the
   activity simply ends. No replacement is created.

### 9.5 Phase State Machine

```
preQueue → queueing → onDeck → onField → (activity ends)
```

| Phase     | `label`  | `sublabel`          | `combinedLabel`              |
|-----------|----------|---------------------|------------------------------|
| Pre-Queue | `PRE`    | `UNTIL QUEUEING`    | `PRE · UNTIL QUEUEING`       |
| Queueing  | `QUEUE`  | `UNTIL ON DECK`     | `QUEUE · UNTIL ON DECK`      |
| On Deck   | `DECK`   | `UNTIL ON FIELD`    | `DECK · UNTIL ON FIELD`      |
| On Field  | `FIELD`  | `MATCH IN PROGRESS` | `FIELD · MATCH IN PROGRESS`  |

The Live Activity hero uses `combinedLabel`. The Dynamic Island and all
complications use just `label`. The chevron bar always uses `label`.

---

## 10. Implementation Notes

### Color(hex:) Extension

The `Color(hex:)` initializer used throughout is not built into SwiftUI.
Implement as a small extension:

```swift
extension Color {
    init(hex: String) {
        let hex = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        let scanner = Scanner(string: hex)
        var rgbValue: UInt64 = 0
        scanner.scanHexInt64(&rgbValue)
        self.init(
            red: Double((rgbValue & 0xFF0000) >> 16) / 255.0,
            green: Double((rgbValue & 0x00FF00) >> 8) / 255.0,
            blue: Double(rgbValue & 0x0000FF) / 255.0
        )
    }
}
```

### ActivityKit (Live Activities)

- Define `FRCMatchAttributes` exactly as in §5.
- Use `Activity.update(.init(state:staleDate:))` on each phase
  transition.
- Set `staleDate` to `phaseDeadline + 30s` so the system expires the
  activity if no follow-up update arrives.

### WidgetKit (Complications)

- Families: `.accessoryCircular`, `.accessoryRectangular`.
- Return a `TimelineEntry` with a `relevance` score that increases as
  `phaseDeadline` approaches.
- For the countdown, use SwiftUI's `Text(deadline, style: .timer)` to
  get system-managed countdown rendering that stays accurate without
  polling.

### Countdown rendering

```swift
Text(deadline, style: .timer)
    .font(.system(size: 50, weight: .bold, design: .monospaced))
    .kerning(-2)
    .foregroundStyle(.white)
    .monospacedDigit()
```

Use the same pattern at smaller sizes for the Dynamic Island and
complications, with the sizes from §4.

### Card background switching

```swift
private var cardBackground: some View {
    let isOnField = state.currentPhase == .onField

    return RoundedRectangle(cornerRadius: 22)
        .fill(isOnField ? Color(hex: "#112214") : Color(hex: "#1C1C1E"))
        .overlay(
            RoundedRectangle(cornerRadius: 22)
                .strokeBorder(
                    isOnField ? Color(red: 48/255, green: 209/255, blue: 88/255).opacity(0.20)
                              : Color.clear,
                    lineWidth: 0.5
                )
        )
}
```

---

## 11. Asset Summary

| Asset                  | Spec                                                 |
|------------------------|------------------------------------------------------|
| Phase checkmark        | 12pt circle outline + polyline, `rgba(48,209,88,0.65)` |
| Alliance dot (blue)    | Filled circle, `#1E6FFF`, sized per surface          |
| Alliance dot (red)     | Filled circle, `#FF3B30`, sized per surface          |
| Phase progress bar     | Rounded rect, 2.5–3pt tall, `phase.color`            |
| Phase icon square      | 20×20pt rounded rect, `phase.color`, 5pt radius (rectangular complication only) |

---

*End of specification.*
