package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSessionStateMachineTest {
    @Test fun explicitMicTapStartsOneRecognitionAndTranscriptMovesToThinking() {
        val machine = VoiceSessionStateMachine()
        assertEquals(VoiceSessionState.Idle, machine.state)
        assertEquals(VoiceSessionState.Listening, machine.beginListening())
        assertEquals(VoiceSessionState.Thinking, machine.onTranscriptReceived(true))
    }

    @Test fun emptyOneShotRecognitionReturnsToIdle() {
        val machine = VoiceSessionStateMachine()
        machine.beginListening()
        assertEquals(VoiceSessionState.Idle, machine.onTranscriptReceived(false))
        assertEquals(VoiceSessionState.Idle, machine.state)
    }

    @Test fun completedOneShotSpeechReturnsToIdle() {
        val machine = VoiceSessionStateMachine()
        machine.onSpeakingStarted()
        assertEquals(VoiceSessionState.Idle, machine.onSpeakingFinished())
        assertEquals(VoiceSessionState.Idle, machine.state)
    }

    @Test fun continuousSessionRemainsArmedAfterSpeechCompletes() {
        val machine = VoiceSessionStateMachine()
        assertEquals(VoiceSessionState.Listening, machine.beginContinuousSession())
        assertTrue(machine.isContinuousSessionActive())
        machine.onTranscriptReceived(true)
        machine.onCommandStarted()
        machine.onSpeakingStarted()
        assertEquals(VoiceSessionState.Listening, machine.onSpeakingFinished())
        assertTrue(machine.isContinuousSessionActive())
    }

    @Test fun stoppingContinuousSessionDisarmsIt() {
        val machine = VoiceSessionStateMachine()
        machine.beginContinuousSession()
        assertEquals(VoiceSessionState.Idle, machine.stop())
        assertFalse(machine.isContinuousSessionActive())
    }

    @Test fun stopAlwaysReturnsToIdle() {
        val machine = VoiceSessionStateMachine()
        machine.beginListening()
        assertEquals(VoiceSessionState.Idle, machine.stop())
    }
}
