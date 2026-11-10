---
name: jarvis-android-lifecycle
description: Use for Android microphone, speech recognition, foreground service, overlay, notification, permission, activity, and background lifecycle changes.
---

# Android Lifecycle and Resource Ownership

- Identify the single owner for each microphone, SpeechRecognizer, AudioRecord, foreground service, overlay window, and callback.
- Model lifecycle states explicitly; do not infer active state from UI text.
- Make start/stop/restart idempotent. Cancel delayed retries and callbacks when a session ends.
- Use Android on-device speech recognition explicitly when offline STT is required. Never silently call createSpeechRecognizer() as a network-capable fallback.
- Check API level, runtime permission, service type, notification permission, battery/background restrictions, and OEM behavior.
- Keep overlay recovery independent from microphone restart; an overlay watchdog must not create a second recognizer.
- Test no-permission, unavailable-on-device-service, recognizer-busy, timeout, duplicate start, TTS completion, mute/unmute, and service recreation.
- Physical device testing remains a separate evidence level from emulator smoke.
