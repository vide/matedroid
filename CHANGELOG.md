# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Trip grouping**: Automatically detects highway/road trips (drives connected by DC charging stops) and groups them into unified trips. Dedicated Trips screen with list and detail view including route map, summary stats, and per-leg breakdown.
- **Sentry alert history**: Persistent on-device log of sentry alert events. Tap the red sentry dot on the dashboard or widget to view current session alerts and past alerts grouped by day. Sentry notifications also deep-link to the history screen.

## [1.3.0] - 2026-03-30

### Added
- **Where was I that day?**: New feature on the dashboard to look up the car's position and activity at any past date and time. Shows map, location breadcrumb (country/region/city), car state (driving/charging/parked), state-specific details, and weather. Tapping a card navigates to the corresponding detail screen. When the car was parked/sleeping all day, it finds the last known location and shows how long the car has been parked with a "since" timestamp.
- **Line chart visual revamp**: Smooth cubic Bezier curves, gradient fills, dashed grid lines, vertical crosshair, glowing indicators, animated entrance, and theme-aware tooltips.
- **Battery heater overlay**: Grafana-style orange annotation bands on drive detail Power and Battery charts highlighting battery pre-heating periods.
- **Speed distribution histogram**: Drive details now include a speed histogram showing the percentage of time spent in each speed bucket (10 km/h or 5 mph).
- **Chinese language support**: Added Simplified Chinese (简体中文) as the 5th supported language.

### Changed
- **Map markers**: Replaced default OSMDroid markers with custom pin-needle markers (accent-colored circle head with thin needle) across all map screens (dashboard, Where was I?, charge details).
- **HTTP requests**: All HTTP requests now include a `MateDroid/<version>` User-Agent header.

## [1.2.3] - 2026-03-08

### Added
- **Notifications**: One-time permission dialog at startup explaining notification features (sentry events, tyre pressure, charging status) before requesting Android 13+ POST_NOTIFICATIONS permission

### Fixed
- **Home screen widget**: Fixed aspect-ratio distortion on Nova Launcher and other third-party launchers that report widget dimensions inconsistently. Status bar icons and temperatures are now rendered as Glance composables instead of being drawn into the background bitmap.

## [1.2.2] - 2026-03-07

