#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")"
project_dir="$PWD"
if [[ -x /usr/lib/jvm/java-17-openjdk/bin/java ]]; then
    export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
fi
if [[ -n "${JAVA_HOME:-}" ]]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if ! command -v java >/dev/null || ! command -v javac >/dev/null; then
    echo 'Install JDK 17 first: sudo pacman -Syu jdk17-openjdk curl unzip' >&2; exit 1
fi
java_version="$(java -version 2>&1 | head -1)"
if [[ ! "$java_version" =~ \"17\. ]]; then
    echo "This build expects JDK 17. Set JAVA_HOME to your JDK 17 directory. Found: $java_version" >&2; exit 1
fi
for utility in curl unzip; do command -v "$utility" >/dev/null || { echo "Missing $utility" >&2; exit 1; }; done
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$project_dir/.tools/android-sdk}}"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
mkdir -p "$project_dir/.tools" "$ANDROID_HOME"
fetch() {
    curl --fail --location --retry 3 --connect-timeout 20 "$1" --output "$2.part"
    mv -- "$2.part" "$2"
}
sdkmanager="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
    fetch 'https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip' .tools/cmdline-tools.zip
    unzip -tq .tools/cmdline-tools.zip
    staging="$(mktemp -d "$project_dir/.tools/sdk.XXXXXX")"
    unzip -q .tools/cmdline-tools.zip -d "$staging"
    mkdir -p "$ANDROID_HOME/cmdline-tools"
    mv -- "$staging/cmdline-tools" "$ANDROID_HOME/cmdline-tools/latest"
    rmdir -- "$staging"
fi
echo 'Review and accept the Android SDK licenses below to continue.'
"$sdkmanager" --sdk_root="$ANDROID_HOME" --licenses
"$sdkmanager" --sdk_root="$ANDROID_HOME" 'platforms;android-35' 'build-tools;34.0.0'
if [[ ! -f "$ANDROID_HOME/platforms/android-35/android.jar" || ! -x "$ANDROID_HOME/build-tools/34.0.0/aapt2" ]]; then
    echo 'Android SDK installation is incomplete. Review SDK license prompts and answer y to accept if you agree.' >&2
    echo 'Also check sdkmanager output above for download/network errors, then rerun bash build.sh.' >&2
    exit 1
fi
if [[ ! -x .tools/gradle-8.9/bin/gradle ]]; then
    fetch 'https://services.gradle.org/distributions/gradle-8.9-bin.zip' .tools/gradle.zip
    unzip -tq .tools/gradle.zip
    unzip -qo .tools/gradle.zip -d .tools
fi
.tools/gradle-8.9/bin/gradle --no-daemon :app:assembleDebug :app:lintDebug
echo "APK: $project_dir/app/build/outputs/apk/debug/app-debug.apk"
