# Home Screen Widget Layout Refresh — Design Spec

**Date:** 2026-04-10
**Branch:** `feature/widget-style-refresh`
**Scope:** The three iOS home screen widgets in `PitWatchWidgets/WidgetViews/` — `SmallWidgetView`, `MediumWidgetView`, `LargeWidgetView`. Lock screen widgets, Dynamic Island, the Live Activity, and watchOS widgets are out of scope.

## Goals

1. **Densify the large widget's upcoming section.** Show more upcoming matches, and show all six teams on each upcoming row. Tracked team's alliance is visually highlighted.
2. **Unify the home widgets' visual language with the Live Activity.** Flatten the layout (kill the nested white-opacity cards), adopt the LA alliance badge header, bring in phase color accents on the NEXT countdown.
3. **Preserve each widget's distinct purpose** rather than forcing all three to mirror the Live Activity's expanded view structure. The widgets are family members, not twins.

## Non-goals

- Lock screen / Dynamic Island changes (refreshed separately in `7755f02`).
- Watch widget changes.
- New data sources or API calls — all data already flows through `MatchWidgetEntry`.
- Snapshot/pixel-diff tests (not currently used in the project).
- Timeline refresh policy changes.

## Shared visual language

All three widgets share a common vocabulary. Changes below are additive to `SharedWidgetComponents.swift`.

### Color tokens (new)

- `widgetCardBackground` = `Color(hex: "#1C1C1E")` — container background, already in use, promoted to a named token.
- `widgetLabelDim` = `Color(red: 235/255, green: 235/255, blue: 245/255)` — used with opacities `0.30` / `0.45` / `0.65` for tertiary / secondary-dim / secondary text respectively. Replaces all uses of `.secondary` and `.tertiary` in the three widget views. Matches the palette used by `ExpandedLiveActivityView.lastUpdatedView` and `ChevronBar`.
- Phase colors (`Phase.queueing.color`, `Phase.onDeck.color`, `Phase.onField.color`, `Phase.preQueue.color`) already exist in `TBAKit/Sources/TBAKit/Models/Phase.swift` and are reused directly.

### New shared component: `AllianceBadge`

A colored rounded-rect capsule matching `ExpandedLiveActivityView.headerRow`:

```
[Red · Q32]    ← 10pt medium font, radius 4, alliance-colored background
```

- Red alliance → red-tinted background and text (mirrors LA `attrs.alliance.badgeBackground` / `badgeText`).
- Blue alliance → blue-tinted background and text.
- Unknown alliance color (no next match, or match without tracked team) → the badge is **omitted at the call site** (each widget view checks `entry.nextMatchAllianceColor` and only renders the badge when it resolves). Match label is still shown elsewhere in the widget, so omitting the badge doesn't hide information.

### Modified shared component: `AllianceLineCompact`

Add a new initializer parameter:

```swift
struct AllianceLineCompact: View {
    let allianceColor: String
    let teamKeys: [String]
    let trackedTeamKey: String
    let opr: Double?
    var highlighted: Bool = false  // new, default false
    // ...
}
```

When `highlighted: true`, the row renders with a subtle alliance-color background tint:
- Red: `Color.red.opacity(0.12)`
- Blue: `Color.blue.opacity(0.12)`

The tint sits behind the team numbers with minimal horizontal padding. Existing call sites (NEXT cards in medium and large) pass `highlighted: false` explicitly or rely on the default; their appearance is unchanged.

### New entry helper: `MatchWidgetEntry.nextMatchPhase`

A computed property on `MatchWidgetEntry` in `MatchTimelineProvider.swift`:

```swift
var nextMatchPhase: Phase? {
    guard let match = nextMatch, let nexusEvent,
          let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent)
    else { return nil }
    return PhaseDerivation.derivePhase(from: nexusMatch).phase
}
```

