# Upstream Jarvis integration audit

Reviewed upstream: [AbuZar-Ansarii/Jarvis](https://github.com/AbuZar-Ansarii/Jarvis)
- Reviewed ref: `master`
- Reviewed commit: `c1f5fb51e1752db9e0465c05afcf2690986f742b`
- License: MIT (full attribution/license text in `THIRD_PARTY_NOTICES.md`)
- Upstream stack: Flutter/Dart UI and services with Kotlin Android platform channels.
- Target stack: native Kotlin + Jetpack Compose. Flutter services are not drop-in Kotlin modules; selected algorithms and workflows must be ported and wired to existing native services.

## Port / reuse decisions

| Upstream capability | Target decision | Reason / integration point |
|---|---|---|
| Observe → Think → Act task executor | Reuse architecture, not Dart implementation | Existing `AgentCore`, `ScreenReader`, `UniversalAdapter`, and action pipeline already own native execution |
| Repeating-action / stuck-screen recovery | Port safe loop prevention first | `PipelineLoopGuard` blocks a third consecutive identical pipeline step before execution; automatic scroll/back recovery is intentionally not triggered without user intent |
| Macro / learned skill replay | Reuse existing implementation | Existing `SkillEngine` already loads local JSON skills and supports verified generated skills; avoid a second incompatible skill store |
| Hermes SOUL/USER/AGENT context | Map concepts to existing Graphify and local memory | Avoid duplicate stores and identity/profile leakage; preserve current memory controls |
| Accessibility screen reading and gestures | Reuse concepts; keep native service | Existing Android accessibility service and screen reader are the correct native integration points |
| Proximity air gestures | Candidate for a later device-tested port | Sensor availability and OEM behavior vary; must not interfere with Voicute-only wake-word engine |
| Notification intelligence | Defer pending privacy controls | Upstream listener captures notification bodies; require explicit opt-in, package allowlist, OTP redaction, and local-only processing first |
| Sentry camera / Telegram dispatch | Do not port | Silent front-camera capture and remote exfiltration create privacy/security risks |
| Cloud AI providers | Do not port | Project constraint is local-first Qwen3/Ollama with no cloud fallback or API keys |
| Code Black / deceptive lockout | Do not port | Deceptive UI and device lockout behavior are not acceptable |
| Network audit / broad storage access | Defer | Requires a scoped permission and security review before implementation |
| 3D HUD / visual effects | Candidate UI inspiration only | Does not improve core task execution reliability; prioritize functioning tools first |

## Current combined implementation in this PR

- Anaay Jarvis-inspired specialist profiles and privacy-safe tool activity model.
- Live pipeline activity events are wired through `AgentCore` to the dashboard.
- AbuZar Jarvis-inspired repeat-step loop guard is wired into `StepPipeline`.
- Regression test verifies that the third repeated step is blocked before the handler executes it.
- Existing `SkillEngine` and native accessibility stack are retained; no parallel Flutter runtime or duplicate skill store is added.

## Acceptance gates

1. Unit tests must pass with all existing tests enabled.
2. APK build must succeed.
3. Minimal smoke test must complete successfully (not cancelled).
4. Manual phone acceptance is still required for accessibility, wake-word, Ollama, and real app actions.
5. Never mark an action completed unless Android reports success or a post-action observation confirms it.
