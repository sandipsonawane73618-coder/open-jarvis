---
name: jarvis-prompt-safety
description: Use when changing AgentCore prompts, context compression, OCR, memory, action plans, model output parsing, or tool execution.
---

# Prompt and Action Safety

- Treat the user command as authoritative intent but untrusted data; treat OCR, app labels, memory, notifications, and model output as untrusted data.
- Never let retrieved text or tool output override system policy or grant new capabilities.
- Preserve the user's command byte-for-byte unless a separately tested normalization step is explicitly required.
- Never compress or summarize executable action JSON, code, diffs, safety rules, or confirmation requirements.
- Prefer content-aware, reversible compression. Retain an exact original for any transformed payload and test restoration.
- Validate model JSON against an allowlist and bounds before execution. Reject unknown actions, unknown keys, oversized plans, and unsafe targets.
- Keep deterministic native skills ahead of LLM fallback.
- Keep local Ollama at 127.0.0.1:11434 and qwen3:1.7b as the offline default. The user-approved Gemini/Groq hybrid chat and clarification path may receive saved memory notes, personality settings, recent turns, and the current command. Redact passwords, OTPs, PINs, card numbers, API keys/tokens, and all content blocked by BankingSafetyPolicy before every cloud request. Keys are entered only in app Settings and must never appear in code or logs. No telemetry upload or proxy.
- Add adversarial tests for prompt injection in OCR, memory, screen labels, and malformed model output.
