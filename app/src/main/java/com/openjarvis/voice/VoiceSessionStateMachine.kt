package com.openjarvis.voice

enum class VoiceSessionState {
    Idle, Listening, Thinking, Speaking
}

/**
 * Deterministic voice-session state model.
 * Continuous mode is enabled only by an explicit user mic action and ends only
 * when the user stops the session or the service is released.
 */
class VoiceSessionStateMachine {
    var state: VoiceSessionState = VoiceSessionState.Idle
        private set

    private var continuousSessionActive = false

    fun beginListening(): VoiceSessionState {
        state = VoiceSessionState.Listening
        return state
    }

    fun beginContinuousSession(): VoiceSessionState {
        continuousSessionActive = true
        return beginListening()
    }

    fun isContinuousSessionActive(): Boolean = continuousSessionActive

    fun onTranscriptReceived(hasText: Boolean): VoiceSessionState {
        state = if (hasText) VoiceSessionState.Thinking else VoiceSessionState.Idle
        return state
    }

    fun onCommandStarted(): VoiceSessionState {
        state = VoiceSessionState.Thinking
        return state
    }

    fun onSpeakingStarted(): VoiceSessionState {
        state = VoiceSessionState.Speaking
        return state
    }

    fun onSpeakingFinished(): VoiceSessionState {
        state = if (continuousSessionActive) VoiceSessionState.Listening else VoiceSessionState.Idle
        return state
    }

    fun stop(): VoiceSessionState {
        continuousSessionActive = false
        state = VoiceSessionState.Idle
        return state
    }
}
