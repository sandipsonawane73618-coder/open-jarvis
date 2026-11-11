#!/usr/bin/env bash
set -euo pipefail

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
ARTIFACT_DIR="artifacts"
LOGCAT_PATH="$ARTIFACT_DIR/minimal-smoke-logcat.txt"
APP_ID=""
MAIN_ACTIVITY=""

mkdir -p "$ARTIFACT_DIR"
: > "$LOGCAT_PATH"
[[ -f "$APK_PATH" ]] || { echo "Debug APK not found: $APK_PATH" >&2; exit 10; }

# Read the package and launcher component from the exact APK under test. This
# prevents a stale hard-coded applicationId/activity from hiding a valid build.
SDK_ROOT="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[[ -n "$SDK_ROOT" ]] || { echo "Android SDK path is unavailable." >&2; exit 17; }
AAPT_PATH="$(find "$SDK_ROOT/build-tools" -maxdepth 2 -type f -name aapt | sort -V | tail -n 1)"
[[ -x "$AAPT_PATH" ]] || { echo "Android aapt tool was not found under $SDK_ROOT/build-tools." >&2; exit 17; }
APK_BADGING="$("$AAPT_PATH" dump badging "$APK_PATH")"
APP_ID="$(printf '%s\n' "$APK_BADGING" | sed -n "s/^package: name='\\([^']*\\)'.*/\\1/p" | head -n 1)"
LAUNCHER_ACTIVITY="$(printf '%s\n' "$APK_BADGING" | sed -n "s/^launchable-activity: name='\\([^']*\\)'.*/\\1/p" | head -n 1)"
[[ -n "$APP_ID" && -n "$LAUNCHER_ACTIVITY" ]] || {
  echo "APK metadata is missing its application ID or launcher activity." >&2
  exit 17
}
case "$LAUNCHER_ACTIVITY" in
  .*) LAUNCHER_ACTIVITY="${APP_ID}${LAUNCHER_ACTIVITY}" ;;
  *.*) ;;
  *) LAUNCHER_ACTIVITY="${APP_ID}.${LAUNCHER_ACTIVITY}" ;;
esac
MAIN_ACTIVITY="$APP_ID/$LAUNCHER_ACTIVITY"
echo "APK identity: $APP_ID; launcher: $MAIN_ACTIVITY"

echo "Waiting for Android boot and stable system-provider readiness."
adb wait-for-device
PACKAGE_READY=0
STABLE_CHECKS=0
for ((attempt=1; attempt<=180; attempt++)); do
  BOOTED=""
  PACKAGE_SERVICE=""
  ACTIVITY_SERVICE=""
  if BOOTED="$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"; then :; fi
  if PACKAGE_SERVICE="$(adb shell service check package 2>/dev/null | tr -d '\r')"; then :; fi
  if ACTIVITY_SERVICE="$(adb shell service check activity 2>/dev/null | tr -d '\r')"; then :; fi

  # A boot-complete property alone is not enough: Android 14 can report it
  # while system providers are still starting (or system_server is restarting).
  if [[ "$BOOTED" == "1" && "$PACKAGE_SERVICE" == *"found"* &&
        "$ACTIVITY_SERVICE" == *"found"* ]] &&
     adb shell cmd package list packages >/dev/null 2>&1 &&
     adb shell settings get global device_provisioned >/dev/null 2>&1 &&
     adb shell dumpsys activity activities >/dev/null 2>&1; then
    STABLE_CHECKS=$((STABLE_CHECKS + 1))
    if [[ "$STABLE_CHECKS" -ge 5 ]]; then
      PACKAGE_READY=1
      echo "Android system providers and Package Manager remained ready for 5 checks (after ${attempt}s)."
      break
    fi
  else
    STABLE_CHECKS=0
  fi
  sleep 2
done
if [[ "$PACKAGE_READY" -ne 1 ]]; then
  echo "Android system providers and Package Manager did not remain ready within 180 checks." >&2
  if ! adb devices -l; then echo "Could not collect adb device diagnostics." >&2; fi
  if ! adb shell getprop sys.boot_completed; then echo "Could not read boot state." >&2; fi
  if ! adb shell service check package; then echo "Could not query Package Manager." >&2; fi
  if ! adb shell service check activity; then echo "Could not query Activity Manager." >&2; fi
  if ! adb logcat -b all -d -t 1600 > "$LOGCAT_PATH"; then echo "Could not collect startup logcat." >&2; fi
  exit 15
