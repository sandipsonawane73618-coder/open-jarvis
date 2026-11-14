# Jarvis Phase 3 — Memory and Smart Retrieval

## Goal
Build a bounded, local-first memory context that combines user-controlled personal facts with privacy-preserving recall of relevant successful task categories.

## Existing foundation to preserve
- `PersonalMemoryStore`: encrypted local storage, explicit remember/forget controls, max 20 facts, max 180 characters per fact.
- `PersonalMemoryCodec`: deterministic Hindi/Marathi/English token recall, capped at 3 facts / 360 characters.
- Existing Room task and pattern history.
- Existing Qwen3:1.7b / local Ollama path.

## Phase 3 implementation
1. Keep personal facts user-controlled and encrypted; do not auto-promote task history into personal facts.
2. Add a pure `MemoryContextBuilder` that emits only allow-listed task categories, not raw commands/results.
3. Recall only successful task categories from the last 30 days.
4. Recall workflow categories only when confidence is at least 0.60 and the pattern is recent.
5. Exclude contact, calling, messaging, OTP, password, payment, phone-number, email and URL-like content from episodic recall.
6. Return no episodic context for unrelated or unclassified commands.
7. Bound episodic context to 480 characters and combined AgentCore memory context to 900 characters.
8. Replace the old blocking `runBlocking` memory query with a suspend IO query.
9. Keep all retrieval local; no embeddings, network, cloud provider or new model.

## Safety and compatibility
- Do not change wake word, VoiceCute, overlay, TTS, CommandRouter, Accessibility action semantics, action confirmation, or Ollama model defaults.
- Do not include raw contact names, message bodies, screen OCR, provider data, or task results in the LLM memory prompt.
- Do not change the database schema or delete existing history.
- Memory is context only; it cannot authorize or trigger an action.

## Tests added
- Relevant successful task-category recall.
- Failure and unrelated-task exclusion.
- Contact/message privacy exclusion.
- 30-day retention boundary.
- Pattern confidence threshold.
- Output length bound.

## Acceptance gates
- `unit-tests`: PASS.
- `apk-build`: PASS.
- `smoke-test-minimal`: PASS.
- Manual phone validation: saved fact recall, relevant successful task recall, unrelated query receives no episodic context, and sensitive messaging/calling query receives no episodic context.
- Confirm Qwen3:1.7b remains default and no cloud fallback is introduced.

## Not included in Phase 3
- Automatic learning from corrections (Phase 4).
- Self-healing or autonomous action loops (Phase 5).
- Headroom compression runtime integration (separate Phase 2 benchmark/integration track).
