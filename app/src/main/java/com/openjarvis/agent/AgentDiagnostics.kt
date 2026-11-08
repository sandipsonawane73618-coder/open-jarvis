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
        val doctor = com.openjarvis.diagnostics.ReportDoctor.diagnose(stage, safeReason)
        val report = buildString {
            appendLine("OPEN JARVIS DEBUG REPORT")
            appendLine("--- REPORT DOCTOR ---")
            appendLine("Area: ${doctor.area} | Severity: ${doctor.severity}")
            appendLine("Diagnosis: ${doctor.summary}")
            appendLine("Likely cause: ${doctor.likelyCause}")
            appendLine("Safe next step: ${doctor.safeNextStep}")
            appendLine("Time: $timestamp")
            appendLine("Stage: $stage")
            appendLine("Provider: $provider")
            appendLine("Model: $model")
            appendLine("Command: [redacted; ${command.length} chars]")
            appendLine("Reason: ${safeReason.take(1200)}")
            if (rawResponse.isNotBlank()) {
                appendLine("--- MODEL RESPONSE SUMMARY ---")
                appendLine(summarizeDiagnosticResponse(rawResponse))
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

    fun recordPipeline(
        context: Context,
        command: String,
        traces: List<StepTrace>,
        skipped: List<String>
    ) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val report = buildString {
            appendLine("OPEN JARVIS DEBUG REPORT")
            appendLine("Time: $timestamp")
            appendLine("Stage: multi-step-pipeline")
            appendLine("Provider: Android deterministic pipeline")
            appendLine("Model: Not used")
            appendLine("Command: [redacted; ${command.length} chars]")
            appendLine("--- STEP ROUTE TRACE ---")
            traces.forEach { trace ->
                appendLine("Step ${trace.index + 1}: ${trace.step.take(180)}")
                appendLine("Route: ${trace.route} | ok=${trace.ok}")
                appendLine("Reply: ${trace.reply.take(500)}")
            }
            if (skipped.isNotEmpty()) {
                appendLine("--- SKIPPED STEPS ---")
                skipped.forEachIndexed { index, step -> appendLine("${traces.size + index + 1}: ${step.take(180)}") }
            }
        }.trim()
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_LATEST, report).apply()
        if (traces.all { it.ok } && skipped.isEmpty()) Log.i(TAG, report) else Log.e(TAG, report)
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
