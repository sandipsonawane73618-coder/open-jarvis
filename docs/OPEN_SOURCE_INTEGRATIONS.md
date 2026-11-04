# Open-source integration record

This document records selectively implemented architecture ideas. It does not imply that upstream applications or their source trees were copied into JARVIS.

## Jandal AI — local memory recall

Upstream: https://github.com/NickMonrad/kernel-ai-assistant  
License: Apache-2.0 (see upstream LICENSE)

Integrated in JARVIS:
- Bounded local relevance retrieval over facts the user explicitly saved.
- Query-token overlap ranking with deterministic tie-breaking.
- English, Hindi, and Marathi stop-word handling.
- At most three recalled facts and 360 characters are added to an action-planning prompt.
- Retrieval runs locally; it adds no embedding model, network request, background worker, or resident LLM.
- Existing encrypted SharedPreferences storage and explicit remember/forget controls remain authoritative.

JARVIS implementation:
- `PersonalMemoryCodec.retrieve` and `promptContext(items, query)`
- `AgentCore` injects only relevant saved facts into the planner context.
- `PersonalMemoryRecallTest` covers relevance, multilingual tokens, unrelated-query privacy, and bounds.

This is a JARVIS-native implementation of the local semantic-recall pattern, not copied Jandal source code. It is lexical relevance matching, not vector embeddings; semantic embeddings remain a future optional upgrade after on-device performance testing.

## OpenDroid — guarded plan re-evaluation

Upstream: https://github.com/yashab-cyber/opendroid  
License: Apache-2.0 (see upstream LICENSE)

The current JARVIS branch already contains a native guarded re-evaluation policy in `AgentReEvaluationPolicy`: replanning is restricted to navigation/observation-only steps, and potentially side-effecting completed actions are not replayed. This policy predates this memory-recall integration branch; this change does not replace it with OpenDroid's application or agent loop.

## Compatibility and safety decisions

- Keep the existing Java 17 / Gradle 8.2 / compileSdk 34 toolchain.
- Do not import upstream app modules, provider stacks, databases, or model runtimes wholesale.
- Keep Qwen3 1.7B and the existing Ollama provider path unchanged.
- No automatic memory creation: only explicit user-saved facts can be recalled.
- No external network access or background model loading is introduced.
