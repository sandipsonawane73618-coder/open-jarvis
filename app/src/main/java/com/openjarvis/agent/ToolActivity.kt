package com.openjarvis.agent

/**
 * Local, privacy-preserving execution timeline adapted from the tool-activity concept
 * in AnaaySampat/jarvis (MIT; see THIRD_PARTY_NOTICES.md).
 *
 * This layer is deliberately observational: listener failures must never change task
 * execution, and raw screen text/message bodies are never stored in activity events.
 */
enum class ActivityStatus {
    QUEUED, RUNNING, WAITING_FOR_APPROVAL, COMPLETED, FAILED, STOPPED, UNCONFIRMED, NOT_RUN
}

enum class ProgressKind { PLAN, TAP, TYPE, SCROLL, OPEN, SEARCH, WAIT, CHECK, STEP }

data class ActivityProgress(
    val sequence: Int,
    val kind: ProgressKind,
    val failed: Boolean = false,
    val atEpochMs: Long
)

data class ToolActivityEvent(
    val id: String,
    val tool: String,
    val title: String,
    val safeInput: String,
    val status: ActivityStatus,
    val atEpochMs: Long,
    val elapsedMs: Long? = null,
    val summary: String = status.defaultSummary(),
    val progress: ActivityProgress? = null
)

fun interface ToolActivityListener {
    fun onEvent(event: ToolActivityEvent)
}

/** Creates safe, user-visible activity metadata. Unknown inputs remain private. */
object ToolActivityFactory {
    private val titles = mapOf(
        "phone_task" to "Run a phone task",
        "web_search" to "Search the web",
        "read_file" to "Read a file",
        "set_reminder" to "Set a reminder",
        "manage_routine" to "Manage a routine",
        "device_control" to "Control the phone",
        "open_application" to "Open an app",
        "tv_control" to "Control the TV",
        "pc_task" to "Run a PC task",
        "consult_agent" to "Consult an agent"
    )

    fun create(
        id: String,
        tool: String,
        numericInput: Map<String, Int> = emptyMap(),
        agent: String? = null,
        atEpochMs: Long = System.currentTimeMillis()
    ): ToolActivityEvent {
        val safeTool = tool.takeIf { it in titles } ?: "unknown_tool"
        val safeInput = when {
            safeTool == "device_control" && numericInput["level"] in 0..100 ->
                "Level: ${numericInput.getValue("level")}%"
            safeTool == "set_reminder" && numericInput["minutes"] in 1..10080 ->
                "Delay: ${numericInput.getValue("minutes")} minutes"
            safeTool == "consult_agent" && agent in setOf("phone", "research", "files", "scheduler", "content") ->
                "Agent: $agent"
            else -> "Inputs kept private"
        }
        return ToolActivityEvent(
            id = id.take(200),
            tool = safeTool,
            title = titles[safeTool] ?: "Unrecognized tool",
            safeInput = safeInput,
            status = ActivityStatus.QUEUED,
            atEpochMs = atEpochMs
        )
    }
}

class ToolActivityEngine(private val listener: ToolActivityListener? = null) {
    fun emit(event: ToolActivityEvent) {
        try {
            listener?.onEvent(event.copy(summary = event.status.defaultSummary()))
        } catch (_: Exception) {
            // UI observers are not allowed to break the action pipeline.
        }
    }

    fun progress(
        previous: ToolActivityEvent,
        sequence: Int,
        kind: ProgressKind,
        failed: Boolean = false,
        atEpochMs: Long = System.currentTimeMillis()
    ): ToolActivityEvent {
        val updated = previous.copy(
            status = ActivityStatus.RUNNING,
            atEpochMs = atEpochMs,
            progress = ActivityProgress(sequence.coerceAtLeast(1), kind, failed, atEpochMs)
        )
        emit(updated)
        return updated
    }

    fun finish(
        previous: ToolActivityEvent,
        status: ActivityStatus,
        startedAtEpochMs: Long,
        finishedAtEpochMs: Long = System.currentTimeMillis()
    ): ToolActivityEvent {
        require(status in setOf(
            ActivityStatus.COMPLETED, ActivityStatus.FAILED, ActivityStatus.STOPPED,
            ActivityStatus.UNCONFIRMED, ActivityStatus.NOT_RUN
        )) { "A terminal activity status is required" }
        val result = previous.copy(
            status = status,
            atEpochMs = finishedAtEpochMs,
            elapsedMs = (finishedAtEpochMs - startedAtEpochMs).coerceAtLeast(0)
        )
        emit(result)
        return result
    }
}

private fun ActivityStatus.defaultSummary(): String = when (this) {
    ActivityStatus.QUEUED -> "Waiting to run."
    ActivityStatus.RUNNING -> "The tool is running."
    ActivityStatus.WAITING_FOR_APPROVAL -> "Waiting for your decision."
    ActivityStatus.COMPLETED -> "The tool's result was confirmed."
    ActivityStatus.FAILED -> "The tool reported a failure."
    ActivityStatus.STOPPED -> "Stopped; check any already-started action."
    ActivityStatus.UNCONFIRMED -> "The outcome is unconfirmed. Check before retrying."
    ActivityStatus.NOT_RUN -> "The call was not run."
}
