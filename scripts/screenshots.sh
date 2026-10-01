#!/usr/bin/env bash
#
# screenshots.sh - regenerate the README gallery from demo mode.
#
# Runs the ScreenshotsTest instrumented suite on the connected device or emulator, copies the
# images it produces into docs/screenshots/ and rewrites the gallery block in README.md from
# the manifest the suite writes. No server is involved: the suite puts the app into demo mode
# itself.
#
# Usage:
#   ./scripts/screenshots.sh [--screens id1,id2,...] [--no-readme] [--offline]
#
# Options:
#   --screens LIST   capture only these spec ids (see ScreenshotSpecs.kt); the README is still
#                    regenerated from the full manifest, so the other images stay as they are
#   --no-readme      copy the images but leave README.md alone
#   --offline        run Gradle offline (handy on a warm machine without network)
#
# Environment:
#   ANDROID_SERIAL    device to use; required when more than one is connected
#   SCREENSHOT_CLOCK  ISO-8601 instant both the device clock and the demo clock are pinned to,
#                     so the live charging session is in a known phase and a same-day re-run
#                     produces the same pixels. Default: today at 09:00:00Z. Set to "off" to
#                     leave the clocks alone. Pinning needs a rootable device (an emulator
#                     without Play Store); elsewhere the script warns and runs unpinned.
#   SCREENSHOT_WIDTH  width of the saved images in px (default 576, as in the README)
#
# The device is prepared for the run (light theme, 24h clock, animations off, screen kept on)
# and restored on exit. The locale is not touched: the committed pictures are en-US, which is
# the emulator default; on a device in another language the script only warns.

set -euo pipefail

cd "$(dirname "$0")/.."

PACKAGE="com.matedroid"
TEST_CLASS="com.matedroid.screenshots.ScreenshotsTest"
DOCS_DIR="docs/screenshots"
README="README.md"
OUTPUT_ROOT="app/build/outputs/connected_android_test_additional_output"
FALLBACK_DEVICE_DIR="/sdcard/Android/data/${PACKAGE}/files/screenshots"
START_MARKER="<!-- screenshots:start -->"
END_MARKER="<!-- screenshots:end -->"

SCREENS=""
UPDATE_README=1
GRADLE_EXTRA=()
SCREENSHOT_CLOCK="${SCREENSHOT_CLOCK:-$(date -u +%Y-%m-%d)T09:00:00Z}"
SCREENSHOT_WIDTH="${SCREENSHOT_WIDTH:-576}"

while [ $# -gt 0 ]; do
    case "$1" in
        --screens) SCREENS="$2"; shift 2 ;;
        --screens=*) SCREENS="${1#*=}"; shift ;;
        --no-readme) UPDATE_README=0; shift ;;
        --offline) GRADLE_EXTRA+=(--offline); shift ;;
        -h|--help) sed -n '2,40p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
        *) echo "Unknown option: $1" >&2; exit 2 ;;
    esac
done

log() { echo "→ $*"; }
warn() { echo "⚠ $*" >&2; }
die() { echo "✗ $*" >&2; exit 1; }

# --- Device selection -------------------------------------------------------------------

command -v adb >/dev/null || die "adb not found in PATH"

if [ -n "${ANDROID_SERIAL:-}" ]; then
    adb devices | awk 'NR>1 && $2=="device" {print $1}' | grep -qx "$ANDROID_SERIAL" \
        || die "ANDROID_SERIAL=$ANDROID_SERIAL is not online"
else
    mapfile -t online < <(adb devices | awk 'NR>1 && $2=="device" {print $1}')
    [ "${#online[@]}" -eq 1 ] || die "Found ${#online[@]} devices; set ANDROID_SERIAL to pick one"
    ANDROID_SERIAL="${online[0]}"
fi
export ANDROID_SERIAL
log "Device: $ANDROID_SERIAL ($(adb shell getprop ro.product.model | tr -d '\r'), API $(adb shell getprop ro.build.version.sdk | tr -d '\r'))"

sh() { adb shell "$@" | tr -d '\r'; }

# --- Device preparation, restored on exit --------------------------------------------------

orig_night="$(sh cmd uimode night | sed 's/.*: *//')"
orig_time_12_24="$(sh settings get system time_12_24)"
orig_window_anim="$(sh settings get global window_animation_scale)"
orig_transition_anim="$(sh settings get global transition_animation_scale)"
orig_animator="$(sh settings get global animator_duration_scale)"
orig_auto_time="$(sh settings get global auto_time)"
orig_hide_dialogs="$(sh settings get global hide_error_dialogs)"
clock_pinned=0

restore_setting() {
    # "null" is what `settings get` prints for an unset key.
    if [ "$2" = "null" ]; then adb shell settings delete "$1" >/dev/null 2>&1 || true
    else adb shell settings put "$1" "$2" >/dev/null 2>&1 || true; fi
}

cleanup() {
    log "Restoring device state"
    adb shell svc power stayon false >/dev/null 2>&1 || true
    [ -n "$orig_night" ] && adb shell cmd uimode night "$orig_night" >/dev/null 2>&1 || true
    restore_setting "system time_12_24" "$orig_time_12_24"
    restore_setting "global window_animation_scale" "$orig_window_anim"
    restore_setting "global transition_animation_scale" "$orig_transition_anim"
    restore_setting "global animator_duration_scale" "$orig_animator"
    restore_setting "global hide_error_dialogs" "$orig_hide_dialogs"
    if [ "$clock_pinned" = 1 ]; then
        restore_setting "global auto_time" "$orig_auto_time"
        adb unroot >/dev/null 2>&1 || true
        adb wait-for-device
    fi
}
trap cleanup EXIT

