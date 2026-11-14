# Branch coverage — final all-in-one audit

Final audit base: `origin/feat/jarvis-all-in-one` at `b4e0a6b87ce8b1e1289af02123ee0ca856c68b7a` (2026-11-14; post PR #96 and CI guard commits).
Scope: all 67 remote branches matching `feat/*` or `feature/*`.
Method: `git cherry -v origin/feat/jarvis-all-in-one origin/<branch>` for every remote branch. In the count column, first number is `+` (commits not patch-equivalent in all-in-one); second is `-` (patch-equivalent commits). `already contained` means exactly zero `+` commits. The full per-commit output is in the CI artifact [branch-cherry-counts](https://github.com/sandipsonawane73618-coder/open-jarvis/actions/runs/43569969445).

Summary: **67 branches audited; 44 already contained; 10 selectively ported; 13 skipped.** `git cherry` totals: **583 commits not patch-equivalent; 20 patch-equivalent; 603 total branch-side commits inspected**.

| Remote branch | git cherry (+ / -) | Final state | Port detail or skip reason |
|---|---:|---|---|
| `feat/jarvis-agent-loop` | 10 / 0 | **skipped** | Large divergent AgentLoopRunner/AgentTaskSession/ActionVerifier and AgentCore planner rewrite; 396 commits behind. No wholesale port without separate RED tests and review. |
| `feat/jarvis-all-in-one` | 0 / 0 | **already contained** | This is the comparison base. |
| `feat/jarvis-app-integrations` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-command-lab` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-floating-ui` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-home-ui` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-hybrid-chat` | 8 / 1 | **skipped** | Adds Gemini/Groq cloud providers and hybrid routing; conflicts with local-only Ollama/no-cloud-fallback requirement. 398 commits behind. |
| `feat/jarvis-maharashtra-voice-full-control` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-memory-personality` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-overlay-stt-locale-recovery` | 0 / 0 | **already contained** | PR #95 changes already in all-in-one. |
| `feat/jarvis-reality-fixes` | 0 / 0 | **already contained** | PR #75 changes already in all-in-one. |
| `feat/jarvis-send-safety` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-tier2-selective-ports` | 0 / 0 | **already contained** | PR #96 changes already in all-in-one. |
| `feat/jarvis-v3-agent-loop` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-v4-understanding` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-v5` | 2 / 0 | **skipped** | Only remaining unique commits add then revert a scroll-until-target test; no unique feature implementation to port. |
| `feat/jarvis-voice-personality` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feat/jarvis-voice-power-off` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/autonomous-self-healing-edition` | 82 / 4 | **selectively ported** | PR #96 commit f043a5fcc98db62bfe4a5a27108be5d757a77190 ports CommandDictionary, ErrorDiagnosis, RepairMemory and SelfHealingEngine behind default-OFF setting. SocketServer, unauthenticated bridge, SignedUpdateManager, network/manifest rewrites and patch auto-apply are excluded. |
| `feature/euronics-webview-integration` | 29 / 0 | **skipped** | Separate Euronics app/workstream; Euronics HTML, activity, manifest and data are explicitly out of scope. |
| `feature/jarvis-accessibility-agent-loop` | 60 / 0 | **selectively ported** | PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d includes HeadroomLiteContext and its tests; remaining diagnostics/wakeword bundle not ported. |
| `feature/jarvis-action-session-multistep-fix` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-advanced-hud-hardening` | 5 / 0 | **skipped** | Older HUD/HeadroomLite changes are superseded; branch compresses TEXT/UI content and carries alternate wakeword code, both excluded. |
| `feature/jarvis-command-listener-no-wakeword` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-contact-app-intent-priority` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-continuous-voice-session` | 0 / 1 | **already contained** | 0 commits not in all-in-one; one patch-equivalent commit. |
| `feature/jarvis-conversation-memory-marathi` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-deterministic-fast-skills` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-diagnostic-export` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-false-completion-guard` | 12 / 0 | **skipped** | No unique behaviour missing: current AgentPlanPolicy, foreground verification, required-payload gate and screenshot consent path are stronger/current. |
| `feature/jarvis-fast-response-streaming` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-foreground-banking-guard` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-full-potential-foundation` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-headroom-fidelity` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-headroom-hybrid` | 2 / 1 | **selectively ported** | PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d contains HeadroomCompressor and its tests; one source commit is patch-equivalent. |
| `feature/jarvis-jandal-memory-recall` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-keyfree-wakeword` | 0 / 0 | **already contained** | No commits unique by patch ID; retain user's Voicute-only selection. |
| `feature/jarvis-long-command-engine-pr17` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-m3-m5-proactive-hud` | 2 / 0 | **skipped** | Unique commits are workflow-only; Sherpa wakeword/model workflow is excluded by the Voicute-only decision. |
| `feature/jarvis-manual-mic-hud-headroom` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-manual-mic-report-doctor` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-multilingual-volume-routing` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-offline-multilingual-stt` | 4 / 1 | **selectively ported** | PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d ports bounded error 12/13 locale recovery, model-support/consent flow and per-locale Settings status; old overlay rewrite excluded. |
| `feature/jarvis-opendroid-agent-loop` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-phase1-p0-security` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-phase2-continuation` | 24 / 0 | **selectively ported** | PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d includes HeadroomLiteContext and tests; diagnostic logger and wakeword bundle excluded. |
| `feature/jarvis-phase3-headroom-ecc` | 53 / 0 | **selectively ported** | PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d includes HeadroomLiteContext and tests; unrelated logger and wakeword bundle excluded. |
| `feature/jarvis-phase3-memory` | 5 / 4 | **selectively ported** | PR #96 commit f043a5fcc98db62bfe4a5a27108be5d757a77190 makes GraphifyRepository suspend and privacy-filtered via MemoryContextBuilder; builder/compressor implementations and tests were already in PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d. |
| `feature/jarvis-report-doctor-safe-recovery` | 11 / 2 | **skipped** | Current ReportDoctor is equivalent; current SelfHealingExecutor/SafetyPolicy are stricter and evidence-gated than this branch's older WAIT-only retry path. |
| `feature/jarvis-sanna-memory-learning-audit` | 61 / 0 | **selectively ported** | PR #95 SHA 3ff7e627b28fb1321f61501919099b0e5693880d includes HeadroomLiteContext and tests; bundled diagnostic logger and wakeword changes excluded. |
| `feature/jarvis-screenshot-capture-reliability` | 5 / 0 | **skipped** | No missing screenshot behaviour; current permission-denied lifecycle, capture readiness, cleanup and bitmap sizing are already present. Branch AgentCore is older. |
| `feature/jarvis-self-healing-retry-gates` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-single-floating-overlay` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-slice-c-router-llm-schema` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-stable-1.0` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-stable-1.0-phase2-wakeword` | 0 / 0 | **already contained** | No commits unique by patch ID; retain Voicute-only engine. |
| `feature/jarvis-stable-1.0-phase3-ollama` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-stable-1.0-phase4-command-execution` | 16 / 0 | **selectively ported** | PR #96 merge SHA aeabc2c0acc5fba29f7da1ebed5b974124201afa includes two regression tests for scroll target-to-direction and error value-to-message mappings; implementation already existed. |
| `feature/jarvis-streaming-response` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-stt-multilingual-ci` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-stt-multilingual-fix` | 3 / 0 | **skipped** | Older locale retry is superseded by PR #95's bounded error 12/13 locale fallback and consented model support. |
| `feature/jarvis-tts-utterance-callback-isolation` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-uktam-indic-stt` | 53 / 0 | **skipped** | No accepted missing non-wakeword feature; current Indic compatibility remains. Bundled wakeword/diagnostic changes not imported. |
| `feature/jarvis-uktam-indic-stt-audit` | 61 / 0 | **skipped** | No accepted missing non-wakeword feature; current Indic compatibility remains. Bundled wakeword/diagnostic changes not imported. |
| `feature/jarvis-voice-screen-command-fixes` | 0 / 0 | **already contained** | No commits unique by patch ID. |
| `feature/jarvis-wakeword-reliability` | 29 / 4 | **skipped** | Explicit user decision: no Sherpa ONNX integration; retain Voicute-only wakeword engine. |
| `feature/self-healing-command-dictionary` | 46 / 2 | **selectively ported** | Same four helper files as autonomous branch were selectively ported in PR #96 commit f043a5fcc98db62bfe4a5a27108be5d757a77190; no duplicate port. Euronics asset/activity and bridge/security changes excluded. |

## Final-state rules

- Selective ports are recorded with the exact PR commit SHA and the specific behavior/files carried forward. A selective port does not authorize importing the rest of a divergent branch.
- Branches with zero `+` commits are marked already contained even if their `-` count is nonzero; those commits are patch-equivalent to commits on all-in-one.
- Skips preserve the current safer implementation and explicit constraints: local-only Ollama, Voicute-only wakeword, test count must not decrease, and Euronics remains untouched.
- No branch was force-merged. PR #96 was merged normally as `aeabc2c0acc5fba29f7da1ebed5b974124201afa`.

## CI feature-presence guard

`scripts/check-required-feature-classes.py` is executed in the Unit tests job. It fails if any required source file or class/object/interface declaration for core agent routing, LLM response validation, banking safety, self-healing safety, pipeline handling, screenshot capture, screen-capture permission, overlay attachment, Headroom, Graphify memory or voice/STT is missing.

`scripts/audit-branch-cherry.sh` enumerates every fetched remote `feat/*` and `feature/*` ref, runs `git cherry` against the all-in-one base, writes per-branch counts and commit details to `artifacts/branch-cherry-counts.txt`, and uploads that report in CI.

## Self-healing conflict report

The old `feature/autonomous-self-healing-edition` branch adds a bridge `SocketServer`, Graphify `AnalysisEngine`, command dictionary/error diagnosis, repair memory/center/patch policy, self-healing engine, Ollama health monitor and signed-update manager. Those source files are absent from current All-in-One, but the branch is 87 commits ahead and 1298 behind and also changes AgentCore, manifest, Gradle, Settings, providers and STT. **No forced merge**; isolate and test the unique bridge/analysis features before considering any port.

The old `feature/self-healing-command-dictionary` branch adds command dictionary, error diagnosis, repair memory and self-healing engine, but also carries Euronics asset/activity changes and overlaps the current AgentCore/provider/Settings/STT stack. **No forced merge**; report the conflict set and keep Euronics untouched.

## Integration order

1. Finish PR #95 urgent overlay + offline locale recovery; require CI per job and exact SHA.
2. Add RED tests, then integrate `HeadroomLiteContext` with the current OCR context budget.
3. Add RED tests, then integrate `HeadroomCompressor` into the shared LLM system-prompt path without rewriting the user request, action JSON or safety instructions.
4. Add RED privacy tests, then integrate `MemoryContextBuilder` using only allow-listed task/pattern categories; never pass raw command, contact, message, OCR or provider details into memory recall.
5. Re-run unit tests, ECC, APK build and smoke per SHA. Never delete or loosen a test; `@Test` count must not decrease.


## Selective port audit — ordered Tier 2 batches

### 1. `feature/jarvis-screenshot-capture-reliability` — completed audit

Merge base: `88676a1926ba9bc8ad5f9934487445a754f66f21`; branch head file set compared to All-in-One `3ff7e627b28fb1321f61501919099b0e5693880d`.

| Source file | Branch hunks | Classification | Action |
|---|---|---|---|
| `app/src/main/java/com/openjarvis/agent/AgentCore.kt` | `captureBitmapOrThrow`: keep the original command alive while MediaProjection consent is open; wait up to 45s for readiness or denial; return an honest denial/timeout error; retry frame acquisition 20 times with 100ms delay | New behaviour relative to merge base, but the exact block already exists in current All-in-One | No code change; current implementation retained |
| `app/src/main/java/com/openjarvis/ui/ScreenCapturePermissionActivity.kt` | Import ScreenshotCapture and call `markPermissionDenied()` when the consent result is not OK | New behaviour relative to merge base, but current file blob is identical to branch | No code change |
| `app/src/main/java/com/openjarvis/vision/ScreenshotCapture.kt` | Add volatile permissionDenied state, reset it on start/initialize, expose mark/wasPermissionDenied | New behaviour relative to merge base, but current file blob is identical to branch | No code change |

The only other changed file is `.github/workflows/android-build.yml`; its workflow-only hunk is not an app feature and is not copied. No screenshot source hunk is missing from All-in-One, so no new test/code port is necessary. Existing permission lifecycle remains untouched.


### 2. `feature/jarvis-false-completion-guard` — completed audit

Merge base: `88676a1926ba9bc8ad5f9934487445a754f66f21`; source branch is 12 commits ahead / 394 behind All-in-One. The source branch contains 4 app source files plus one modified test file and workflow-only changes.

| Source file | Branch hunks / behaviour | Classification against current All-in-One | Action |
|---|---|---|---|
| `app/src/main/java/com/openjarvis/agent/AgentCore.kt` | (1) Time-budget exhaustion becomes `AgentState.Error`, not Done. (2) LLM app launch is not marked complete until the target package is confirmed foreground. (3) Native OPEN_APP paths use truthful “launch requested; foreground confirmation unavailable” wording. (4) Screenshot consent keeps the command alive for up to 45 seconds and handles denial/timeout. | All four behaviours are already present in current All-in-One. It has more recent command normalization, hybrid chat routing, explicit approval gates and stronger diagnostics around the same execution flow. | No port; keep newer AgentCore. |
| `app/src/main/java/com/openjarvis/agent/AgentPlanPolicy.kt` | Adds `isAppLaunchVerified(launchAccepted, foregroundConfirmed)`. | Exact function already exists. Current file additionally validates required action payloads and excludes COMPLETE, so current policy is stricter. | No port; keep stricter policy. |
| `app/src/main/java/com/openjarvis/ui/ScreenCapturePermissionActivity.kt` | Marks MediaProjection permission denied when consent result is not OK. | Current file blob is identical to branch. | No port. |
| `app/src/main/java/com/openjarvis/vision/ScreenshotCapture.kt` | Adds volatile denial state and resets it on new capture/initialization. | Current file blob is identical to branch. | No port. |
| `app/src/test/java/com/openjarvis/agent/AgentPlanPolicyTest.kt` | Adds app-launch verification truth table. | Current suite contains the same test plus newer payload/guard coverage: 14 `@Test` methods vs 13 on this branch. | No test port; no tests removed or loosened. |

The workflow-only hunk is not copied. No missing false-completion behaviour was found, so this branch requires no code change. The current stricter send/banking rules, retry gates, overlay attachment gate and permission lifecycle are retained.


### 3. `feature/jarvis-stable-1.0-phase4-command-execution` — selective audit and safe port

Merge base: `adeb4cc89be27d77acaf56d38feef752c0bcfd83`; source branch is 16 commits ahead / 1186 behind the All-in-One baseline. Changed files: five app source files and three test files, plus workflow metadata.

| Source file | Branch hunk / behaviour | Classification against current All-in-One | Action |
|---|---|---|---|
| `app/src/main/java/com/openjarvis/agent/AgentCore.kt` | System prompt changes from an action array to a strict `{steps:[{action,target,value}]}` object; adds a second format-retry call after invalid output; logs validation repair/retry stages. | Current validator accepts both array and `steps` wrappers. Current Ollama schema is a richer action array and also supports skill/native-intent schemas. The branch's second format retry bypasses the shared 45-second budget and current `validateWithOneRepair` already permits only one deadline-bounded repair. | Keep current prompt/schema and one-repair budget. Port safe summary-only validation repair/retry diagnostic events in PR #96; do not add an unbounded second retry. |
| `app/src/main/java/com/openjarvis/agent/AgentDiagnostics.kt` | Line 38 appends up to 4000 raw model-response characters to the locally stored report and log. | Current implementation intentionally stores only response length/action-shape summary; raw model output can contain user message text, OCR, contacts, prompts or other private content. | **Not ported pending user approval.** Ask whether an explicit Settings opt-in, default OFF, may store raw response locally. Never enable by default. |
| `app/src/main/java/com/openjarvis/agent/LLMResponseValidator.kt` | (1) Generic `repairMalformedSingleActionArray` repairs any single action plus JSON fields. (2) `target` becomes SCROLL direction; `value` becomes ERROR message. (3) steps wrapper, think stripping, target-to-app mapping. | Current validator already supports steps wrappers, think stripping, app target mapping, and a stricter known-shape `repairCertainMixedArray` with an allowlist. The generic repair is broader and can accept ambiguous payloads. The two field mappings were missing. | Port only SCROLL target→direction and ERROR value→message with RED tests first; keep the narrower existing repair. |
| `app/src/main/java/com/openjarvis/llm/providers/OllamaResponseParser.kt` | Adds `supportsStructuredOutputs` and an object/steps JSON schema. | Current parser has richer array action schema plus separate skill/native-intent schemas and bounded output settings. | Keep current schema; no code port. |
| `app/src/main/java/com/openjarvis/llm/providers/Providers.kt` | Caches Ollama version and enables schema for v0.5+. | Current provider checks `/api/version` under a 3-second/request-deadline bound and safely falls back to plain JSON if unsupported/unreachable. | Keep current bounded per-call check; no code port. |

Test files: `LLMResponseValidatorTest.kt` on source branch had 12 tests vs 11 on current baseline; PR #96 adds two regression tests (13 total), retaining all current stricter payload/safety tests. Source `OllamaResponseParserTest.kt` had 13 vs 15 current; no tests copied because current suite already covers its richer schema. Source `AgentDiagnosticsTest.kt` has 3, matching current; raw-response persistence is intentionally not enabled.

PR #96 test-first evidence: before implementation, run #1345 on test-only SHA `750efd4302d968aa0ac8c20200a3b3bb0b9d99b9` failed exactly the two new tests: `mapsStepsSchemaScrollTargetToDirection` and `mapsStepsSchemaErrorValueToUserFacingMessage`. ECC scan passed. Test count guard reported before=647, after=649, delta=+2. The two mappings and summary-only repair diagnostics then landed in commits `127ac05c8acb1f4bff28b878dc40f5d91f0444c0` and `16813eb6c0a2ac704e8d58a4b34c9f1a060b3144`; docs audit in `d2b0e18d991d39a2a2e3ed82fb53fde1f4f11aaa`. Latest post-fix CI is pending verification.


### 4. `feature/jarvis-advanced-hud-hardening` — completed audit

Merge base: `1388daa00ce57458aeb974d70df3d8451ef66845`; source branch is 5 commits ahead / 1017 behind the All-in-One baseline. Three source files and one source test file are changed/added, plus workflow metadata.

| Source file | Branch hunk / behaviour | Classification against current All-in-One | Action |
|---|---|---|---|
| `app/src/main/java/com/openjarvis/agent/AgentCore.kt` | Compress system prompt using HeadroomLite POLICY mode; log character/token savings; pass compressed system context while leaving user command unchanged. | Current AgentCore already runs HeadroomCompressor then HeadroomLite for system prompts; user command remains separate and byte-identical. Current implementation is stronger and includes bounded logging. | No port; keep current hybrid + lite path. |
| `app/src/main/java/com/openjarvis/agent/HeadroomLite.kt` | Introduces strict JSON minification, exact restoration, token estimates and repeated-line RLE. | Current file exists and is newer: multilingual-aware token estimation; TEXT/UI_TEXT/POLICY preserve repeated nonblank lines; only LOG mode uses explicit repeat encoding; malformed JSON and safety payloads are preserved. | No port; source RLE semantics would weaken current preservation guarantees. |
| `app/src/main/java/com/openjarvis/ui/OverlayService.kt` | On VoiceState.Idle, keep HUD in THINKING while AgentCore is Running; otherwise render mute/ready state. | Current service already has the same guard, plus voice-disabled, continuous-session, mute-state and explicit microphone status handling. AgentCore state collector independently updates THINKING/DONE/ERROR. | No port; current state machine is richer. |
| `app/src/test/java/com/openjarvis/agent/HeadroomLiteTest.kt` | 13 tests for JSON, RLE, blank lines, UI and policy preservation. | Current suite has 15 tests, including stronger multilingual token estimation, malformed JSON preservation, and nonblank text/UI/policy preservation. | No test copy; current suite is strictly broader. |

The only other changed file is workflow metadata. No unique app behaviour is missing from All-in-One; no source/test changes are required for this branch.


### 5. `feature/jarvis-report-doctor-safe-recovery` — completed audit

Merge base: `1388daa00ce57458aeb974d70df3d8451ef66845`; source branch is 13 commits ahead / 1017 behind All-in-One. Six app source files plus three test files and workflow metadata are changed.

| Source file | Branch hunk / behaviour | Classification against current All-in-One | Action |
|---|---|---|---|
| `app/src/main/java/com/openjarvis/agent/AgentCore.kt` | Adds HeadroomLite POLICY compression to system prompt while preserving the user's command. | Current AgentCore already applies HeadroomCompressor then HeadroomLite, with bounded LLM deadlines and the command separate. | No port; current implementation is stronger. |
| `app/src/main/java/com/openjarvis/agent/HeadroomLite.kt` | Older Headroom Lite: RLE repeated TEXT/LOG lines and rough ASCII-only token estimate. | Current implementation uses multilingual-aware token estimates, exact restoration, strict JSON validation, and never rewrites repeated nonblank TEXT/UI_TEXT/POLICY lines. | No port; preserve current safer semantics. |
| `app/src/main/java/com/openjarvis/agent/SelfHealingExecutor.kt` | Replaces old 3-attempt LLM-generated alternative-action loop with fail-closed execution: side-effect actions once, bounded WAIT only. | Current executor is a recovery planner, not a model-driven action executor. AgentCore's execution loop uses current `SelfHealingSafetyPolicy`, retries only classified read-only observations, requires evidence-verified recovery, tracks recurrence and never replays side effects. | No port; current architecture is more complete and evidence-based. |
| `app/src/main/java/com/openjarvis/agent/SelfHealingSafetyPolicy.kt` | Allows only WAIT retry (2 attempts, max 5s); rejects model alternatives. | Current policy rejects model alternatives, retains bounded waits, and additionally gates WAIT/WAIT_FOR/READ_SCREEN/EXTRACT_TEXT through failure classification and learned recurrence thresholds. | No port; current policy and 9 tests supersede source branch's 3 tests. |
| `app/src/main/java/com/openjarvis/diagnostics/ReportDoctor.kt` | Offline deterministic categories and user-approved next steps; no automatic setting changes or recovery. | Current file blob is identical to branch. | Already present; no port. |
| `app/src/main/java/com/openjarvis/ui/OverlayService.kt` | Prevents transient VoiceState.Idle from hiding THINKING while AgentCore is Running. | Current file has the same guard plus continuous-session, mic-disabled and command-mute state handling. | Already present; no port. |

Tests: source `SelfHealingSafetyPolicyTest.kt` has 3 tests; current suite has 9, including classified failures, read-only retries, permission/unknown fail-closed behaviour, recurrence pausing and verified recovery reset. Source `ReportDoctorTest.kt` has 8; current is identical (8). Source `HeadroomLiteTest.kt` has 13; current has 15 with stricter preservation and multilingual token metrics. No test was deleted or loosened. All unique source behaviours are already present or superseded; no new tests/code required for this branch.


### 6. `feature/jarvis-m3-m5-proactive-hud` — completed full-scope audit

Compare against All-in-One: 2 commits ahead / 1017 behind; the two unique commits change only `.github/workflows/android-build.yml` and `.github/workflows/main.yml`. There are **no app source or test changes** in those two commits.

The wider M3/M5 feature set from PR #16 was also checked against the current All-in-One tree:

| PR #16 source feature | Current All-in-One path | Result |
|---|---|---|
| Local reminder/daily briefing/battery/charging notifications | `app/src/main/java/com/openjarvis/proactive/ProactiveAssistant.kt` | Present |
| Android-native accessibility bridge | `app/src/main/java/com/openjarvis/automation/AndroidUseBridge.kt` | Present; remains an Android-native bridge, not upstream Python/ADB host runtime |
| Optional LocalAI connector | `app/src/main/java/com/openjarvis/llm/providers/LocalAiProvider.kt` | Present but optional; does not replace local Ollama default or create automatic fallback |
| MobileAgent-compatible adapter | `app/src/main/java/com/openjarvis/vision/MobileAgentClient.kt` | Present; external service only, validated proposals |
| OmniParser-compatible adapter | `app/src/main/java/com/openjarvis/vision/OmniParserClient.kt` | Present; external service only, validated proposals |
| Dashboard/HUD | `app/src/main/java/com/openjarvis/ui/dashboard/DashboardScreen.kt`, `ui/OverlayService.kt` | Present |
| Sherpa ONNX wakeword controller/runtime | `app/src/main/java/com/openjarvis/voice/SherpaOnnxWakeWordController.kt` | Not present in All-in-One and **skipped by explicit user decision**; no wakeword change or model asset import |

Current workflow is newer and has ECC scan, test-count guard, feature/test map, APK build, smoke and release-ready artifact stages. The old branch workflow adds a Sherpa model-preparation step, which is incompatible with the no-wakeword-change decision; its older diagnostics job is not copied. No app feature beyond the excluded Sherpa wakeword is missing.


### 7. `feature/jarvis-phase3-memory` — completed audit and final repository fix

Merge base: `4c40dbbc8d20cd995650255a8bd630613f64e3fc`; source branch is 9 commits ahead / 947 behind All-in-One. All changed source files and hunks:

| Source file | Branch hunk / behaviour | Classification against current All-in-One | Action |
|---|---|---|---|
| `app/src/main/java/com/openjarvis/agent/AgentCore.kt` | Combines user-controlled personal facts and query-relevant episodic task categories into the LLM context. | PR #95 already integrated category-only Graphify context with the current screenshot memory and hard context allocator; personal facts remain separated from action-planning memory as the current privacy design requires. | Already integrated in PR #95; current implementation retained. |
| `app/src/main/java/com/openjarvis/graphify/GraphifyRepository.kt` | Replaces blocking `runBlocking` and raw command/result/contact serialization with suspend IO query and `MemoryContextBuilder.build` (40 tasks, 30-day patterns, confidence ≥0.60). | This hunk was still missing: current legacy helper used `runBlocking` and included raw task commands/results and frequent contact names, although no production callsite remained. | Ported in PR #96 commit `f043a5fcc98db62bfe4a5a27108be5d757a77190`; method is now suspend and delegates to the privacy-filtered builder. |
| `app/src/main/java/com/openjarvis/graphify/MemoryContextBuilder.kt` | Pure category-only recall, 30-day window, successful tasks only, confidence ≥0.60, sensitive intent/phone/email/URL filtering, max 480 chars. | File blob identical to current All-in-One after PR #95. | Already integrated in PR #95; no duplicate. |
| `app/src/main/java/com/openjarvis/headroom/HeadroomCompressor.kt` | Remove only repeated tool/log lines and excess blank formatting; preserve commands, action JSON and safety rules. | File blob identical to current All-in-One after PR #95. | Already integrated in PR #95; no duplicate. |

Tests: `MemoryContextBuilderTest.kt` has 6 tests and `HeadroomCompressorTest.kt` has 6; both current files are byte-identical to source branch tests. Existing tests cover relevant successful categories, sensitive history exclusion, expiry, pattern confidence, unrelated commands, bounded output, and exact command/action/safety preservation. No tests removed or loosened. The Phase 3 acceptance plan was copied to `docs/JARVIS_PHASE3_MEMORY_PLAN.md`.

The remaining physical-device acceptance (personal fact recall, relevant task recall, unrelated query empty, sensitive calling/messaging query empty) is not claimed as complete; CI validates code only.


### 8. Old self-healing branches — source overlap, selective port and security exclusions

The two source branches were compared directly: `feature/self-healing-command-dictionary` is 41 commits behind `feature/autonomous-self-healing-edition` and has no unique commits. Their four requested helper files are byte-identical. Relative to the All-in-One base, the autonomous branch is 87 commits ahead / 1319 behind; the command-dictionary branch is 48 ahead / 1321 behind. Do not merge either branch wholesale: both rewrite the current AgentCore/LLM/provider/Settings/STT stack. The command-dictionary branch also adds Euronics HTML and `EuronicsActivity.kt`; these are explicitly excluded and must never be copied.

| Requested new helper | Source branch path | Current All-in-One | Action |
|---|---|---|---|
| `CommandDictionary` | `app/src/main/java/com/openjarvis/agent/CommandDictionary.kt` | Missing | Add as a new helper; only activate through the experimental Settings switch, default OFF. Keep deterministic exact aliases; unknown commands remain unchanged. |
| `ErrorDiagnosis` | `app/src/main/java/com/openjarvis/agent/ErrorDiagnosis.kt` | Missing | Add as a new pure classifier; permission/security and unknown failures remain non-retryable. Do not use it to weaken current error/permission paths. |
| `RepairMemory` | `app/src/main/java/com/openjarvis/agent/RepairMemory.kt` | Missing | Add as a new helper behind the switch; hash original command keys, bound storage, and save only explicitly verified, non-sensitive corrections. Default OFF. |
| `SelfHealingEngine` | `app/src/main/java/com/openjarvis/agent/SelfHealingEngine.kt` | Missing | Add as a new helper behind the switch; one cooldown-bounded claim only before any device action; never replay a partially executed sequence. Current SelfHealingExecutor remains authoritative. |

Security review — explicitly not ported without separate approval:

- `app/src/main/java/com/openjarvis/bridge/SocketServer.kt`: binds a Java `ServerSocket` to an ephemeral port without binding explicitly to loopback; the file named `jarvis.port` only publishes the port, not an authentication token. `Process.myUid()` reports the server process UID, not the connecting peer UID, so the `allowedUids` check does not authenticate a client. The hard-coded Termux hash is not an OS UID. The protocol exposes task history, provider stats, memory context and arbitrary AgentCore commands; this creates a local-network command and private-data disclosure surface. Do not port until redesigned around authenticated, peer-verified, loopback-only IPC with explicit scopes, request limits and redacted responses.
- Bridge/provider/manifest changes: the source branch changes manifest package visibility and adds network-security configuration. No exported provider, network-security config, socket bridge, bridge activity, or bridge permission change is being copied. Review each exported component and cleartext endpoint separately before approval.
- `app/src/main/java/com/openjarvis/agent/SignedUpdateManager.kt`: the helper verifies a caller-supplied SHA-256 and compares APK signer identity before opening Android's install confirmation UI. The hash is not itself authenticated by a trusted signed manifest; if the expected digest is attacker-controlled, it does not establish update provenance. Installing unknown apps also adds a sensitive permission surface. Do not port until a pinned/trusted release-signing key, signed metadata, version/rollback policy, safe download source and explicit user consent are independently tested.
- `RepairCenter.kt` / patch generation: persists stack traces and sends them to the configured Ollama endpoint; even local logs can contain personal data. It is not part of the four requested helpers and is not ported. No runtime patch application is allowed.

No `SocketServer`, bridge, `SignedUpdateManager`, repair center, patch auto-apply, network-security config, manifest permissions, Euronics assets, or Euronics activities are copied in this batch.


### 9. Selective self-healing helper port — test-first implementation

The RED-only test commit was `a4113f3443ffe9cfcb0e9e08cd4bfca5b70f33e9`. Workflow run #1357 failed at Kotlin test compilation with unresolved references to the six new test targets (`ExperimentalSelfHealingSettings`, `CommandDictionary`, `ErrorDiagnosis`, `RepairMemoryPolicy`, `SelfHealingRetryPolicy`), proving the tests were added before implementation. The ECC scan passed on that test-only commit. Six new tests cover default-off behavior, English/Hindi/Marathi exact aliases, preserving unknown/sensitive commands, retryable timeout vs non-retryable permission errors, hashed/sensitive-filtered verified correction storage, and retry claims only before actions with cooldown.

The implementation adds these new source files only:

| New file | Ported behaviour / safeguards |
|---|---|
| `agent/ExperimentalSelfHealingSettings.kt` | SharedPreferences switch defaults false; only an explicit true enables experimental helpers. |
| `agent/CommandDictionary.kt` | Exact English/Hindi/Marathi aliases. Unknown commands are returned unchanged; app-open normalization requires the entire remainder to exactly match an alias, so a multi-step command is never truncated into an app launch. |
| `agent/ErrorDiagnosis.kt` | Deterministic diagnosis codes and actionable guidance; security/permission, missing model and unknown failures are non-retryable. |
| `agent/RepairMemory.kt` | Hashes original command keys (raw commands are not persisted), bounds the alias map to 100 entries, and only remembers explicitly verified, non-sensitive canonical corrections. Phone/email/URL and sensitive action content are rejected. |
| `agent/SelfHealingEngine.kt` | Persistent cooldown-bounded retry claim, keyed by command/error hashes; refuses non-retryable errors and any retry after device actions have started. It never executes or replays an action. |

Integration deltas are intentionally small: `SettingsScreen.kt` adds the default-OFF toggle; `AgentCore.kt` calls CommandDictionary/RepairMemory only when enabled, records only ErrorDiagnosis category/stage in the local repair memory, and clears a prior retry claim after a fully successful plan. No new automatic retry is wired into execution; the existing SelfHealingExecutor/Retry gates remain authoritative. No changes to banking/send safety, overlay attachment gate, permissions, STT, wakeword, Ollama model/defaults, Euronics assets, or Euronics activities.

The full post-implementation CI result and exact final batch SHA are recorded after run completion below.


## Requested selective branch audit — ordered batch (2026-11-14)

The five branches were checked in the exact requested order using their branch-to-All-in-One per-file diffs. For each file, the branch-side hunk was classified as (a) missing behaviour or (b) an older/superseded implementation. **No missing source behaviour was found in these five branches**, so no source code was copied from the older branches. This is intentional preservation, not a silent skip: each file-level decision is recorded below.

| Order / branch | Changed source files and hunk classification | Result |
|---|---|---|
| 1. screenshot-capture-reliability | `AgentCore.kt`: older command/screenshot orchestration, no screenshot-specific behaviour absent from current flow. `ScreenCapturePermissionActivity.kt`: no branch-only behaviour. `ScreenshotCapture.kt`: current All-in-One already has the permission-denied marker, capture readiness, cleanup and padded-bitmap checks. | Already covered; no code port. |
| 2. false-completion-guard | `AgentCore.kt`: older planning path. `AgentPlanPolicy.kt`: current code has verified completion counts, foreground app-launch verification, required payload checks and a final executable-plan gate. `ScreenCapturePermissionActivity.kt` / `ScreenshotCapture.kt`: no missing permission/capture behaviour. `AgentPlanPolicyTest.kt`: current test suite already checks incomplete action payload rejection. | Already covered; no code port. |
| 3. stable-1.0-phase4-command-execution | `AgentCore.kt`, `Providers.kt`, `AgentDiagnostics.kt`: older steps-only prompt and unbounded adapter path; superseded by current planner, deterministic routing and deadline-bounded provider. `LLMResponseValidator.kt`: current code already extracts wrapped `steps` and maps scroll `target` to direction and error `value` to message. `OllamaResponseParser.kt`: old single steps schema would regress current native-intent/skill/action schemas. | No source port. Added two focused regression tests in PR #96; unit tests pass on `bda79052`. |
| 4. advanced-hud-hardening | `HeadroomLite.kt`: branch compresses TEXT lines; current implementation intentionally preserves TEXT/UI/POLICY and only compresses LOG with explicit markers and restore support. `HeadroomLiteTest.kt`: current tests are broader and preserve exact meaning. `AgentCore.kt` / `OverlayService.kt`: older execution/HUD implementation; overlay branch adds Porcupine/Sherpa wakeword code, which is excluded by the Voicute-only requirement. | Already covered by newer code; no code port. |
| 5. report-doctor-safe-recovery | `ReportDoctor.kt`: identical current implementation. `SelfHealingExecutor.kt`: branch's old WAIT-only retry loop is superseded by current classified read-only dependency verification and learning. `SelfHealingSafetyPolicy.kt`: current policy is stricter and gates WAIT/WAIT_FOR/READ_SCREEN/EXTRACT_TEXT by classified failure; side effects stay one-shot. `AgentCore.kt` / `OverlayService.kt`: older execution/HUD and prohibited wakeword stack. | Already covered by newer safer implementation; no code port. |
| 6. m3-m5-proactive-hud | No app source files changed; only workflow YAML files differ. | No app feature to port; app behaviour already in current HUD. |
| 7. phase3-memory | `MemoryContextBuilder.kt` and `HeadroomCompressor.kt` are already present and byte-identical to the source branch. Existing tests: 6 for each. `AgentCore.kt` / `GraphifyRepository.kt` have newer All-in-One wiring and privacy boundaries. | Already covered; no duplicate source port. |

### Batch evidence and CI

| Batch | SHA tested | ECC | Unit tests / @Test guard | APK | Smoke |
|---|---|---|---|---|---|
| Selective audit + phase-4 regression tests | `bda79052cc59e539f1143470fc4494ba03b6ae11` — PR #96 / run #1367 | PASS | PASS; @Test count guard PASS | PASS | In progress at last check |
| Current All-in-One source baseline for the seven branch audits | `3ff7e627b28fb1321f61501919099b0e5693880d` | PR #95 run #1344: PASS | PASS | PASS | PASS |

No tests were deleted or loosened. No wakeword, banking/send-safety, overlay attachment, permission lifecycle, or Euronics files were changed by this audit.
