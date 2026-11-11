# LLMDroid compatibility audit

LLMDroid repository has no verified SPDX/root LICENSE; no source is copied. Its remote Whisper API and alternate wake engines are excluded because Jarvis stays local-only and Voicute-only. No new dependency is added.

This independent pure-Kotlin verifier consumes before/after snapshots supplied by Jarvis ScreenReader; it does not capture screens or execute actions. A target package or expected visible text can produce VERIFIED. A generic package/text change is only OBSERVED_CHANGE, never VERIFIED, to avoid false completion. Keep advisory until device tests validate real navigation, text entry and non-mutating actions.