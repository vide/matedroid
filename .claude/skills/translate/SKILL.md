---
name: translate
description: Add a new translatable string to all locale files (English, Italian, Spanish, Catalan, German, Chinese). Use when adding user-visible text to the app.
allowed-tools: Read, Edit
---

# Translate Skill

Add a new string resource to all 6 locale files.

## String Resource Files

| Locale  | File                                          |
|---------|-----------------------------------------------|
| English | `app/src/main/res/values/strings.xml`         |
| Italian | `app/src/main/res/values-it/strings.xml`      |
| Spanish | `app/src/main/res/values-es/strings.xml`      |
| Catalan | `app/src/main/res/values-ca/strings.xml`      |
| German  | `app/src/main/res/values-de/strings.xml`      |
| Chinese | `app/src/main/res/values-zh/strings.xml`      |

## Process

1. Ask the user for:
   - The string name (use `snake_case`, e.g., `drive_details_title`)
   - The English text
   - Context for translators (optional but recommended)

2. Generate translations for Italian, Spanish, Catalan, German and Chinese (Simplified)

3. Add to all 6 files with an XML comment for context:
   ```xml
   <!-- Context: Shown as the title of the drive details screen -->
   <string name="drive_details_title">Drive Details</string>
   ```

## Guidelines

- Use `snake_case` for string names (e.g., `settings_title`, `drive_history`)
- Add XML comments above strings to provide context for translators
- Technical terms like AC, DC, kW, kWh should NOT be translated
- Format specifiers (`%s`, `%d`, `%1$s`) must be preserved in translations
- Keep translations natural - don't be overly literal
- For Chinese: keep all unit symbols in Latin alphabet (kWh, kW, km/h, °C, bar) per Chinese national standard GB 3100-1993 and Tesla China convention. Do NOT use Chinese character equivalents (千瓦时, 公里, etc.)
- For Chinese: plurals `one` and `other` should be identical (Chinese does not inflect for number)

## String with Parameters

For strings with parameters, use positional format specifiers:
```xml
<string name="distance_km">%1$d km away</string>
```

In Kotlin:
```kotlin
stringResource(R.string.distance_km, distance)
```

## Plurals

For quantity strings, use plurals:
```xml
<plurals name="days_count">
    <item quantity="one">%d day</item>
    <item quantity="other">%d days</item>
</plurals>
```

## After Adding Strings

Remind the user to use `stringResource(R.string.xxx)` in Compose code:
```kotlin
Text(stringResource(R.string.drive_details_title))
```
