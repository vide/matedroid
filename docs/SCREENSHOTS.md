# Screenshots

The app's Play Store / F-Droid / README screenshots are generated automatically
via Fastlane [screengrab](https://docs.fastlane.tools/actions/screengrab/) and
written to `fastlane/metadata/android/<locale>/images/phoneScreenshots/` — the
same paths F-Droid and Play Store both already read from.

## Status — PoC

The infrastructure is in place but only **one screen is wired up so far**: the
main dashboard. The remaining 12 are stubs and need their navigation logic
added in `app/src/androidTest/java/com/matedroid/screenshots/ScreenshotsTest.kt`.

Target set:

| # | name (PNG basename)     | notes                                            |
|---|-------------------------|--------------------------------------------------|
| 1 | `01-main-dashboard`     | ✅ wired up                                       |
| 2 | `02-current-charge`     | only renders while mock has `--charging` active  |
| 3 | `03-battery-health`     | reachable from car stats / dashboard             |
| 4 | `04-mileage`            | reachable from dashboard                         |
| 5 | `05-software-versions`  | reachable from dashboard / settings              |
| 6 | `06-drives`             | reachable from dashboard                         |
| 7 | `07-drive-details`      | tap into the first row of drives                 |
| 8 | `08-charges`            | reachable from dashboard                         |
| 9 | `09-charge-details`     | tap into the first row of charges                |
|10 | `10-trips`              | reachable from dashboard                         |
|11 | `11-trip-detail`        | tap into the first row of trips                  |
|12 | `12-stats-for-nerds`    | reachable from dashboard                         |
|13 | `13-visited-countries`  | reachable from stats                             |

## Running it

Prereqs (one-off):

- A device or emulator visible to `adb devices` (only one online device).
  Emulator works but is slow on older laptops; a physical phone over ADB-WiFi
  is much faster.
- Docker installed. Fastlane runs inside a container (built from
  `docker/screengrab/Dockerfile`) so the host doesn't need
  ruby / bundler / fastlane installed.

Then:

```
./scripts/screengrab.sh [car_profile]
```

The script:

1. Builds `app-debug.apk` + `app-debug-androidTest.apk` on the host.
2. Builds (or reuses cached) the `matedroid-screengrab` Docker image — Ruby
   slim base + `android-tools-adb` + `bundle install` of the project's
   `Gemfile`.
3. Starts the Teslamate mock (`mockserver/server.py`) with the chosen car profile
   plus an active DC session, so the live-charge / charges / drives screens have
   real-shaped data to render.
4. Points the running app at the mock via the `DebugEndpointReceiver` ADB broadcast.
5. Runs `fastlane screengrab` inside the container. The container shares the
   host's network (`--network host`), so the device the host is already paired
   with via ADB is visible inside too — no port-forwarding gymnastics. The
   project directory is bind-mounted at `/workspace`, and `--user` matches the
   host UID so the resulting PNGs land with the right ownership in
   `fastlane/metadata/android/en-US/images/phoneScreenshots/`.
6. Tears the mock down and points the app back at the real Teslamate API
   (read from `.env`).

`car_profile` defaults to `white_juniper_performance`. Use any name from
`mockserver/cars.json` — see `--list-cars` in the `/mock` skill.

### Legacy `take-screenshots.sh`

The older `scripts/take-screenshots.sh` (adb screencap + imagemagick crops,
driven by intent extras like `EXTRA_NAVIGATE_TO`) is the script that produced
the current `docs/screenshots/*.jpg` set referenced from the README. It still
works and is left in place until the Fastlane flow above covers all 13 target
screens.

## Adding a new screen

Edit `ScreenshotsTest.kt`. Either add a new `@Test` method (one screen each — more
robust, slower) or extend `captureMainDashboard` into a single test that walks
the app and captures along the way (faster, all-or-nothing).

Pattern for a new screen:

```kotlin
// 1. Drive Compose to the target screen.
composeTestRule.onNodeWithText("Drives").performClick()

// 2. Wait for a stable anchor on the destination screen.
composeTestRule.waitUntil(timeoutMillis = 15_000) {
    composeTestRule.onAllNodesWithText("Total drives", substring = true)
        .fetchSemanticsNodes().isNotEmpty()
}

// 3. Capture.
Screengrab.screenshot("06-drives")
```

Use `contentDescription` over hard-coded text where possible — it survives
copy edits and locale changes better. The `LocaleTestRule` on the test class
handles the locale switching for multi-locale runs (currently only `en-US`).

## Locales

`fastlane/Screengrabfile` currently lists only `en-US`. To add the other
locales the app supports, expand it to:

```ruby
locales(['en-US', 'it-IT', 'es-ES', 'ca-ES', 'zh-CN'])
```

Screengrab will run the test once per locale, switching the device language
between runs. Expect ~5× the runtime.

## Pixelating / redacting

Mock data is fake at the source (`mockserver/cars.json` + replayed upstream
data) so there is normally nothing to redact. If a future screenshot needs
masking — e.g., a debug session capturing real data — write a small
post-processor with Pillow / ImageMagick that blurs fixed rectangles per
screen and run it after `screengrab`.
