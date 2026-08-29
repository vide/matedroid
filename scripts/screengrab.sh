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
#   - At least one device visible to `adb devices`.
#   - .env contains TESLAMATE_API_URL for the real upstream.
#
# Targeting a specific device:
#   - Honors the standard `ANDROID_SERIAL` environment variable. Set it (e.g.
#     in .env) when you have multiple devices connected and want to pin the
#     screenshot run to one of them. With ANDROID_SERIAL set, every adb call
#     in this script and the adb client inside the container both target it.
#   - With no ANDROID_SERIAL and exactly one device connected, that device
#     is used. With multiple devices and no ANDROID_SERIAL, the script bails
#     to avoid grabbing the wrong one.
#
# Usage:
#   ./scripts/screengrab.sh [car_profile]
#
#   car_profile defaults to "modely_juniper_perf_white" — see mockserver/cars.json.
#
# Note: this is the new Fastlane-based pipeline. The older
# scripts/take-screenshots.sh (adb screencap + imagemagick) still works and
# remains the source of the existing docs/screenshots/*.jpg files until this
# one fully covers all 13 target screens.

set -euo pipefail
# Propagate failures through pipelines (we tee output) so the script's exit
# code reflects the underlying tool, not tee's status.

cd "$(dirname "$0")/.."

car_profile="${1:-modely_juniper_perf_white}"
mock_port=4002
image_tag="matedroid-screengrab"

# --- Sanity checks ---------------------------------------------------------

if ! command -v docker >/dev/null 2>&1; then
  echo "✗ docker is required on PATH (this script runs fastlane inside a container)." >&2
  exit 1
fi

if [ ! -f .env ]; then
  echo "✗ .env not found — TESLAMATE_API_URL is needed to seed the mock with real upstream history." >&2
  exit 1
fi
# shellcheck disable=SC1091
source .env

# Pick the device. ANDROID_SERIAL (from env, or .env above) wins; otherwise
# require exactly 1 connected device so we don't grab the wrong one when the
# user has multiple paired (e.g. a phone over USB and another over ADB-WiFi).
if [ -n "${ANDROID_SERIAL:-}" ]; then
  if ! adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$ANDROID_SERIAL"; then
    echo "✗ ANDROID_SERIAL=$ANDROID_SERIAL is set but that device is not online." >&2
    adb devices >&2
    exit 1
  fi
  echo "→ Targeting device $ANDROID_SERIAL"
else
  device_count=$(adb devices | awk 'NR>1 && $2=="device"' | wc -l)
  if [ "$device_count" -ne 1 ]; then
    echo "✗ Found $device_count connected ADB devices — set ANDROID_SERIAL to pick one." >&2
    adb devices >&2
    exit 1
  fi
  ANDROID_SERIAL=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
  export ANDROID_SERIAL
fi

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
  adb -s "$ANDROID_SERIAL" shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
    -a com.matedroid.SET_ENDPOINT --es url "$TESLAMATE_API_URL" >/dev/null || true
  if [ -n "${original_locale:-}" ]; then
    echo "→ Restoring device locale to $original_locale"
    adb -s "$ANDROID_SERIAL" shell "setprop persist.sys.locale $original_locale; setprop ctl.restart zygote" >/dev/null || true
  fi
}
trap cleanup EXIT

# Give the mock a moment to bind.
sleep 2
if ! curl -fsS "${mock_url}/cars" >/dev/null 2>&1; then
  echo "✗ Mock did not come up — see /tmp/matedroid-mock.log" >&2
  exit 1
fi

echo "→ Pointing app at mock: $mock_url"
adb -s "$ANDROID_SERIAL" shell am broadcast -n com.matedroid/.receiver.DebugEndpointReceiver \
  -a com.matedroid.SET_ENDPOINT --es url "$mock_url" >/dev/null

# Wake the screen + dismiss the keyguard. The test's @Before does this too,
# but doing it from the script as well means the device's display is on for
# the entire run — without this the screen tends to time out between APK
# install and test start, and screengrab grabs a black frame.
echo "→ Waking device $ANDROID_SERIAL"
adb -s "$ANDROID_SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null
adb -s "$ANDROID_SERIAL" shell wm dismiss-keyguard >/dev/null
adb -s "$ANDROID_SERIAL" shell svc power stayon true >/dev/null

# Force the device's system locale to en-US for the run. screengrab's
# LocaleTestRule retargets only the *test* APK's resources — the app under
# test runs in its own process (under instrumentation) and reads the system
# locale at process-start, so without this the captured screenshots come
# out in whatever language the device was set to (Italian, Spanish, …).
# Force-stopping com.matedroid below ensures the next launch picks up the
# new locale instead of reusing a process started under the old one.
# cleanup() restores the original locale.
original_locale=$(adb -s "$ANDROID_SERIAL" shell getprop persist.sys.locale | tr -d '\r')
if [ "$original_locale" != "en-US" ]; then
  echo "→ Switching device locale: $original_locale → en-US"
  adb -s "$ANDROID_SERIAL" shell setprop persist.sys.locale en-US >/dev/null
  adb -s "$ANDROID_SERIAL" shell am force-stop com.matedroid >/dev/null
fi

# --- Run fastlane screengrab inside the container -------------------------

echo "→ Running fastlane screengrab in Docker (target: $ANDROID_SERIAL)"
docker run --rm \
  --network host \
  --user "$(id -u):$(id -g)" \
  -e "ANDROID_SERIAL=$ANDROID_SERIAL" \
  -v "$PWD":/workspace \
  "$image_tag"

# Screengrab appends a millisecond timestamp to each PNG so re-runs don't
# collide. Strip it so the committed filenames stay stable
# (01-main-dashboard.png, not 01-main-dashboard_1777548982770.png).
echo "→ Normalizing screenshot filenames"
shopt -s nullglob
for f in fastlane/metadata/android/*/images/phoneScreenshots/*_[0-9]*.png; do
  mv -f "$f" "${f%_[0-9]*.png}.png"
done
shopt -u nullglob

echo "✓ Screenshots written to fastlane/metadata/android/<locale>/images/phoneScreenshots/"
