# Android manual test: live notification against a fake event

1. Start an Android 16 (API 36) emulator, e.g. `~/Library/Android/sdk/emulator/emulator -avd NotificationFlow_API36 &`.
2. Serve the captured event, shifted to now:
   ```bash
   python3 scripts/fake-api.py scripts/fixtures/2026cancmp/2026-04-10T22-05-28Z scripts/fixtures/2026cancmp/2026-04-11T00-51-22Z --switch-after 120
   ```
3. Install a debug build pointed at it:
   ```bash
   (cd android && ./gradlew :app:installDebug -Ppitwatch.tbaBaseUrl=http://10.0.2.2:8765/api/v3 -Ppitwatch.nexusBaseUrl=http://10.0.2.2:8765/api/v1)
   ```
4. Open PitWatch: any TBA key, team **5507**, any Nexus key → Continue → allow notifications.
5. Tap **Start live tracking**. Expect a Live Update chip in the status bar (`Q… Nm`) and, in the shade,
   the title/text/segmented progress. After ~2 min the server switches snapshots; within ~30 s the
   notification should change phase (Refresh in the notification forces it immediately).
6. Tap **Stop** (or swipe the notification away). Reopening the app must not restart tracking for the
   same match.
