# Jarvis Engineering Learnings

This file stores reusable engineering facts only. Re-check source and CI before relying on a note after refactors.

## Local AI boundary
- `UniversalAdapter.getActiveProvider()` constructs the local Ollama provider and rewrites legacy provider preferences to `Ollama (Local)`, empty API key, loopback URL, and the configured default model.
- The provider constants currently specify `qwen3:1.7b` and `http://127.0.0.1:11434`.
- Keep this invariant covered by `scripts/ecc-guardrails.sh`; do not infer that optional provider implementation classes are active runtime fallbacks.

## On-device speech boundary
- `AndroidSTTEngine.createRecognizer()` checks Android S+ and `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)`, then creates the on-device recognizer.
- No generic `SpeechRecognizer.createSpeechRecognizer(context)` fallback belongs in the voice package.
- Verify behavior on the actual handset; emulator smoke cannot prove OEM offline speech package availability.

## Headroom boundary
- Headroom's upstream value is content-aware compression, local execution, exact-original retrieval (CCR), and measurable token savings.
- Jarvis uses a Kotlin-native subset because its official library/proxy/MCP surfaces are Python/TypeScript-oriented and a Termux/server dependency would add runtime complexity.
- Never compress the user command, executable action JSON, code, diffs, UI target labels, or safety policy. UI labels and policy text only lose redundant blank lines; other text/log compression retains an exact original in the compression result and reports character/token estimates.
- Compression savings are workload-dependent; do not claim a fixed percentage without measurements from representative Jarvis prompts.

## Evidence discipline
- Record workflow results against the exact commit SHA. A green run for an older head does not verify later changes.
- Distinguish unit tests, APK build, emulator smoke, security scan, and physical-device verification.
