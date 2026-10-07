# Schedule widget, picker previews, schedule notification, API key copy — design

**Status:** approved in conversation 2026-10-06, pending written-spec review
**Builds on:** `2026-10-06-scoreboard-restyle-design.md` (look and feel), `2026-10-05-android-screens-widgets-design.md` (widget data flow)

## Goal

Four small features:

1. A second home-screen widget that shows only the upcoming schedule and the previous match.
2. Generated previews in the widget picker for both widgets.
3. An optional, user-started persistent notification showing the same schedule.
4. A "keep notifications pinned" toggle, plus clearer API key copy on Setup and Settings.

No polling or network changes: everything renders from the persisted cache, like the current widget.

## 1. Schedule widget

**Registration**
- A new `ScheduleWidget : GlanceAppWidget` with its own receiver, `ScheduleWidgetReceiver`, and provider XML `schedule_widget_info.xml`.
- Label "PitWatch schedule". Default size 2×2, resizable from 110 dp up to 530 × 450 dp.

**Model**
- `ScheduleWidgetModel` is built from the same `MatchListModels.build(...)` as everything else.
- It holds:
  - `header`, the same "5507 · #34 · 1-2-0" line as the main widget;
  - `days`, the upcoming schedule including the next match, which is not removed here;
  - `last`, the most recent result;
  - `message`, used in non-ready states. These match the main widget's states and messages.

**Layout** (Glance, the same Scoreboard styling and `CONDENSED` font as the main widget):
1. The header, shown if it fits.
2. The "LAST" line, using the same compact or wide `LastLine` as the main widget. It goes above the list so a long list only truncates itself.
3. The upcoming list as one line per entry:
   - day headers;
   - match rows: alliance dot, short label, a phase pill if the match is in motion, and time;
   - break rows.

**Sizing**
- `SchedulePlans.plan(height, hasLast)` decides whether the header and last line fit, and how many list lines remain. It uses the measured part heights from `WidgetPlans`, sharing the constants.
- `WidgetLines.fit` applies the existing 9-line cap and the rule that a day header never appears without at least one row under it.
- If no list lines fit, the widget shows the next match row alone.

**Updates** come from the existing `WidgetRefresh` hook (refresh job, data changes, deadline alarms). It now updates both widget classes.

## 2. Widget picker previews

- **Generated previews (Android 15+):** both widgets implement `providePreview` and render their normal content from a sample model, `SampleWidgetData`.
  - The sample is a fixed event: Q36 on field, the alliances, three upcoming rows with a lunch break, and the last result Q22 WIN 403–299. It's built from in-code literals, not the fixture files.
- **Registration:** `GlanceAppWidgetManager.setWidgetPreviews(...)` registers the previews for both receivers once per app version code.
  - This runs from `PitWatchApp.onCreate` on the app scope and is recorded in the new prefs store, so it isn't repeated.
  - Failures, such as rate limiting, are logged and ignored.
- **Fallback:** `previewLayout` XML stays for launchers without generated previews. The existing one is reused for the main widget. A new `schedule_widget_preview.xml` shows a few sample rows.

## 3. Schedule notification

- **Channel:** a new `schedule` channel, "Match schedule", at low importance (silent, no badge).
- **Content**, built by the pure `ScheduleNotification.content(model, timeFormat)`:
  - Title: "Next: Q36 · ~11:59 AM", or the widget's message text in non-ready states.
  - Text: "then Q43 ~3:34 AM · Last Q22 W 403–299". Each part is dropped when absent.
  - Expanded: `InboxStyle` with up to 6 lines, each a match row ("Q43 · ~3:34 AM", with " · IN QUEUE" etc. when in motion) or a break ("Lunch 12:02–1:02 PM"). Day changes are prefixed to the first row of each day ("Wed · Q43 · ~3:34 AM"). The summary text is "Last Q22 W 403–299".
