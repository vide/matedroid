---
name: release
description: Create a new release. Bumps version, updates changelog, creates fastlane changelog, commits, tags, and pushes.
allowed-tools: Read, Edit, Write, Bash, Glob
---

# Release Skill

Create a new release for MateDroid.

## Pre-flight Checks

1. Ensure you're on the `main` branch with no uncommitted changes
2. Verify the `[Unreleased]` section in `CHANGELOG.md` has content to release
3. **Beta-first check (MANDATORY for stable minor/major releases)**: if the requested
   release is a stable minor or major version (e.g. `1.11.0`, `2.0.0` — anything that is
   not a patch and not itself a `-beta`), check whether at least one beta of that version
   was released first: `git tag -l "vX.Y.0-beta*"`. If no beta tag exists, STOP and ask
   the user with AskUserQuestion whether they are sure they want to release straight to
   stable, offering "Release vX.Y.0-betaN first (Recommended)" as the first option and
   "Go straight to stable vX.Y.0" as the second. Only proceed to stable after they
   explicitly pick the stable option — even if they named the stable version in the
   request. Patch releases (`X.Y.1`, `X.Y.2`, …) and beta releases skip this check.

## Release Process

### 1. Determine Version

Ask the user what type of release:
- **patch** (0.10.0 → 0.10.1): Bug fixes only
- **minor** (0.10.0 → 0.11.0): New features, backwards compatible
- **major** (0.10.0 → 1.0.0): Breaking changes (requires explicit user confirmation)

### 2. Update Version in build.gradle.kts

Edit `app/build.gradle.kts`:
- Run `./scripts/bump-version-code.sh` to generate a new monotonic `versionCode` (epoch/10). The script updates `build.gradle.kts` in-place and prints the new code to stdout.
- Update `versionName` to the new version

### 3. Update CHANGELOG.md

1. Change `## [Unreleased]` to `## [X.Y.Z] - YYYY-MM-DD` (today's date)
2. Add a new empty `## [Unreleased]` section above it
3. Add the new version link at the bottom:
   ```
   [X.Y.Z]: https://github.com/vide/matedroid/compare/vPREVIOUS...vX.Y.Z
   ```
4. Update the `[Unreleased]` link to compare from the new version

### 4. Create Fastlane Changelog (English)

Create `fastlane/metadata/android/en-US/changelogs/{versionCode}.txt` with the release notes.

**Important — stable vs beta scope:**
- **Beta release** (e.g. `1.1.0-beta2`): the changelog covers only the changes since the previous release (beta or stable).
- **Stable (production) release** (e.g. `1.1.0`): the changelog must cover **all changes since the previous stable release** (e.g. `1.0.0`), NOT just what changed since the last beta. Collect entries from every intermediate beta and the `[Unreleased]` section, then write a single consolidated changelog. This is what end-users on the production track will see — they never saw the beta changelogs.
  - **Stable changelog should only include**: new features, behavioural changes, and bugfixes relative to the previous *stable* release. Do NOT mention fixes for bugs that were introduced during the beta cycle itself — those never existed for stable users. For example, if beta1 introduced a feature with a broken tooltip and beta2 fixed it, the stable changelog should describe the feature as working correctly, without mentioning the intermediate fix.

Format (max 500 chars for Play Store):
```
More human readable and engaging new features and major fixes overview. Do NOT mention external contributors here.

Added:
- Feature 1
- Feature 2

Changed:
- Change 1

Fixed:
- Fix 1
```

Keep it concise - this appears in Play Store and F-Droid. Make the opening text engaging without it being too verbose or showy.

Remember, the fastlane changelog MUST BE less than 500 characters long.

### 5. Translate Changelogs (Automatic)

Automatically translate the English changelog and write the translations to:
- `fastlane/metadata/android/it-IT/changelogs/{versionCode}.txt` (Italian)
- `fastlane/metadata/android/es-ES/changelogs/{versionCode}.txt` (Spanish)
- `fastlane/metadata/android/ca-ES/changelogs/{versionCode}.txt` (Catalan)
- `fastlane/metadata/android/de-DE/changelogs/{versionCode}.txt` (German)
- `fastlane/metadata/android/zh-CN/changelogs/{versionCode}.txt` (Chinese Simplified)

Do this immediately after creating the English changelog - no user interaction needed.

Translation guidelines:
- Keep the same structure and format as the English version
- Do NOT translate technical terms: AC, DC, kW, kWh, API, etc.
- Keep proper nouns unchanged: MateDroid, Teslamate, Tesla, GitHub, etc.
- Maintain the same tone: engaging but concise
- Each translation must also respect the 500 character limit
- For Chinese: keep all unit symbols in Latin alphabet (kWh, kW, km/h, °C, bar) per GB 3100-1993 standard and Tesla China convention. Do NOT use Chinese character equivalents (千瓦时, 公里 etc.)

All the changelogs in fastlane, for any language, MUST BE LESS THAN 500 characters. ALWAYS check with `wc` how long they are.

### 6. Commit and Tag

```bash
git add -A
git commit -m "chore: release vX.Y.Z"
git tag -a vX.Y.Z -m "Release X.Y.Z"
```

### 7. Push

```bash
git push origin main
git push origin vX.Y.Z
```

### 8. Create GitHub Release

Use `gh release create vX.Y.Z --title "vX.Y.Z" --notes-file -` with the changelog content as the one in Fastlane.
Mention external contributors, highlighting new ones and giving credit. 

The GitHub Actions workflow will automatically:
- Build APK and AAB
- Upload APKs to GitHub release
- Deploy to Google Play (beta track for `-beta` releases, production track for stable releases)