### Fixed
- **Units system-wide**: All screens and the home screen widget now correctly respect the unit system configured in TeslamateAPI (metric vs imperial). Distances, speeds, efficiency, and temperatures are now consistently formatted across Drives, Stats, Mileage, Battery, Countries Visited, Regions Visited screens, and the widget range display (#176)
- **Home screen widget**: Location text (geofence name or address) no longer shifts upward when the car is charging. When idle, it stays right-aligned on the same line as the SoC percentage; when charging, it shares the bottom row with the charging details

## [1.2.1] - 2026-03-07

### Added
- **Home screen widget**: On 2×2 and 3×2 sizes, the widget now shows the car's current location (geofence name, or reverse-geocoded address, or raw coordinates as fallback) in the lower right corner. For privacy and security, this is intentionally shown only on the home screen — the lock screen widget retains its previous layout without location data.
- **Home screen widget**: Remaining range is now shown in the top center of 2×2 and 3×2 widgets, on the same line as the status icons, replacing the charge limit label (which is still visible as a zone on the battery bar).

## [1.2.0] - 2026-03-06

### Added
- **Sentry event detection**: Detects sentry mode alert events via polling when sentry mode is active. Fires a heads-up notification on a dedicated "Sentry Alerts" channel and shows a running event counter next to the sentry red dot on both the dashboard and the home screen widget. Counter resets when sentry mode turns off.
- **Home screen widget**: Battery info widget for the Android home screen. Shows battery level, range, state, temperature, and charging details (power, voltage, current, phases, energy added, time to full) for any configured car. Features a dimmed car image background with a static glow effect when charging. One widget per car can be configured; updates every 15 minutes via WorkManager.
- **Dashboard**: Swipe left/right on the car image to switch between vehicles when multiple cars are configured; dot indicators below the image show which car is selected

### Fixed
- **Home screen widget**: Tapping a widget now opens the app and selects the correct car instead of always showing the first car

## [1.2.0-beta1] - 2026-02-26

### Added
- **Sentry event detection**: Detects sentry mode alert events (center display state "6") via fast 10-second polling when sentry mode is active. Fires a heads-up notification on a dedicated "Sentry Alerts" channel and shows a running event counter next to the sentry red dot on both the dashboard and the home screen widget. Counter resets when sentry mode turns off.
- **Home screen widget**: Battery info widget for the Android home screen. Shows battery level, range, state, temperature, and charging details (power, voltage, current, phases, energy added, time to full) for any configured car. Features a dimmed car image background with a static glow effect when charging. One widget per car can be configured; updates every 15 minutes via WorkManager.
- **Dashboard**: Swipe left/right on the car image to switch between vehicles when multiple cars are configured; dot indicators below the image show which car is selected

## [1.1.0] - 2026-02-20

### Changed
- **Live Charge Screen**: Current SoC is now the most prominent element — displayed as a large hero number at the top of the header card, with a 3-column start/current/target row and progress bar below. Energy and power stats are compacted into a single row.

### Fixed
- **Notifications**: Live charge navigation and notification deep-link are now hidden when TeslaMate API < 1.24 (endpoint unavailable)
- **Notifications**: Charging notification now navigates to live charge screen when tapped (fixes regression in fallback path when foreground service cannot start from background)
- **Notifications**: Charging notification worker is now properly rescheduled after device reboot
- **Notifications**: Charging notification correctly navigates to main screen when live charge API is unavailable
- **Live Charge Screen**: Voltage and Current lines in the V/A chart now use distinct colors (amber and blue)
- **Live Charge Screen**: Screen now closes automatically when charging stops
- **Dashboard**: Chevron arrow on charging power gauge is now hidden when live charge screen is unavailable
- **Stats for Nerds**: Fixed missing values (avg cost/kWh, coldest temperatures, cabin temperatures) when filtering by year (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper), fixes #150)

## [1.1.0-beta3] - 2026-02-12

### Fixed
- **Notifications**: Charging notification now navigates to live charge screen when tapped (fixes regression in fallback path when foreground service cannot start from background)
- **Notifications**: Charging notification worker is now properly rescheduled after device reboot

## [1.1.0-beta2] - 2026-02-11

### Fixed
- **Dashboard / Notifications**: Live charge navigation and notification deep-link are now hidden when TeslaMate API < 1.24 (endpoint unavailable) (#155)

## [1.1.0-beta1] - 2026-02-10

### Changed
- **Live Charge Screen**: Current SoC is now the most prominent element — displayed as a large hero number at the top of the header card, with a 3-column start/current/target row and progress bar below. Energy and power stats are compacted into a single row.

### Fixed
- **Notifications**: Charging notification now shows a 3-segment progress bar — charged (bright), charging-to-limit (dimmed), and beyond-limit (gray) — with bolt tracker at current SoC (#147)
- **Live Charge Screen**: Voltage and Current lines in the V/A chart now use distinct colors (amber and blue) instead of similar gray tones (#153)
- **Stats for Nerds**: Fixed missing values (avg cost/kWh, coldest temperatures, cabin temperatures) when filtering by year (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper), fixes #150)

## [1.0.0] - 2026-02-08

### Added
- **Live Charge Screen**: Real-time charging session visualization accessible from the dashboard charge gauge or charging notification tap. Shows live elapsed time, instant power, voltage/current (AC), SoC progress bar, power/voltage/current charts, and battery level chart. Requires TeslaMate API 1.24+.
- **Charging Notifications**: Live update notifications during charging sessions (Android 16+) with visual battery progress bar showing current level and charge limit. Falls back to standard dismissable notifications on older Android versions.
- **Car Profile Picture**: Long-press the car image on the dashboard to choose a different angle
- **Google Play Store**: Now available on the Google Play Store alongside F-Droid

### Changed
- **Dashboard**: Car picker now filters by detected color, trim, and wheels for a cleaner selection
- **Settings**: Teslamate Base URL is now automatically retrieved from the API instead of requiring manual configuration
- **Charges**: Edit cost button now appears on all charges (previously only shown for charges without a cost set)
- **Stats for Nerds**: AC/DC ratio bar now shows percentage values inside with inverted colors
- **Stats for Nerds**: Replaced "Top Speed" with "Cost / 100 km" in Drives Overview - a more useful metric for tracking EV operating costs

### Fixed
- **Drives**: Show all days when filtered by last 7 or last 30 days (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper), fixes #94)
- **Stats for Nerds**: Fixed AC/DC translation inconsistency - technical terms should not be translated (CA/CC → AC/DC)
- **Dashboard**: Loading and error messages now properly center each line when text wraps
- **Car Images**: Fixed wheel mappings and removed incomplete wheelless assets
- **Notifications**: Charging notification properly cancelled when service stops

## [0.12.4] - 2026-01-29

### Changed
- **Charges**: Cleaner summary totals with adaptive decimal places

### Fixed
- **Model X**: Wheels now display correctly in car images (fixes #119)
- **Drives/Charges**: Screen no longer flickers when changing time filters (fixes #117)
- **Charge Details**: Date/time now displays in proper locale format (fixes #103)
- **Battery Health**: Fixed duplicate % symbol in "Loss (%)" label for ES/IT/CA locales (fixes #120)

## [0.12.3] - 2026-01-27

### Changed
- **Dashboard**: Show elapsed time for all vehicle states (driving, online, charging), not just asleep/offline
- **Dashboard**: Use bolt/zap icon for charging state instead of generic power icon
- **Dashboard**: Align elevation icon and text with location icon and text in location card

### Fixed
- **Drives**: Y-axis labels in driving time histogram now display in HH:MM format instead of raw minutes

## [0.12.2] - 2026-01-25

### Changed
- **Duration Format**: Standardized duration display to "H:MM" format across drives and charges screens (fixes #104)
- **Distance Format**: Added locale-aware thousands separator to all distance displays (fixes #105)

### Fixed
- **Drive Details**: Date/time now displays in proper locale format instead of mixed languages (fixes #103)
- **Battery Health**: Fixed duplicate % symbol in "Loss (%)" label for ES/IT/CA locales (fixes #102)

## [0.12.1] - 2026-01-24

### Added
- **App Icon**: Monochrome/themed icon support for Android 13+ (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper))
- **Notifications**: Dedicated notification icon for tire pressure alerts (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper))

## [0.12.0] - 2026-01-24

### Added
- **Tire Pressure Notifications**: Background monitoring with alerts when any tire enters or exits a warning state
  - Uses Teslamate API's TPMS warning flags for detection
  - Notifications show which tires have low pressure (e.g., "Model 3: Low pressure on Front Left, Rear Right")
  - Notification when all tires return to normal
  - Checks every 15 minutes; persists across app restarts and device reboots
  - Notification channel can be enabled/disabled in Android Settings
- **Stats for Nerds**: New "Countries Visited" record showing unique countries visited with your Tesla
- **Countries Visited**: Detail screen with country flag, localized name, drive count, total distance, energy used, and charge count
- **Countries Visited**: Tap a country to drill down into **Regions Visited** showing stats per region/state
- **Countries Visited**: Sorting options by first visit, alphabetically, drive count, distance, energy, or charges
- **Countries Visited**: Interactive OSM map showing charge/drive locations with country boundary highlighting
  - Toggle between Charges and Drives view (steering wheel icon for drives)
  - AC charges shown in green, DC charges in yellow (matching app-wide color scheme)
  - Tappable legend to filter by AC or DC charge type
  - Year filter chips to view data from specific years
- **Geocoding**: Background location identification using OpenStreetMap Nominatim with rate limiting and caching
- **Drives/Charges**: "Today" filter option to quickly view today's activity
- **Model X**: Added SteelGrey color and Slipstream wheel support

### Changed
- **Stats Sync**: Faster detail sync with parallel batch processing (10 concurrent API calls)
- **Stats Sync**: Progress bar now accurately reflects reprocessing progress when app updates require data migration
- **Stats for Nerds**: Energy now displays in MWh when exceeding 999 kWh

### Fixed
- **Stats for Nerds**: Deep sync progress bar now properly disappears when sync completes
- **Dashboard**: AC charging now shows actual number of phases instead of always showing 3
- **Dashboard**: Show offline time duration when car has been offline
- Various translation fixes

## [0.11.3] - 2026-01-20

### Added
- **Dashboard**: Remember last selected car for users with multiple vehicles
- **Settings**: "Report an issue" link below version number opens GitHub issues page
- **Error Handling**: "Show details" button on API errors displays diagnostic information for troubleshooting

### Changed
- **Build**: Updated Hilt from 2.53.1 to 2.56

### Fixed
- **Tests**: Resolved memory consumption issues in unit tests

## [0.11.2] - 2026-01-19

### Fixed
- **CI/CD**: Google Play releases now include changelog in "What's New" section

## [0.11.1] - 2026-01-19

### Fixed
- **Dashboard**: Show green steering wheel icon when driving instead of grey power button

## [0.11.0] - 2026-01-19

### Added
- **Internationalization (i18n)**: Full multi-language support for English, Italian, Spanish, and Catalan
- **Internationalization (i18n)**: Per-app language selection on Android 13+ via system settings
- **Dashboard**: Sleep duration display with bedtime icon when car is asleep
- **Dashboard**: Improved status indicators with chip design and new icons

### Changed
- **Drives**: Now shows total battery consumed instead of average per drive
- **Charge Details & Drive Details**: Responsive column layout adapts to screen width (2-4 columns) (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper))

### Fixed
- **Charge Details**: Hide charger voltage/current section and charts for DC charging sessions (contributed by [@MARMdeveloper](https://github.com/MARMdeveloper)) (fixes #65)
- **Dashboard**: Power icon now shows green when charging
- Various translation fixes

## [0.10.0] - 2026-01-17

### Changed
- **Drive Details**: Charts now use optimized rendering with data downsampling (LTTB algorithm) for smooth scrolling on long trips
- **Drive Details**: Charts now display time labels on X-axis (start, 1st quarter, half, 3rd quarter, end)
- **Drive Details**: Charts Y-axis now shows 4 labels at quarter intervals (25%, 50%, 75%, 100%)
- **Charge Details**: Charts now use optimized rendering with data downsampling (LTTB algorithm) for smooth scrolling on long charging sessions
- **Charge Details**: Charts X-axis now shows 5 time labels (start, 1st quarter, half, 3rd quarter, end)
- **Charge Details**: Charts Y-axis now shows 4 labels at quarter intervals (25%, 50%, 75%, 100%)

### Added
- **Drive Details & Charge Details**: Fullscreen mode for line charts
  - Small fullscreen icon in the lower-right corner of each chart
  - Tap to expand chart to fullscreen in landscape orientation
  - Back arrow button in top-left corner to exit fullscreen
  - Chart automatically scales to fill available screen space
- **Drive Details**: Weather Along the Way - shows historical weather conditions along your drive route
  - Uses Open-Meteo API to fetch historical weather data for points along the route
  - Displays time, distance from start, weather icon, and temperature in a table
  - Weather point frequency adapts to drive length:
    - Under 10 km: shows weather at destination only
    - Under 30 km: shows weather at start and end
    - Under 150 km: shows weather every 25 km
    - Over 150 km: shows weather every 35 km
  - Weather icons for: Clear, Partly Cloudy, Fog, Drizzle, Rain, Snow, Thunderstorm

### Fixed
- **Drives**: Date and distance filters now persist when navigating to drive details and back
- **Drives**: Scroll position is now preserved when returning from drive details

## [0.9.4] - 2026-01-14

### Fixed
- **Stats for Nerds**: "Driving Days" now shows correct count when filtering by year instead of "Null" (fixes #52)

## [0.9.3] - 2026-01-13

### Fixed
- **Settings**: Force Full Resync now properly deletes all cached data before resyncing, instead of only retrying missing items

## [0.9.2] - 2026-01-12

### Fixed
- **Dashboard**: Tire pressure now displays correctly when configured in PSI (fixes #46)

## [0.9.1] - 2026-01-12

### Fixed
- **Stats for Nerds**: Record cards now scale with system font size to prevent vertical text clipping (fixes #47)
- **Dashboard**: Elevation label no longer wraps "m" unit to next line with larger fonts

## [0.9.0] - 2026-01-11

### Added
- **Stats for Nerds**: New records organized into swipeable categories
  - Swipe left/right between Drives, Battery, Weather & Altitude, and Distances categories
  - Drives: Longest drive, Top speed, Most efficient, Longest driving streak, Most distance day, Busiest day
  - Battery: Biggest gain, Biggest drain, Biggest charge, Peak power, Most expensive, Priciest per kWh
  - Weather & Altitude: Highest point, Most climbing, Hottest/coldest drives and charges
  - Distances: Longest range (tap to see drives), Longest gap without charging/driving
- **Stats for Nerds**: New "Longest Range" record showing maximum distance traveled between charges (fixes #24)
  - Tap to see all drives that made up the record
- **Dashboard**: Breathing glow effect around car image when charging
  - Glow pulses smoothly in opacity with 2-second cycle
  - Color shifts from palette accent toward AC (green) or DC (orange) charging color
  - Glow follows the exact shape of the car
- **Charges**: Swipeable charts showing Energy, Cost, and Number of Charges
  - Swipe left/right on the chart to switch between metrics
  - Page indicator dots show current chart position
- **Drives**: Swipeable charts showing Number of Drives, Time Spent, Distance, and Top Speed
  - Swipe left/right on the chart to switch between metrics
  - Distance and speed respect metric/imperial unit setting
  - Page indicator dots show current chart position
- **Charges**: AC/DC filter to show only AC or DC charging sessions (fixes #22)
  - Tap AC or DC to filter, tap again to reset to show all
  - Filter chips match the AC (green) and DC (orange) badge colors
  - Filter applies to Summary card and charts as well as the list
- **Drives**: Distance filter (Commute/Day trip/Road trip) now applies to Summary card and charts
- **Dashboard**: Charging power gauge with AC/DC badge next to battery percentage while charging
  - Circular gauge shows charging rate relative to max capacity
  - AC (green): gauge fills based on current vs max requested amps
  - DC (yellow): gauge fills based on power vs max (250 kW NMC, 170 kW LFP)
- **Dashboard**: AC charging details below SoC bar showing Voltage, Current, and Phases
- **Domain**: Battery chemistry detection (LFP vs NMC) based on trim_badging

### Changed
- **Requirements**: Minimum Android version raised from 8.0 to 10 (released 2019)

## [0.8.3] - 2026-01-11

### Fixed
- **Stats for Nerds**: Currency now uses user's configured currency instead of hardcoded EUR (fixes #37)

## [0.8.2] - 2026-01-09

### Changed
- **Build**: Disable DependencyInfoBlock for F-Droid compatibility

## [0.8.1] - 2026-01-06

### Added
- **Stats Sync**: Pull-to-refresh in Stats screen now triggers a background sync
- **Stats Sync**: Automatic sync every 60 seconds while Stats screen is visible

### Changed
- **Dashboard**: Simplified stats button overlay on car image - now shows only arrow indicator

## [0.8.0] - 2026-01-05

### Added
- **Model Y Juniper Performance**: Support for P74D trim with 21" Überturbine wheels and red brake calipers
- **Model Y Juniper Premium**: Support for Premium (74/74D trim) with 19" Crossflow and 20" Helix 2.0 wheels in 6 colors (PPSW, PN01, PX02, PN00, PR01, PPSB)
- **Model Y Juniper Trim Detection**: Proper variant detection based on trim_badging (50=Standard, 74/74D=Premium, P74D=Performance)
- **Stats for Nerds**: Tap the car image on Dashboard to access advanced statistics
  - Quick Stats: Total drives/charges, distance, energy, efficiency, top speed
  - Records: Longest drive, fastest drive, most efficient drive, biggest charge, busiest day
  - Deep Stats (synced in background): Elevation extremes, temperature extremes, max charging power, AC/DC ratio
  - Year filter to view stats for specific years or all time
  - Background sync of drive/charge details for Deep Stats computation
- **Charges**: Tap the Cost card to edit the charge cost directly in Teslamate (requires Teslamate Base URL in Settings)
- **Settings**: New "Teslamate Settings" section with Base URL for direct Teslamate integration
- **Mileage**: Info icon next to "Avg/Year" explaining how the calculation works
- **CI/CD**: Debug APK now built alongside release APK

### Fixed
- **Mileage**: Fixed incorrect Avg/Year calculation that counted calendar years instead of actual elapsed time since first drive (fixes #10)

## [0.7.1] - 2025-12-25

### Fixed
- **Dashboard**: Use pre-computed drive/charge counts from API instead of fetching all records

## [0.7.0] - 2025-12-24

### Added
- **Software Versions**: Tap the external link icon next to any version to view release notes on NotATeslaApp
- **Drives**: Filter drives by distance - Commute (< 10 km), Day trip (10-100 km), Road trip (> 100 km). Labels adapt to metric/imperial units.

### Changed
- **Mileage**: Round all distance values to whole numbers for cleaner display (Total, Avg/Year, year cards, month cards)
- **Mileage**: Add arrow icon to year and month cards to indicate they are navigable

### Fixed
- **Dashboard**: Fix race condition where drive/charge counts could fail to display for users with large datasets
- **Software Versions**: Show all software updates instead of only the first 100

## [0.6.1] - 2025-12-22

### Fixed
- **CI/CD**: Fixed release signing configuration for GitHub Actions

## [0.6.0] - 2025-12-22

### Added
- **GitHub Release**: First public release on GitHub with automated APK builds

## [0.5.1] - 2025-12-22

### Added
- **Version Display**: Show app version at bottom of Settings screen

## [0.5.0] - 2025-12-22

### Added
- **App Icon**: New MateDroid logo
- **GitHub Actions CI**: Automatic APK build and release asset upload on new releases
- **Model Y Juniper Support**: Crossflow19 wheel detection and car images
- **Highland M3 Support**: Nova19/Helix19 wheel detection (visual fallback to Photon18)

### Fixed
- **Car Name Display**: Show "Model Y/3/S/X" when owner hasn't set a custom name

## [0.4.0] - 2025-12-22

### Added
- **Multi-Car Support**: Switch between vehicles via dropdown in the title bar
- **Interactive Bar Charts**: Tap any bar to see exact values in a tooltip
- **Dynamic Chart Granularity**: Charts adapt to date range (daily/weekly/monthly)
- **Show Short Drives/Charges Setting**: Hide trivial entries from lists (keeps them in totals)

### Changed
- **Tire Pressure Display**: Redesigned with compact Tesla outline and status dots
- **Settings Toggles**: Certificate and display options now use toggle switches

### Fixed
- **HTTP Connections**: Allow unsecure HTTP connections to the TeslamateApi server
- **Location Card**: Shows reverse-geocoded address when outside geofences
- **Dashboard Cards**: Consistent styling across all cards

## [0.3.0] - 2025-12-21

### Added
- **Dashboard**: Real-time vehicle status with dynamic Tesla 3D car images matching your vehicle's color, model, and wheels
- **Charges Screen**: Charging history with statistics, date filtering, and detailed graphs
- **Drives Screen**: Drive history with efficiency metrics, route maps, and detailed graphs
- **Mileage Screen**: Yearly/monthly/daily mileage breakdown with drill-down navigation
- **Software Versions Screen**: Update history with statistics and version timeline
- **Battery Health Screen**: Battery degradation tracking
- **Car Color Palettes**: UI theming adapts to your car's exterior color
- **Settings**: Server configuration with currency selection

## [0.2.0] - 2025-12-20

### Added
- Drives screen with drive history
- Charge detail screen with graphs
- Drive detail screen with route map
- Mileage tracking screen
- Software versions screen
- Battery health screen

## [0.1.0] - 2025-12-19

### Added
- Initial project setup
- Settings screen for server configuration
- Dashboard with basic vehicle status
- Charges screen with history list

[Unreleased]: https://github.com/vide/matedroid/compare/v1.3.0...HEAD
[1.3.0]: https://github.com/vide/matedroid/compare/v1.2.3...v1.3.0
[1.3.0-beta2]: https://github.com/vide/matedroid/compare/v1.3.0-beta1...v1.3.0-beta2
[1.3.0-beta1]: https://github.com/vide/matedroid/compare/v1.2.3...v1.3.0-beta1
[1.2.3]: https://github.com/vide/matedroid/compare/v1.2.2...v1.2.3
[1.2.2]: https://github.com/vide/matedroid/compare/v1.2.1...v1.2.2
[1.2.1]: https://github.com/vide/matedroid/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/vide/matedroid/compare/v1.1.0...v1.2.0
[1.2.0-beta1]: https://github.com/vide/matedroid/compare/v1.1.0...v1.2.0-beta1
[1.1.0]: https://github.com/vide/matedroid/compare/v1.0.0...v1.1.0
[1.1.0-beta3]: https://github.com/vide/matedroid/compare/v1.1.0-beta2...v1.1.0-beta3
[1.1.0-beta2]: https://github.com/vide/matedroid/compare/v1.1.0-beta1...v1.1.0-beta2
[1.1.0-beta1]: https://github.com/vide/matedroid/compare/v1.0.0...v1.1.0-beta1
[1.0.0]: https://github.com/vide/matedroid/compare/v0.12.4...v1.0.0
[0.12.4]: https://github.com/vide/matedroid/compare/v0.12.3...v0.12.4
[0.12.3]: https://github.com/vide/matedroid/compare/v0.12.2...v0.12.3
[0.12.2]: https://github.com/vide/matedroid/compare/v0.12.1...v0.12.2
[0.12.1]: https://github.com/vide/matedroid/compare/v0.12.0...v0.12.1
[0.12.0]: https://github.com/vide/matedroid/compare/v0.11.3...v0.12.0
[0.11.3]: https://github.com/vide/matedroid/compare/v0.11.2...v0.11.3
[0.11.2]: https://github.com/vide/matedroid/compare/v0.11.1...v0.11.2
[0.11.1]: https://github.com/vide/matedroid/compare/v0.11.0...v0.11.1
[0.11.0]: https://github.com/vide/matedroid/compare/v0.10.0...v0.11.0
[0.10.0]: https://github.com/vide/matedroid/compare/v0.9.4...v0.10.0
[0.9.4]: https://github.com/vide/matedroid/compare/v0.9.3...v0.9.4
[0.9.3]: https://github.com/vide/matedroid/compare/v0.9.2...v0.9.3
[0.9.2]: https://github.com/vide/matedroid/compare/v0.9.1...v0.9.2
[0.9.1]: https://github.com/vide/matedroid/compare/v0.9.0...v0.9.1
[0.9.0]: https://github.com/vide/matedroid/compare/v0.8.3...v0.9.0
[0.8.3]: https://github.com/vide/matedroid/compare/v0.8.2...v0.8.3
[0.8.2]: https://github.com/vide/matedroid/compare/v0.8.1...v0.8.2
[0.8.1]: https://github.com/vide/matedroid/compare/v0.8.0...v0.8.1
[0.8.0]: https://github.com/vide/matedroid/compare/v0.7.1...v0.8.0
[0.7.1]: https://github.com/vide/matedroid/compare/v0.7.0...v0.7.1
[0.7.0]: https://github.com/vide/matedroid/compare/v0.6.1...v0.7.0
[0.6.1]: https://github.com/vide/matedroid/compare/v0.6.0...v0.6.1
[0.6.0]: https://github.com/vide/matedroid/releases/tag/v0.6.0
[0.5.1]: https://github.com/vide/matedroid/compare/v0.5.0...v0.5.1
[0.5.0]: https://github.com/vide/matedroid/compare/v0.4.0...v0.5.0
[0.4.0]: https://github.com/vide/matedroid/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/vide/matedroid/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/vide/matedroid/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/vide/matedroid/releases/tag/v0.1.0
