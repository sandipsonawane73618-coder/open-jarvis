package com.openjarvis.ui

/**
 * Deterministic transcript routing for the single command listener.
 * In muted mode, all recognized speech except the explicit unmute phrase is discarded.
 */
object CommandListeningPolicy {
    enum class Action { IGNORE, MUTE, UNMUTE, EXECUTE_COMMAND }

    fun classify(transcript: String, muted: Boolean): Action {
        if (transcript.isBlank()) return Action.IGNORE
        // Voice-session controls are intercepted in both states. In particular,
        // "awaaz chalu karo" must never fall through and become a volume-up action.
        if (MuteCommandPolicy.isMuteCommand(transcript)) return if (muted) Action.IGNORE else Action.MUTE
        if (MuteCommandPolicy.isUnmuteCommand(transcript)) return Action.UNMUTE
        return if (muted) Action.IGNORE else Action.EXECUTE_COMMAND
    }
}