- Returns `nil` when Nexus is unavailable or no Nexus match record exists for the next match.
- Widget views call `entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65)` for the phase-colored countdown text.
- Single point where phase is derived for home widgets — views never call `PhaseDerivation` directly.

### Flattened layout rule

No widget view uses nested `RoundedRectangle` backgrounds for **grouping sections** (e.g., wrapping a NEXT block, UPCOMING block, or RESULTS block in a white-opacity card). Grouping happens via spacing, section labels, and dim-color separators — not cards-inside-cards. Small UI elements like the `AllianceBadge` capsule, the Nexus status pill, and alliance-line highlight tints still use rounded rectangles internally — the rule is about section grouping, not every shape in the widget.

### Typography

Unchanged from current. Monospaced throughout, 8pt tracked labels, 10-14pt body text, 28pt hero in small, 18pt heroes in medium/large NEXT. No font size changes.

---

## Small widget

The small widget's job is unchanged: one glance at the next match. Current structure is preserved; only colors and the header badge change.

### Layout

```
┌─────────────────────────┐
│ 1700  [Red · Q32]       │  ← Header row
│ #12 · 7-3-0             │  ← Rank line, dim
│                         │
│          Q32            │  ← 28pt mono bold match label
│        in 12m           │  ← 13pt phase-colored countdown
│        ~2:47 PM         │  ← 10pt dim wall clock
│                         │
│            Carver Regnl │  ← Event name, dim, bottom-right
└─────────────────────────┘
```

### Changes from current `SmallWidgetView.swift`

1. **Header:** Replace the current `AllianceDot` (6pt colored circle next to team number) with the new `AllianceBadge` component showing `[Red · Q32]`. Team number remains `14pt .bold .monospaced`.
2. **Countdown color:** Change from `.foregroundStyle(.secondary)` to `.foregroundStyle(entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65))`. When Nexus data is available and the next match is in a phase, the countdown carries the phase's color (orange for queuing, green for on-field, etc.). Falls back to the dim gray token otherwise.
3. **Wall clock line:** `.tertiary` → `widgetLabelDim.opacity(0.45)`.
4. **Rank line:** `.secondary` → `widgetLabelDim.opacity(0.65)`.
5. **Event name:** `.tertiary` → `widgetLabelDim.opacity(0.45)`. Position and `lineLimit(1)` unchanged.

### What does NOT change

