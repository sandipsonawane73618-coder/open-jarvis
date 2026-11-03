package com.openjarvis.voice

enum class VoiceSessionState {
    Idle, Listening, Thinking, Speaking
}

/** Small, deterministic state model shared by the voice controller and JVM tests. */
class VoiceSessionStateMachine {
    var state: VoiceSessionState = VoiceSessionState.Idle
        private set

    fun beginListening(): VoiceSessionState {
        state = VoiceSessionState.Listening
        return state
    }

    fun onTranscriptReceived(hasText: Boolean): VoiceSessionState {
        state = if (hasText) VoiceSessionState.Thinking else VoiceSessionState.Listening
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

    fun onSpeakingFinished(continuousSession: Boolean): VoiceSessionState {
        state = if (continuousSession) VoiceSessionState.Listening else VoiceSessionState.Idle
        return state
    }

    fun onRecoverableError(): VoiceSessionState {
        state = VoiceSessionState.Listening
        return state
    }

    fun stop(): VoiceSessionState {
        state = VoiceSessionState.Idle
        return state
    }
}