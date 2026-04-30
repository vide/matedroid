#!/usr/bin/env bash
#
# End-to-end Fastlane screengrab driver. Runs fastlane inside a throw-away
# Docker container so the host laptop doesn't need ruby/bundler/fastlane.
#
# Flow:
#   1. Build the debug app + androidTest APK on the host (uses the existing
#      Gradle + Android SDK setup).
#   2. Build (or reuse cached) the matedroid-screengrab Docker image.
#   3. Start the Teslamate API mock with a sensible profile + an active DC charge
#      so the live-charge / charges / drives / trips screens have real-shaped data.
#   4. Point the connected device at the mock via the debug ADB receiver.
#   5. Run `fastlane screengrab` inside the container — the host's adb-server
#      is reused via --network host, so the device the host is paired with
#      shows up inside the container automatically.
#   6. Tear the mock down and point the app back at the real Teslamate API.
#
# Prereqs:
#   - Docker installed (no ruby/bundler needed on the host).
#   - Physical device or emulator visible to `adb devices` (only one online).
#   - .env contains TESLAMATE_API_URL for the real upstream.
#
# Usage:
#   ./scripts/screengrab.sh [car_profile]
#
#   car_profile defaults to "white_juniper_performance" — see mockserver/cars.json.
#
# Note: this is the new Fastlane-based pipeline. The older
# scripts/take-screenshots.sh (adb screencap + imagemagick) still works and
# remains the source of the existing docs/screenshots/*.jpg files until this
# one fully covers all 13 target screens.

set -euo pipefail

cd "$(dirname "$0")/.."

car_profile="${1:-white_juniper_performance}"
mock_port=4002
image_tag="matedroid-screengrab"

# --- Sanity checks ---------------------------------------------------------

if ! command -v docker >/dev/null 2>&1; then
  echo "✗ docker is required on PATH (this script runs fastlane inside a container)." >&2
  exit 1
fi

device_count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
if [ "$device_count" -ne 1 ]; then
  echo "✗ Expected exactly 1 connected ADB device, found $device_count." >&2
  echo "  adb devices output:" >&2
  adb devices >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "✗ .env not found — TESLAMATE_API_URL is needed to seed the mock with real upstream history." >&2
  exit 1
fi
# shellcheck disable=SC1091
source .env

local_ip=$(hostname -I | awk '{print $1}')
mock_url="http://${local_ip}:${mock_port}"

# --- Build APKs ------------------------------------------------------------

echo "→ Assembling debug APK + androidTest APK"
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest

# --- Build (cached) screengrab Docker image --------------------------------

echo "→ Building screengrab Docker image (${image_tag})"
docker build -q -t "$image_tag" -f docker/screengrab/Dockerfile . >/dev/null

# --- Start mock + redirect app --------------------------------------------

echo "→ Starting Teslamate mock with profile '${car_profile}' + active DC charge on :${mock_port}"
mockserver/server.py \
  -u "$TESLAMATE_API_URL" \
  -c "$car_profile" \
  -p "$mock_port" \
  --charging --charging-dc --charging-start-soc 32 --charging-limit-soc 80 \
  > /tmp/matedroid-mock.log 2>&1 &
mock_pid=$!

cleanup() {
  echo "→ Stopping mock (pid $mock_pid)"
  kill "$mock_pid" 2>/dev/null || true
  wait "$mock_pid" 2>/dev/null || true
  echo "→ Pointing app back at real Teslamate API"
  adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
    -a com.matedroid.SET_ENDPOINT --es url "$TESLAMATE_API_URL" >/dev/null
}
trap cleanup EXIT

# Give the mock a moment to bind.
sleep 2
if ! curl -fsS "${mock_url}/cars" >/dev/null 2>&1; then
  echo "✗ Mock did not come up — see /tmp/matedroid-mock.log" >&2
  exit 1
fi

echo "→ Pointing app at mock: $mock_url"
adb shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
  -a com.matedroid.SET_ENDPOINT --es url "$mock_url" >/dev/null

# --- Run fastlane screengrab inside the container -------------------------

echo "→ Running fastlane screengrab in Docker"
docker run --rm \
  --network host \
  --user "$(id -u):$(id -g)" \
  -v "$PWD":/workspace \
  "$image_tag"

echo "✓ Screenshots written to fastlane/metadata/android/<locale>/images/phoneScreenshots/"
