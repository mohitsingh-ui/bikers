#!/usr/bin/env bash
# One-command debug APK build for macOS / Linux.
# Prereqs: JDK 17 and the Android SDK installed, with ANDROID_HOME set
# (or a local.properties with sdk.dir=...). See README "Getting the APK".
set -e
cd "$(dirname "$0")"

chmod +x ./gradlew 2>/dev/null || true
./gradlew :app:assembleDebug --no-daemon

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
  echo ""
  echo "✅ APK built: $(pwd)/$APK"
  echo "   Install on a connected phone with:  adb install -r \"$APK\""
else
  echo "Build finished but APK not found at $APK" >&2
  exit 1
fi
