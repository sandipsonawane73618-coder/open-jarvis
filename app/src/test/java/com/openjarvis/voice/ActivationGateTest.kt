package com.openjarvis.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivationGateTest {
    @Test fun rejectsBackgroundSpeechWithoutActivation() {
        val gate = ActivationGate()
        assertFalse(gate.validateTranscript("turn on torch", 1_000L).accepted)
    }

    @Test fun acceptsTranscriptInsideWakeWordWindowOnce() {
        val gate = ActivationGate()
        gate.activate(ActivationGate.Source.WAKE_WORD, 1_000L)
        assertTrue(gate.validateTranscript("turn on torch", 9_000L).accepted)
        assertFalse(gate.validateTranscript("turn off torch", 9_100L).accepted)
    }

    @Test fun rejectsTranscriptAfterWindowExpires() {
        val gate = ActivationGate(windowMs = 10_000L)
        gate.activate(ActivationGate.Source.WAKE_WORD, 1_000L)
        val result = gate.validateTranscript("open settings", 11_001L)
        assertFalse(result.accepted)
        assertEquals("activation window expired", result.reason)
    }

    @Test fun rejectsDuplicateTranscriptDuringCooldownEvenAfterNewActivation() {
        val gate = ActivationGate(duplicateCooldownMs = 2_500L)
        gate.activate(ActivationGate.Source.PUSH_TO_TALK, 1_000L)
        assertTrue(gate.validateTranscript("open youtube", 1_500L).accepted)
        gate.activate(ActivationGate.Source.PUSH_TO_TALK, 2_000L)
        val duplicate = gate.validateTranscript(" OPEN   YOUTUBE ", 2_100L)
        assertFalse(duplicate.accepted)
        assertEquals("duplicate transcript cooldown", duplicate.reason)
    }

    @Test fun rejectsEmptyAndNoiseTranscripts() {
        val gate = ActivationGate()
        gate.activate(ActivationGate.Source.AUTO_VAD, 1_000L)
        assertFalse(gate.validateTranscript("  ", 1_100L).accepted)
        gate.activate(ActivationGate.Source.AUTO_VAD, 1_200L)
        assertFalse(gate.validateTranscript("uh", 1_300L).accepted)
    }

    @Test fun explicitPushToTalkActivationIsAccepted() {
        val gate = ActivationGate()
        gate.activate(ActivationGate.Source.PUSH_TO_TALK, 5_000L)
        assertTrue(gate.validateTranscript("volume up", 5_500L).accepted)
    }
}
