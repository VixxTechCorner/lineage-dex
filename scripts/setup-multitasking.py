#!/usr/bin/env python3
"""Enable/restore Android developer window settings through the user's authorized ADB."""
import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

SETTINGS = (
    "enable_freeform_support",
    "force_desktop_mode_on_external_displays",
    "force_resizable_activities",
)


def adb(arguments):
    return subprocess.run(["adb", *arguments], check=True, text=True,
                          stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=25).stdout.strip()


def validate_backup(data, serial):
    if data.get("serial") != serial or set(data.get("values", {})) != set(SETTINGS):
        raise ValueError("Backup does not match this device or settings")
    if any(not isinstance(v, str) or not re.fullmatch(r"null|-?\d+", v)
           for v in data["values"].values()):
        raise ValueError("Invalid settings backup")
    return data["values"]


def configure(action, run=adb, backup_dir=None):
    run(["get-state"])
    serial = run(["shell", "getprop", "ro.serialno"])
    if not serial or not re.fullmatch(r"[A-Za-z0-9._:-]+", serial):
        raise ValueError("Cannot identify the device; no settings changed")
    backup_dir = Path(backup_dir) if backup_dir else Path(__file__).resolve().parents[1] / "logs"
    backup = backup_dir / ("multitasking-settings-" + re.sub(r"[^A-Za-z0-9._-]", "_", serial) + ".json")
    if action == "status":
        return {key: run(["shell", "settings", "get", "global", key]) for key in SETTINGS}
    if action == "enable":
        if backup.exists():
            validate_backup(json.loads(backup.read_text()), serial)
        else:
            original = {key: run(["shell", "settings", "get", "global", key]) for key in SETTINGS}
            data = {"serial": serial, "values": original}
            validate_backup(data, serial)
            backup_dir.mkdir(parents=True, exist_ok=True)
            # Save before any mutation; never overwrite the original on repeated enable.
            with backup.open("x") as stream:
                json.dump(data, stream, indent=2)
        for key in SETTINGS:
            run(["shell", "settings", "put", "global", key, "1"])
            if run(["shell", "settings", "get", "global", key]) != "1":
                raise RuntimeError("Android did not accept " + key + "; backup kept at " + str(backup))
        return {"result": "enabled", "backup": str(backup), "next": "Save app work, reboot the tablet with adb reboot, then restart monitor.sh."}
    if not backup.exists():
        raise ValueError("No saved settings backup exists for this device")
    original = validate_backup(json.loads(backup.read_text()), serial)
    for key, value in original.items():
        command = ["shell", "settings", "delete", "global", key] if value == "null" else ["shell", "settings", "put", "global", key, value]
        run(command)
        if run(["shell", "settings", "get", "global", key]) != value:
            raise RuntimeError("Could not restore " + key + "; backup kept")
    backup.unlink()
    return {"result": "restored", "next": "Save app work and reboot the tablet to apply the restored settings."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("enable", "restore", "status"))
    action = parser.parse_args().action
    try:
        print(json.dumps(configure(action), indent=2))
    except (OSError, ValueError, RuntimeError, subprocess.SubprocessError) as error:
        print("Multitasking setup failed: " + str(error), file=sys.stderr)
        if isinstance(error, subprocess.CalledProcessError) and error.stderr:
            print(error.stderr.strip(), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
