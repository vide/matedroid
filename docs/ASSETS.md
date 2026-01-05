# Tesla Car Image Assets

This document describes how Tesla car images are fetched and mapped for MateDroid.

## Overview

Tesla provides a compositor service that generates car renders based on configuration options. The images are downloaded at build time using `util/fetch_tesla_assets.py` and bundled into the APK.

## Compositor Endpoints

Tesla has **two different compositor APIs**:

### Old Compositor (Legacy Models)
- **URL**: `https://static-assets.tesla.com/v1/compositor/`
- **For**: Pre-2024 Model 3, Pre-2025 Model Y
- **Output**: PNG with transparency
- **File size**: ~100-140 KB per image

**Example URL**:
```
https://static-assets.tesla.com/v1/compositor/?model=m3&view=STUD_3QTR&size=800&options=PMNG,W38B&bkba_opt=1
```

**Parameters**:
| Parameter | Description | Example |
|-----------|-------------|---------|
| `model` | Model code | `m3`, `my` |
| `view` | Camera angle | `STUD_3QTR` |
| `size` | Image width | `800` |
| `options` | Comma-separated codes | `PMNG,W38B` |
| `bkba_opt` | Background (1=transparent) | `1` |

### New Compositor (Highland/Juniper)
- **URL**: `https://static-assets.tesla.com/configurator/compositor`
- **For**: Model 3 Highland (2024+), Model Y Juniper (2025+)
- **Output**: PNG with transparency (using `bkba_opt=1`)
- **File size**: ~50-80 KB per image

**Example URL**:
```
https://static-assets.tesla.com/configurator/compositor?context=design_studio_2&options=$MT370,$PPSW,$W38A,$IPB3&view=STUD_3QTR&model=m3&size=800&bkba_opt=1
```

**Note**: We use `STUD_3QTR` view for both compositors for consistent car sizing across all models.

**Parameters**:
| Parameter | Description | Example |
|-----------|-------------|---------|
| `context` | API context | `design_studio_2` |
| `model` | Model code | `m3`, `my` |
| `view` | Camera angle | `STUD_FRONT34` |
| `size` | Image width | `800` |
| `options` | `$`-prefixed codes | `$MT370,$PPSW,$W38A,$IPB3` |
| `bkba_opt` | Background (1=transparent PNG, 2=opaque JPEG) | `1` |

**Important**: New compositor uses `$` prefix for all option codes!

---

## Model Mappings

### TeslamateAPI Model → Compositor Model Code

| TeslamateAPI `car_details.model` | Compositor Code | Notes |
|----------------------------------|-----------------|-------|
| `3` | `m3` | Model 3 |
| `Y` | `my` | Model Y |
| `S` | `ms` | Model S |
| `X` | `mx` | Model X |

### Internal Model Variants (for asset naming)

| Variant | Description | Compositor | File Prefix |
|---------|-------------|------------|-------------|
| Legacy Model 3 | Pre-2024 | Old | `m3` |
| Highland Model 3 | 2024+ | New | `m3h` |
| Highland Model 3 Performance | 2024+ Perf | New | `m3hp` |
| Legacy Model Y | Pre-2025 | Old | `my` |
| Juniper Model Y | 2025+ Standard/Premium | New | `myj` |
| Juniper Model Y Performance | 2025+ Performance | New | `myjp` |
| Model S | All | Old | `ms` |
| Model X | All | Old | `mx` |

### Highland/Juniper Trim Codes

