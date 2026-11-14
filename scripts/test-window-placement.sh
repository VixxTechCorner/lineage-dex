#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
java_bin="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}/bin"
[[ -x "$java_bin/javac" ]] || { echo 'Set JAVA_HOME to a JDK 17 installation.' >&2; exit 1; }
classes="$(mktemp -d)"
trap 'rm -rf -- "$classes"' EXIT
"$java_bin/javac" --release 17 -d "$classes" app/src/main/java/dev/lineagedex/WindowPlacement.java tests/WindowPlacementTest.java
"$java_bin/java" -cp "$classes" dev.lineagedex.WindowPlacementTest
