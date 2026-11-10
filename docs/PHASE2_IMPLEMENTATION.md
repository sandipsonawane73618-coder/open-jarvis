# JARVIS Phase 2 — Voicute-only wake word and persistent overlay

## Branch and baseline

- Phase 2 branch: `feature/jarvis-phase2-continuation`
- Parent baseline: Phase 1 P0 PR #20 head `6c44d36ce75a462da5a59048ed3a1a858cb44f09`
- PR #20 remains draft/open and is not merged.
- Keep local-only Ollama `qwen3:1.7b` default unchanged. No cloud/API fallback.

## Wake-word engine

- Voicute ONNX is the only wake-word engine.
- No Sherpa engine, Porcupine/Picovoice SDK, paid key, or automatic engine fallback.
- Bundled model phrases: `Jarvis`, `Hey Jarvis`, and `Hi Jarvis`.
- Model assets are prepared by `scripts/prepare-voicute.sh`; inference is offline/on-device.
- High sensitivity is the default and uses a lower model score threshold. This is a tuning change, not proof of reliable real-phone detection.

## Overlay recovery

- The foreground service watchdog checks whether the floating view is still attached.
- A detached/stale overlay view is recreated while the service remains alive.
- If the listener unexpectedly stops outside an active command/response, the watchdog attempts to re-arm Voicute.
- Android force-stop, revoked overlay/microphone permissions, or OEM battery restrictions can still stop the service and require user action.

## Validation

- Required CI: `unit-tests`, `apk-build`, `smoke-test-minimal`, and preview APK artifact.
- CI/emulator PASS does not equal physical-phone PASS.
- Real-device detection accuracy, auto-rearm, overlay persistence, idle CPU, and battery use still require physical-device validation.
- Do not claim wake-word accuracy or overlay reliability without device evidence.

## Safety

- Draft only. Do not merge without explicit user approval.
- Keep all wake-word audio processing local.
- Do not modify Euronics HTML, Firebase configuration, or report workflows in this Phase 2 branch.
