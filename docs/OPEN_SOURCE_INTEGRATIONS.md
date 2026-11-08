# Open-source integration record

This document distinguishes JARVIS-native Android implementations and service adapters from upstream projects that require a separate host/model runtime. No upstream application is copied wholesale into the APK.

## LocalAI — optional OpenAI-compatible LLM connector

- `LocalAiProvider` implements the existing `LLMProvider` contract using LocalAI's `/v1/chat/completions` and `/v1/models` endpoints.
- Supports an optional bearer token and either a server root URL or a URL already ending in `/v1`.
- It is an optional provider class. The active `UniversalAdapter` default remains the user's existing local Ollama / Qwen3 setup; this integration does not silently switch providers.
- LocalAI server deployment and model weights are not bundled into the Android APK.

## android-use — native Accessibility action bridge

- `AndroidUseBridge` exposes bounded screen observation and tap/type/scroll/back/home actions through the existing `JarvisAccessibilityService`.
- This is the Android-native on-device equivalent for the observe/act surface. It does not embed android-use's Python/ADB host runtime.
- Android Accessibility permission remains user-controlled. The bridge does not add unattended messaging/payment actions.

## MobileAgent — external vision-agent adapter

- `MobileAgentClient` sends a JPEG screenshot and task to a separately hosted service and validates the returned action against a strict allow-list (`tap/type/scroll/back/home/wait/done`).
- The adapter's JARVIS wire contract is `{task,image,image_mime_type,response_format}` and a response with `action` plus optional `target/text/rationale`.
- The upstream MobileAgent Python/model runtime and model weights are not embedded in this APK. A compatible endpoint must be configured by a future settings/UI wiring step.

## OmniParser — external visual grounding adapter

- `OmniParserClient` sends a JPEG screenshot to a separately hosted parser and validates normalized element boxes, confidence values, and a 300-element response cap.
- Its JARVIS response contract is `{elements:[{id,label,content_description,box:{x,y,width,height},confidence}]}`; coordinates are normalized to 0..1.
- OmniParser model weights/runtime are not embedded in the APK. A compatible endpoint must be configured before this capability can be used on-device.

## Compatibility and safety decisions

- Keep Java 17 / Gradle 8.2 / compileSdk 34 and the existing app architecture.
- Keep Qwen3 1.7B and Ollama as the active default; all network connectors are optional.
- Vision service calls require explicit configured HTTP(S) endpoints. Local network HTTP may be used for LAN inference; do not expose these endpoints to the public internet without authentication.
- Vision-agent output is only a proposed UI action. It must pass through the existing JARVIS action policy and user confirmation rules before execution.
- No external model runtime is downloaded or started automatically by the APK.