- VStack structure, spacing, all font sizes.
- No hero 50pt timer (won't fit at this size).
- No "matches away" indicator (too dense).
- No explicit Nexus status badge — phase color on the countdown is the signal.

---

## Medium widget

The medium widget's job is unchanged: NEXT and LAST side-by-side. Structure preserved; the inner cards get killed and colors swap.

### Layout

```
┌──────────────────────────────────────────────────┐
│ 1700  [Red · Q32]     #12 · 7-3-0    Carver Regnl│  ← Header
│                                                  │
│ NEXT                    │ LAST                   │  ← Section labels, dim + tracked
│ Q32        in 12m       │ Q29                    │
│ ~2:47 PM                │                        │
│ ● 1700 254 1678  45.2   │         78 - 64        │  ← ScoreDisplay
│ ● 2337 973  118  38.7   │          WIN           │  ← WinLossLabel
└──────────────────────────────────────────────────┘
```

### Changes from current `MediumWidgetView.swift`

1. **Kill the inner cards.** Remove the two `RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.06))` backgrounds. Both columns sit flat on the container.
2. **Column separator.** Add a thin 0.5pt vertical separator between the columns — a `Rectangle` filled with `widgetLabelDim.opacity(0.15)` stretching the full column height. Subtle, just enough to show the boundary now that the cards are gone.
3. **Header:** Insert the new `AllianceBadge` after the team number: `1700  [Red · Q32]  #12 · 7-3-0  [spacer]  Carver Regnl`. Rank and event name switch to `widgetLabelDim` tokens (0.65 and 0.45 respectively).
4. **NEXT column color updates:**
   - `NEXT` label: `.secondary` → `widgetLabelDim.opacity(0.45)`.
   - Match label (`Q32`): unchanged, 18pt bold mono.
   - Countdown: `.secondary` → `entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65)`.
   - Wall clock: `.tertiary` → `widgetLabelDim.opacity(0.45)`.
   - Alliance lines: use `AllianceLineCompact` unchanged (no `highlighted: true` on medium — phase-colored countdown is the only accent at this size). OPR stays shown.
5. **LAST column color updates:**
   - `LAST` label: same color update as `NEXT`.
   - Match label, `ScoreDisplay`, `WinLossLabel`: unchanged. They already use semantic red/blue/green which match the LA language.
   - No phase accent on the LAST column (no phase applies to a finished match).

### What does NOT change

- Side-by-side NEXT/LAST column layout.
- VStack / HStack nesting.
- Font sizes.
- No hero 50pt timer.

---

## Large widget

The biggest change. The large widget's purpose shifts slightly: it becomes "everything you need to plan the next hour at the event" — maximizing upcoming match visibility at the expense of the results list.

### Layout

```
┌──────────────────────────────────────────────────┐
│ 1700  [Red · Q32]    · Carver Regnl    #12 · 7-3 │  ← Header
│                                                  │
│ ● Now Queuing: Qualification 31                  │  ← Conditional queuing indicator
│                                                  │
│ NEXT  Q32  [QUEUING]              in 12m         │  ← NEXT label row, phase-colored countdown
│                                    ~2:47 PM      │
│ ● 1700 254 1678                       45.2       │  ← Red alliance line (with OPR)
│ ● 2337 973 118                        38.7       │  ← Blue alliance line (with OPR)
│                                                  │
│ UPCOMING                                         │  ← Section label, dim tracked
│                                                  │
│  Q35                              ~3:04 PM       │  ← Upcoming row 1 header
│  ● 1700 254 4414                                 │  ← Tracked alliance (tinted bg)
│  ● 8812 1982 5940                                │  ← Opposition (plain)
│                                                  │
│  Q38                              ~3:22 PM       │
│  ● 1390 2056 3707                                │  ← Opposition (plain)
│  ● 1700 1796 4043                                │  ← Tracked alliance (tinted bg)
│                                                  │
│  Q41                              ~3:40 PM       │  ← (as many rows as fit)
│  ● ...                                           │
│  ● ...                                           │
│                                                  │
│ LAST   Q29         78 - 64  WIN        ~2:12 PM  │  ← Single-row RESULTS
└──────────────────────────────────────────────────┘
```

### Changes from current `LargeWidgetView.swift`

**Header row:**
- Insert `AllianceBadge` between the team number and the event name: `1700  [Red · Q32]  · Carver Regnl  [spacer]  #12 · 7-3`.
- Column ordering matches the existing large widget: team + badge + event name on the left, rank on the right. (This is intentionally different from the medium widget, which has rank before the event name. Both layouts are preserved from their current orderings — the refresh does not unify them.)
- Secondary/tertiary colors swap to `widgetLabelDim` tokens.
- `lineLimit(1)` stays on the event name, so it shortens first if the row overflows — event name has the lowest visual priority in this row.

**Queuing indicator:**
- Text changes from `"Queuing: \(nowQueuing)"` to `"Now Queuing: \(nowQueuing)"`.
- The `#FF9500` orange dot stays; this color is functionally identical to `Phase.queueing.color`, but the implementation migrates to reference `Phase.queueing.color` directly for consistency.

**NEXT section:**
- Remove the `RoundedRectangle(cornerRadius: 8).fill(Color.white.opacity(0.08))` card background. The section sits flat.
- Internal layout is preserved:
  - Left: `NEXT` tracked label + `Q32` match label + optional Nexus status pill.
  - Right: countdown (phase-colored) stacked over wall clock time.
  - Below: two `AllianceLineCompact` rows with OPR shown.
- Countdown: `.secondary` → `entry.nextMatchPhase?.color ?? widgetLabelDim.opacity(0.65)`.
- Nexus status pill: currently uses `nexusStatusColor(status)` which hashes the status string. Migrate to `entry.nextMatchPhase?.color ?? .gray` so the pill and the countdown are driven by the same derivation. When `entry.nextMatchPhase` is `nil` but a raw status string exists, fall back to the existing `nexusStatusColor` to preserve current behavior.
- All other text colors swap to `widgetLabelDim` tokens.

**UPCOMING section — new row design:**

Each upcoming row is a 3-line mini-card:

```
 Q35                              ~3:04 PM       ← Line 1: match label + time
 ● 1700 254 4414                                 ← Line 2: red alliance (AllianceLineCompact)
 ● 8812 1982 5940                                ← Line 3: blue alliance (AllianceLineCompact)
```

- **Line 1:** Match `shortLabel` (12pt bold mono) left-aligned, wall clock time (11pt mono, `widgetLabelDim.opacity(0.45)`) right-aligned via `Spacer()`. No alliance dot (alliances are shown in full below).
- **Lines 2 & 3:** Two `AllianceLineCompact` rows, red then blue. The tracked team's alliance line passes `highlighted: true` to render the subtle alliance-tint background. The non-tracked alliance renders plain. **OPR is hidden on upcoming rows** — the `AllianceLineCompact` initializer is called with `opr: nil`.
- **Internal spacing:** 3pt between the three lines within a single match; 8pt between separate matches.
- **No section card background** — `UPCOMING` label sits above the rows on the flat container background.

**UPCOMING row count:**

SwiftUI widget layouts can't compute "how many fit" at runtime. The view renders a fixed target, hardcoded as a constant. **Start with 4 upcoming rows**; during the preview validation pass (see Testing section), if 4 rows causes clipping on the smallest iPhone large widget, drop the constant to 3. The starting value is 4, the fallback is 3, and the decision locks in before implementation wraps.

The timeline provider's upcoming fetch is bumped from `prefix(2)` to `prefix(8)` to give the view headroom without bloating the entry.

**LAST section — single row:**

The current large widget renders up to 3 past matches. This collapses to a single row using `entry.lastMatch` (the entry's designated single-last-match field, already used by the medium widget for the same purpose). `entry.pastMatches` is no longer read by any widget after this change, but the timeline provider still populates it (reduced to `prefix(1)`) in case future widgets need it.

Row layout:
```
 LAST   Q29         78 - 64  WIN        ~2:12 PM
```

- `LAST` label: 8pt tracked mono, `widgetLabelDim.opacity(0.45)`.
- Match `shortLabel`: 12pt bold mono.
- `ScoreDisplay`: unchanged (red / gray / blue number sequence).
- `WinLossLabel`: unchanged (green WIN / red LOSS).
- Wall clock time: 10pt mono, `widgetLabelDim.opacity(0.45)`, right-aligned via `Spacer()`.
- No alliance lines. No card background. Sits flat at the bottom of the widget.

### Information removed from the large widget

- Multi-row RESULTS list — reduced to 1 row.
- OPR on upcoming rows (OPR stays visible on the NEXT card).

### Information added

- All 6 teams per upcoming row (previously: just the match label and tracked team's alliance dot).
- Tracked-alliance highlight (subtle alliance-color background tint) on upcoming rows.
- Phase color accent on NEXT countdown.

---

## Data plumbing

Changes to `PitWatchWidgets/MatchTimelineProvider.swift`. No new API calls. No changes to `TBADataStore`, `MatchSchedule`, `NexusMatchMerge`, or `PhaseDerivation` — all reused as-is.

### `makeEntry()` adjustments

- `upcomingMatches`: `prefix(2)` → `prefix(8)`. Headroom for the large widget's target of 4 visible rows, without constraining the small/medium widgets (which don't use this array).
- `pastMatches`: `prefix(3)` → `prefix(1)`. Only the large widget uses this array, and it's now a single-row display. The medium widget uses `entry.lastMatch` separately.

### `MatchWidgetEntry.nextMatchPhase` (new computed property)

```swift
var nextMatchPhase: Phase? {
    guard let match = nextMatch, let nexusEvent,
          let nexusMatch = NexusMatchMerge.nexusInfo(for: match, in: nexusEvent)
    else { return nil }
    return PhaseDerivation.derivePhase(from: nexusMatch).phase
}
```

Pure computed property, no side effects, returns `nil` when Nexus is unavailable.

### Why this is low-risk

- The array changes are just `prefix()` values on already-sorted schedule data.
- `nextMatchPhase` composes existing helpers; the placeholder entry returns `nil` automatically.
- No new config fields, no new storage, no new network calls.

---

## Testing & validation

### Unit tests (XCTest, TBAKit package)

1. **`MatchWidgetEntry.nextMatchPhase`:**
   - No Nexus data → returns `nil`.
   - Nexus data present with status "queuing" → returns `.queueing`.
   - Nexus data present with status "on field" → returns `.onField`.
   - Nexus data present with no status, current time past `queueDate` → returns `.queueing` (confirms time-based fallback works through the entry).

2. **`AllianceLineCompact` highlight parameter:**
   - `highlighted: true` + red → asserts the row's background view is non-clear with red tint.
   - `highlighted: true` + blue → blue tint.
   - `highlighted: false` → no background (or clear).

3. **`AllianceBadge`:** no test needed — the component is a pure layout wrapper over match label + alliance color, and the "unknown alliance" case is handled at the call site by omission, not by a branch inside the component.

### SwiftUI previews

Every widget view gets or keeps `#Preview` blocks covering:

1. **All three `widgetFamily` sizes** (`.systemSmall`, `.systemMedium`, `.systemLarge`).
2. **Phase coverage:**
   - Nexus unavailable (fallback gray countdown).
   - Nexus queueing (orange countdown).
   - Nexus on-deck (orange-red countdown).
   - Nexus on-field (green countdown).
3. **Tracked alliance coverage:** one preview with the tracked team on red and another with the tracked team on blue, to visually confirm the highlighted row renders correctly for both.
4. **Empty state:** no next match, no upcoming matches — fallback text and stable layout.
5. **Conditional rows:** large widget with and without the `Now Queuing` indicator present, to confirm spacing doesn't jump.
6. **Row count validation:** large widget with 8 upcoming matches in the entry, rendered on iPhone 15 Pro and iPhone SE preview sizes. This is the preview that decides whether the target of 4 rows holds or drops to 3. The decision locks in before implementation completes.

### Simulator validation

After previews pass:

1. Build and install PitWatch in the iOS simulator.
2. Add all three widget sizes to the home screen.
3. Verify live rendering matches previews.
4. Toggle the Nexus time source on/off in settings → verify phase colors appear and disappear on the NEXT countdown.
5. Use test fixture data (or scrub time) to observe the countdown color transitioning across phase boundaries.

### Out of scope for testing

- Pixel-diff / snapshot testing — not currently used in this project.
- Lock screen and Dynamic Island — separately refreshed, not touched by this change.
- Watch widgets — separate target, unaffected.

### Definition of done

- All three home widget sizes render on the simulator with the new layout.
- Phase colors appear on the NEXT countdown when Nexus data is present, and fall back gracefully when it isn't.
- The large widget shows 4 (or 3 after preview validation) upcoming rows with full team visibility and the tracked alliance highlighted.
- The large widget shows a single-row LAST at the bottom.
- The large widget's queuing indicator reads `Now Queuing: <match label>`.
- All existing TBAKit unit tests still pass.
- New unit tests for `nextMatchPhase` and `AllianceLineCompact` highlight logic pass.
