#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

fail() {
  printf 'ECC guardrail FAILED: %s\n' "$1" >&2
  exit 1
}

require_text() {
  local file="$1"
  local text="$2"
  if ! grep -Fq -- "$text" "$file"; then
    fail "required invariant missing from $file: $text"
  fi
}

require_text "app/src/main/java/com/openjarvis/llm/providers/Providers.kt" 'const val DEFAULT_MODEL = "qwen3:1.7b"'
require_text "app/src/main/java/com/openjarvis/llm/providers/Providers.kt" 'const val DEFAULT_BASE_URL = "http://127.0.0.1:11434"'
require_text "app/src/main/java/com/openjarvis/llm/UniversalAdapter.kt" 'return OllamaProvider(baseUrl, model, context.applicationContext)'
require_text "app/src/main/java/com/openjarvis/voice/AndroidSTTEngine.kt" 'SpeechRecognizer.createOnDeviceSpeechRecognizer(context)'

# Continuous voice is allowed only after one explicit user mic action.
# The VoiceManager owns bounded retries/rearm; the STT engine remains one-attempt-per-call.
OVERLAY_FILE="app/src/main/java/com/openjarvis/ui/OverlayService.kt"
STT_FILE="app/src/main/java/com/openjarvis/voice/AndroidSTTEngine.kt"
VOICE_MANAGER_FILE="app/src/main/java/com/openjarvis/voice/VoiceManager.kt"
VOICE_START_COUNT="$(grep -Fc 'voiceManager.startContinuousSession()' "$OVERLAY_FILE")"
[[ "$VOICE_START_COUNT" -eq 1 ]] || fail "expected exactly one explicit mic-button startContinuousSession call in $OVERLAY_FILE; found $VOICE_START_COUNT"
require_text "$VOICE_MANAGER_FILE" 'fun startContinuousSession()'
require_text "$VOICE_MANAGER_FILE" 'fun stopContinuousSession()'
require_text "$VOICE_MANAGER_FILE" 'MAX_RETRY_DELAY_MS = 8_000L'
require_text "$VOICE_MANAGER_FILE" 'mainHandler.postDelayed(rearmRunnable, delay)'

if grep -nE 'scheduleRetry|retryRunnable|retryAttempt|RETRYING:' "$STT_FILE"; then
  fail "STT engine must not own its own retry/rearm loop: $STT_FILE"
fi

if grep -R -nE 'VoiceCute|Voicute|WakeWord|Porcupine|Sherpa|sherpa-onnx|prepare-voicute|prepare-sherpa' app/src/main .github/workflows; then
  fail "wake-word/ONNX runtime or model preparation references remain"
fi

if grep -R -nE 'VoiceCute|Voicute|WakeWord|Porcupine|Sherpa|sherpa-onnx|prepare-voicute|prepare-sherpa' scripts --exclude='ecc-guardrails.sh'; then
  fail "obsolete wake-word/ONNX preparation script remains"
fi

if grep -R -nE 'SpeechRecognizer\.createSpeechRecognizer\(' app/src/main/java/com/openjarvis/voice; then
  fail "network-capable/default SpeechRecognizer factory found in voice package"
fi

if grep -R -nE '@Ignore|@Disabled' app/src/test; then
  fail "ignored or disabled tests are not allowed"
fi

if grep -R -nF '|| true' scripts --exclude='ecc-guardrails.sh'; then
  fail "scripts must not hide failures with || true"
fi

printf 'ECC guardrails PASS: local Ollama/Qwen default, on-device STT, explicit-start continuous voice lifecycle, no wake-word runtime, and test/shell bypass checks verified.\n'
