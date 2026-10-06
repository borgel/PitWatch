# Android manual test: live notification against a fake event

1. Start an Android 16 (API 36) emulator, e.g. `~/Library/Android/sdk/emulator/emulator -avd NotificationFlow_API36 &`.
2. Serve the captured event, shifted to now (leave it running): `make fake-api`
3. Install a debug build pointed at it on that emulator only (`make devices` for the serial):
   ```bash
   make fake-install DEVICE=emulator-5554
   ```
4. Open PitWatch: any TBA key, team **5507**, any Nexus key → Continue → allow notifications.
5. Tap **Start live tracking**. Expect a Live Update chip in the status bar (`Q… Nm`) and, in the shade,
   the title/text/segmented progress. After ~2 min the server switches snapshots; within ~30 s the
   notification should change phase (Refresh in the notification forces it immediately).
6. Tap **Stop** (or swipe the notification away). Reopening the app must not restart tracking for the
   same match.
7. Check the screens: Matches (status card, next-match card with phase badge, break rows, results),
   the event picker (calendar icon), Pit map (zoomed on your pit), Settings — in light and dark mode
   (`adb shell cmd uimode night yes|no`).
8. Add the PitWatch widget to the home screen; resize it through small, medium and large.