fi

echo "Installing debug APK using non-streaming mode with bounded ADB reconnect retry."
INSTALL_OK=0
for ((attempt=1; attempt<=3; attempt++)); do
  # --no-streaming avoids PackageInstaller's streamed-session storage allocation
  # path, which can fail while Android's Settings provider is still initializing.
  if adb install --no-streaming -r "$APK_PATH"; then
    # adb can report a completed install while Package Manager is restarting;
    # require the package path to be visible before accepting the attempt.
    if adb shell pm path "$APP_ID" 2>/dev/null | tr -d '\r' | grep -q '^package:'; then
      INSTALL_OK=1
      echo "APK install and Package Manager visibility succeeded on attempt $attempt."
      break
    fi
    echo "Install command returned success, but Package Manager cannot see $APP_ID yet." >&2
  fi
  echo "APK install attempt $attempt failed verification; reconnecting emulator ADB and rechecking system providers." >&2
  if ! adb reconnect device; then echo "ADB reconnect command failed; retrying device wait anyway." >&2; fi
  adb wait-for-device
  # system_server may restart under emulator memory pressure. Give Package
  # Manager time to come back instead of burning the next install attempt.
  RECOVERED=0
  for ((recovery=1; recovery<=60; recovery++)); do
    if ! PACKAGE_SERVICE="$(adb shell service check package 2>/dev/null | tr -d '\r')"; then
      echo "Package Manager query failed during recovery attempt $recovery." >&2
      sleep 1
      continue
    fi
    if ! ACTIVITY_SERVICE="$(adb shell service check activity 2>/dev/null | tr -d '\r')"; then
      echo "Activity Manager query failed during recovery attempt $recovery." >&2
      sleep 1
      continue
    fi
    if [[ "$PACKAGE_SERVICE" == *"found"* && "$ACTIVITY_SERVICE" == *"found"* ]] &&
       adb shell cmd package list packages >/dev/null 2>&1 &&
       adb shell settings get global device_provisioned >/dev/null 2>&1; then
      RECOVERED=1
      echo "Android Package Manager recovered after ${recovery}s."
      break
    fi
    sleep 1
  done
  if [[ "$RECOVERED" -ne 1 ]]; then
    echo "Android system providers are still unavailable after 60s recovery wait." >&2
  fi
done
if [[ "$INSTALL_OK" -ne 1 ]]; then
  echo "APK installation failed after 3 attempts." >&2
  if ! adb devices -l; then echo "Could not collect adb device diagnostics." >&2; fi
  if ! adb shell service check package; then echo "Could not query Package Manager." >&2; fi
  if ! adb logcat -b all -d -t 1600 > "$LOGCAT_PATH"; then echo "Could not collect install logcat." >&2; fi
  exit 16
fi

echo "Pre-granting runtime permissions used by the first-run flow so system permission UI cannot block the startup smoke test."
adb shell pm grant "$APP_ID" android.permission.RECORD_AUDIO
# MainActivity requests notification permission immediately after microphone permission.
# Grant it here as well; otherwise the permission controller can remain foreground
# and the smoke test incorrectly reports that MainActivity never resumed.
if [[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]]; then
  adb shell pm grant "$APP_ID" android.permission.POST_NOTIFICATIONS
fi

echo "Verifying installed package and resolving the APK-declared launcher."
INSTALLED_PATHS="$(adb shell pm path "$APP_ID" | tr -d '\r')"
[[ "$INSTALLED_PATHS" == *"package:"* ]] || {
  echo "Installed package $APP_ID is not visible to Package Manager: $INSTALLED_PATHS" >&2
  exit 17
}
RESOLVED_ACTIVITY="$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -p "$APP_ID" | tr -d '\r' | tail -n 1)"
[[ "$RESOLVED_ACTIVITY" == "$MAIN_ACTIVITY" ]] || {
  echo "APK launcher mismatch. Expected $MAIN_ACTIVITY; Package Manager resolved: $RESOLVED_ACTIVITY" >&2
  exit 17
}

echo "Clearing logcat before app launch."
adb logcat -c

echo "Launching APK-declared MainActivity: $MAIN_ACTIVITY"
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
