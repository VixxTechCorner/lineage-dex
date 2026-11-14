#!/usr/bin/env bash
set -euo pipefail
if ! command -v scrcpy >/dev/null; then
    echo 'Install scrcpy: sudo pacman -Syu scrcpy' >&2; exit 1
fi
scrcpy_help="$(scrcpy --help)"
if [[ "$scrcpy_help" != *'--new-display'* || "$scrcpy_help" != *'--start-app'* ]]; then
    echo 'Update scrcpy: this script needs virtual display and app launching support.' >&2; exit 1
fi
mode="${1:-usb}"
args=(--new-display=1920x1080/160 --start-app=dev.lineagedex --window-title='Lineage DeX' --max-fps=30 --video-bit-rate=8M --no-vd-destroy-content)
case "$mode" in
    usb) args+=(--select-usb) ;;
    wifi)
        echo 'Use Android Wireless debugging to pair/connect first (see README).'
        args+=(--select-tcpip) ;;
    *) echo 'Usage: bash scripts/monitor.sh [usb|wifi] [additional scrcpy options]' >&2; exit 1 ;;
esac
if (( $# > 0 )); then shift; fi
project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "$project_dir/logs"
log_file="$project_dir/logs/monitor-$(date +%Y%m%d-%H%M%S).log"
exec > >(tee "$log_file") 2>&1
echo "Starting Lineage DeX monitor session ($mode): 1920x1080, 160 DPI."
echo "Session log: $log_file"
exec scrcpy "${args[@]}" "$@"