| Trim Code | Model | Variant | Interior Code |
|-----------|-------|---------|---------------|
| `MT369` | Model 3 Highland | Standard | `IPB2` |
| `MT370` | Model 3 Highland | Premium | `IPB3` |
| `MT371` | Model 3 Highland | Performance | `IPB4` |
| `MTY68` | Model Y Juniper | Standard/Long Range | `IBB3` |
| `MTY52` | Model Y Juniper | Premium (19") | `IPB7` |
| `MTY60` | Model Y Juniper | Premium (20") | `IPB8` |
| `MTY53` | Model Y Juniper | Performance | `IPB10` |

**Note on Model Y Juniper compositor support:**
- `MTY68` works with `STUD_3QTR` view for 3 colors (PPSW, PN01, PX02) with 18"/19" wheels
- `MTY52` has limited support on `STUD_3QTR` - we use `MTY68` with 19" wheels instead
- `MTY60` works with `STUD_3QTR` view for 5 colors (PPSW, PN01, PX02, PN00, PR01) with 20" wheels
- `MTY53` works with `STUD_3QTR` view for all 6 colors with 21" wheels

### Model Y Juniper Trim Badging (from TeslamateAPI)

| Trim Badge | Variant | Default Wheels | Notes |
|------------|---------|----------------|-------|
| `50` | Standard Range | 18" Photon (WY18P) | |
| `74`, `74D` | Long Range/Premium | 19" Crossflow (WY19P) | |
| `P74D` | Performance | 21" Überturbine (WY21A) | Red brake calipers |

---

## Color Mappings

### TeslamateAPI Color → Compositor Code

| TeslamateAPI `car_exterior.exterior_color` | Compositor Code | Color Name | Available On |
|--------------------------------------------|-----------------|------------|--------------|
| `Black`, `SolidBlack` | `PBSB` | Solid Black | All |
| `ObsidianBlack` | `PMBL` | Obsidian Black Metallic | Legacy M3 |
| `MidnightSilver`, `MidnightSilverMetallic` | `PMNG` | Midnight Silver Metallic | Legacy |
| `Silver`, `SilverMetallic` | `PMSS` | Silver Metallic | Legacy M3 |
| `White`, `PearlWhite`, `PearlWhiteMultiCoat` | `PPSW` | Pearl White Multi-Coat | All |
| `DeepBlue`, `DeepBlueMetallic`, `Blue` | `PPSB` | Deep Blue Metallic | All except Juniper |
| `Red`, `RedMultiCoat` | `PPMR` | Red Multi-Coat | Legacy |
| `Quicksilver` | `PN00` | Quicksilver | Highland M3 |
| `StealthGrey`, `StealthGray` | `PN01` | Stealth Grey | Highland, Juniper |
| `MidnightCherryRed` | `PR00` | Midnight Cherry Red | None (discontinued) |
| `UltraRed` | `PR01` | Ultra Red | Highland M3 |
| `BlackDiamond` | `PX02` | Black Diamond | Highland, Juniper |

### Color Availability by Model Variant

| Color Code | Legacy M3 | Highland M3 | Legacy MY | Juniper MY | Juniper MY Perf |
|------------|-----------|-------------|-----------|------------|-----------------|
| `PBSB` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `PMBL` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PMNG` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `PMSS` | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PPSW` | ✅ | ✅ | ✅ | ✅ | ✅ |
| `PPSB` | ✅ | ✅ | ✅ | ❌ | ❌ |
| `PPMR` | ✅ | ❌ | ✅ | ❌ | ❌ |
| `PN00` | ❌ | ✅ | ❌ | ❌ | ✅ |
| `PN01` | ❌ | ✅ | ❌ | ✅ | ✅ |
| `PR01` | ❌ | ✅ | ❌ | ❌ | ✅ |
| `PX02` | ❌ | ✅ | ❌ | ✅ | ✅ |
| `PB02` | ❌ | ❌ | ❌ | ❌ | ✅ |

---

## Wheel Mappings

### TeslamateAPI Wheel → Compositor Code

| TeslamateAPI `car_exterior.wheel_type` Pattern | M3 Code | MY Code | Description |
|------------------------------------------------|---------|---------|-------------|
| `Pinwheel18*`, `Aero18*` | `W38B` | `WY18B` | 18" Aero Wheels |
| `AeroTurbine19*`, `Stiletto19*`, `Sport19*` | `W39B` | `WY19B` | 19" Sport Wheels |
| `Gemini19*` | - | `WY19B` | 19" Gemini (MY) |
| `Apollo19*` | - | `WY9S` | 19" Apollo (MY) |
| `Induction20*` | - | `WY0S` | 20" Induction (MY) |
| `Performance20*` | `W32P` | `WY20P` | 20" Performance |
| `Uberturbine21*` | - | `WY1S` | 21" Uberturbine (MY) |
| `Photon18*` | `W38A` | `WY18P` | 18" Photon (Highland/Juniper) |
| `Nova19*`, `Helix19*` | `W38A`* | - | 19" Nova (Highland M3) - *fallback, not in compositor |
| `Crossflow19*` | - | `WY19P` | 19" Crossflow (Juniper MY) |
| `Helix20*` | - | `WY20A` | 20" Helix 2.0 (Juniper MY Premium) |
| `Glider18*`, `Nova18*` | `W38A` | - | 18" Glider/Nova (Highland M3) |

**Note**: TeslamateAPI may append suffixes like `CapKit`, `Cover`, etc. The mapping strips these.

### Wheel Availability by Model Variant

| Wheel Code | Legacy M3 | Highland M3 | Highland M3P | Legacy MY | Juniper MY | Juniper MY Perf |
|------------|-----------|-------------|--------------|-----------|------------|-----------------|
| `W38B` | ✅ | ❌ | ❌ | - | - | - |
| `W39B` | ✅ | ❌ | ❌ | - | - | - |
| `W32P` | ✅ | ❌ | ❌ | - | - | - |
| `W38A` | ❌ | ✅ | ❌ | - | - | - |
| `W30P` | ❌ | ❌ | ✅ | - | - | - |
| `WY18B` | - | - | - | ✅ | ❌ | ❌ |
| `WY19B` | - | - | - | ✅ | ❌ | ❌ |
| `WY20P` | - | - | - | ✅ | ❌ | ❌ |
| `WY0S` | - | - | - | ✅ | ❌ | ❌ |
| `WY1S` | - | - | - | ✅ | ❌ | ❌ |
| `WY18P` | - | - | - | ❌ | ✅ | ❌ |
| `WY19P` | - | - | - | ❌ | ✅ | ❌ |
| `WY20A` | - | - | - | ❌ | ✅ | ❌ |
| `WY21A` | - | - | - | ❌ | ❌ | ✅ |

---

## Gotchas and Known Issues

### 1. Placeholder Images from Old Compositor
The old compositor returns **grayscale shadow placeholders** (~26KB) for color/wheel combinations that don't exist. These look like car silhouettes but are not usable images.

**Detection**: Check file size. Valid images are 100KB+, placeholders are ~26KB.

### 2. New Colors Don't Work on Old Compositor
Colors introduced with Highland/Juniper (`PN00`, `PN01`, `PR01`, `PX02`) return placeholders on the old compositor. Must use the new compositor for these colors.

### 3. Highland/Juniper Have Limited Wheel Options
- Highland Model 3: Only `W38A` (standard) or `W30P` (Performance)
- Juniper Model Y: Only `WY18P`

Each trim level has exactly ONE wheel option available.

### 4. Highland/Juniper Detection from TeslamateAPI
TeslamateAPI doesn't explicitly indicate if a car is Highland/Juniper. Detection heuristics:

1. **Color-based**: If exterior color is `PN00`, `PN01`, `PR01`, or `PX02` → Highland/Juniper
2. **Wheel-based**: If wheel type is Highland/Juniper-only:
   - Model 3: `Photon18`, `Glider18`, `Nova18` → Highland
   - Model Y: `Photon18` → Juniper

This correctly identifies Highland/Juniper cars even with common colors like Pearl White (`PPSW`).

### 5. Trim Badging for Performance Detection
- Legacy: `P` prefix (e.g., `P74D`) indicates Performance
- Highland: Uses `MT371` trim code

### 6. Model S/X Support
The old compositor supports Model S (`ms`) and Model X (`mx`) with legacy colors. Both models are now included with 5 colors each.

### 7. Discontinued Colors
- `PR00` (Midnight Cherry Red): Was briefly available, now discontinued. Compositor may still work but we don't download.

### 8. Missing Wheel Options in Compositor
Some newer wheel options are not yet available in Tesla's compositor:
- **Nova 19"** (Highland M3): TeslamateAPI reports as "Helix19" - falls back to W38A (18" Photon) visually

### 9. bkba_opt Parameter Controls Output Format
Both compositors support the `bkba_opt` parameter:
- `bkba_opt=1`: Transparent PNG (used for all images)
- `bkba_opt=2`: Opaque JPEG with background

We use `bkba_opt=1` for all images to get consistent transparent PNGs.

---

## Asset File Naming Convention

Format: `{model_variant}_{color_code}_{wheel_code}.png`

| Model Variant | Example Filename |
|---------------|------------------|
| Legacy Model 3 | `m3_PMNG_W38B.png` |
| Highland Model 3 | `m3h_PN01_W38A.png` |
| Highland M3 Performance | `m3hp_PR01_W30P.png` |
| Legacy Model Y | `my_PPSW_WY19B.png` |
| Juniper Model Y | `myj_PX02_WY18P.png` |
| Juniper MY Performance | `myjp_PN01_WY21A.png` |
| Model S | `ms_PPSW_WT19.png` |
| Model X | `mx_PPSB_WX20.png` |

---

## Current Asset Inventory

| Model Variant | Colors | Wheels | Total Images | Format |
|---------------|--------|--------|--------------|--------|
| Legacy Model 3 | 7 | 3 | 21 | PNG |
| Highland Model 3 | 7 | 1 | 7 | PNG |
| Highland M3 Performance | 7 | 1 | 7 | PNG |
| Legacy Model Y | 5 | 5 | 25 | PNG |
| Juniper Model Y | 6 | 3 | 15 | PNG |
| Juniper MY Performance | 6 | 1 | 6 | PNG |
| Model S | 5 | 1 | 5 | PNG |
| Model X | 5 | 1 | 5 | PNG |
| **Total** | | | **91** | **~9 MB** |

---

## Updating Assets

To re-download all assets:

```bash
# Preview what would be downloaded
./util/fetch_tesla_assets.py --dry-run

# Download all assets
./util/fetch_tesla_assets.py

# Download to custom directory
./util/fetch_tesla_assets.py --output-dir /path/to/assets
```

The script will:
1. Skip invalid combinations (checks response size/format)
2. Report warnings for failed downloads
3. Download in parallel batches

---

## Future Considerations

1. **New Model Variants**: When Tesla releases new refreshes (e.g., Model S/X Plaid+), new compositor codes may be needed.

2. **New Colors**: Tesla periodically introduces new colors. Check Tesla's configurator for current options.

3. **API Changes**: Tesla may deprecate or change compositor endpoints without notice.

4. **Better Highland/Juniper Detection**: Could potentially use VIN decoding or manufacture date if available from TeslamateAPI.
