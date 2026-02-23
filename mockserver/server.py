#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = [
#     "flask>=3.0",
#     "httpx>=0.27",
# ]
# ///
"""
Teslamate API Mock Server

Proxies requests to a real Teslamate API instance and injects
car-specific information (badge, trimming, color, model, etc.)
from a JSON configuration file.

Usage:
    ./server.py --upstream http://teslamate-api:4000 --car modely_juniper_grey_19
    ./server.py -u http://localhost:4000 -c model3_highland_white_18 --port 5000
"""

import argparse
import copy
import json
import sys
import time
from pathlib import Path

import httpx
from flask import Flask, Response, request

app = Flask(__name__)

# Global configuration
config = {
    "upstream_url": "",
    "car_overrides": {},
    "charging": {
        "enabled": False,
        "dc": False,
        "start_soc": 40,
        "limit_soc": 80,
        "power_kw": None,  # None = auto (11 for AC, 150 for DC)
        "start_time": None,
    },
}

# Approximate usable battery capacity in kWh (used for SOC progression calculation)
BATTERY_CAPACITY_KWH = 82.0


def load_cars_config(config_path: Path) -> dict:
    """Load car configurations from JSON file."""
    if not config_path.exists():
        print(f"Error: Cars config file not found: {config_path}", file=sys.stderr)
        sys.exit(1)

    with open(config_path) as f:
        return json.load(f)


def deep_merge(base: dict, overrides: dict) -> dict:
    """Deep merge overrides into base dict."""
    result = copy.deepcopy(base)
    for key, value in overrides.items():
        if key in result and isinstance(result[key], dict) and isinstance(value, dict):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = copy.deepcopy(value)
    return result


def inject_car_overrides(car_data: dict, overrides: dict) -> dict:
    """Inject car override values into a single car object.

    Handles the nested structure:
    - car_details.model, car_details.trim_badging
    - car_exterior.exterior_color, car_exterior.wheel_type, car_exterior.spoiler_type
    """
    if not overrides:
        return car_data

    return deep_merge(car_data, overrides)


def inject_overrides_into_response(data: dict, overrides: dict) -> dict:
    """Inject overrides into the API response.

    Handles the Teslamate API response structure:
    {
        "data": {
            "cars": [
                { "car_id": 1, "car_details": {...}, "car_exterior": {...}, ... }
            ]
        }
    }
    """
    if not overrides:
        return data

    result = copy.deepcopy(data)

    # Handle /api/v1/cars response format: { "data": { "cars": [...] } }
    if "data" in result and isinstance(result["data"], dict):
        if "cars" in result["data"] and isinstance(result["data"]["cars"], list):
            result["data"]["cars"] = [
                inject_car_overrides(car, overrides) for car in result["data"]["cars"]
            ]
            return result

    # Handle single car response format: { "data": { "car": {...} } }
    if "data" in result and isinstance(result["data"], dict):
        if "car" in result["data"] and isinstance(result["data"]["car"], dict):
            result["data"]["car"] = inject_car_overrides(result["data"]["car"], overrides)
            return result

    # Handle direct car object (fallback)
    if "car_details" in result or "car_exterior" in result:
        return inject_car_overrides(result, overrides)

    return result


def should_inject_overrides(path: str) -> bool:
    """Determine if car overrides should be injected for this endpoint."""
    car_endpoints = [
        "/api/v1/cars",
        "/cars",
    ]

    for endpoint in car_endpoints:
        if path.startswith(endpoint):
            return True

    return False


@app.route("/api/v1/globalsettings", methods=["GET"])
def global_settings():
    """Return mock global settings with base_url derived from upstream."""
    # Extract the base URL from the upstream (remove /api suffix if present)
    upstream = config["upstream_url"]
    # Assume the Teslamate instance is at the same base as the API
    # In real deployments, these are often the same host
    base_url = upstream.rstrip("/")
    if base_url.endswith("/api"):
        base_url = base_url[:-4]

    return Response(
        json.dumps({
            "data": {
                "settings": {
                    "teslamate_urls": {
                        "base_url": base_url,
                        "grafana_url": f"{base_url}:3000"
                    },
                    "teslamate_units": {
                        "unit_of_length": "km",
                        "unit_of_temperature": "C"
                    }
                }
            }
        }),
        status=200,
        content_type="application/json",
    )


