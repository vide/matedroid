# Development

## Notes on development methodology

**This project was completely vibe-coded**, 100%, with the help of Claude Code and Opus 4.5. There it is, I said it, the shame is now gone.

I am/was pretty skeptical on LLM-generated code and this was born as an experiment to learn how it actually was to *vibe-code* something from scratch.

Turns out it's pretty awesome and easy to follow the happy path for a stock, modern Android app which just displays data consumed from a JSON REST API.
I'm a DevOps guy and I have zero mobile development skills, so achieving this can be considered pretty awesome in my book. But I'm pretty sure in the eyes of a skilled Kotlin Android developer, this code might induce different feelings.

At the moment, I completely depend on CC to maintain the app, but I would like to take this opportunity as an excuse to learn more about the Android development ecosystem, beside learning how to tame an LLM agent.

### Project Structure

```
matedroid/
├── app/src/main/java/com/matedroid/
│   ├── data/           # Data layer (API, repository, local storage)
│   ├── domain/         # Domain layer (models, use cases)
│   ├── ui/             # UI layer (screens, components, theme)
│   └── di/             # Dependency injection modules
├── gradle/             # Gradle wrapper and version catalog
├── util/               # Utility scripts
├── ASSETS.md           # Tesla car image asset documentation
└── PLAN.md             # Detailed implementation plan
```

### Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose with Material Design 3
- **Architecture**: MVVM + Clean Architecture
- **DI**: Hilt
- **Networking**: Retrofit + OkHttp + Moshi
- **Local Storage**: DataStore
- **Charts**: Vico
- **Maps**: osmdroid (OpenStreetMap)

### Loading spinners

Always use `MateDroidLoadingPlaceholder` (in `ui/components/MateDroidPulseSpinner.kt`) for any user-facing loading state, instead of `androidx.compose.material3.CircularProgressIndicator`. The placeholder is the branded MD-logotype pulse spinner with a built-in 200 ms debounce (sub-threshold loads stay silent) and a 45 % black scrim that dims the body — never skip the scrim, it's how the white M+D outline reads against any theme's surface color.

```kotlin
if (uiState.isLoading) {
    MateDroidLoadingPlaceholder(color = palette.accent)
} else {
    Content(...)
}
```

If the screen has a car palette in scope pass `palette.accent`; otherwise leave the default (Material primary). Keep small inline progress (button spinners, sub-section card loaders, weather card) on `CircularProgressIndicator` — the MD spinner is too visually heavy at that scale.

### Embedded maps

Never instantiate an osmdroid `MapView` inside a raw `AndroidView` — use `RouteMapView` (in `ui/components/RouteMapView.kt`). It owns the shared boilerplate: MAPNIK tile source, gesture handling (`MapGestureMode.TWO_FINGER_PAN` for maps embedded in scrollable pages, `INERT` for tap-through mini-maps, `FULL` for fullscreen), the optional dim/desaturate tile filter (`dimTiles`), an optional 120 ms deferred mount (`deferMount`) so the first frame paints before osmdroid's synchronous constructor runs, and the mandatory `onDetach()` on release. Screen-specific content goes through `onMapReady` (one-time setup: markers, polylines, zoom/center) and `update` (change-driven passes — guard expensive overlay rebuilds against unchanged inputs, see `TripDetailScreen`/`RegionsVisitedScreen`). The same file exports `mapDimFilter(isDark)` and `boundingBoxOf(points)` for padded route viewports.

### Settings screen architecture

Settings is a **category list → detail page** structure (the pattern Android and iOS system settings use), not a single scrolling form.

- `ui/screens/settings/SettingsScreen.kt` is the **hub**: one tappable card per section, each showing a live summary of its current value. Backed by `SettingsHubViewModel`, which reads the DataStore only — it deliberately does not depend on the repository or sync manager.
- `ui/screens/settings/SettingsSection.kt` is the **registry**: an enum of sections carrying the navigation id, title/summary string resources, icon, and a `debugOnly` flag. Order of the enum constants is the display order.
- `ui/screens/settings/sections/` holds one composable per detail page. They share `SettingsViewModel`, each instantiating its own copy via `hiltViewModel()`.
- `ui/screens/settings/SettingsComponents.kt` holds the shared building blocks: `SettingsSectionScaffold` (top bar + back arrow + snackbar + scrolling column), `SettingsCategoryCard`, `SettingsSwitchRow`, `SettingsLinkRow`, `SettingsGroupHeader`, `SettingsSpacer`.

