# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://github.com/vide/matedroid/compare/v0.7.1...HEAD
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
