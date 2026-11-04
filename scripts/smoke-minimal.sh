#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ARTIFACT_DIR="artifacts"
LOGCAT_PATH="$ARTIFACT_DIR/minimal-smoke-logcat.txt"

mkdir -p "$ARTIFACT_DIR"
: > "$LOGCAT_PATH"
[[ -f "$APK_PATH" ]] || { echo "Debug APK not found: $APK_PATH" >&2; exit 10; }

echo "Installing debug APK."
adb install -r "$APK_PATH"

echo "Pre-granting microphone permission so the first-run dialog cannot block the startup smoke test."
adb shell pm grant com.openjarvis android.permission.RECORD_AUDIO

echo "Clearing logcat before app launch."
adb logcat -c

echo "Launching MainActivity."
adb shell am start -W -n com.openjarvis/.ui.MainActivity

echo "Waiting up to 60 seconds for MainActivity to be resumed."
RESUMED=""
for ((second=1; second<=60; second++)); do
  ACTIVITY_STATE="$(adb shell dumpsys activity activities | tr -d '\r')"
  if RESUMED="$(printf '%s\n' "$ACTIVITY_STATE" | grep -E 'mResumedActivity|topResumedActivity' | grep -F 'com.openjarvis/.ui.MainActivity')"; then
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

PID="$(adb shell pidof com.openjarvis | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process is not running after resume." >&2; exit 12; }

echo "MainActivity is resumed and process PID is $PID. Observing for 10 seconds."
sleep 10

PID="$(adb shell pidof com.openjarvis | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process crashed or exited during observation." >&2; exit 13; }

adb logcat -d > "$LOGCAT_PATH"
if grep -Fq "FATAL EXCEPTION" "$LOGCAT_PATH"; then
  echo "FATAL EXCEPTION detected in logcat." >&2
  grep -F -A 40 -B 5 "FATAL EXCEPTION" "$LOGCAT_PATH"
  exit 14
fi

echo "MINIMAL_ANDROID_SMOKE: PASS"
echo "Verified: APK installed, MainActivity resumed, process remained alive, and no FATAL EXCEPTION appeared in logcat."
