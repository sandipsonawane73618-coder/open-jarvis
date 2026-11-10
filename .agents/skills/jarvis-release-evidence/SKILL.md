---
name: jarvis-release-evidence
description: Use for CI verification, APK generation, release readiness, PR updates, and physical-device test reporting.
---

# Evidence-Based Release Gate

For the exact head commit, verify:
1. Focused and full unit tests.
2. APK build and artifact upload.
3. Minimal emulator install/start/crash smoke.
4. ECC AgentShield report and guardrail checks.
5. Changed-file diff, dependency changes, and repository policy.
6. PR state and merge status.

Use precise labels: implemented, unit-tested, CI-passed, APK-built, emulator-smoke-passed, phone-verified. A skipped optional job is not a pass. A previous commit's green run does not verify a newer head. Do not merge or publish without explicit approval.
