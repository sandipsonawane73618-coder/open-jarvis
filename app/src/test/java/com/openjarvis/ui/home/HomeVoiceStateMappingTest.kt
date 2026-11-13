package com.openjarvis.ui.home

import com.openjarvis.voice.VoiceSessionState
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeVoiceStateMappingTest {
    @Test fun disabledVoiceAlwaysMapsToOff() {
        assertEquals(HomeVoiceState.OFF, mapHomeVoiceState(false, VoiceSessionState.Listening, true))
    }

    @Test fun listeningMapsToListening() {
        assertEquals(HomeVoiceState.LISTENING, mapHomeVoiceState(true, VoiceSessionState.Listening))
    }

    @Test fun thinkingAndRunningAgentMapToThinking() {
        assertEquals(HomeVoiceState.THINKING, mapHomeVoiceState(true, VoiceSessionState.Thinking))
        assertEquals(HomeVoiceState.THINKING, mapHomeVoiceState(true, VoiceSessionState.Idle, agentRunning = true))
    }

    @Test fun speakingTakesPriorityOverAgentRunning() {
        assertEquals(HomeVoiceState.SPEAKING, mapHomeVoiceState(true, VoiceSessionState.Speaking, agentRunning = true))
    }

    @Test fun idleMapsToOff() {
        assertEquals(HomeVoiceState.OFF, mapHomeVoiceState(true, VoiceSessionState.Idle))
    }

    @Test fun labelsMatchHomeCopyExactly() {
        assertEquals("JARVIS OFF · TAP TO START", HomeVoiceState.OFF.label)
        assertEquals("MIC ON · LISTENING", HomeVoiceState.LISTENING.label)
        assertEquals("THINKING", HomeVoiceState.THINKING.label)
        assertEquals("JARVIS SPEAKING · tap to interrupt", HomeVoiceState.SPEAKING.label)
    }
}
