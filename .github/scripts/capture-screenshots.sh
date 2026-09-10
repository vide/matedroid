#!/usr/bin/env bash
#
# CI wrapper around scripts/screenshots.sh for the Screenshots workflow.
#
# The emulator-runner action executes its `script` input line by line with /bin/sh, so the
# retry and the logcat dump live here instead. Environment:
#   SCREENS           optional comma-separated spec ids (workflow input)
#   SCREENSHOT_CLOCK  optional ISO-8601 instant (workflow input; empty = script default)

set -uo pipefail

cd "$(dirname "$0")/../.."

args=()
if [ -n "${SCREENS:-}" ]; then
    args+=(--screens "$SCREENS")
fi

if ./scripts/screenshots.sh "${args[@]}"; then
    exit 0
fi
echo "::warning::Capture failed, retrying once"
if ./scripts/screenshots.sh "${args[@]}"; then
    exit 0
fi
adb logcat -d > logcat.txt || true
exit 1