- **Notification settings:** `setOngoing(true)`, `setOnlyAlertOnce(true)`, `setShowWhen(false)`. It is not promoted and has no chronometer.
  - Tapping it opens the app.
  - A "Turn off" action switches the setting off and cancels the notification.
- **Lifecycle (`ScheduleNotifier`):**
  - `update(context, container)` posts or updates the notification when it's enabled, and cancels it when it isn't. It's called from the `WidgetRefresh` hook, so it refreshes whenever widgets do.
  - It's also called when the setting changes and from `BootReceiver`.
  - If notification permission isn't granted, it doesn't post. The Settings switch then shows a hint linking to the notification settings, reusing the existing pattern.
- **Turning it on and off:**
  - Settings → "Notifications" group → "Schedule in notifications" switch.
  - The Matches screen gets a small text button below the results, "Show schedule in notifications", visible only while the setting is off.

## 4. Keep notifications pinned

- **Setting:** Settings → Notifications → "Keep notifications pinned", off by default.
- **Why re-post:** since Android 13–14, users can swipe away ongoing and foreground-service notifications (apart from call, media and work-policy ones). "Pinned" is therefore implemented by re-posting on dismissal.
- **Schedule notification:** a delete intent goes to `ScheduleNotificationReceiver`.
  - When pinned and still enabled, it re-posts at once.
  - Otherwise the dismissal turns the setting off, so it stays off until switched back on.
- **Live tracking notification:** its delete intent currently stops tracking.
  - When pinned, it goes to the service as `ACTION_REPOST`, which rebuilds and re-posts the current notification.
  - Stop stays available only through the "Stop" action. When not pinned, behavior is unchanged.
- **Prefs:** both settings live in a new app-side DataStore, `NotificationPrefs(scheduleEnabled = false, pinned = false, previewsVersion = 0)`, in `Stores`. `UserConfig` in `:core` is unchanged.

## 5. API key copy (Setup and Settings)

**Labels:** "TBA API key" and "FRC Nexus API key"; "(optional)" is removed.

**Supporting text** under each field (the `supportingText` slot, body small, `onSurfaceVariant`). The strings are shared through `ApiKeyHelp`.

*TBA:*
> Required. Match schedule, times, scores, rankings and alliances. Get a read key from your account page on thebluealliance.com.

*Nexus:*
> Optional, recommended at events. Live queue status (queuing → on deck → on field), more accurate times, breaks, the pit map and live tracking's phase timeline. Without it PitWatch uses TBA's estimated times.

The Setup screen's separate "Get a read API key…" line is removed, because the TBA blurb now carries it.

## Testing

All tests run with `make test`.

- **Model:** `ScheduleWidgetModel` states, `SchedulePlans.plan` at 2×2, 4×2 and 4×4 heights, and `ScheduleNotification.content` for ready, TBA-only, no-upcoming and not-configured states. Also day prefixes, in-motion suffixes, and the 6-line cap.
- **Glance:** the schedule widget at 2×2, 2×4 and 4×4: rows present, LAST line, no orphan day header, and the outer Column under 10 children.
- **Notifier:**
  - enabled → posted on the `schedule` channel;
  - disabled → cancelled;
  - the Turn-off action disables it.
- **Pinned:**
  - the schedule delete intent re-posts when pinned and disables when not;
  - the live service's delete intent re-posts when pinned and stops when not.
- **Compose:** the Setup and Settings blurbs are present, and "(optional)" is absent.
- **Previews:** `SampleWidgetData` builds a ready model, and registration runs once per version code. The `GlanceAppWidgetManager` call sits behind a small interface so it can be faked.
- **Manual, on emulator-5580 only:**
  - both widgets in the picker, with generated previews;
  - the schedule widget at 2×2, 2×3, 4×2 and 4×3 with no clipping;
  - the notification collapsed and expanded, in light and dark;
  - swipe with pinned on and off for both notifications.

## Out of scope

- Countdown or chronometer in the schedule notification.
- Promoting the schedule notification to a Live Update.
- Lock-screen-specific layouts.
- Configurable row counts.
