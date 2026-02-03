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

### Localization (i18n)

The app supports multiple languages using Android's standard resource-based localization system. Currently supported languages:

- **English** (default) - `res/values/strings.xml`
- **Italian** - `res/values-it/strings.xml`
- **Spanish** - `res/values-es/strings.xml`
- **Catalan** - `res/values-ca/strings.xml`

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

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

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
└── ca-ES/           # Catalan
```

Each release requires a changelog file named `{versionCode}.txt` (e.g., `24.txt`) in all locale directories. The `/release` skill automatically creates translated changelogs for all supported languages.

#### Manual Release

If releasing manually:

```bash
# 1. Update version in app/build.gradle.kts (versionCode and versionName)
# 2. Update CHANGELOG.md with release notes
# 3. Create changelogs in fastlane/metadata/android/{locale}/changelogs/{versionCode}.txt
# 4. Commit and push

# 5. Create a release with GitHub CLI
gh release create v0.5.0 --generate-notes

# Or create a draft release to edit notes first
gh release create v0.5.0 --generate-notes --draft
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
