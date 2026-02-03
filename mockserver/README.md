# Teslamate API Mock Server

A simple proxy server that forwards requests to a real Teslamate API instance while injecting mock car information (model, color, trim badging, wheels, etc.) from a configuration file.

Useful for testing the app with different car configurations without owning multiple Teslas.

## Requirements

- Python 3.11+
- [uv](https://github.com/astral-sh/uv) (for automatic dependency management)

## Usage

```bash
# List available car profiles
./server.py --list-cars

# Start the mock server
./server.py --upstream http://your-teslamate-api:4000 --car modely_juniper_grey_19

# With custom port
./server.py -u http://localhost:4000 -c model3_highland_white_18 -p 5000
```

## Options

| Option | Description |
|--------|-------------|
| `-u, --upstream` | Upstream Teslamate API URL (required) |
| `-c, --car` | Car profile name from cars.json (required) |
| `-p, --port` | Port to run mock server on (default: 4001) |
| `--host` | Host to bind to (default: 127.0.0.1) |
| `--cars-file` | Path to cars config JSON (default: cars.json) |
| `--list-cars` | List available car profiles and exit |

## Car Profiles

Edit `cars.json` to add or modify car profiles. Each profile follows the Teslamate API structure:

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

### Known values

**Models**: `3`, `Y`, `S`, `X`, `Cybertruck`

**Trim badging examples**:
- Model 3 Highland: `LRAWD`
- Model 3 Legacy: `74D`, `P74D`
- Model Y Juniper: `50`, `74`, `P74D`
- Model Y Legacy: `74D`, `P74D`
- Model S/X: `100D`, `Plaid`

**Exterior colors**: `PearlWhite`, `StealthGrey`, `DeepBlue`, `RedMulticoat`, `MidnightSilver`, `SolidBlack`

**Wheel types**:
- Model 3 Highland: `Glider18`, `Pinwheel18CapKit`, `Photon18`
- Model Y Juniper: `Crossflow19`, `Apollo19`, `Uberturbine21`
- Model Y Legacy: `Gemini19`, `Induction20`
- Model S/X: `Tempest19`, `AeroTurbine22`, `Turbine22`, `Cyclone20`

## How It Works

1. The server receives requests on the configured port
2. All requests are proxied to the upstream Teslamate API
3. For `/api/v1/cars/*` endpoints, the response JSON is modified to include the overrides from the selected car profile (deep-merged into `car_details` and `car_exterior`)
4. Other endpoints are passed through unchanged
