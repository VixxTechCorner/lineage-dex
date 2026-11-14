#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
command -v adb >/dev/null || { echo 'Install android-tools first.' >&2; exit 1; }
adb get-state >/dev/null
output="lineage-dex-crash-$(date +%Y%m%d-%H%M%S).log"
# Read the existing crash buffer without clearing logs or app data.
adb logcat -b crash -d -v threadtime > "$output"
if [[ ! -s "$output" ]]; then
    echo 'No crash entries found. Open Lineage DeX, reproduce the crash, then run this script again.'
    rm -- "$output"
    exit 1
fi
echo "Saved crash log: $PWD/$output"
echo 'Share the FATAL EXCEPTION block whose Process is dev.lineagedex, including all Caused by lines.'
echo 'This log may also contain crashes from other apps; only the Lineage DeX block is needed.'
