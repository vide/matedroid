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

### Running Tests

```bash
# Unit tests
./gradlew test

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest
```

### Releasing

Releases are automated via GitHub Actions. When a release is published, the workflow builds the APK and attaches it to the release.

```bash
# 1. Update version in app/build.gradle.kts (versionCode and versionName)
# 2. Update CHANGELOG.md with release notes
# 3. Commit and push

# 4. Create a release with GitHub CLI
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
