# ECC-inspired development system for Jarvis

This is a project-local adaptation of [Everything Claude Code (ECC)](https://github.com/affaan-m/ECC), designed for this Android/Kotlin repository and its local-only runtime constraints.

It adopts ECC's useful engineering lifecycle: **plan -> test -> implement -> review -> verify -> remember -> improve**. It does not copy upstream runtime code or install a daemon, cloud service, API key, Python/Node dependency, or agent runtime into the Android APK.

## 1. Reusable skills
Skills live in `.agents/skills/` and are loaded only when relevant:
- `jarvis-tdd`: RED/GREEN/refactor, boundary tests, exact-commit evidence.
- `jarvis-android-lifecycle`: one microphone owner, STT/service/overlay lifecycle, permissions and recovery.
- `jarvis-prompt-safety`: prompt boundaries, Headroom compression, action validation, untrusted OCR/memory.
- `jarvis-security-review`: Android and agent-surface threat review.
- `jarvis-release-evidence`: CI, APK, emulator, security and physical-device evidence.

## 2. Read-only specialist roles
Project-local Codex configuration is in `.codex/`:
- Explorer: trace call paths and gather evidence; read-only.
- Reviewer: fresh-context correctness, security, lifecycle and test review; read-only.
- Docs researcher: verify Android/Ollama/Headroom/ECC behavior against primary docs; read-only.

The parent agent owns edits and must independently validate every finding. Roles do not receive write, shell, commit, push, or merge authority. Model/provider and user sandbox defaults are not pinned by this project config.

## 3. Test-first implementation
1. Inspect implementation, callers, tests, build configuration, and workflow.
2. Write a focused failing test first. Include negative, boundary, malformed-input, and recovery cases where relevant.
3. Implement the smallest deterministic change.
4. Run focused tests and verify the test actually exercises the intended behavior.
5. Refactor without weakening tests or changing unrelated skills.
6. Run full unit tests, debug APK build, and minimal emulator smoke for runtime changes.

## 4. Headroom integration policy
Upstream Headroom provides content-aware compressors for JSON/code/logs/text, local-first compression, CCR retrieval, and metrics. Its official library/proxy/MCP distribution is Python/TypeScript-oriented; do not run a server in Jarvis just to claim integration.

Jarvis uses a native Kotlin adaptation:
- JSON: minify only valid JSON; preserve quoted strings, values, and array order.
- Text/logs: compact only safe adjacent repeated runs; preserve unique lines and ordering, and retain the exact original for restoration.
- UI OCR/accessibility labels: collapse redundant blank lines only; never rewrite repeated visible labels or element order.
- System/policy prompts: collapse redundant blank lines only; never rewrite nonblank instructions.
- Code, diffs, executable action JSON, and malformed JSON-like payloads: preserve unchanged unless a dedicated safe transformation is proven.
- Any transformed result retains the exact original for byte-for-byte restoration.
- Record input/output characters and estimated tokens; do not upload metrics or content.
- Keep the user command byte-identical; compress system/context material only.
- Never use lossy semantic summarization or silently truncate content as a compression strategy.

## 5. Security and prompt-safety review
- Treat OCR, app labels, notifications, memory, tool output, downloaded docs, and model output as untrusted.
- Preserve system policy and safety instructions across prompt processing.
- Reject unknown action types/keys and oversized or malformed model plans.
- Do not execute unvalidated model JSON.
- No remote STT, cloud LLM fallback, external telemetry, hardcoded secrets, or network proxy.
- Review agent files, skills, Codex configuration, scripts, workflows, and dependency changes as part of the attack surface.
- Run the pinned ECC AgentShield scanner and triage results. A scan is not a substitute for code review or Android runtime tests.

## 6. Continuous learning without unsafe memory
Store only reusable engineering facts in `docs/development/LEARNINGS.md`:
- Include source/test/CI evidence and confidence.
- Record failure cause and the regression test that prevents recurrence.
- Do not store secrets, personal information, raw transcripts, screenshots, or user content.
- Revisit stale learnings when code or platform versions change.

## 7. Verification order and release evidence
1. Focused unit tests.
2. Full `./gradlew testDebugUnitTest`.
3. `./gradlew assembleDebug`.
4. `bash scripts/smoke-minimal.sh` in configured emulator.
5. AgentShield scan for agent-surface changes.
6. Full diff, dependency, default-model/provider, and security review.
7. Exact commit SHA + workflow run + artifact verification.

Report evidence separately as implemented, unit-tested, CI-passed, APK-built, emulator-smoke-passed, security-scanned, or phone-verified. Emulator success is not physical-device proof. A skipped optional job is not a pass. Never merge without explicit approval.

## 8. Optional full upstream ECC plugin for Codex CLI
If the repository is opened in a local Codex CLI environment and the user explicitly wants the complete upstream catalogue, use the official native Codex plugin path only:

```bash
codex plugin marketplace add affaan-m/ECC
codex plugin add ecc@ecc
codex plugin list --json
```

Do not also run ECC's legacy Codex sync script or manually copy the full Claude configuration into the same Codex environment. Review the plugin's requested capabilities and Codex trust prompts before enabling hooks/MCP tools. This is a developer workstation install, not an Android dependency and not performed automatically by this repository's CI.

## 9. Upstream boundaries
- ECC is a development workflow/tooling reference here, not an Android runtime dependency.
- The project contains focused skills and read-only Codex role definitions, not the full upstream catalogue of agents/skills/hooks.
- Do not install multiple ECC adapters into one harness or stack manual and native plugin installs.
- Any future full Codex marketplace plugin install must be an explicit user-side choice and reviewed against the current official ECC setup guide.