log "Preparing device: light theme, 24h clock, animations off"
adb shell cmd uimode night no >/dev/null
adb shell settings put system time_12_24 24
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
# A software-rendered emulator under load throws "System UI isn't responding" dialogs, which
# would end up in the pictures.
adb shell settings put global hide_error_dialogs 1
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb shell svc power stayon true

locale="$(sh getprop persist.sys.locale)"
if [ -n "$locale" ] && [ "$locale" != "en-US" ]; then
    warn "Device locale is '$locale'; the committed screenshots are en-US"
fi

runner_args=("class=${TEST_CLASS}" "screenshotWidth=${SCREENSHOT_WIDTH}")
[ -n "$SCREENS" ] && runner_args+=("screenshotScreens=${SCREENS}")

if [ "$SCREENSHOT_CLOCK" != "off" ]; then
    if adb root >/dev/null 2>&1 && adb wait-for-device && [ "$(sh id -u)" = "0" ]; then
        log "Pinning device clock to $SCREENSHOT_CLOCK"
        adb shell settings put global auto_time 0
        adb shell date -u "$(date -u -d "$SCREENSHOT_CLOCK" +%m%d%H%M%Y.%S)" >/dev/null
        adb shell am broadcast -a android.intent.action.TIME_SET >/dev/null 2>&1 || true
        clock_pinned=1
        runner_args+=("screenshotClock=${SCREENSHOT_CLOCK}")
    else
        warn "Device is not rootable; running with the real clock (live session state depends on the hour)"
    fi
fi

# --- Capture ----------------------------------------------------------------------------

gradle_args=(:app:connectedDebugAndroidTest "${GRADLE_EXTRA[@]}")
for arg in "${runner_args[@]}"; do
    gradle_args+=("-Pandroid.testInstrumentationRunnerArguments.${arg}")
done

rm -rf "$OUTPUT_ROOT"
log "Running $TEST_CLASS"
set +e
./gradlew "${gradle_args[@]}"
gradle_status=$?
set -e

# --- Collect ----------------------------------------------------------------------------

manifest="$(find "$OUTPUT_ROOT" -name manifest.tsv 2>/dev/null | head -1 || true)"
if [ -z "$manifest" ]; then
    # AGP did not hand the suite an output directory (older AGP, or run outside Gradle);
    # the suite then writes to the app's external files directory.
    warn "No pulled output under $OUTPUT_ROOT; pulling from $FALLBACK_DEVICE_DIR"
    pulled="$OUTPUT_ROOT/fallback"
    mkdir -p "$pulled"
    adb pull "$FALLBACK_DEVICE_DIR/." "$pulled/" >/dev/null || die "Nothing to pull either"
    manifest="$pulled/manifest.tsv"
fi
[ -f "$manifest" ] || die "manifest.tsv not found"
run_dir="$(dirname "$manifest")"

mkdir -p "$DOCS_DIR"
copied=0
missing=()
while IFS=$'\t' read -r file alt group; do
    [ -n "$file" ] || continue
    if [ -f "$run_dir/$file" ]; then
        cp "$run_dir/$file" "$DOCS_DIR/$file"
        copied=$((copied + 1))
    elif [ ! -f "$DOCS_DIR/$file" ]; then
        missing+=("$file")
    fi
done < "$manifest"
log "Copied $copied image(s) to $DOCS_DIR/"
[ "${#missing[@]}" -eq 0 ] || warn "In the gallery but captured by no run yet: ${missing[*]}"

for existing in "$DOCS_DIR"/*.jpg; do
    grep -q "^$(basename "$existing")	" "$manifest" || warn "Not in the gallery any more: $existing"
done

# --- README -----------------------------------------------------------------------------

if [ "$UPDATE_README" = 1 ]; then
    gallery="$(
        current=""
        while IFS=$'\t' read -r file alt group; do
            [ -n "$file" ] || continue
            if [ "$group" != "$current" ]; then
                [ -n "$current" ] && echo "</p>"
                echo "<p>"
                current="$group"
            fi
            echo "<img src=\"$DOCS_DIR/$file\" alt=\"$alt\" height=\"300\">"
        done < "$manifest"
        [ -n "$current" ] && echo "</p>"
    )"
    grep -q "$START_MARKER" "$README" && grep -q "$END_MARKER" "$README" \
        || die "$README has no $START_MARKER / $END_MARKER markers"
    awk -v block="$gallery" -v start="$START_MARKER" -v end="$END_MARKER" '
        index($0, start) { print; print block; skipping = 1; next }
        index($0, end)   { skipping = 0 }
        !skipping        { print }
    ' "$README" > "$README.tmp" && mv "$README.tmp" "$README"
    log "Gallery in $README regenerated"
fi

if [ "$gradle_status" -ne 0 ]; then
    die "Gradle exited with $gradle_status: some screens were not captured (see app/build/reports/androidTests/)"
fi
log "Done"
