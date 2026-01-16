# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- **Drive Details**: Weather Along the Way - shows historical weather conditions along your drive route
  - Uses Open-Meteo API to fetch historical weather data for points along the route
  - Displays time, distance from start, weather icon, and temperature in a table
  - Weather point frequency adapts to drive length:
    - Under 10 km: shows weather at destination only
    - Under 30 km: shows weather at start and end
    - Under 150 km: shows weather every 25 km
    - Over 150 km: shows weather every 35 km
  - Weather icons for: Clear, Partly Cloudy, Fog, Drizzle, Rain, Snow, Thunderstorm

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

[Unreleased]: https://github.com/vide/matedroid/compare/v0.9.3...HEAD
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