def _build_charging_status_response(car_id: int) -> dict:
    """Build a mock /status response simulating an ongoing charge session."""
    charging_cfg = config["charging"]
    is_dc = charging_cfg["dc"]
    start_soc = charging_cfg["start_soc"]
    limit_soc = charging_cfg["limit_soc"]
    power_kw = charging_cfg["power_kw"] or (150 if is_dc else 11)

    elapsed_hours = (time.time() - charging_cfg["start_time"]) / 3600
    energy_added = elapsed_hours * power_kw

    current_soc = min(start_soc + (energy_added / BATTERY_CAPACITY_KWH) * 100, limit_soc)
    current_soc_int = int(current_soc)

    is_charging = current_soc_int < limit_soc
    charging_state = "Charging" if is_charging else "Complete"

    remaining_kwh = max((limit_soc - current_soc) / 100 * BATTERY_CAPACITY_KWH, 0.0)
    time_to_full = round(remaining_kwh / power_kw, 2) if is_charging else 0.0

    # AC: phases=3, 230V, 16A; DC: phases=null, 400V, high current
    if is_dc:
        charger_phases = None
        charger_voltage = 400
        charger_current = round(power_kw * 1000 / charger_voltage)
    else:
        charger_phases = 3
        charger_voltage = 230
        charger_current = round(power_kw * 1000 / (charger_voltage * charger_phases))

    active_power = power_kw if is_charging else 0
    active_voltage = charger_voltage if is_charging else 0
    active_current = charger_current if is_charging else 0

    # Rough range estimates: ~5.5 km/% rated, 5.0 km/% estimated
    rated_range = round(current_soc_int * 5.5, 1)
    est_range = round(current_soc_int * 5.0, 1)

    return {
        "data": {
            "status": {
                "display_name": "Mock Tesla",
                "state": "charging" if is_charging else "online",
                "state_since": "2024-01-01T00:00:00Z",
                "odometer": 12345.0,
                "car_status": {
                    "healthy": True,
                    "locked": True,
                    "sentry_mode": False,
                    "windows_open": False,
                    "doors_open": False,
                    "trunk_open": False,
                    "frunk_open": False,
                    "is_user_present": False,
                },
                "car_geodata": {
                    "geofence": "Home",
                    "latitude": 52.52,
                    "longitude": 13.405,
                },
                "car_versions": {
                    "version": "2024.44.25",
                    "update_available": False,
                    "update_version": None,
                },
                "driving_details": {
                    "shift_state": None,
                    "power": 0,
                    "speed": None,
                    "heading": 180,
                    "elevation": 50,
                },
                "climate_details": {
                    "is_climate_on": False,
                    "inside_temp": 22.5,
                    "outside_temp": 15.0,
                    "is_preconditioning": False,
                },
                "battery_details": {
                    "battery_level": current_soc_int,
                    "usable_battery_level": max(current_soc_int - 1, 0),
                    "est_battery_range": est_range,
                    "rated_battery_range": rated_range,
                    "ideal_battery_range": round(current_soc_int * 6.0, 1),
                },
                "charging_details": {
                    "plugged_in": True,
                    "charging_state": charging_state,
                    "charge_energy_added": round(energy_added, 2),
                    "charge_limit_soc": limit_soc,
                    "charger_phases": charger_phases,
                    "charger_power": active_power,
                    "charger_voltage": active_voltage,
                    "charger_actual_current": active_current,
                    "charge_current_request": active_current,
                    "charge_current_request_max": charger_current,
                    "charge_port_door_open": True,
                    "time_to_full_charge": time_to_full,
                },
                "tpms_details": {
                    "tpms_pressure_fl": 2.9,
                    "tpms_pressure_fr": 2.9,
                    "tpms_pressure_rl": 2.9,
                    "tpms_pressure_rr": 2.9,
                    "tpms_soft_warning_fl": False,
                    "tpms_soft_warning_fr": False,
                    "tpms_soft_warning_rl": False,
                    "tpms_soft_warning_rr": False,
                },
            },
            "units": {
                "unit_of_length": "km",
                "unit_of_pressure": "bar",
                "unit_of_temperature": "C",
            },
        }
    }


@app.route("/api/v1/cars/<int:car_id>/status", methods=["GET"])
def car_status(car_id: int):
    """Return a mock charging status when --charging is active; otherwise proxy."""
    if config["charging"]["enabled"]:
        data = _build_charging_status_response(car_id)
        return Response(json.dumps(data), status=200, content_type="application/json")
    return proxy(f"api/v1/cars/{car_id}/status")


