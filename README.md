# Lineage DeX — prototype 0.4.1

[All archived APKs](RELEASES.md) · [GitHub releases](https://github.com/VixxTechCorner/lineage-dex/releases) · [Publication workflow](PUBLISHING.md)

An independent Android desktop experiment for the Lenovo TB-X306X with LineageOS 20 / Android 13. Not affiliated with LineageOS or Samsung. Native Java and Android views, without Google services or runtime libraries.

Version 0.4.1 explicitly requests freeform launch mode on Android 10–13, rather than only requesting window bounds. Apps opened normally from the monitor desktop now default to floating mode. Desktop backgrounds remain fullscreen, and Open normally explicitly requests fullscreen. The native LineageOS taskbar stays under the system launcher's control.

## One-time multitasking setup

The old bounds-only implementation could launch both apps fullscreen, leaving only the last app visible. Before testing the updated window modes, enable the underlying Android developer settings from your authorized PC:

```bash
cd ~/lineage-dex
python scripts/setup-multitasking.py enable
```

This backs up the original values for `enable_freeform_support`, `force_desktop_mode_on_external_displays`, and `force_resizable_activities`, then sets each to 1 and verifies the writes. Backups are saved under the ignored `logs/` folder and tied to the tablet serial number. Repeated enable does not replace the original backup. Save open work, then run `adb reboot`. Wait for Android to finish booting, unlock it, and restart the monitor session.

To inspect or restore the original values:

```bash
python scripts/setup-multitasking.py status
python scripts/setup-multitasking.py restore
```

Reboot after restoring as well. No automatic reboot, force-stop, or wipe occurs in the setup script. If more than one ADB device is connected, select the tablet with `ANDROID_SERIAL` before running it.

The launch-mode request uses AOSP's internal `android.activity.windowingMode` bundle field on Android 10–13, with mode 5 for freeform and mode 1 for fullscreen. It avoids hidden-method reflection, but is still a version-specific compatibility path, not a guaranteed public API. Android continues to enforce permissions and window support; newer OS versions use the bounds-only fallback. The GSI must support freeform windows for this to work.

AOSP field reference: https://android.googlesource.com/platform/frameworks/base/+/8b1e072210796e9772d3d07bdbae424b38447db6/core/java/android/app/ActivityOptions.java

## Multitasking

Open **Multitask → Open two apps side by side**, choose Android split screen or floating halves, then choose two different apps. Both are requested on the current display. Split screen uses Android's adjacent-launch flag; floating halves request left/right launch bounds. Placement and multi-window availability depend on the ROM and the target apps; a successful launch does not guarantee that Android accepted the requested window arrangement. Existing tasks may keep their previous bounds. If needed, arrange the apps through Android Recents or the native taskbar.

Hold an icon in the desktop or app drawer to choose **Open normally**, **Floating window**, **Left half**, **Right half**, or **Split screen beside desktop**. Floating launches cascade so new window requests do not all use identical bounds. Enable freeform windows in Developer options and reboot before testing floating layouts. The app does not bypass system window restrictions or require root.

**Multitask → Recent launches** remembers up to twelve apps opened through Lineage DeX. It does not inspect all running tasks or promise to track apps launched elsewhere. Reopening an entry requests the existing app task when Android allows it. You can clear this local history from the Multitask menu.

Keyboard shortcuts while the desktop has focus: **Ctrl+Tab** opens recent launches, **Ctrl+Space** opens apps, and **Ctrl+M** opens multitasking. The original system taskbar remains responsible for system-wide task switching, minimizing and closing apps.

Android multi-window reference: https://developer.android.com/develop/ui/views/layout/support-multi-window-mode

## Desktop and original LineageOS taskbar

Version 0.3 removes the custom bottom dock from the desktop, stops the previous overlay on desktop startup, and removes the custom dock controls. App pins from previous versions become desktop shortcuts. It also removes the Home intent filter so Lineage DeX does not replace the system launcher. Keep Trebuchet (or your original system launcher) as Home. If Android asks you to choose Home after upgrading, select the original launcher.

The desktop has a blue-violet background, real app shortcuts with labels, a compact searchable app drawer, a clock, and settings. Hold an app in the drawer to add/remove a shortcut. The native Android status/navigation bars are kept visible. This app does not imitate or draw the original taskbar: its availability, especially on a virtual secondary display, is controlled by the ROM/system launcher. We cannot guarantee that enabling system decorations on scrcpy makes Trebuchet's taskbar available there.

Android manages app windows, captions, resizing and task switching. Floating windows remain experimental: enable freeform windows in Developer options, reboot, and enable the window request in desktop settings. The ROM may still launch apps fullscreen. This is not a full window manager, Samsung DeX implementation, or ROM modification.

## Build on Arch

```bash
sudo pacman -Syu jdk17-openjdk curl unzip android-tools
cd ~/lineage-dex
bash build.sh
bash scripts/install.sh
```

Review/accept the SDK licenses when prompted. The build script downloads the Android SDK and Gradle, builds the debug APK, and runs Android lint. Output: `app/build/outputs/apk/debug/app-debug.apk`. Preserve your debug signing key for updates. The SDK defaults to `.tools/android-sdk`, or use `ANDROID_HOME`.

The locally packaged 0.4.1 update is at `app/build/outputs/apk/multitasking/lineage-dex-0.4.1-debug.apk`. To install it:

```bash
bash scripts/install.sh app/build/outputs/apk/multitasking/lineage-dex-0.4.1-debug.apk
```

No firmware, partition, bootloader or root changes are involved.

## Monitor mode through your Arch PC

The TB-X306X USB-C port is documented for data/charging, without DisplayPort output. A normal USB-C–HDMI adapter or charging cable connected directly to a monitor should not be expected to provide video. Through a PC, use a USB cable that supports data. scrcpy creates an independent Android display and shows it in a PC window that you can move to a monitor.

```bash
sudo pacman -Syu scrcpy
cd ~/lineage-dex
bash scripts/monitor.sh usb
```

The script now uses **1920×1080 (1080p)** at 160 DPI, with 30 FPS / 8 Mbps video. Close the previous scrcpy window and start a new session to change its resolution. Android system decorations are left enabled. Optional fullscreen: `bash scripts/monitor.sh usb --fullscreen`.

For Wi-Fi, put the PC and tablet on the same network. In Android Developer options → Wireless debugging → Pair device with pairing code, get the pairing address, then:

```bash
adb pair TABLET_IP:PAIRING_PORT
```

Enter the pairing code when prompted. Use the separate connection address shown on the main Wireless debugging page:

```bash
adb connect TABLET_IP:DEBUG_PORT
bash scripts/monitor.sh wifi
```

Replace uppercase placeholders with the displayed IP addresses/ports. The pairing and connection ports are usually different. These scripts do not enable legacy port 5555. USB can be disconnected after wireless ADB connects. If more than one network device is connected, disconnect the extra devices before using `wifi`.

Close scrcpy to end the monitor session. Apps are moved back to the tablet when supported by `--no-vd-destroy-content`; save work first. The monitor desktop has a separate task from tablet Home. App launch permissions still depend on Android. Direct wireless-to-TV sessions require support in the ROM and receiver; this project does not implement a Miracast transmitter. Automatic display detection works while the desktop is visible, not as an always-running monitor service.

Hardware reference: https://psref.lenovo.com/Product/Lenovo_Tablets/Tab_M10_HD_2nd_Gen

API reference: https://developer.android.com/reference/android/app/ActivityOptions#setLaunchDisplayId(int)

## Validation

The 0.4.1 APK is compiled with JDK 17 against Android SDK 35 using aapt2, javac, d8, zipalign and apksigner. The build checks that its signing certificate matches the original debug APK. Window geometry previously passed 630 combinations of display size, density, layout and cascade index, with checks for nonempty in-display bounds, nonoverlapping halves, invalid display rejection and different cascade positions. Run these tests with `bash scripts/test-window-placement.sh`.

Six setup-script tests pass for original-value restoration, absent-setting deletion, repeated enable, partial failure recovery, read-only status, and wrong-device rejection. Run `python -m unittest discover -s tests -p 'test_*.py'`. Mocked USB/Wi-Fi script checks verify the 1080p dimensions, bitrate, device selection and forwarded fullscreen option; these do not test video encoding on hardware.

This environment cannot access the tablet's USB bus or visually test the screen. Run the standard build script on the host for full Android lint. Multitasking requests, launch history, keyboard shortcuts, monitor routing and native taskbar appearance need device testing. Geometry tests do not establish that the ROM honors the requested window layouts.

Device checks: existing pins become shortcuts; app search/launch works; old custom overlay is gone; Home uses the original launcher; normal taskbar behavior; USB and Wi-Fi monitor sessions; rotation; app window behavior; monitor disconnection. Keep the original launcher installed.
