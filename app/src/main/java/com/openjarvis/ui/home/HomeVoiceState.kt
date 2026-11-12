package com.openjarvis.ui.home

import com.openjarvis.voice.VoiceSessionState

enum class HomeVoiceState(val label: String) {
    OFF("JARVIS OFF · TAP TO START"),
    LISTENING("MIC ON · LISTENING"),
    THINKING("THINKING"),
    SPEAKING("JARVIS SPEAKING · tap to interrupt")
}

/** Pure presentation mapping; does not mutate or start the voice pipeline. */
fun mapHomeVoiceState(
    voiceEnabled: Boolean,
    voiceState: VoiceSessionState,
    agentRunning: Boolean = false
): HomeVoiceState {
    if (!voiceEnabled) return HomeVoiceState.OFF
    return when {
        voiceState == VoiceSessionState.Speaking -> HomeVoiceState.SPEAKING
        voiceState == VoiceSessionState.Thinking || agentRunning -> HomeVoiceState.THINKING
        voiceState == VoiceSessionState.Listening -> HomeVoiceState.LISTENING
        else -> HomeVoiceState.OFF
    }
}
