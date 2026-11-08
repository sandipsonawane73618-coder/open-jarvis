package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionStateMachineTest {
    @Test fun explicitMicTapStartsOneRecognitionAndTranscriptMovesToThinking() {
        val machine = VoiceSessionStateMachine()
        assertEquals(VoiceSessionState.Idle, machine.state)
        assertEquals(VoiceSessionState.Listening, machine.beginListening())
        assertEquals(VoiceSessionState.Thinking, machine.onTranscriptReceived(true))
    }

    @Test fun emptyRecognitionReturnsToIdleAndDoesNotRearm() {
        val machine = VoiceSessionStateMachine()
        machine.beginListening()
        assertEquals(VoiceSessionState.Idle, machine.onTranscriptReceived(false))
        assertEquals(VoiceSessionState.Idle, machine.state)
    }

    @Test fun completedSpeechReturnsToIdleAndRequiresAnotherMicTap() {
        val machine = VoiceSessionStateMachine()
        machine.onSpeakingStarted()
        assertEquals(VoiceSessionState.Idle, machine.onSpeakingFinished())
        assertEquals(VoiceSessionState.Idle, machine.state)
    }

    @Test fun stopAlwaysReturnsToIdle() {
        val machine = VoiceSessionStateMachine()
        machine.beginListening()
        assertEquals(VoiceSessionState.Idle, machine.stop())
    }
}
