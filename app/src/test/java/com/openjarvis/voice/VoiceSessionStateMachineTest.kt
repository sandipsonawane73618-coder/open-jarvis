package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceSessionStateMachineTest {
    @Test fun commandMovesThroughThinkingAndSpeakingBackToListening() {
        val machine = VoiceSessionStateMachine()
        assertEquals(VoiceSessionState.Listening, machine.beginListening())
        assertEquals(VoiceSessionState.Thinking, machine.onTranscriptReceived(true))
        assertEquals(VoiceSessionState.Thinking, machine.onCommandStarted())
        assertEquals(VoiceSessionState.Speaking, machine.onSpeakingStarted())
        assertEquals(VoiceSessionState.Listening, machine.onSpeakingFinished(true))
    }

    @Test fun recoverableRecognizerErrorsKeepContinuousSessionListening() {
        val machine = VoiceSessionStateMachine()
        machine.beginListening()
        assertEquals(VoiceSessionState.Listening, machine.onRecoverableError())
        assertEquals(VoiceSessionState.Listening, machine.onRecoverableError())
        assertEquals(VoiceSessionState.Listening, machine.onRecoverableError())
    }

    @Test fun completedSpeechReturnsToIdleWhenContinuousSessionIsOff() {
        val machine = VoiceSessionStateMachine()
        machine.onSpeakingStarted()
        assertEquals(VoiceSessionState.Idle, machine.onSpeakingFinished(false))
    }

    @Test fun stopAlwaysReturnsToIdle() {
        val machine = VoiceSessionStateMachine()
        machine.beginListening()
        assertEquals(VoiceSessionState.Idle, machine.stop())
    }
}