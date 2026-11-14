#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
apk="${1:-app/build/outputs/apk/debug/app-debug.apk}"
[[ -f "$apk" ]] || { echo 'Run bash build.sh first.' >&2; exit 1; }
command -v adb >/dev/null || { echo 'Install android-tools first.' >&2; exit 1; }
# ANDROID_SERIAL can select a particular device if multiple devices are connected.
adb get-state
adb install -r "$apk"
adb shell am start -n dev.lineagedex/.DesktopActivity
