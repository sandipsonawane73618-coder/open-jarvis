# Branch coverage audit

Comparison baseline: `feat/jarvis-all-in-one` at `83186353a0c9f0e01d62d2bb5fc3848b511ad3cc` (2026-11-14).
Scope: every repository branch matching `feature/*`, including the separate Euronics branch.
Policy: do not force-merge diverged branches. Preserve all existing tests; total `@Test` count must never decrease. The three context/memory integrations are test-first and are being carried on the current integration PR. Wakeword-reliability/Sherpa is explicitly skipped.

| branch | feature | already in all-in-one (path) | missing | action |
|---|---|---|---|---|
| `feature/autonomous-self-healing-edition` | Autonomous repair, SocketServer bridge, AnalysisEngine, command dictionary, signed updates | `AgentCore.kt`, `LLMResponseValidator.kt`, `UniversalAdapter.kt`, `Providers.kt` | `SocketServer.kt`, `AnalysisEngine.kt`, `SelfHealingEngine.kt`, `RepairCenter.kt`, `RepairMemory.kt`, `RepairPatchPolicy.kt`, `SignedUpdateManager.kt`, `CommandDictionary.kt`, `ErrorDiagnosis.kt` | **skip + conflict report**; 87 commits ahead / 1298 behind; conflicts across AgentCore, manifest, Gradle, provider, Settings and STT files. Do not force. |
| `feature/euronics-webview-integration` | Separate Euronics WebView feature | Jarvis app baseline only | Euronics assets/activity and app wiring are outside Jarvis scope | **skip**; separate Euronics workstream; do not alter Euronics app or data. |
| `feature/jarvis-accessibility-agent-loop` | Screen evidence and compact repeated OCR context | `accessibility/ScreenEvidenceVerifier.kt`, `agent/AgentDiagnostics.kt`, `ui/OverlayService.kt` | `agent/HeadroomLiteContext.kt`; diagnostic logger also absent | **integrate HeadroomLiteContext test-first**; defer unrelated diagnostic logger and wakeword bundle. |
| `feature/jarvis-action-session-multistep-fix` | Multi-step action session | `agent/AgentCore.kt`, `agent/StepPipeline.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-advanced-hud-hardening` | HUD and HeadroomLite hardening | `agent/HeadroomLite.kt`, `ui/OverlayService.kt` | No new isolated path; branch edits existing implementations | **skip for now**; existing paths have newer All-in-One changes; review deltas rather than overwrite. |
| `feature/jarvis-command-listener-no-wakeword` | Manual command listener | `voice/VoiceManager.kt`, `voice/CommandListeningPolicy.kt`, `ui/OverlayService.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-contact-app-intent-priority` | Contact/app intent priority | `agent/StepIntentParser.kt`, `skills/AndroidSkillExecutor.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-continuous-voice-session` | Continuous voice session | Existing voice session lifecycle in `voice/VoiceManager.kt` | Branch-only workflow-file change | **skip**; no missing app implementation. |
| `feature/jarvis-conversation-memory-marathi` | Marathi/Hinglish language profile | `voice/SpeechLanguage.kt`, `voice/MaharashtraSpeechProfile.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-deterministic-fast-skills` | Deterministic fast skills | `skills/SkillRegistry.kt`, `intelligence/FastCommandParser.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-diagnostic-export` | Diagnostic export | `agent/AgentDiagnostics.kt`, `ui/settings/SettingsScreen.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-false-completion-guard` | False completion guard and truthful app launch reporting | `agent/AgentPlanPolicy.kt`, `agent/AgentCore.kt`, `ui/ScreenCapturePermissionActivity.kt`, `vision/ScreenshotCapture.kt` | None: foreground verification, timeout-as-Error, truthful native launch replies, and screenshot consent wait are already in current All-in-One; current `AgentPlanPolicy` additionally enforces required payloads and excludes COMPLETE from executable plans | **already present; no code port needed**. Existing stricter policy retained; source test in current branch has 14 tests vs 13 on source branch. |
| `feature/jarvis-fast-response-streaming` | Fast response streaming | `agent/AgentCore.kt`, `llm/providers/OllamaProvider.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-foreground-banking-guard` | Foreground banking guard | `agent/BankingSafetyPolicy.kt`, `agent/ActionRiskPolicy.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-full-potential-foundation` | Agent foundation and deterministic routing | `agent/AgentCore.kt`, `agent/StepPipeline.kt`, `agent/AgentPlanPolicy.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-headroom-fidelity` | Headroom Lite fidelity | `agent/HeadroomLite.kt`, `agent/HeadroomContextBudget.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-headroom-hybrid` | Headroom Hybrid conservative context compression | `agent/HeadroomLite.kt`, `agent/AgentCore.kt` | `headroom/HeadroomCompressor.kt` and tests | **integrate HeadroomCompressor test-first**; use the same implementation also referenced by Phase 3 Memory, not duplicate it. |
| `feature/jarvis-jandal-memory-recall` | Local personal-memory recall | `agent/PersonalMemoryStore.kt`, `agent/ScreenshotMemoryStore.kt`, `graphify/GraphifyRepository.kt` | None identified; branch is behind All-in-One | **skip**; existing memory system is already present. |
| `feature/jarvis-keyfree-wakeword` | Key-free wakeword work | `voice/VoicuteOnnxWakeWordController.kt`, `voice/WakeWordDiagnostics.kt` | No accepted missing implementation identified | **skip**; retain the user's Voicute-only wakeword decision; do not add Sherpa or paid engines. |
| `feature/jarvis-long-command-engine-pr17` | Long-command splitting | `agent/CommandSplitter.kt`, `agent/StepPipeline.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-m3-m5-proactive-hud` | Proactive HUD | Existing HUD in `ui/OverlayService.kt` | Branch-only workflow changes | **skip**; no missing app code identified. |
| `feature/jarvis-manual-mic-hud-headroom` | Manual mic HUD and Headroom | `ui/OverlayService.kt`, `agent/HeadroomLite.kt`, `ui/settings/SettingsScreen.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-manual-mic-report-doctor` | Manual mic and Report Doctor | `diagnostics/ReportDoctor.kt`, `ui/settings/SettingsScreen.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-multilingual-volume-routing` | Multilingual volume routing | `skills/SkillRegistry.kt`, `voice/SpeechLanguage.kt` | None identified; branch is behind All-in-One | **skip**; already included; directional reply remains tracked under PR #75. |
| `feature/jarvis-offline-multilingual-stt` | Offline multilingual STT and overlay lifecycle | `voice/AndroidSTTEngine.kt`, `voice/SpeechLanguage.kt`, `ui/OverlayService.kt` | Error 12/13 locale recovery, support check, consented model download and per-locale Settings status | **integrate via PR #95**; its older overlay-view reconciliation overlaps the current `overlayAttachmentGate` and is not copied. |
| `feature/jarvis-opendroid-agent-loop` | OpenDroid agent loop | `agent/AgentCore.kt`, `agent/LLMResponseValidator.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-phase1-p0-security` | Phase 1 security gates | `agent/ActionRiskPolicy.kt`, `agent/BankingSafetyPolicy.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-phase2-continuation` | Diagnostics, voice continuity and context work | `agent/AgentDiagnostics.kt`, `ui/OverlayService.kt`, `ui/settings/SettingsScreen.kt` | `agent/HeadroomLiteContext.kt`; `diagnostics/JarvisDiagnosticLog.kt` absent | **integrate HeadroomLiteContext test-first**; defer unrelated logger and wakeword changes for separate review. |
| `feature/jarvis-phase3-headroom-ecc` | Headroom/ECC diagnostics and OCR context | `agent/AgentDiagnostics.kt`, `ui/OverlayService.kt`, `ui/settings/SettingsScreen.kt` | `agent/HeadroomLiteContext.kt`; `diagnostics/JarvisDiagnosticLog.kt` absent | **integrate HeadroomLiteContext test-first**; do not import bundled wakeword changes. |
| `feature/jarvis-phase3-memory` | Graphify episodic memory and Headroom Hybrid | `graphify/GraphifyRepository.kt`, `agent/AgentCore.kt` | `graphify/MemoryContextBuilder.kt`, `headroom/HeadroomCompressor.kt` | **integrate both test-first**; memory builder must remain category-only and privacy-filtered. |
| `feature/jarvis-report-doctor-safe-recovery` | Safe recovery and Report Doctor | `agent/SelfHealingExecutor.kt`, `agent/SelfHealingSafetyPolicy.kt`, `diagnostics/ReportDoctor.kt` | No new isolated path identified; branch changes existing safety files | **skip for now**; preserve current retry gates and report overlapping deltas instead of overwriting. |
| `feature/jarvis-sanna-memory-learning-audit` | App-task hints, diagnostics and memory learning | `agent/AppTaskHintPolicy.kt`, `agent/AppTaskHintStore.kt`, `agent/AgentDiagnostics.kt` | `agent/HeadroomLiteContext.kt`; diagnostic logger absent | **integrate HeadroomLiteContext test-first**; skip bundled wakeword diagnostics. |
| `feature/jarvis-screenshot-capture-reliability` | Screenshot capture reliability | `ui/ScreenCapturePermissionActivity.kt` (denial tracking), `vision/ScreenshotCapture.kt` (permissionDenied state), `agent/AgentCore.kt` (45s permission wait and bounded frame retries) | None: all three source hunks are already present identically in All-in-One at `3ff7e627`; the two smaller files have matching blob SHAs and AgentCore's `captureBitmapOrThrow` contains the same wait/error/retry block | **already present; no port needed**. Workflow-only change not imported. Preserve current permission lifecycle. |
| `feature/jarvis-self-healing-retry-gates` | Bounded self-healing retry gates | `agent/SelfHealingExecutor.kt`, `agent/SelfHealingSafetyPolicy.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-single-floating-overlay` | Single floating overlay attachment | `ui/OverlayService.kt`, `ui/OverlayAttachmentGate.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-slice-c-router-llm-schema` | Router and LLM schema | `agent/CommandRouter.kt`, `llm/providers/OllamaSkillPlanParser.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-stable-1.0-phase2-wakeword` | Stable wakeword phase | `voice/VoicuteOnnxWakeWordController.kt`, `voice/WakeWordDiagnostics.kt` | No accepted missing implementation identified | **skip**; keep the current Voicute-only engine. |
| `feature/jarvis-stable-1.0-phase3-ollama` | Local Ollama integration | `llm/providers/OllamaProvider.kt`, `agent/AgentCore.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-stable-1.0-phase4-command-execution` | Command execution and response validation | `agent/LLMResponseValidator.kt`, `llm/providers/OllamaResponseParser.kt`, `agent/AgentCore.kt` | No new isolated file; branch-specific changes overlap current implementations | **skip for now**; inspect individual deltas before any selective port. |
| `feature/jarvis-stable-1.0` | Stable 1.0 baseline | `agent/AgentCore.kt`, `agent/LLMResponseValidator.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-streaming-response` | Streaming response | `llm/providers/OllamaProvider.kt`, `agent/AgentCore.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-stt-multilingual-ci` | Multilingual STT CI coverage | `voice/AndroidSTTEngine.kt`, `voice/SpeechLanguage.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-stt-multilingual-fix` | Earlier STT locale fix | `voice/AndroidSTTEngine.kt`, `voice/SpeechLanguage.kt` | Its older retry path is superseded by PR #95's bounded locale policy and model-support flow | **skip**; superseded by PR #95; do not duplicate or regress the new fallback. |
| `feature/jarvis-tts-utterance-callback-isolation` | TTS utterance callback isolation | `voice/AndroidTTSEngine.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-uktam-indic-stt` | Indic model compatibility and voice diagnostics | `voice/IndicModelCompatibility.kt`, `agent/AgentDiagnostics.kt` | No accepted missing non-wakeword feature identified | **skip**; current compatibility implementation exists; do not import its wakeword bundle. |
| `feature/jarvis-uktam-indic-stt-audit` | Indic STT audit and diagnostics | `voice/IndicModelCompatibility.kt`, `agent/AgentDiagnostics.kt` | No accepted missing non-wakeword feature identified | **skip**; current compatibility implementation exists; do not import its wakeword bundle. |
| `feature/jarvis-voice-screen-command-fixes` | Voice screen command fixes | `intelligence/FastCommandParser.kt`, `agent/AgentCore.kt` | None identified; branch is behind All-in-One | **skip**; already included. |
| `feature/jarvis-wakeword-reliability` | Sherpa ONNX wakeword reliability | Existing Voicute controller remains the selected engine | Sherpa controller and Sherpa-specific config/tests | **skip by user decision**; no Sherpa integration. |
| `feature/self-healing-command-dictionary` | Command dictionary and self-healing engine | `AgentCore.kt`, `LLMResponseValidator.kt`, `UniversalAdapter.kt`, `SettingsScreen.kt` | `CommandDictionary.kt`, `ErrorDiagnosis.kt`, `RepairMemory.kt`, `SelfHealingEngine.kt` | **skip + conflict report**; 48 ahead / 1300 behind; branch also changes Euronics assets/activity, manifest, Gradle, Settings and provider stack. |
 
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
