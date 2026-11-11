#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ARTIFACT_DIR="artifacts"
XML_PATH="$ARTIFACT_DIR/euronics-ui.xml"
APP_ID="com.openjarvis.preview"
MAIN_ACTIVITY="$APP_ID/com.openjarvis.ui.MainActivity"
EURONICS_ACTIVITY="$APP_ID/com.openjarvis.ui.EuronicsActivity"
ACCESSIBILITY_SERVICE="$APP_ID/com.openjarvis.accessibility.JarvisAccessibilityService"
PARSER_ERROR="$ARTIFACT_DIR/find-bounds-error.txt"
REMOTE_XML="/sdcard/jarvis-window.xml"
LABEL="EURONICS SERVICE REPORT"

mkdir -p "$ARTIFACT_DIR"
[[ -f "$APK_PATH" ]] || { echo "Debug APK not found: $APK_PATH" >&2; exit 10; }

dump_ui() {
  adb shell uiautomator dump "$REMOTE_XML" >/dev/null
  adb shell cat "$REMOTE_XML" > "$XML_PATH"
}

on_exit() {
  local result="$1"
  trap - EXIT
  if [[ "$result" -ne 0 ]]; then
    echo "Smoke test failed (exit $result); capturing complete UI hierarchy."
    set +e
    dump_ui
    if [[ -f "$XML_PATH" ]]; then
      echo "----- BEGIN FULL UI DUMP: $XML_PATH -----"
      cat "$XML_PATH"
      echo "----- END FULL UI DUMP -----"
    else
      echo "UI dump could not be captured."
    fi
    echo "----- RECENT ANDROID LOGCAT -----"
    adb logcat -d -t 2000
  fi
  exit "$result"
}
trap 'on_exit "$?"' EXIT

adb install -r "$APK_PATH"
adb shell pm grant "$APP_ID" android.permission.RECORD_AUDIO
adb shell appops set "$APP_ID" SYSTEM_ALERT_WINDOW allow
adb shell settings put secure enabled_accessibility_services "$ACCESSIBILITY_SERVICE"
adb shell settings put secure accessibility_enabled 1
adb shell am start -n "$MAIN_ACTIVITY"

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
  echo "MainActivity did not reach a resumed state within 60 seconds."
  adb shell dumpsys activity activities
  exit 11
}

PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process is not running" >&2; exit 12; }
adb shell dumpsys accessibility | grep -F "$ACCESSIBILITY_SERVICE"

dump_ui
if grep -Eiq 'text="(Allow|While using the app|Only this time|Don.t allow)"|text="Permission Required"|text="Enable Accessibility Service"|text="Enable Overlay Permission"' "$XML_PATH"; then
  echo "Permission dialog or PermissionScreen is blocking the dashboard; refusing to tap through it."
  cat "$XML_PATH"
  exit 13
fi

FOUND=0
for attempt in 0 1 2 3 4 5; do
  if TAP_COORDS="$(python3 scripts/find_bounds.py "$XML_PATH" 2>"$PARSER_ERROR")"; then
    FOUND=1
    break
  else
    parser_status=$?
    if [[ "$parser_status" -ne 3 ]]; then
      cat "$PARSER_ERROR"
      exit "$parser_status"
    fi
  fi

  if [[ "$attempt" -lt 5 ]]; then
    echo "Label not visible yet; bounded scroll $((attempt + 1))/5."
    adb shell input swipe 540 1550 540 520 450
    dump_ui
  fi
done

if [[ "$FOUND" -ne 1 ]]; then
  echo "Exact dashboard label not found after 5 bounded scrolls: $LABEL"
  cat "$XML_PATH"
  exit 14
fi

read -r TAP_X TAP_Y <<< "$TAP_COORDS"
[[ "$TAP_X" =~ ^[0-9]+$ && "$TAP_Y" =~ ^[0-9]+$ ]] || { echo "Invalid tap coordinates: $TAP_COORDS" >&2; exit 15; }
echo "Tapping exact source label '$LABEL' at $TAP_X,$TAP_Y"
adb shell input tap "$TAP_X" "$TAP_Y"
sleep 15

PID="$(adb shell pidof "$APP_ID" | tr -d '\r')"
[[ -n "$PID" ]] || { echo "Jarvis process stopped after Euronics tap" >&2; exit 16; }
adb shell dumpsys activity activities | grep -F "$EURONICS_ACTIVITY"
adb logcat -d | grep -F 'EURONICS_HTML_TITLE:'
echo "EURONICS_SMOKE_TEST: PASS"
