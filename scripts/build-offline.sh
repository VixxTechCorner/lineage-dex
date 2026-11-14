#!/usr/bin/env bash
# Build with the installed SDK without starting Gradle's local socket server.
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}"
export PATH="$JAVA_HOME/bin:$PATH"
sdk="${ANDROID_HOME:-$PWD/.tools/android-sdk}"
bt="$sdk/build-tools/34.0.0"
android="$sdk/platforms/android-35/android.jar"
work="$(mktemp -d "$PWD/app/build/offline.XXXXXX")"
trap 'rm -rf -- "$work"' EXIT
mkdir -p "$work/gen" "$work/classes" "$work/dex" app/build/outputs/apk/debug
version_code="$(sed -n 's/.*versionCode \([0-9]*\).*/\1/p' app/build.gradle)"
version_name="$(sed -n "s/.*versionName '\([^']*\)'.*/\1/p" app/build.gradle)"
python - "$work/AndroidManifest.xml" <<'PYMANIFEST'
from pathlib import Path
import sys
source=Path('app/src/main/AndroidManifest.xml').read_text()
Path(sys.argv[1]).write_text(source.replace('<manifest ', '<manifest package="dev.lineagedex" ', 1))
PYMANIFEST
"$bt/aapt2" compile --dir app/src/main/res -o "$work/resources.zip"
"$bt/aapt2" link -o "$work/base.apk" -I "$android" --manifest "$work/AndroidManifest.xml" \
  --java "$work/gen" --min-sdk-version 29 --target-sdk-version 33 \
  --version-code "$version_code" --version-name "$version_name" "$work/resources.zip"
find app/src/main/java "$work/gen" -name '*.java' > "$work/sources.txt"
javac --release 17 -cp "$android" -d "$work/classes" @"$work/sources.txt"
jar cf "$work/classes.jar" -C "$work/classes" .
"$bt/d8" --min-api 29 --lib "$android" --output "$work/dex" "$work/classes.jar"
python - "$work/base.apk" "$work/dex/classes.dex" <<'PY'
import sys, zipfile
with zipfile.ZipFile(sys.argv[1], 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(sys.argv[2], 'classes.dex')
PY
"$bt/zipalign" -f 4 "$work/base.apk" "$work/aligned.apk"
keystore="${ANDROID_DEBUG_KEYSTORE:-${ANDROID_USER_HOME:-${XDG_CONFIG_HOME:-$HOME/.config}/.android}/debug.keystore}"
if [[ ! -f "$keystore" && -f "$HOME/.android/debug.keystore" ]]; then keystore="$HOME/.android/debug.keystore"; fi
"$bt/apksigner" sign --ks "$keystore" --ks-pass pass:android --key-pass pass:android \
  --out app/build/outputs/apk/debug/app-debug.apk "$work/aligned.apk"
"$bt/apksigner" verify --verbose app/build/outputs/apk/debug/app-debug.apk
printf 'Built version %s: %s/app/build/outputs/apk/debug/app-debug.apk\n' "$version_name" "$PWD"
