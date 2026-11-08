package com.openjarvis.voice

enum class VoiceSessionState {
    Idle, Listening, Thinking, Speaking
}

/** Deterministic one-shot voice state model; only an explicit mic tap begins Listening. */
class VoiceSessionStateMachine {
    var state: VoiceSessionState = VoiceSessionState.Idle
        private set

    fun beginListening(): VoiceSessionState {
        state = VoiceSessionState.Listening
        return state
    }

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
        state = VoiceSessionState.Idle
        return state
    }

    fun stop(): VoiceSessionState {
        state = VoiceSessionState.Idle
        return state
    }
}
