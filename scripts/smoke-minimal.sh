#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ARTIFACT_DIR="artifacts"
LOGCAT_PATH="$ARTIFACT_DIR/minimal-smoke-logcat.txt"
APP_ID="com.openjarvis.preview"
MAIN_ACTIVITY="$APP_ID/com.openjarvis.ui.MainActivity"

mkdir -p "$ARTIFACT_DIR"
: > "$LOGCAT_PATH"
[[ -f "$APK_PATH" ]] || { echo "Debug APK not found: $APK_PATH" >&2; exit 10; }

echo "Waiting for Android boot and Package Manager readiness."
adb wait-for-device
PACKAGE_READY=0
for ((attempt=1; attempt<=120; attempt++)); do
  BOOTED=""
  PACKAGE_SERVICE=""
  if BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"; then :; fi
  if PACKAGE_SERVICE="$(adb shell service check package 2>/dev/null | tr -d '\r')"; then :; fi
  if [[ "$BOOTED" == "1" && "$PACKAGE_SERVICE" == *"found"* ]] &&
     adb shell cmd package list packages >/dev/null 2>&1; then
    PACKAGE_READY=1
    echo "Android and Package Manager ready after ${attempt}s."
    break
  fi
  sleep 1
done
if [[ "$PACKAGE_READY" -ne 1 ]]; then
  echo "Package Manager did not become ready within 120 seconds." >&2
  if ! adb devices -l; then echo "Could not collect adb device diagnostics." >&2; fi
  if ! adb shell getprop sys.boot_completed; then echo "Could not read boot state." >&2; fi
  if ! adb shell service check package; then echo "Could not query Package Manager." >&2; fi
  if ! adb logcat -b all -d -t 1200 > "$LOGCAT_PATH"; then echo "Could not collect startup logcat." >&2; fi
  exit 15
fi

echo "Installing debug APK with bounded ADB reconnect retry."
INSTALL_OK=0
for ((attempt=1; attempt<=3; attempt++)); do
  if adb install -r "$APK_PATH"; then
    INSTALL_OK=1
    echo "APK install succeeded on attempt $attempt."
    break
  fi
  echo "APK install attempt $attempt failed; reconnecting emulator ADB and rechecking Package Manager." >&2
  if ! adb reconnect device; then echo "ADB reconnect command failed; retrying device wait anyway." >&2; fi
  adb wait-for-device
  sleep $((attempt * 5))
  if ! adb shell cmd package list packages >/dev/null 2>&1; then
    echo "Package Manager is still unavailable after reconnect." >&2
  fi
done
if [[ "$INSTALL_OK" -ne 1 ]]; then
  echo "APK installation failed after 3 attempts." >&2
  if ! adb devices -l; then echo "Could not collect adb device diagnostics." >&2; fi
  if ! adb shell service check package; then echo "Could not query Package Manager." >&2; fi
  if ! adb logcat -b all -d -t 1600 > "$LOGCAT_PATH"; then echo "Could not collect install logcat." >&2; fi
  exit 16
fi

echo "Pre-granting microphone permission so the first-run dialog cannot block the startup smoke test."
adb shell pm grant "$APP_ID" android.permission.RECORD_AUDIO

echo "Clearing logcat before app launch."
adb logcat -c

echo "Launching MainActivity."
adb shell am start -W -n "$MAIN_ACTIVITY"

echo "Waiting up to 60 seconds for MainActivity to be resumed."
RESUMED=""
for ((second=1; second<=60; second++)); do
  ACTIVITY_STATE="$(adb shell dumpsys activity activities | tr -d '\r')"
  if RESUMED="$(printf '%s\n' "$ACTIVITY_STATE" | grep -E 'mResumedActivity|topResumedActivity' | grep -F "$MAIN_ACTIVITY")"; then
    :
  else
    RESUMED=""
  fi
  if [[ -n "$RESUMED" ]]; then
    echo "MainActivity resumed after ${second}s: $RESUMED"
    break
  fi
  sleep 1
done

[[ -n "$RESUMED" ]] || {
  echo "MainActivity did not reach a resumed state within 60 seconds." >&2
  adb shell dumpsys activity activities
  exit 11
}

PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process is not running after resume." >&2; exit 12; }

echo "MainActivity is resumed and process PID is $PID. Observing for 10 seconds."
sleep 10

PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process crashed or exited during observation." >&2; exit 13; }

adb logcat -d > "$LOGCAT_PATH"
if grep -Fq "FATAL EXCEPTION" "$LOGCAT_PATH"; then
  echo "FATAL EXCEPTION detected in logcat." >&2
  grep -F -A 40 -B 5 "FATAL EXCEPTION" "$LOGCAT_PATH"
  exit 14
fi

echo "MINIMAL_ANDROID_SMOKE: PASS"
echo "Verified: APK installed, MainActivity resumed, process remained alive, and no FATAL EXCEPTION appeared in logcat."
