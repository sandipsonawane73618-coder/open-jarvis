package com.openjarvis.ui

/**
 * Deterministic transcript routing for the single command listener.
 * In muted mode, all recognized speech except the explicit unmute phrase is discarded.
 */
object CommandListeningPolicy {
    enum class Action { IGNORE, MUTE, UNMUTE, EXECUTE_COMMAND }

    fun classify(transcript: String, muted: Boolean): Action {
        if (transcript.isBlank()) return Action.IGNORE
        return if (muted) {
            if (MuteCommandPolicy.isUnmuteCommand(transcript)) Action.UNMUTE else Action.IGNORE
        } else {
            if (MuteCommandPolicy.isMuteCommand(transcript)) Action.MUTE else Action.EXECUTE_COMMAND
        }
    }
}
