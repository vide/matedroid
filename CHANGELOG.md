# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/vide/matedroid/compare/v0.12.2...HEAD
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
