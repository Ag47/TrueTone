#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
: "${JAVA_HOME:?Set JAVA_HOME to JDK 17 or newer}"
: "${ANDROID_HOME:?Set ANDROID_HOME to your Android SDK}"
./gradlew :core:test :app:assembleDebug :app:lintDebug --console=plain
if [ "${1:-}" = "--device" ]; then
  ./gradlew :app:connectedDebugAndroidTest --console=plain
fi
