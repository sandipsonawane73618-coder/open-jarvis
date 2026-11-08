---
name: jarvis-tdd
description: Use for every Jarvis bug fix, feature, refactor, and regression. Enforces evidence-backed Android/Kotlin test-first development.
---

# Jarvis Test-First Workflow

1. Trace the actual call path, state owner, lifecycle, existing tests, and CI job before editing.
2. Record the behavior that must remain unchanged: local Ollama only, qwen3:1.7b default, deterministic skills before LLM fallback, existing Android automation, user data and safety policy.
3. Add a focused test first. Include success, negative, boundary, and recovery behavior where relevant.
4. Verify that the test would fail for the old behavior when practical. Never weaken, skip, ignore, or delete a meaningful regression test to obtain green CI.
5. Implement the smallest change. Keep pure policy/compression/parser logic Android-independent where practical.
6. Run the focused test, full unit suite, debug APK build, and minimal emulator smoke for app/lifecycle changes.
7. Review the final diff and exact commit's CI. Report each evidence level separately.

## Jarvis-specific rules
- Preserve Hindi, Marathi, Hinglish, and English command behavior.
- Keep Qwen3 1.7B as the default local model. No API key, cloud LLM, or remote STT fallback.
- Never claim emulator success proves physical-phone behavior.
- Never merge or distribute without explicit user approval.
