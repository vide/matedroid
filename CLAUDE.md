# RULES TO FOLLOW FOR THIS PROJECT

## Development style

* create a new branch and associated Github pull request for each new feature or set of bugfixes.
* automatically commit to git any meaningful change, with a short but clear commit message.
* follow the "Keep a changelog" and Semantic versioning best practices but DO NOT release major versions by yourself. Ask me if you think they might be released.
* create a new release only when specifically told.
* keep an always up-to-date README.md, but don't change its writing style.
* always update `docs/DEVELOPMENT.md` and `docs/ASSETS.md` if anything related to those parts changes.

## Charts and graphs

### Histograms

* Every histogram bar MUST be tappable, and when tapped it will show a tooltip with its value
* The bars will be in the palette accent color

### Line graphs

* The line graph MUST have 4 labels on the Y axis: 1st quarter, half, 3rd quarter, end.
* It MUST have 5 labels on the X axis (which is usually time): start, 1st quarter, half, 3rd quarter, end.
* Tapping on one point of the graph will show a tooltip with the value of the Y axis.

## F-Droid and Fastlane

* The app is published on F-Droid, which builds from source.
* Fastlane metadata is stored in `fastlane/metadata/android/` with locale subdirectories:
  - `en-US/` (English), `it-IT/` (Italian), `es-ES/` (Spanish), `ca-ES/` (Catalan)
  - Each locale contains: `title.txt`, `short_description.txt`, `full_description.txt`, and `changelogs/`
  - `changelogs/<versionCode>.txt` for each release (e.g., `24.txt` for versionCode 24)
  - Images are only in `en-US/images/` (icon.png, phoneScreenshots/)
* When releasing a new version:
  - Create changelog files in ALL locale directories matching the versionCode
  - The `/release` skill handles this automatically, including translations
* The F-Droid build recipe is stored in `fdroid/com.matedroid.yml` (reference copy; actual recipe lives in fdroiddata repo).

## Localization

* NEVER hardcode user-visible strings in Kotlin code. Always use `stringResource(R.string.xxx)`.
* When adding new UI text, add the string to all 4 locale files:
  - `res/values/strings.xml` (English, default)
  - `res/values-it/strings.xml` (Italian)
  - `res/values-es/strings.xml` (Spanish)
  - `res/values-ca/strings.xml` (Catalan)
* Use snake_case for string names (e.g., `settings_title`, `drive_history`).
* Add XML comments above strings to provide context for translators.
* Technical terms like AC, DC, kW, kWh should NOT be translated.
* Always run `./gradlew lintDebug` before committing to catch hardcoded strings.

## Gotchas and notes

* Always check the local instance of Teslamate API and its documentation to know what's the returned JSON format.
* The TeslamateApi's parseDateParam function only accepts:
  - RFC3339 format: 2024-12-07T00:00:00Z
  - DateTime format: 2024-12-07 00:00:00
* **TeslamateAPI pre-converts ALL values to the user's preferred unit system before returning them.**
  Distance fields (odometer, range, trip distance) are already in km or mi. Speed is already in km/h or mph.
  Temperature is already in °C or °F. Efficiency is already in Wh/km or Wh/mi.
  Pressure is already in bar or psi.
  → `UnitFormatter` must NEVER apply conversion math — it only attaches the correct unit label.
  → Never multiply by 0.621371, 1.60934, or apply °C→°F formulas on values coming from the API.

