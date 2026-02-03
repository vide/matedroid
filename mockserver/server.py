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
from pathlib import Path

import httpx
from flask import Flask, Response, request

app = Flask(__name__)

# Global configuration
config = {
    "upstream_url": "",
    "car_overrides": {},
}


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
        default="127.0.0.1",
        help="Host to bind to (default: 127.0.0.1)",
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

    # Configure the server
    config["upstream_url"] = args.upstream.rstrip("/")
    config["car_overrides"] = cars_config[args.car]

    print("Starting Teslamate Mock Server")
    print(f"  Upstream: {config['upstream_url']}")
    print(f"  Car profile: {args.car}")
    print(f"  Overrides: {json.dumps(config['car_overrides'], indent=2)}")
    print(f"  Listening on: http://{args.host}:{args.port}")
    print()

    app.run(host=args.host, port=args.port, debug=False)


if __name__ == "__main__":
    main()
