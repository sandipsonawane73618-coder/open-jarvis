package com.openjarvis.diagnostics

/**
 * Offline, deterministic diagnosis for Jarvis failures.
 *
 * This component only explains a failure and suggests a user-approved check.
 * It never restarts services, changes settings, taps UI, or sends data anywhere.
 */
object ReportDoctor {
    enum class Area {
        MANUAL_LISTENING, MICROPHONE_PERMISSION, SPEECH_RECOGNITION, OVERLAY,
        OLLAMA_CONNECTION, MODEL_RESPONSE, ACCESSIBILITY, APP_LAUNCH,
        ACTION_EXECUTION, UNKNOWN
    }

    enum class Severity { INFO, WARNING, ERROR }

    data class Report(
        val area: Area,
        val severity: Severity,
        val summary: String,
        val likelyCause: String,
        val safeNextStep: String
    )

    /**
     * Inputs must be short diagnostic labels/reasons, not raw commands, transcripts,
     * message bodies, screen OCR, prompts, or provider credentials.
     */
    fun diagnose(stage: String, reason: String): Report {
        val text = "$stage $reason".lowercase()

        return when {
            containsAny(text, "microphone permission", "record_audio", "mic permission", "permission denied") ->
                report(Area.MICROPHONE_PERMISSION, Severity.ERROR,
                    "Microphone access is blocked.",
                    "Android microphone permission is missing or was revoked.",
                    "Open Android App info > Permissions > Microphone and allow access; then start listening with the in-app mic button.")

            containsAny(text, "manual listening", "mic button", "microphone button", "recording did not start", "start listening failed", "audio capture failed") ->
                report(Area.MANUAL_LISTENING, Severity.WARNING,
                    "Jarvis did not start manual listening.",
                    "The mic button flow may not have started audio capture, or another app may be holding the microphone.",
                    "Open Jarvis, tap its mic button once, check the listening indicator, and close other apps currently using the microphone.")

            containsAny(text, "overlay", "floating window", "draw over other apps", "overlay detached") ->
                report(Area.OVERLAY, Severity.WARNING,
                    "The floating Jarvis panel is unavailable.",
                    "Overlay permission may be off or Android may have stopped the foreground component.",
                    "Check Display over other apps permission, reopen Jarvis, and verify the panel remains visible before retrying.")

            containsAny(text, "ollama", "11434", "connection refused", "failed to connect", "socket timeout") ->
                report(Area.OLLAMA_CONNECTION, Severity.ERROR,
                    "The local Ollama server is unreachable.",
                    "Ollama may not be running, or the local endpoint may not be reachable from the app.",
                    "In Termux, check that Ollama is running and listening on 127.0.0.1:11434; do not change the configured default model.")

            containsAny(text, "model not found", "unknown model", "qwen3:1.7b") ->
                report(Area.MODEL_RESPONSE, Severity.ERROR,
                    "The configured local model is unavailable.",
                    "The selected model may not be downloaded or its local tag may differ.",
                    "In Termux, verify the qwen3:1.7b model is installed; keep it as Jarvis's default.")

            containsAny(text, "accessibility service", "accessibility disabled", "node not found", "visible text") ->
                report(Area.ACCESSIBILITY, Severity.WARNING,
                    "Jarvis could not inspect or operate the current screen.",
                    "The accessibility service may be disabled, disconnected, or unable to find the requested control.",
                    "Check Jarvis Accessibility permission in Android Settings, reopen the target app, and retry the action manually first.")

            containsAny(text, "app not installed", "package not found", "launch activity", "app open failed") ->
                report(Area.APP_LAUNCH, Severity.WARNING,
                    "Jarvis could not open the requested app.",
                    "The app may be missing or its installed package name could not be resolved.",
                    "Confirm the app is installed and launch it once manually; then retry using its visible app name.")

            containsAny(text, "speech recogn", "stt", "no speech", "no match", "transcription") ->
                report(Area.SPEECH_RECOGNITION, Severity.WARNING,
                    "Speech was not recognized.",
                    "The on-device recognizer may not be ready, may lack the required language model, or may have received unclear audio.",
                    "Check the selected on-device language support and microphone permission; try a short, clear phrase.")

            containsAny(text, "invalid json", "malformed response", "empty response", "llm response") ->
                report(Area.MODEL_RESPONSE, Severity.WARNING,
                    "The local model returned an unusable response.",
                    "The response may be empty or not match the expected structured format.",
                    "Retry once with a short request; deterministic phone actions should still run without the language model.")

            containsAny(text, "tap failed", "type failed", "action failed", "executor") ->
                report(Area.ACTION_EXECUTION, Severity.WARNING,
                    "The requested action did not complete.",
                    "The target screen or control may have changed, or the action may not be supported in the current context.",
                    "Stop automatic retries, inspect the current screen, and confirm the target before trying again.")

            else ->
                report(Area.UNKNOWN, Severity.INFO,
                    "Jarvis recorded an issue but could not classify it.",
                    "The diagnostic stage or reason does not match a known failure pattern.",
                    "Run the built-in diagnostic export and share only the redacted report; do not include commands, transcripts, or private screen text.")
        }
    }

    private fun containsAny(value: String, vararg needles: String): Boolean =
        needles.any { value.contains(it) }

    private fun report(area: Area, severity: Severity, summary: String, cause: String, next: String) =
        Report(area, severity, summary, cause, next)
}
