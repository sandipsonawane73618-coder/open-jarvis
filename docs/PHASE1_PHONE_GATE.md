# Phase 1 P0 — real-device verification gate

This checklist is intentionally separate from unit tests and emulator launch smoke tests.
Do not mark Phase 1 complete until the device-specific checks below have evidence.

## Test build

Install the latest CI debug APK. Its Android application ID is
`com.openjarvis.preview`. Open Jarvis, complete first-run setup, enable the
Overlay Service, and install Termux with package name `com.termux`.

## J-09 — bridge provider and token access

Run from the host computer:

```bash
adb shell content query --uri content://com.openjarvis.preview.bridgeconfig/config
```

Run the same query from Termux:

```bash
/system/bin/content query --uri content://com.openjarvis.preview.bridgeconfig/config
```

Expected: both trusted callers receive a non-zero dynamic port and the same
URL-safe token (43 characters). An ordinary unrelated app UID must receive a
SecurityException; do not grant that app shell/root privileges for this test.
Repeat after stopping and restarting Overlay Service: the port may change,
but the stored token should remain valid.

## J-10 — request limits and rate enforcement

The unit suite covers the exact 8,192-byte accept / 8,193-byte reject boundary,
2,000 / 2,001 command characters, request-ID limit, and rolling-window
10-request / 11th-request behavior. On device, send authenticated requests
from Termux and confirm normal commands still work; then burst more than 10
requests within one second and confirm the excess request returns
`rate limit exceeded`. A request without a token must return
`unauthorized` (or be rejected as an invalid request if the token field is
missing). The listener must remain responsive after the burst.

## J-11 — Android permission, MediaProjection, and OCR

1. Say/type: `read screen`.
2. Accept Android's screen-capture consent prompt.
3. Say/type: `read screen` again after consent.
4. Confirm Jarvis returns visible on-screen text, not just a permission message.
5. Stop screen capture from Android's system privacy controls and retry; Jarvis
   must ask for consent again rather than crash.

## J-12 — repeated-capture memory stability

Record baseline memory:

```bash
adb shell dumpsys meminfo com.openjarvis.preview
```

Perform at least 30 successful screen-read captures, with a short pause between
captures. Wait 30 seconds and capture memory again with the same command.
Check that memory does not grow continuously, Jarvis remains responsive, and
there are no `AndroidRuntime`, `OutOfMemoryError`, or ImageReader
`maxImages` errors:

```bash
adb logcat -d -s AndroidRuntime OpenJarvis
```

## J-13 — notification privacy regression

With notification access enabled, generate test notifications from installed
banking, UPI, wallet, and payment apps. Confirm their title/body do not appear
in Jarvis notification history or summaries. Generate a WhatsApp notification
as a control and confirm ordinary messaging notifications remain processable.

## Evidence to record

Record device Android version, APK commit SHA, each test result, and relevant
logcat excerpts. A PASS from unit tests or emulator app-launch smoke alone is
not a substitute for these real-device checks.