**Save semantics**: only the Connection page has an explicit Save, because its URL and credentials need validating together. Every other preference writes through to the DataStore the moment it changes — do not add a save button to a new section.

**Navigation**: sections are real destinations (`Screen.SettingsSection(sectionId, onboarding)`), not local state, so the back stack, rotation and deep links work for free. `sectionId` is the stable `SettingsSection.id` string — renaming one breaks existing deep links.

**First run**: when no server is configured, `StartDestinationViewModel` starts directly on the Connection page with `onboarding = true`. That hides the back arrow, skips the hub entirely (the other sections are meaningless without a server), and makes Save continue to the dashboard instead of staying put.

**Notifications** deep-link into the Android per-channel settings rather than duplicating toggles in-app, so sound/importance/DND stay owned by the OS. A channel only exists once its first notification has fired, so the intent falls back to the app-level notification page.

#### Adding a new settings section

1. Add a constant to the `SettingsSection` enum with its id, title/summary string resources and icon.
2. Add the string resources to all six locale files (see [Adding a New String](#adding-a-new-string)).
3. Create the composable in `ui/screens/settings/sections/`, wrapping the content in `SettingsSectionScaffold`.
4. Add the branch to the `when` in `NavGraph.kt`'s `composable<Screen.SettingsSection>`.

#### State-of-charge warning levels

The warning triangle next to the battery percentage on the dashboard fires above a
**user-configurable level** (Settings → Display → "Warn above", default 90%, presets in
`domain/HighSocWarning.kt`). `HighSocWarning.DISABLED` (`0`) is the "Never" option and hides it
entirely — that is the LFP case (#310): those packs are meant to be charged to 100% regularly, so
the warning is wrong for them.

The chemistry is **not** auto-detected. `BatteryTypeHelper` infers LFP from `trim_badging == "50"`
for the DC power ceiling, but that badging is not reliable enough across markets and model years to
silence a battery-health warning on, so the level is a preference instead.

The predicate lives in `HighSocWarning.shouldWarn(batteryLevel, isCharging, threshold)` — a charging
car is never flagged, since it is on its way to the limit set in the car. The threshold reaches
`BatteryCard` through `DashboardUiState`, which `DashboardViewModel` keeps in sync with the DataStore
flow (not a one-shot read) so a change applies on the way back from Settings.

The low end is the mirror image: `domain/LowSocWarning.kt` (Settings → Display → "Warn below",
default 20%) decides when the battery percentage turns red, with an amber band covering the
`AMBER_MARGIN` (20) points just above — at the default that is red under 20% and amber under 40%,
exactly where both used to be hardcoded. `isLow()` / `isGettingLow()` are checked in that order, and
`DISABLED` leaves the percentage in the palette colour at any level.

Unlike the high warning, this one also drives the **widget**: it renders in its own process from
Glance state, so the threshold is read once per run by `CarWidgetUpdateWorker`, carried in
`CarWidgetDisplayData` and persisted to `CarWidget.LOW_SOC_THRESHOLD_KEY`. Both widget colour sites
(the percentage text and `buildProgressBarBitmap`) read it from there — the bar bitmap is cached, so
the threshold must stay in its `remember` keys or a changed setting won't repaint it.

### Localization (i18n)

The app supports multiple languages using Android's standard resource-based localization system. Currently supported languages:

- **English** (default) - `res/values/strings.xml`
- **Italian** - `res/values-it/strings.xml`
- **Spanish** - `res/values-es/strings.xml`
- **Catalan** - `res/values-ca/strings.xml`
- **German** - `res/values-de/strings.xml`
- **Chinese (Simplified)** - `res/values-zh/strings.xml`

#### Terminology: "drives" vs "trips"

These are two distinct concepts in the app and **must not collapse to the same word** in any
locale:

- **drive** — a single driving session (one A→B leg, the "Drives" list/screen).
- **trip** — a road trip: several drives joined by charge stops (the "Trips" list/screen).

In English they're naturally distinct. In Romance languages "trip" maps to the obvious cognate,
so "drive" needs a *different* word — otherwise both render identically (e.g. both as "Viajes")
and the navigation/stats become ambiguous. Established mapping:

| Concept | en | it | es | ca | de | zh |
|---------|-----|-----|-----|-----|-----|-----|
| drive | drive | tragitto | trayecto | trajecte | Fahrt | 行程 |
| trip | trip | viaggio | viaje | viatge | Trip | 旅程 |

When adding a new `drive_*`/`*_drive*` string, translate "drive" with the drive-column term, and
reserve the trip-column term for `trip_*`/`trips_*` strings — never let the two collapse to the
same word in a locale, or the Drives/Trips navigation and stats become ambiguous.

#### Hiding short drives / charges

The "Show short drives / charges" setting (`showShortDrivesCharges`, default off) hides trivial
entries — by default drives under 1 min or 1 km, and charges of 0.1 kWh or less — from **list-like
surfaces** while still counting them in totals, averages and statistics.

This filter is **purely presentational**. Short entries are always fetched, always stored, and
always counted; nothing in the data layer filters on these thresholds, so changing one is a
re-render and never needs a resync.

The rule lives in **one place**: `domain/ShortEntryFilter.kt`. It exposes the thresholds plus
`isSignificant()` helpers for every drive/charge model (`DriveData`, `ChargeData`, `DriveSummary`,
`ChargeSummary`). **Any screen that renders individual drives/charges must filter through these
helpers** — never re-implement the thresholds at a call site. This is what keeps the behaviour
consistent (it previously diverged: the trip timeline shipped without the filter and showed short
legs). Current call sites: `DrivesViewModel`, `ChargesViewModel`, `TripTimelineBuilder` and the
trip leg list / counts in `TripsScreen` + `TripDetailScreen`. Add a new model? Add its
`isSignificant()` helper in `ShortEntryFilter.kt`.

**The thresholds are user-configurable** (Settings → Display), chosen from presets defined in
`ShortEntryFilter.*_PRESETS`. Like `UnitSystem`, the object is a process-wide mirror of the stored
preference: restored at app start by `MateDroidApp` and written through by `SettingsViewModel` the
moment the user picks a value. That mirror is what lets the `isSignificant()` helpers stay
zero-argument — no call site has to thread thresholds through its own state. A threshold of `0`
means "no minimum" for that dimension.

The distance threshold is compared **in the user's display unit, not km**. TeslamateAPI
pre-converts every distance it returns and the presets are labelled in the active unit, so both
sides of the comparison already match — do not scale it through `UnitSystem.thresholdKmToUserUnits`
(that helper remains for genuinely km-defined constants such as the trip-detection minimum).

#### Adding/Modifying Translations

1. **All user-visible strings must be in string resources** - never hardcode text in Kotlin files
2. **String naming convention**: Use `snake_case` (e.g., `settings_title`, `drive_history`)
3. **Add context comments** for translators above each string:
   ```xml
   <!-- Dialog title when user has multiple vehicles -->
   <string name="select_vehicle">Select Vehicle</string>
   ```

#### Adding a New String

1. Add the English string to `res/values/strings.xml`:
   ```xml
   <!-- Description of what this string is for -->
   <string name="new_feature_label">Feature Name</string>
   ```

2. Add translations to all locale files:
   - `res/values-it/strings.xml`
   - `res/values-es/strings.xml`
   - `res/values-ca/strings.xml`

3. Use in Kotlin code:
   ```kotlin
   import androidx.compose.ui.res.stringResource
   import com.matedroid.R

   Text(text = stringResource(R.string.new_feature_label))
   ```

#### Format Strings

For strings with dynamic values, use placeholders:
```xml
<!-- %d is the percentage -->
<string name="charge_limit_format">Limit: %d%%</string>

<!-- %1$s is the date, %2$d is the number of days -->
<string name="avg_year_message">Since %1$s (%2$d days ago)</string>
```

Usage:
```kotlin
stringResource(R.string.charge_limit_format, chargeLimit)
stringResource(R.string.avg_year_message, formattedDate, dayCount)
```

#### Adding a New Language

1. Create a new folder: `res/values-{language_code}/`
2. Copy `res/values/strings.xml` to the new folder
3. Translate all strings, keeping the same `name` attributes
4. Android will automatically use the correct language based on device settings

#### Testing Translations

Change your device/emulator language in Settings > System > Languages to test different locales.

### Utility Scripts

#### `util/fetch_tesla_assets.py`

Python script to download Tesla car 3D renders from Tesla's compositor service. Requires [uv](https://github.com/astral-sh/uv) for dependency management.

```bash
# Download all car images (Model 3 & Y, various colors/wheels)
./util/fetch_tesla_assets.py

# Preview what would be downloaded
./util/fetch_tesla_assets.py --dry-run

# Custom output directory
./util/fetch_tesla_assets.py --output-dir /path/to/assets
```

See [ASSETS.md](ASSETS.md) for detailed documentation on Tesla compositor APIs, color/wheel code mappings, and troubleshooting.

### Mock Server

The `mockserver/` directory contains a proxy server that lets you test the app with different Tesla car configurations without owning multiple vehicles. It forwards requests to a real Teslamate API instance while injecting mock car information (model, color, trim, wheels).

#### Requirements

- Python 3.11+
- [uv](https://github.com/astral-sh/uv) (for automatic dependency management)

#### Usage

```bash
# List available car profiles
./mockserver/server.py --list-cars

# Start the mock server (proxies to upstream and injects car overrides)
./mockserver/server.py --upstream http://your-teslamate-api:4000 --car modely_juniper_grey_19

# With custom port
./mockserver/server.py -u http://localhost:4000 -c model3_highland_white_18 -p 5000
```

Then configure the app to connect to `http://localhost:4001` (or your chosen port) instead of the real Teslamate API.

#### Command-line Options

| Option | Description |
|--------|-------------|
| `-u, --upstream` | Upstream Teslamate API URL (required) |
| `-c, --car` | Car profile name from cars.json (required) |
| `-p, --port` | Port to run mock server on (default: 4001) |
| `--host` | Host to bind to (default: 127.0.0.1) |
| `--cars-file` | Path to cars config JSON (default: cars.json) |
| `--list-cars` | List available car profiles and exit |

#### Car Profiles

Car profiles are defined in `mockserver/cars.json`. Each profile specifies overrides that get deep-merged into the API response for `/api/v1/cars/*` endpoints:

```json
{
  "profile_name": {
    "car_details": {
      "model": "Y",
      "trim_badging": "74"
    },
    "car_exterior": {
      "exterior_color": "StealthGrey",
      "spoiler_type": "None",
      "wheel_type": "Crossflow19"
    }
  }
}
```

The naming convention for profiles is: `{model}_{generation}_{color}_{wheels}`

Pre-configured profiles include:
- **Model 3 Legacy**: `model3_legacy_white_18`, `model3_legacy_black_18`, etc.
- **Model 3 Highland**: `model3_highland_white_18`, `model3_highland_grey`, `model3_highland_perf_red`, etc.
- **Model Y Legacy**: `modely_legacy_white_gemini`, `modely_legacy_blue_induction`, etc.
- **Model Y Juniper**: `modely_juniper_white_19`, `modely_juniper_grey_20`, `modely_juniper_perf_red`, etc.
- **Model S/X**: `models_plaid_white`, `modelx_plaid_blue`, `models_100d_silver`, etc.
- **Cybertruck**: `cybertruck_foundation`

#### Known Values Reference

**Models**: `3`, `Y`, `S`, `X`, `Cybertruck`

**Trim badging**:
- Model 3 Highland: `LRAWD`, `P` (Performance)
- Model 3 Legacy: `74D`, `P74D`
- Model Y Juniper: `50` (RWD), `74` (AWD), `P74D` (Performance)
- Model Y Legacy: `74D`, `P74D`
- Model S/X: `100D`, `Plaid`

**Exterior colors**: `PearlWhite`, `StealthGrey`, `DeepBlue`, `UltraRed`, `RedMulticoat`, `MidnightSilver`, `SolidBlack`, `Quicksilver`, `BlackDiamond`, `ObsidianBlack`, `StainlessSteel`

**Wheel types**:
- Model 3 Highland: `Glider18`, `Pinwheel18CapKit`, `Photon18`, `Performance20`
- Model 3 Legacy: `Pinwheel18`, `Sport19`, `Performance20`
- Model Y Juniper: `Photon18`, `Crossflow19`, `Helix20`, `Uberturbine21`
- Model Y Legacy: `Gemini19`, `Induction20`, `Uberturbine21`
- Model S: `Tempest19`
- Model X: `Turbine22`, `Cyberstream20`
- Cybertruck: `Cybertruck20`

#### How It Works

1. The server listens on the configured port (default 4001)
2. All incoming requests are proxied to the upstream Teslamate API
3. For `/api/v1/cars/*` endpoints, the response JSON is modified to include the overrides from the selected car profile (deep-merged into `car_details` and `car_exterior`)
4. Other endpoints are passed through unchanged

### Debug API Endpoint Switching

In debug builds, the Teslamate API endpoint can be changed via ADB broadcast without opening the app. This is useful for switching between the real server and the mock server during testing.

```bash
# Switch to mock server
adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
  -a com.matedroid.SET_ENDPOINT --es url "http://192.168.x.x:4001"

# Switch back to your real server (from .env)
adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
  -a com.matedroid.SET_ENDPOINT --es url "$TESLAMATE_API_URL"  # from .env
```

The `-n` flag (explicit component) is required on Android 14+ since implicit broadcasts to manifest receivers are restricted. The change takes effect immediately for the next API call (no app restart needed). The receiver is guarded by `BuildConfig.DEBUG` and is silently ignored in release builds.

**Typical testing workflow:**

1. Start the mock server: `./mockserver/server.py -u http://your-api:4000 -c modely_juniper_grey_19`
2. Switch the app to mock: `adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver -a com.matedroid.SET_ENDPOINT --es url "http://<your-ip>:4001"`
3. Test your changes
4. Switch back: `adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver -a com.matedroid.SET_ENDPOINT --es url "$TESLAMATE_API_URL"`

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

### Screenshots

Store-listing and README screenshots are generated automatically via Fastlane
[screengrab](https://docs.fastlane.tools/actions/screengrab/). See
[`SCREENSHOTS.md`](SCREENSHOTS.md) for the workflow and how to add a new
screen. The driver is `./scripts/take-screenshots.sh`.

### Releasing

Releases are automated via GitHub Actions. When a release is published, the workflow builds the APK and attaches it to the release, and deploys to Google Play.

The recommended way to create releases is using the `/release` skill in Claude Code, which automates:
1. Version bumping in `app/build.gradle.kts` (versionCode and versionName)
2. Updating `CHANGELOG.md` with the release date
3. Creating Fastlane changelogs in all supported languages
4. Committing, tagging, and pushing
5. Creating the GitHub release

#### Fastlane Metadata

The Play Store listing is managed through Fastlane metadata in `fastlane/metadata/android/`:

```
fastlane/metadata/android/
├── en-US/           # English (default)
│   ├── title.txt
│   ├── short_description.txt
│   ├── full_description.txt
│   └── changelogs/
│       └── {versionCode}.txt
├── it-IT/           # Italian
├── es-ES/           # Spanish
├── ca-ES/           # Catalan
├── de-DE/           # German
└── zh-CN/           # Chinese (Simplified)
```

Each release requires a changelog file named `{versionCode}.txt` (e.g., `24.txt`) in all locale directories. The `/release` skill automatically creates translated changelogs for all supported languages.

#### Version Code

The `versionCode` is generated from Unix epoch / 10 via `scripts/bump-version-code.sh`. This produces a monotonically increasing integer with 10-second granularity, shared by both stable and nightly alpha releases. The scheme is safe until ~2650 (well within int32 range).

```bash
# Preview what the next versionCode would be
./scripts/bump-version-code.sh --dry-run

# Update build.gradle.kts with a new versionCode
./scripts/bump-version-code.sh
```

#### Nightly Alpha Builds

A GitHub Actions workflow runs daily at 04:00 UTC. If there are new commits on `main` since the last 24h, it builds and uploads an alpha AAB to the Google Play closed testing (alpha) track with version name `MAJOR.MINOR-alpha-YYYYMMDD`.

#### Manual Release

If releasing manually:

```bash
# 1. Bump versionCode
./scripts/bump-version-code.sh

# 2. Update versionName in app/build.gradle.kts
# 3. Update CHANGELOG.md with release notes
# 4. Create changelogs in fastlane/metadata/android/{locale}/changelogs/{versionCode}.txt
# 5. Commit and push

# 6. Create a release with GitHub CLI
gh release create v1.2.0 --generate-notes

# Or create a draft release to edit notes first
gh release create v1.2.0 --generate-notes --draft
```

#### Signing Configuration (Optional)

For release signing with a custom keystore, set these repository secrets:
- `KEYSTORE_BASE64`: Base64-encoded keystore file (`base64 -w0 your.keystore`)
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password

Without secrets, the APK is signed with a debug keystore (fine for sideloading, not for Play Store).

### Development Workflow

1. Start your Android emulator or connect a device
2. Build and install: `make install`
3. View logs: `adb logcat | grep -i matedroid`

#### Makefile Targets

| Target         | Description                                     |
|----------------|-------------------------------------------------|
| `make build`   | Build debug APK                                 |
| `make install` | Build and install debug APK on connected device |
| `make run`     | Build, install, and launch the app              |
| `make clean`   | Clean build artifacts                           |
| `make test`    | Run unit tests                                  |

Or use Android Studio:
1. Open the project folder
2. Wait for Gradle sync
3. Click Run (green play button)

## Configuration

On first launch, you'll be prompted to configure your TeslamateApi connection:

1. **Server URL**: Your TeslamateApi instance URL (e.g., `https://teslamate-api.example.com`)
2. **API Token**: (Optional) If your instance requires authentication
