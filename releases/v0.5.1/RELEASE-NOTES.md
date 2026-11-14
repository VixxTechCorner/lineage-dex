# Lineage DeX 0.5.1 — Monitor windows and smoother desktop

Experimental build for the Lenovo TB-X306X running LineageOS 20 / Android 13. Independent project, not affiliated with LineageOS or Samsung.

- Added PC-assisted floating windows and side-by-side placement through an authorized ADB companion. Checks task reports for freeform mode and reports placement failures.
- Fixed tablet detection for GSI installations by checking vendor/ODM identity.
- Added shortcut cards, a cleaner desktop header, monitor resolution/DPI details and a keep-awake toggle.
- Removed the header Multitask button. Window controls remain in Settings → Window controls, Ctrl+M and app icon long-press actions.
- Added short desktop and drawer entrance/exit animations, staggered initial icons, and press feedback. Respects Android animation settings.
- Keeps 1920×1080 monitor mode and the original LineageOS taskbar.

## Install and use

Install `lineage-dex-0.5.1.apk`. It uses the same development signing certificate as previous releases, allowing an update installation. Keep the original system launcher as Home.

Extract the source ZIP on the Arch PC. If multitasking has not been configured:

```bash
python scripts/setup-multitasking.py enable
# Save open work before rebooting the tablet.
adb reboot
```

After reboot, unlock the tablet. Start monitor mode in one terminal:

```bash
bash scripts/monitor.sh usb
```

In another terminal in the extracted project, run:

```bash
python scripts/monitor-companion.py
```

Keep both terminals open. Use `--serial SERIAL` when multiple devices are connected. Wi-Fi works with authorized Wireless debugging. Choose Settings → Window controls → Open two apps side by side → Side-by-side windows. Type `desktop` in the companion terminal to return to the workspace.

## Validation and limits

APK compilation and signature verification passed. The certificate matches 0.4.1. All 22 Python tests and 630 window geometry checks passed. The user confirmed monitor multitasking works after enabling the three developer settings and rebooting. The new 0.5.1 animation appearance has not yet been visually confirmed on the tablet. Full Gradle lint could not run in the agent environment because local sockets are restricted.

Window behavior depends on the ROM and target apps; requested bounds can be constrained by app minimum sizes. This is a desktop app plus PC companion, not a privileged Android window manager. No root or Google services required. No bootloader or partition changes are involved.
