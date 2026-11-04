package com.openjarvis.agent

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Local-only, user-copyable diagnostics. User message bodies and API credentials are omitted. */
object AgentDiagnostics {
    private const val PREFS = "jarvis_agent_diagnostics"
    private const val KEY_LATEST = "latest_report"
    private const val TAG = "JarvisDiagnostics"

    fun record(
        context: Context,
        stage: String,
        command: String,
        reason: String,
        rawResponse: String = "",
        error: Throwable? = null,
        provider: String = "Not identified",
        model: String = "Not applicable"
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val safeReason = redactDiagnosticReason(reason, command)
        val report = buildString {
            appendLine("OPEN JARVIS DEBUG REPORT")
            appendLine("Time: $timestamp")
            appendLine("Stage: $stage")
            appendLine("Provider: $provider")
            appendLine("Model: $model")
            appendLine("Command: [redacted; ${command.length} chars]")
            appendLine("Reason: ${safeReason.take(1200)}")
            if (rawResponse.isNotBlank()) {
                appendLine("--- MODEL RESPONSE SUMMARY ---")
                appendLine(summarizeDiagnosticResponse(rawResponse))
                appendLine("--- RAW MODEL RESPONSE (LOCAL DEBUG ONLY) ---")
                appendLine(rawResponse.take(4000))
                if (rawResponse.length > 4000) appendLine("[truncated at 4000 characters]")
            }
            if (error != null) {
                appendLine("--- EXCEPTION ---")
                val stack = Log.getStackTraceString(error)
                    .replace(Regex("(?i)(api[_-]?key|authorization|bearer)\\s*[:=]\\s*[^\\s,}]+"), "$1=[REDACTED]")
                appendLine(stack.take(5000))
            }
        }.trim()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LATEST, report).apply()
        Log.e(TAG, report)
    }

    fun latest(context: Context): String =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LATEST, "No debug log saved yet. Run a command first.").orEmpty()
}

/** Keep useful failure stage/shape while not copying message bodies or prompts. */
internal fun summarizeDiagnosticResponse(rawResponse: String): String {
    val actions = Regex("\\\"action\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .findAll(rawResponse)
        .map { it.groupValues[1] }
        .take(16)
        .toList()
    return buildString {
        append("Response length: ${rawResponse.length} characters")
        if (actions.isNotEmpty()) appendLine().append("Action sequence: ${actions.joinToString(" -> ")}")
        else appendLine().append("Action structure unavailable; response content omitted")
    }
}

internal fun redactDiagnosticReason(reason: String, command: String): String {
    var safe = reason
    if (command.isNotBlank()) safe = safe.replace(command, "[redacted command]", ignoreCase = true)
    safe = safe.replace(
        Regex("(?i)(could not (?:type|click visible text)|timed out waiting for visible text):\\s*[^\\n]+"),
        "$1: [redacted UI text]"
    )
    return safe
}