@app.route("/", defaults={"path": ""}, methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
@app.route("/<path:path>", methods=["GET", "POST", "PUT", "DELETE", "PATCH"])
def proxy(path: str):
    """Proxy all requests to upstream and optionally inject overrides."""
    upstream_url = f"{config['upstream_url']}/{path}"

    # Forward query parameters
    if request.query_string:
        upstream_url += f"?{request.query_string.decode()}"

    # Prepare headers (remove hop-by-hop headers)
    headers = {
        key: value
        for key, value in request.headers
        if key.lower() not in ("host", "connection", "keep-alive", "transfer-encoding")
    }

    try:
        # Make request to upstream
        with httpx.Client(timeout=30.0) as client:
            response = client.request(
                method=request.method,
                url=upstream_url,
                headers=headers,
                content=request.get_data(),
            )

        # Check if we should inject overrides
        if (
            should_inject_overrides(f"/{path}")
            and response.headers.get("content-type", "").startswith("application/json")
            and config["car_overrides"]
        ):
            try:
                data = response.json()
                data = inject_overrides_into_response(data, config["car_overrides"])

                return Response(
                    json.dumps(data),
                    status=response.status_code,
                    content_type="application/json",
                )
            except json.JSONDecodeError:
                pass  # Fall through to return original response

        # Return original response
        excluded_headers = ("content-encoding", "content-length", "transfer-encoding", "connection")
        response_headers = [
            (name, value)
            for name, value in response.headers.items()
            if name.lower() not in excluded_headers
        ]

        return Response(
            response.content,
            status=response.status_code,
            headers=response_headers,
        )

    except httpx.RequestError as e:
        return Response(
            json.dumps({"error": f"Upstream request failed: {e}"}),
            status=502,
            content_type="application/json",
        )


def main():
    parser = argparse.ArgumentParser(
        description="Teslamate API Mock Server - proxies requests and injects car overrides"
    )
    parser.add_argument(
        "-u",
        "--upstream",
        required=True,
        help="Upstream Teslamate API URL (e.g., http://localhost:4000)",
    )
    parser.add_argument(
        "-c",
        "--car",
        required=True,
        help="Car profile name from cars.json to use for overrides",
    )
    parser.add_argument(
        "-p",
        "--port",
        type=int,
        default=4001,
        help="Port to run the mock server on (default: 4001)",
    )
    parser.add_argument(
        "--host",
        default="0.0.0.0",
        help="Host to bind to (default: 0.0.0.0)",
    )
    parser.add_argument(
        "--cars-file",
        type=Path,
        default=Path(__file__).parent / "cars.json",
        help="Path to cars configuration JSON file (default: cars.json)",
    )
    parser.add_argument(
        "--list-cars",
        action="store_true",
        help="List available car profiles and exit",
    )

    charging_group = parser.add_argument_group("charging simulation")
    charging_group.add_argument(
        "--charging",
        action="store_true",
        help="Simulate an ongoing charge session on the /status endpoint",
    )
    charging_group.add_argument(
        "--charging-dc",
        action="store_true",
        help="Simulate DC fast charging instead of AC (default: AC 3-phase 11 kW)",
    )
    charging_group.add_argument(
        "--charging-start-soc",
        type=int,
        default=40,
        metavar="PCT",
        help="Starting battery %% when the server starts (default: 40)",
    )
    charging_group.add_argument(
        "--charging-limit-soc",
        type=int,
        default=80,
        metavar="PCT",
        help="Charge limit / target battery %% (default: 80)",
    )
    charging_group.add_argument(
        "--charging-power",
        type=int,
        default=None,
        metavar="KW",
        help="Charger power in kW (default: 11 for AC, 150 for DC)",
    )

    args = parser.parse_args()

    # Load cars configuration
    cars_config = load_cars_config(args.cars_file)

    # List cars mode
    if args.list_cars:
        print("Available car profiles:")
        for name, car in cars_config.items():
            details = car.get("car_details", {})
            exterior = car.get("car_exterior", {})
            model = details.get("model", "?")
            trim = details.get("trim_badging", "?")
            color = exterior.get("exterior_color", "?")
            wheels = exterior.get("wheel_type", "?")
            print(f"  {name}:")
            print(f"    Model {model} ({trim}) - {color}, {wheels}")
        sys.exit(0)

    # Validate car selection
    if args.car not in cars_config:
        print(f"Error: Car profile '{args.car}' not found in {args.cars_file}", file=sys.stderr)
        print(f"Available profiles: {', '.join(cars_config.keys())}", file=sys.stderr)
        sys.exit(1)

    # Validate charging SOC values
    if args.charging:
        if not (0 <= args.charging_start_soc <= 100):
            print("Error: --charging-start-soc must be between 0 and 100", file=sys.stderr)
            sys.exit(1)
        if not (0 <= args.charging_limit_soc <= 100):
            print("Error: --charging-limit-soc must be between 0 and 100", file=sys.stderr)
            sys.exit(1)
        if args.charging_start_soc >= args.charging_limit_soc:
            print("Error: --charging-start-soc must be less than --charging-limit-soc", file=sys.stderr)
            sys.exit(1)

    # Configure the server
    config["upstream_url"] = args.upstream.rstrip("/")
    config["car_overrides"] = cars_config[args.car]
    config["charging"] = {
        "enabled": args.charging,
        "dc": args.charging_dc,
        "start_soc": args.charging_start_soc,
        "limit_soc": args.charging_limit_soc,
        "power_kw": args.charging_power,
        "start_time": time.time(),
    }

    effective_power = args.charging_power or (150 if args.charging_dc else 11)

    print("Starting Teslamate Mock Server")
    print(f"  Upstream: {config['upstream_url']}")
    print(f"  Car profile: {args.car}")
    print(f"  Overrides: {json.dumps(config['car_overrides'], indent=2)}")
    if args.charging:
        charger_type = f"DC {effective_power} kW" if args.charging_dc else f"AC 3-phase {effective_power} kW"
        print(f"  Charging simulation: {args.charging_start_soc}% → {args.charging_limit_soc}% ({charger_type})")
    print(f"  Listening on: http://{args.host}:{args.port}")
    print()

    app.run(host=args.host, port=args.port, debug=False)


if __name__ == "__main__":
    main()
