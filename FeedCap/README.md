# Finite Feed

Android app that limits Instagram Reels and YouTube Shorts to a daily time budget and a per-session video cap (default 20 min/day, 15 per session), plus an optional nightly sleep time with no Reels or Shorts at all. Stories and DMs are never limited. A reel opened from a DM or link always plays, even at a limit or during sleep time. Swiping on from it counts like any other Reel, and goes back to the chat only at a limit or during sleep time. "Never swipe past a shared reel" blocks it outright. Instagram's main home feed: by default, scrolling down opens the Following feed instead (via the logo menu), and coming back to the main feed jumps to the top, where Stories are. Other options: stop at "You're all caught up" (`detect/FeedWall.kt`) or no limit. A reel or Short opened from a DM, or from a link in another app, always plays. Swiping past it at a limit puts you back on it and shows the limit screen. Strict mode delays loosening changes by 24 hours. Turning Strict mode off is immediate, unless a change is waiting or was queued in the last 24 hours: then you have to type "I am a loser", and the waiting change applies too. See `../PRD.md`.

Debug builds log what the service sees (full-screen view IDs, scrolls, counts, blocks): `adb logcat -s FeedCap`. That's the quickest way to fix IDs after an Instagram or YouTube update.

## Build

```sh
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew testDebugUnitTest assembleDebug
```

- **Debug = Pro test build**: `com.feedcap.app.test`, label "FeedCap Pro Test", `BuildConfig.PRO_UNLOCKED = true`, plus a Test tools section.
- **Release**: `com.feedcap.app`, Pro locked. There's no billing yet.

## Releases (CI)

Every push to `main` runs `.github/workflows/release-apk.yml`: the unit tests, then the Pro Test APK, published as a GitHub Release named `build-<run number>`. The run number is used as `versionCode`, so each build installs as an update to the last one.

CI signs with a fixed key from the repo secrets `FEEDCAP_KEYSTORE_BASE64`, `FEEDCAP_KEYSTORE_PASSWORD`, `FEEDCAP_KEY_ALIAS` and `FEEDCAP_KEY_PASSWORD`. Local builds use Android's normal debug key, so a CI APK can't update a locally built one: uninstall first.

## Install on a phone

```sh
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or copy the APK to the phone and open it. If you install from Files or a browser, Android 13+ greys out the Accessibility switch. To fix it, open **App info → ⋮ → Allow restricted settings**, then turn FeedCap on in Accessibility settings. Installing with `adb` avoids this.

## Parental controls and onboarding

- **Parental controls:** a 4-digit parent PIN (salted PBKDF2 hash, with longer lockouts after wrong guesses) locks every setting. A correct PIN unlocks for 5 minutes. While it's locked, `TamperGuard` closes FeedCap's Accessibility page, its App info page and the uninstall dialog. Debug builds have a "Forgot PIN" escape hatch.
- **Onboarding:** first run only (replay it from Test Tools). Answers and the current step are saved as you go, so leaving for Settings, or the app being closed, resumes where it left off. When the permission is switched on, the service brings FeedCap back to the front.
- **Design:** `ui/design/Design.kt` follows Apple's Human Interface Guidelines: system grouped backgrounds, inset grouped sections, large titles, iOS switches, sheets with steppers and wheels, and one indigo accent, in light and dark.

## Code map

| File | What it does |
|---|---|
| `detect/Rules.kt` | Per-app view IDs for the Reels/Shorts surface, the pager and DM markers |
| `budget/Budget.kt` | Pure budget rules (daily time, session cap, session reset), unit tested |
| `budget/SleepTime.kt` | Sleep-time window (may cross midnight), unit tested |
| `service/BlockerService.kt` | Accessibility service: detects the surface, counts swipes and time, sends you back |
| `service/BlockOverlay.kt` | The "limit reached" screen |
| `data/Store.kt` | Settings, counters and Strict-mode pending changes (SharedPreferences) |
| `ui/HomeScreen.kt` | The app screen |

## Detection IDs need checking on a real phone

The view IDs in `Rules.kt` are best guesses from how Instagram and YouTube name their layouts, and they change with app updates. To verify them:

1. Turn on **Test tools → Screen inspector**.
2. Open Reels in Instagram (or Shorts in YouTube), then switch back to FeedCap.
3. "On Reels/Shorts" should say **yes**. If it says no, find the Reels/Shorts container in the "Visible view IDs" list and add it to `surfaceIds` in `Rules.kt`.
4. Open a DM chat in Instagram, then switch back. "On a DM chat" should say **yes**. If it says no, add a chat-screen ID to `dmIds`. Without it, reels from DMs are treated like normal Reels.
